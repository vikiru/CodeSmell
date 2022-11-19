from cpgqls_client import CPGQLSClient, import_code_query
import os
import json

# Execute joern queries to get all the fields of classes with their access modifiers and write to a file.
def get_fields_with_modifiers():

    # Perform queries to get private, public, protected, and static fields.
    query = "cpg.member.isPrivate.code.toJsonPretty"
    result = client.execute(query)
    data = result["stdout"]
    privateFields = clean_json(data)

    query = "cpg.member.isPublic.code.toJsonPretty"
    result = client.execute(query)
    data = result["stdout"]
    publicFields = clean_json(data)

    query = "cpg.member.isProtected.code.toJsonPretty"
    result = client.execute(query)
    data = result["stdout"]
    protectedFields = clean_json(data)

    query = "cpg.member.isStatic.code.toJsonPretty"
    result = client.execute(query)
    data = result["stdout"]
    staticFields = clean_json(data)

    # Load JSON into a python list for each field
    privateArr = json.loads(privateFields)
    publicArr = json.loads(publicFields)
    protectedArr = json.loads(protectedFields)
    staticArr = json.loads(staticFields)
    return privateArr, publicArr, protectedArr, staticArr


# OLD
# Create a custom JSON object of the source code and save it to a JSON file.
def source_code_json_creation():
    # Initial query to get all of the classes within the source code
    query = "cpg.typeDecl.isExternal(false).toJsonPretty"
    result = client.execute(query)
    data = result["stdout"]
    allClasses = json.loads(clean_json(data))

    # Dictionary which will store everything about the source code.
    sourceCodeJSON = {}
    sourceCodeJSON["classes"] = []

    privateArr, publicArr, protectedArr, staticArr = get_fields_with_modifiers()
    modifiers = ["public", "private", "protected", "static", "final"]

    # Go class by class populating sourceCodeJSON
    for currClass in allClasses:
        # Define a dict for each class in the source code
        className = currClass["name"]
        if "$" in className:
            className = className.replace("$", ".")
        currClassDict = {"name": className, "fields": [], "methods": []}

        # Perform queries to get all of the fields and methods of the class
        query = (
            "cpg.typeDecl("
            + '"'
            + className
            + '"'
            + ").astChildren.isMember.toJsonPretty"
        )
        result = client.execute(query)
        data = result["stdout"]
        allFields = json.loads(clean_json(data))

        query = (
            "cpg.typeDecl("
            + '"'
            + className
            + '"'
            + ").astChildren.isMethod.toJsonPretty"
        )
        result = client.execute(query)
        data = result["stdout"]
        allMethods = json.loads(clean_json(data))

        # Create dicts for each field and method of each class
        for currField in allFields:
            fieldCode = currField["code"]
            currFieldDict = {
                "name": currField["name"],
                "modifiers": [],
                "type": currField["typeFullName"],
            }

            # Add the modifiers of the field
            if fieldCode in privateArr:
                currFieldDict["modifiers"].append("private")
            if fieldCode in publicArr:
                currFieldDict["modifiers"].append("public")
            if fieldCode in protectedArr:
                currFieldDict["modifiers"].append("protected")
            if fieldCode in staticArr:
                currFieldDict["modifiers"].append("static")
            currClassDict["fields"].append(currFieldDict)

        for currMethod in allMethods:
            methodName = currMethod["name"]
            currMethodDict = {
                "code": currMethod["code"],
                "methodBody": "",
                "modifiers": [],
                "name": methodName,
                "instructions": [],
                "returnType": "",
            }
            fullMethodCodeDecl = currMethod["code"]
            index = fullMethodCodeDecl.find(methodName)

            if index == -1 and methodName == "<init>":
                newIndex = fullMethodCodeDecl.find("(")
                result = fullMethodCodeDecl[0:newIndex].split()
                if len(result) == 2:
                    currMethodDict["name"] = result[1]
                elif len(result) == 1:
                    currMethodDict["name"] = result[0]
                index = fullMethodCodeDecl.find(currMethodDict["name"])

            modifiersWithReturn = fullMethodCodeDecl[0:index]
            methodBody = fullMethodCodeDecl[index : len(fullMethodCodeDecl)]
            methodBodyArr = []
            methodBodyArr.extend(modifiersWithReturn.split())
            methodBodyArr.append(methodBody)

            # Extract the modifiers, methodBody, and returnType of a method
            for currItem in methodBodyArr:
                if currItem in modifiers:
                    currMethodDict["modifiers"].append(currItem)
                else:
                    if "(" in currItem:
                        currMethodDict["methodBody"] = currItem
                    else:
                        currMethodDict["returnType"] = currItem

            # Execute a query to get the children of each method (aka the instructions and calls)
            query = (
                "cpg.method("
                + '"'
                + methodName
                + '"'
                + ").where(node => node.astParent.isTypeDecl.name("
                + '"'
                + className
                + '"'
                + ")).astChildren.astChildren.toJsonPretty"
            )
            result = client.execute(query)
            data = result["stdout"]
            allInstructions = json.loads(clean_json(data))

            # Go instruction by instruction for each method and append to currMethodDict
            def create_instruction_dict(instruction):
                currInstructionDict = {
                    "_label": instruction["_label"],
                    "code": instruction["code"],
                    "lineNumber": instruction.get("lineNumber", None),
                }
                return currInstructionDict

            currMethodDict["instructions"] = list(
                map(create_instruction_dict, allInstructions)
            )

            # Append the currMethodDict to currClassDict["methods"] list
            currClassDict["methods"].append(currMethodDict)

        # Add the class dict to the main sourceCodeJSON dict
        sourceCodeJSON["classes"].append(currClassDict)

    # Finally, write everything to a file.
    joernResult = json.dumps(sourceCodeJSON)
    write_to_file(joernResult, "sourceCode.json")


# Cleans up json output from joern so that it can be a proper JSON file.
def clean_json(joernResult):
    stringToFind = '"""'
    index = joernResult.find(stringToFind)
    joernResult = joernResult[index : len(joernResult)]
    joernResult = joernResult.replace('"""', "")
    return joernResult


def new_source_code_json_creation():
    query = "cpg.typeDecl.isExternal(false).map(node => (node.name, node.astChildren.isMember.l.map(node => (node, node.astChildren.isModifier.modifierType.l)), node.astChildren.isMethod.l.map(node => (node, node.astChildren.astChildren.l)))).toJsonPretty"
    result = client.execute(query)
    data = result["stdout"]
    allData = json.loads(clean_json(data))
    sourceCodeJSON = {"classes": []}

    def create_class_dict(currClass):
        currClassDict = {
            "name": currClass["_1"].replace("$", "."),
            "fields": currClass["_2"],
            "methods": currClass["_3"],
        }
        return currClassDict

    sourceCodeJSON["classes"] = list(map(create_class_dict, allData))
    joernResult = json.dumps(sourceCodeJSON)
    write_to_file(joernResult, "sourceCode_new.json")


# Write the joern output of a query to a specified filename
def write_to_file(joernResult, filename):
    dirName = "src/main/python/joernFiles/"
    finalName = dirName + filename
    with open(finalName, "w") as f:
        f.write(joernResult)


if __name__ == "__main__":
    server_endpoint = "localhost:8080"
    client = CPGQLSClient(server_endpoint)

    # Eventually replace dirName with a directory which we decide upon
    # which will always store the imported source code
    directory = os.getcwd()
    directory = directory.replace("\\", "//")
    # dirName = directory + "/src/main/python/joernTestProject/src"
    projectName = "analyzedProject"

    ourProjectDir = (
        "C:/Users/viski/OneDrive/Documents/GitHub/CodeSmell/src/main/java/com/CodeSmell"
    )

    # Original directory of where the source code we are analyzing came from. We could use the user's
    # original dir instead of taking .java source files, or just take in .java files as store in a dir
    # originalDir = "D:/SYSC3110/Lab1"
    query = import_code_query(ourProjectDir, projectName)
    result = client.execute(query)
    new_source_code_json_creation()

"""
    import cProfile

    cProfile.run("source_code_json_creation()", "output.dat")

    import pstats
    from pstats import SortKey

    with open("output_time.txt", "w") as f:
        p = pstats.Stats("output.dat", stream=f)
        p.sort_stats("time").print_stats()
    with open("output_calls.txt", "w") as f:
        p = pstats.Stats("output.dat", stream=f)
        p.sort_stats("calls").print_stats()
"""
