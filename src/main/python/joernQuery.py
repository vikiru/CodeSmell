from cpgqls_client import CPGQLSClient, import_code_query
import os
import re

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
    getAndWriteJoernResults("cpg.argument.toJson", "allArguments.txt")
    getAndWriteJoernResults("cpg.call.name.toJson", "allCalls.txt")
    getAndWriteJoernResults("cpg.file.name.toJson", "allFiles.txt")
    getAndWriteJoernResults("cpg.identifier.name.toJson", "allIdentifiers.txt")
    getAndWriteJoernResults("cpg.literal.toJson", "allLiterals.txt")
    getAndWriteJoernResults("cpg.local.name.toJson", "allLocalVars.txt")
    getAndWriteJoernResults("cpg.member.name.toJson", "allMembers.txt")
    getAndWriteJoernResults("cpg.method.fullName.toJson", "allMethods.txt")
    getAndWriteJoernResults("cpg.parameter.name.toJson", "allParameters.txt")


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

    if "name" not in query:
        writeToFile(joernResult, filename)
        return

    # Cleanup joern result so everything is on a new line, without quotations
    start = "= "
    index = joernResult.find(start)
    joernResult = joernResult[index + 1 : len(joernResult)]
    joernResult = joernResult[3 : len(joernResult) - 3]

    if filename != "allFiles.txt":
        joernResult = joernResult.replace("\\", "")
        joernResult = joernResult.replace('",', "\n")
        joernResult = joernResult.replace('"', "")
    else:
        joernResult = joernResult.replace("\\", "")
        joernResult = joernResult.replace('",', "\n")
        joernResult = joernResult.replace('"', "")
        resultArr = joernResult.splitlines()

        # Ensure that only the class name and the file type is left (i.e. "Class.java")
        searchStr = "joernTestProjectsrc"
        for i in range(0, len(resultArr)):
            index = resultArr[i].find(searchStr)
            if index != -1:
                resultArr[i] = resultArr[i][index + len(searchStr) :]
        joernResult = ("\n").join(resultArr)
    writeToFile(joernResult, filename)


if __name__ == "__main__":
    writeAll()
