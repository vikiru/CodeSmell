from cpgqls_client import CPGQLSClient, import_code_query
import os

# Connect to the server
server_endpoint = "localhost:8080"
client = CPGQLSClient(server_endpoint)

# Eventually replace dirName with a directory which we decide upon
# which will always store the imported source code
dirName = "src/main/python/joernTestProject/src"
projectName = "analyzedProject"

# Import the code from a specified dir and give it a name
query = import_code_query(dirName, projectName)
result = client.execute(query)

# Perform all defined queries and write all results to respective files.
def writeAll():
    getAndWriteAllMethods()
    getAndWriteAllLiterals()
    getAndWriteAllParameters()
    getAndWriteAllMembers()
    getAndWriteAllCalls()
    getAndWriteAllLocals()
    getAndWriteAllIdentifiers()
    getAndWriteAllArguments()
    getAndWriteAllFiles()

# Write the joern output to a specified filename
def writeToFile(joernResult, filename):
    dirName = "src/main/python/joernFiles/"
    finalName = dirName + filename
    file = open(finalName, "w")
    file.write(joernResult)
    file.close()


# Perform a joern query to get all the methods within the source code and write to a file.
def getAndWriteAllMethods():
    # execute a simple CPGQuery to list all methods in the code
    query = "cpg.method.toList"
    result = client.execute(query)
    allMethods = result["stdout"]
    writeToFile(allMethods, "allMethods.txt")


# Perform a joern query to get all the literals within the source code and write to a file.
def getAndWriteAllLiterals():
    # execute a simple CPGQuery to list all literals in the code
    query = "cpg.literal.toList"
    result = client.execute(query)
    allLiterals = result["stdout"]
    writeToFile(allLiterals, "allLiterals.txt")


# Perform a joern query to get all the parameters within the source code and write to a file.
def getAndWriteAllParameters():
    # execute a simple CPGQuery to list all literals in the code
    query = "cpg.parameter.toList"
    result = client.execute(query)
    allParameters = result["stdout"]
    writeToFile(allParameters, "allParameters.txt")


# Perform a joern query to get all the members within the source code and write to a file.
def getAndWriteAllMembers():
    # execute a simple CPGQuery to list all complex types such as classes, structs, etc
    query = "cpg.member.toList"
    result = client.execute(query)
    allMembers = result["stdout"]
    writeToFile(allMembers, "allMembers.txt")


# Perform a joern query to get all the calls within the source code and write to a file.
def getAndWriteAllCalls():
    # execute a simple CPGQuery to list all calls in the code
    query = "cpg.call.toList"
    result = client.execute(query)
    allCalls = result["stdout"]
    writeToFile(allCalls, "allCalls.txt")


# Perform a joern query to get all the local variables within the source code and write to a file.
def getAndWriteAllLocals():
    # execute a simple CPGQuery to list all local variables in the code
    query = "cpg.local.toList"
    result = client.execute(query)
    allLocalVars = result["stdout"]
    writeToFile(allLocalVars, "allLocalVars.txt")


# Perform a joern query to get all the identifiers within the source code and write to a file.
def getAndWriteAllIdentifiers():
    # execute a simple CPGQuery to list all identifiers (local variables in methods) in the code
    query = "cpg.identifier.toList"
    result = client.execute(query)
    allIdentifiers = result["stdout"]
    writeToFile(allIdentifiers, "allIdentifiers.txt")


# Perform a joern query to get all the arguments within the source code and write to a file.
def getAndWriteAllArguments():
    # execute a simple CPGQuery to list all arguments in the code
    query = "cpg.argument.toList"
    result = client.execute(query)
    allArguments = result["stdout"]
    writeToFile(allArguments, "allArguments.txt")


# Perform a joern query to get all the files within the source code and write to a file.
def getAndWriteAllFiles():
    # execute a simple CPGQuery to list all source files in the code
    query = "cpg.file.toList"
    result = client.execute(query)
    allFiles = result["stdout"]
    writeToFile(allFiles, "allFiles.txt")


if __name__ == "__main__":
    writeAll()
