import json
import os
import re
from cpgqls_client import CPGQLSClient, import_code_query


# Cleans up json output from joern so that it can be a proper JSON file.
def clean_json(joernResult):
    stringToFind = '"""'
    index = joernResult.find(stringToFind)
    joernResult = joernResult[index : len(joernResult)]
    joernResult = joernResult.replace('"""', "")
    return joernResult


# Execute a single joern query to get all the data required to create a json representation of the source code.
def source_code_json_creation():
    # Obtain the classes, fields and modifiers, methods and their instructions
    query = (
        "cpg.typeDecl.isExternal(false).map(node => (node.name, node.astChildren.isMember.l.map(node => (node, "
        "node.astChildren.isModifier.modifierType.l)), node.astChildren.isMethod.l.map(node => (node, "
        "node.ast.label.l, node.ast.code.l, node.ast.lineNumber.l)))).toJsonPretty "
    )
    result = client.execute(query)
    allData = json.loads(clean_json(result["stdout"]))

    # For every field, create a dictionary and return it.
    def create_field_dict(currField):
        currFieldDict = {
            "name": currField["_1"]["name"],
            "modifiers": [x.lower() for x in currField["_2"]],
            "type": currField["_1"]["typeFullName"],
        }
        return currFieldDict

    # For every method, create a dictionary and return it.
    def create_method_dict(curr_method):
        # For every method's instruction, create a dictionary and return it.
        def create_instruction_dict(currLabel, currInstruction, currLineNumber):
            # Get the method call in each line of code, if any.
            methodCallPattern = re.compile("([a-zA-Z]*\()")
            calls = methodCallPattern.findall(currInstruction)
            methodCall = ""
            if calls:
                methodCall = calls[0].replace("(", "")

            currInstructionDict = {
                "_label": currLabel,
                "code": currInstruction.replace("\r\n", ""),
                "lineNumber": currLineNumber or "none",
                "methodCall": methodCall,
            }
            return currInstructionDict

        if curr_method["_1"]["code"] == "<empty>":
            return
        else:
            # Get the modifiers, return type and the method body from the full method body provided by Joern.
            modifiersPattern = re.compile("(private|public|protected|static|final)")
            returnTypePattern = re.compile("(^[a-zA-z]*\s)")
            methodNamePattern = re.compile("(^[a-zA-z]*)")
            methodWithReturn = re.sub(
                modifiersPattern, "", curr_method["_1"]["code"]
            ).strip()
            methodReturnType = returnTypePattern.findall(methodWithReturn)
            returnType = ""
            if methodReturnType:
                returnType = methodReturnType[0].strip()
            methodBody = re.sub(returnTypePattern, "", methodWithReturn)
            constructorName = methodNamePattern.findall(methodBody)[0]

            currMethodDict = {
                "code": curr_method["_1"]["code"],
                "methodBody": methodBody,
                "modifiers": modifiersPattern.findall(curr_method["_1"]["code"]),
                "name": curr_method["_1"]["name"].replace("<init>", constructorName),
                # For the instructions,
                # _2 corresponds to the labels,
                # _3 corresponds to the code,
                # _4 corresponds to the lineNumbers
                "instructions": list(
                    map(
                        create_instruction_dict,
                        curr_method["_2"],
                        curr_method["_3"],
                        curr_method["_4"],
                    )
                ),
                "returnType": returnType,
            }
            return currMethodDict

    # For every class, create a dictionary and return it.
    def create_class_dict(currClass):
        currClassDict = {
            "name": currClass["_1"],
            "fields": list(map(create_field_dict, currClass["_2"])),
            "methods": list(
                filter(None, list(map(create_method_dict, currClass["_3"])))
            ),
        }
        return currClassDict

    # Create a dictionary with all of the info about the source code and write it to a .json file.
    sourceCodeJSON = {"classes": list(map(create_class_dict, allData))}
    joernResult = json.dumps(sourceCodeJSON, indent=4)
    write_to_file(joernResult, "sourceCode.json")


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
    dirName = directory + "/src/main/python/joernTestProject/src"
    projectName = "analyzedProject"

    # For testing purposes. Get the path of src/main/java/com/CodeSmell as shown (replace '\\' with '/')
    ourProjectDir = (
        "C:/Users/viski/OneDrive/Documents/GitHub/CodeSmell/src/main/java/com/CodeSmell"
    )

    # Original directory of where the source code we are analyzing came from. We could use the user's
    # original dir instead of taking .java source files, or just take in .java files as store in a dir
    # originalDir = "D:/SYSC3110/Lab1"
    query = import_code_query(ourProjectDir, projectName)
    result = client.execute(query)

    # Create the source code json representation
    source_code_json_creation()

    # Close and delete the project from user's bin/joern/joern-cli/workspace
    query = 'delete ("' + projectName + '")'
    result = client.execute(query)
    if result["success"]:
        print("The source code has been successfully removed.")
