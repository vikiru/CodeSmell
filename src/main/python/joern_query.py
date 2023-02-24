import sys
import json
import platform
import time
import os
from pathlib import Path
from time import sleep
from cpgqls_client import CPGQLSClient, import_code_query, delete_query
from create_dictionary import *
import logging


# Execute a single query to retrieve all the class names within the source code
def retrieve_all_class_names():
    query = 'cpg.typeDecl.isExternal(false).filter(node => !node.name.contains("lambda")).fullName.toJson'
    result = client.execute(query)
    class_names = []
    if result["success"] and result["stdout"] != "":
        index = result["stdout"].index('"')
        all_names = json.loads(
            json.loads(result["stdout"][index : len(result["stdout"])])
        )
        class_names = [name.replace("$", ".") for name in all_names]
    else:
        logging.error("Retrieve class names failure")
        logging.error(result["stderr"].strip())
        client.execute(delete_query(project_name))
        exit(1)
    return class_names


# Execute a single query to get all the data of a class
def retrieve_class_data(name):
    class_query = (
        'cpg.typeDecl.isExternal(false).fullName("'
        + name
        + '").map(node => (node.name, node.fullName, node.inheritsFromTypeFullName.l, node.code, '
        "node.lineNumber, node.astChildren.isModifier.modifierType.l, node.astChildren.isMember.l.map("
        "node => (node.name, node.typeFullName, node.code, node.lineNumber, "
        "node.astChildren.isModifier.modifierType.l)), node.filename,"
        "node.astChildren.isMethod.filter(node => "
        '!node.code.contains("<lambda>") && '
        '!node.name.contains("<clinit>")).l.map('
        "node => (node.name, node.code, "
        "node.lineNumber, node.lineNumberEnd, "
        "node.astChildren.isModifier"
        ".modifierType.l, "
        "node.astChildren.isParameter.filter("
        "node => !node.name.contains("
        '"this")).l.map(node => (node.code, '
        "node.name, node.typeFullName)))))).toJson"
    )
    start = time.time()
    result = client.execute(class_query)
    end = time.time()
    if result["success"] and result["stdout"] != "":
        index = result["stdout"].index('"')
        # Returns a list of dictionaries, extract first element of that list
        # joern_class_data = json.loads(
        #    json.loads(result["stdout"][index : len(result["stdout"])])
        # )
        # name = joern_class_data[0]["_1"]
        logging.info(
            "The class data for "
            + name.replace(".", "$")
            + " has been retrieved. Completed in {0} seconds.".format(
                format(end - start, ".2f")
            )
        )
        # class_dict = create_class_dict(joern_class_data[0])
    else:
        logging.error("Retrieve class data failure for " + name)
        logging.error(result["stderr"].strip())
        client.execute(delete_query(project_name))
        exit(1)


def source_code_json_creation(class_names):
    source_code_json = {"relations": [], "classes": []}
    for class_name in class_names:
        source_code_json["classes"].append(retrieve_class_data(class_name))

    # Handle deletion of any classes which inherit from something that is external (i.e. not present within java or
    # code base)
    source_code_json = clean_up_external_classes(source_code_json)
    return source_code_json


if __name__ == "__main__":
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s :: %(levelname)s :: %(message)s",
        datefmt="%m/%d/%Y %I:%M:%S",
        filename="joern_query.log",
        filemode="w",
    )
    total_time = 0

    server_endpoint = "127.0.0.1:" + sys.argv[-1]
    project_dir = sys.argv[-2]
    project_name = "analyzedProject222"

    client = None
    index = 1
    sleep(4)
    while True:
        try:
            client = CPGQLSClient(server_endpoint)
            break
        except OSError:
            print(
                "joern_query :: failed to connect to port "
                + str(sys.argv[-1])
                + "retrying",
                file=sys.stderr,
            )
            sleep(1)

    if client:
        logging.info("joern_query is starting and connected to CPGQLSClient.")
    logging.info("Analyzing project_dir " + project_dir)

    if "Windows" in platform.platform():
        index = project_dir.find(":")
        win_drive = project_dir[0 : index + 1]
        project_dir = project_dir.replace(win_drive, win_drive.upper()).replace(
            "\\", "//"
        )

    # Import the source code to Joern for analyzing.
    start = time.time()
    query = import_code_query(project_dir, project_name)
    result = client.execute(query)
    end = time.time()

    if result["success"] and result["stdout"] != "":
        logging.info(
            "The source code has been successfully imported. Completed in {0} seconds.".format(
                format(end - start, ".2f")
            )
        )
        import_time = end - start
        total_time += import_time

        # Retrieve all the class names within the source code
        start = time.time()
        class_names = retrieve_all_class_names()
        end = time.time()
        if class_names:
            logging.info(
                "The class names within the source code have been retrieved. Completed in {0} seconds.".format(
                    format(end - start, ".2f")
                )
            )
            class_name_retrieval_time = end - start
            total_time += class_name_retrieval_time
        else:
            logging.info("No class names or error in retrieving class names")
            client.execute(delete_query(project_name))
            exit(1)

        # Create the source code json representation
        start = time.time()
        source_code_json = source_code_json_creation(class_names)
        end = time.time()

        if source_code_json["classes"]:
            logging.info(
                "A .json representation of the source code has been created. Completed in {0} seconds.".format(
                    format(end - start, ".2f")
                )
            )
            source_code_json_creation_time = end - start
            total_time += source_code_json_creation_time

            for class_dict in source_code_json["classes"]:
                class_contents = bytes(str(class_dict), "utf-8")
                size_bytes = len(class_contents).to_bytes(
                    4, byteorder=sys.byteorder, signed=True
                )
                print("class content size: ", len(class_contents), file=sys.stderr)
                print("size bytes size: ", file=sys.stderr)
                sys.stdout.buffer.write(size_bytes)
                sys.stdout.buffer.write(class_contents)

            sys.stdout.buffer.write(
                (-1).to_bytes(4, byteorder=sys.byteorder, signed=True)
            )
        else:
            print("joern_query :: Source code json creation failure", file=sys.stderr)

        # Close and delete the project from user's bin/joern/joern-cli/workspace
        start = time.time()
        query = 'delete ("' + project_name + '")'
        result = client.execute(query)
        end = time.time()
        if result["success"] and result["stdout"] != "":
            logging.info(
                "The source code has been successfully removed. Completed in {0} seconds.".format(
                    format(end - start, ".2f")
                )
            )
        logging.info("Total time taken: {0} seconds.".format(format(total_time, ".2f")))
        exit(0)
    else:
        logging.error("Source Code Import Failure for project_dir " + project_dir)
        logging.error(result["stderr"].strip())
        exit(1)
