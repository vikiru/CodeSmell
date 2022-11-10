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

    if "allFiles" in filename:
        joernJsonData = json.loads(joernResult)
        stringToFind = "joernTestProject\\src\\"
        for i in range(0, len(joernJsonData)):
            currItem = joernJsonData[i]
            index = currItem.find(stringToFind)
            print(index)
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
def getFieldsWithModifiers():

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

    # Add the access modifier in front of the field
    if len(privateArr) != 0:
        for i in range(0, len(privateArr)):
            privateArr[i] = "private " + privateArr[i]
    if len(publicArr) != 0:
        for i in range(0, len(privateArr)):
            privateArr[i] = "private " + privateArr[i]
    if len(protectedArr) != 0:
        for i in range(0, len(privateArr)):
            privateArr[i] = "private " + privateArr[i]
    if len(staticArr) != 0:
        for i in range(0, len(privateArr)):
            privateArr[i] = "private " + privateArr[i]

    # Combine lists and seperate by new line and write to a file
    finalArr = privateArr + publicArr + protectedArr + staticArr
    joernResult = ("\n").join(finalArr)
    writeToFile(joernResult, "allFieldsWithModifiers.txt")


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


if __name__ == "__main__":
    # writeAll()
    getFieldsWithModifiers()
