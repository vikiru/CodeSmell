from cpgqls_client import CPGQLSClient, import_code_query
import os
import json

# Connect to the server
server_endpoint = "localhost:8080"
client = CPGQLSClient(server_endpoint)

# Eventually replace dirName with a directory which we decide upon
# which will always store the imported source code
directory = os.getcwd()
directory = directory.replace("\\", "//")
dirName = directory + "/src/main/python/joernTestProject/src"
projectName = "analyzedProject"

query = import_code_query(dirName, projectName)
result = client.execute(query)

# Perform all defined queries and write all results to respective files.
def writeAll():
    # Perform queries and write the result to a file, like so: getAndWriteJoernResults(query, filename)
    getAndWriteJoernResults("cpg.argument.toJsonPretty", "allArguments.json")
    getAndWriteJoernResults("cpg.call.name.toJsonPretty", "allCalls.json")
    getAndWriteJoernResults("cpg.file.name.toJsonPretty", "allFiles.json")
    getAndWriteJoernResults("cpg.identifier.name.toJsonPretty", "allIdentifiers.json")
    getAndWriteJoernResults("cpg.literal.toJsonPretty", "allLiterals.json")
    getAndWriteJoernResults("cpg.local.name.toJsonPretty", "allLocalVars.json")
    getAndWriteJoernResults("cpg.member.code.toJsonPretty", "allMembers.json")
    getAndWriteJoernResults("cpg.method.fullName.toJsonPretty", "allMethods.json")
    getAndWriteJoernResults("cpg.parameter.name.toJsonPretty", "allParameters.json")


# Write the joern output of a query to a specified filename
def writeToFile(joernResult, filename):
    dirName = "src/main/python/joernFiles/"
    finalName = dirName + filename
    file = open(finalName, "w")
    file.write(joernResult)
    file.close()


# Execute joern queries, cleanup the result and write to a file.
def getAndWriteJoernResults(query, filename):
    # execute a simple CPGQuery
    result = client.execute(query)
    joernResult = result["stdout"]
    joernResult = cleanJson(joernResult)

    # Clean up file names to exclude the path and only contain, for example "AddressBook.java"
    if "allFiles" in filename:
        joernJsonData = json.loads(joernResult)
        stringToFind = "joernTestProject\\src\\"
        for i in range(0, len(joernJsonData)):
            currItem = joernJsonData[i]
            index = currItem.find(stringToFind)
            if index != -1:
                currItem = currItem[index + len(stringToFind) :]
                joernJsonData[i] = currItem
    joernResult = json.dumps(joernJsonData)
    writeToFile(joernResult, filename)


## todo: need to update this for jsonPretty format
def getMethodsWithModifiers(query, filename):
    query = "cpg.method.map(node => (node.code, node.fullName)).toJsonPretty"
    result = client.execute(query)
    joernResult = result["stdout"]
    joernResult = cleanJson(joernResult)
    resultArr = []
    finalArr = []
    for i in range(0, len(resultArr)):
        currItem = resultArr[i]
        currItemArr = currItem.split(":")
        stringToClear = currItemArr[1]
        stringToFind = "main.python.joernTestProject.src."
        index = stringToClear.find(stringToFind)

        if index != -1:
            stringToClear = stringToClear[
                index + len(stringToFind) : len(stringToClear)
            ]

            if "<empty>" not in currItemArr[0]:
                finalStr = currItemArr[0] + ":" + stringToClear
                finalArr.append(finalStr)
    joernResult = ("\n").join(finalArr)
    writeToFile(joernResult, filename)


# Execute joern queries to get all the fields of classes with their access modifiers and write to a file.
def getFieldsWithModifiers(write=False):

    # Perform queries to get private, public, protected, and static fields.
    query = "cpg.member.isPrivate.code.toJsonPretty"
    result = client.execute(query)
    data = result["stdout"]
    privateFields = cleanJson(data)

    query = "cpg.member.isPublic.code.toJsonPretty"
    result = client.execute(query)
    data = result["stdout"]
    publicFields = cleanJson(data)

    query = "cpg.member.isProtected.code.toJsonPretty"
    result = client.execute(query)
    data = result["stdout"]
    protectedFields = cleanJson(data)

    query = "cpg.member.isStatic.code.toJsonPretty"
    result = client.execute(query)
    data = result["stdout"]
    staticFields = cleanJson(data)

    # Load JSON into a python list for each field
    privateArr = json.loads(privateFields)
    publicArr = json.loads(publicFields)
    protectedArr = json.loads(protectedFields)
    staticArr = json.loads(staticFields)

    if write == True:
        # Add the access modifier in front of the field
        if len(privateArr) != 0:
            for i in range(0, len(privateArr)):
                privateArr[i] = "private " + privateArr[i]
        if len(publicArr) != 0:
            for i in range(0, len(privateArr)):
                publicArr[i] = "public " + privateArr[i]
        if len(protectedArr) != 0:
            for i in range(0, len(privateArr)):
                protectedArr[i] = "protected " + privateArr[i]
        if len(staticArr) != 0:
            for i in range(0, len(privateArr)):
                staticArr[i] = "static " + privateArr[i]

        # Combine lists and separate by new line and write to a file
        finalArr = privateArr + publicArr + protectedArr + staticArr
        joernResult = ("\n").join(finalArr)

        writeToFile(joernResult, "allFieldsWithModifiers.txt")
    return privateArr, publicArr, protectedArr, staticArr


# Cleans up jsonPretty output from joern so that it can be a proper JSON file.
def cleanJson(joernResult):
    stringToFind = "["
    index = joernResult.find(stringToFind)
    joernResult = joernResult[index : len(joernResult)]
    stringToFind = ']"'
    index = joernResult.find(stringToFind)
    endLen = len(joernResult) - (index + 1)
    joernResult = joernResult[0 : len(joernResult) - endLen]
    return joernResult


# Create a custom JSON object of the source code and save it to a JSON file.
def sourceCodeJsonCreation():
    # Initial query to get all of the classes within the source code
    query = "cpg.typeDecl.isExternal(false).toJsonPretty"
    result = client.execute(query)
    data = result["stdout"]
    allClasses = json.loads(cleanJson(data))

    # Dictionary which will store everything about the source code.
    sourceCodeJSON = {}
    sourceCodeJSON["classes"] = []

    privateArr, publicArr, protectedArr, staticArr = getFieldsWithModifiers(False)

    # Go class by class populating sourceCodeJSON
    for i in range(0, len(allClasses)):

        # Define a dict for each class in the source code
        currClassDict = {}
        currClass = allClasses[i]
        className = currClass["name"]
        currClassDict["name"] = className
        currClassDict["fields"] = []
        currClassDict["methods"] = []

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
        allFields = json.loads(cleanJson(data))

        query = (
            "cpg.typeDecl("
            + '"'
            + className
            + '"'
            + ").astChildren.isMethod.toJsonPretty"
        )
        result = client.execute(query)
        data = result["stdout"]
        allMethods = json.loads(cleanJson(data))

        # Create dicts for each field and method of each class
        for j in range(0, len(allFields)):
            currFieldDict = {}
            fieldCode = allFields[j]["code"]
            currFieldDict["name"] = allFields[j]["name"]
            currFieldDict["modifiers"] = []
            currFieldDict["type"] = allFields[j]["typeFullName"]

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

        for k in range(0, len(allMethods)):
            currMethodDict = {}
            methodName = allMethods[k]["name"]

            currMethodDict["code"] = allMethods[k]["code"]
            currMethodDict["methodBody"] = ""
            currMethodDict["modifiers"] = []
            currMethodDict["name"] = methodName
            currMethodDict["instructions"] = []
            currMethodDict["returnType"] = ""

            modifiers = ["public", "private", "protected", "static", "final"]
            fullMethodCodeDecl = allMethods[k]["code"]
            index = fullMethodCodeDecl.find(methodName)

            if index == -1 and methodName == "<init>":
                newIndex = fullMethodCodeDecl.find("(")
                result = fullMethodCodeDecl[0:newIndex].split()
                currMethodDict["name"] = result[1]
                index = fullMethodCodeDecl.find(currMethodDict["name"])

            modifiersWithReturn = fullMethodCodeDecl[0:index]
            methodBody = fullMethodCodeDecl[index : len(fullMethodCodeDecl)]
            methodBodyArr = []
            methodBodyArr.extend(modifiersWithReturn.split())
            methodBodyArr.append(methodBody)

            # Extract the modifiers, methodBody, and returnType of a method
            for l in range(0, len(methodBodyArr)):
                currItem = methodBodyArr[l]
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
                + ")).astChildren.astChildren.where(node => node.lineNumber).toJsonPretty"
            )
            result = client.execute(query)
            data = result["stdout"]
            allInstructions = json.loads(cleanJson(data))

            # Go instruction by instruction for each method and append to currMethodDict
            for m in range(0, len(allInstructions)):
                currInstructionDict = {}
                currInstructionDict["_label"] = allInstructions[m]["_label"]
                currInstructionDict["code"] = allInstructions[m]["code"]
                currInstructionDict["lineNumber"] = allInstructions[m]["lineNumber"]
                currMethodDict["instructions"].append(currInstructionDict)

            # Append the currMethodDict to currClassDict["methods"] list
            currClassDict["methods"].append(currMethodDict)

        # Add the class dict to the main sourceCodeJSON dict
        sourceCodeJSON["classes"].append(currClassDict)

    # Finally, write everything to a file.
    joernResult = json.dumps(sourceCodeJSON)
    writeToFile(joernResult, "sourceCode.json")


if __name__ == "__main__":
    #writeAll()
    #getFieldsWithModifiers()
    sourceCodeJsonCreation()
