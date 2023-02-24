import sys
import json
from json.decoder import JSONDecodeError
import platform
import time
import os
from pathlib import Path
from time import sleep
from cpgqls_client import CPGQLSClient, import_code_query, delete_query
from create_dictionary import *
import logging


# Given a result["stdout"], return the resulting object
def clean_json(result):
    index = result.index('"')
    try:
        result_obj = json.loads(json.loads(result[index : len(result)]))
    except JSONDecodeError:
        logging.debug("Provided result[stdout]: %s", result)
        logging.debug("Type should be str: %s", type(result))
        handle_errors("JSON Decode Error")
    return result_obj


# Handle all error situations by logging the error message and joern's error message, if present.
# Followed by deleting the project from /bin/joern-cli/workspace/ and exiting with error code
def handle_errors(error_message, stderr=""):
    logging.error(error_message)
    if stderr:
        logging.error(stderr.strip())
    result = client.execute(delete_query(project_name))
    logging.debug("Delete Project Query Result: %s", result)
    if result["success"] and result["stdout"]:
        logging.error(
            "The source code has been successfully removed from Joern, after the experienced error."
        )
    exit(1)


# Execute a single query to retrieve all the class names within the source code
def retrieve_all_class_names():
    query = 'cpg.typeDecl.isExternal(false).filter(node => !node.name.contains("lambda")).fullName.toJson'
    result = client.execute(query)
    logging.debug("Class Name Retrieval Result: %s", result)
    class_names = []
    if result["success"] and result["stdout"] != "":
        all_names = clean_json(result["stdout"])
        class_names = [name.replace("$", ".") for name in all_names]
    else:
        handle_errors(
            "Retrieve class names failure for {dir}".format(dir=project_dir),
            result["stderr"],
        )
    return class_names


# Execute a single query to get all the data of a class
def retrieve_class_data(full_name, class_name):
    class_query = """cpg.typeDecl.isExternal(false).fullName("{full_name}").map(node => (node.name, node.fullName, 
                node.inheritsFromTypeFullName.l, node.code,node.lineNumber, node.astChildren.isMember.l.map(
                node => (node.name, node.typeFullName, node.lineNumber, node.astChildren.isModifier.modifierType.l)), 
                node.filename, node.astChildren.isMethod.filter(node => !node.code.contains("<lambda>") 
                && !node.name.contains("<clinit>")).l.map(node => (node.name, node.code, node.lineNumber, node.lineNumberEnd, 
                node.astChildren.isModifier.modifierType.l, node.astChildren.isParameter.filter(node => 
                !node.name.contains("this")).l.map(node => (node.code, node.name, node.typeFullName)))))).toJson
                """.format(
        full_name=full_name
    )
    start = time.time()
    result = client.execute(class_query)
    logging.debug(
        "Class Data Retrieval Result for {name}: %".format(name=class_name), result
    )
    end = time.time()
    if result["success"] and result["stdout"] != "":
        joern_class_data = clean_json(result["stdout"])
        logging.info(
            "The class data for {name}".format(name=class_name)
            + " has been retrieved. Completed in {0} seconds.".format(
                format(end - start, ".2f")
            )
        )
        class_dict = joern_class_data[0]
    else:
        handle_errors(
            "Retrieve class data failure for {class_name}".format(full_name=full_name),
            result["stderr"],
        )
    return class_dict


# Retrieve the method instructions for every method belonging to every class, one by one
def retrieve_all_method_instructions(source_code_json):
    curr_count = 0
    for class_dict in source_code_json["classes"]:
        class_name = class_dict["_1"]
        class_full_name = class_dict["_2"]
        class_declaration = class_dict["_4"]

        total_class_time = 0
        curr_count += 1
        logging.info(
            "Starting retrieval of method instructions for {name}, class # {current}/{total} classes.".format(
                name=class_name, current=curr_count, total=total_classes
            )
        )
        # Ignore interfaces
        if INTERFACE not in class_declaration:
            for method in class_dict["_8"]:
                method_name = method["_1"]
                method_code = method["_2"]
                # Ignore abstract methods and default constructor methods ('_3' corresponds to lineNumber)
                if "abstract" not in method_code and "_3" in method:
                    method_instruction_query = """cpg.typeDecl.fullName("{class_full_name}").astChildren.
                                                isMethod.name("{method_name}").ast.filter(node => node.lineNumber != None  
                                                && !node.code.equals("<empty>")).map(node => (node.code, node.label, node.lineNumber)).toJson
                                                """.format(
                        class_full_name=class_full_name, method_name=method_name
                    )
                    start = time.time()
                    result = client.execute(method_instruction_query)
                    logging.debug(
                        "Method Instruction Retrieval Result for {method_name}(): %s".format(
                            class_name=class_name, method_name=method_name
                        ),
                        result,
                    )
                    end = time.time()
                    difference = end - start
                    total_class_time += difference
                    if result["success"] and result["stdout"] != "":
                        logging.info(
                            "The method instructions for {method_name}() have been retrieved. Completed in {time} seconds.".format(
                                class_name=class_name,
                                method_name=method_name,
                                time=format(difference, ".2f"),
                            )
                        )
                        # Add the instructions to the method
                        method["_7"] = clean_json(result["stdout"])
                    else:
                        handle_errors(
                            "Retrieve method instruction data failure for {class_name}.{method_name}().".format(
                                class_name=class_name, method_name=method_name
                            ),
                            result["stderr"],
                        )
            logging.info(
                "All method instructions for {class_name} have been retrieved. Completed in {total_time} seconds.".format(
                    class_name=class_name,
                    total_time=format(total_class_time, ".2f"),
                )
            )
    return source_code_json


def source_code_json_creation(class_names):
    source_code_json = {"relations": [], "classes": []}
    joern_json = source_code_json.copy()

    class_data_start = time.time()
    class_count = 0
    # Handle retrieving of class data (except all method instructions)
    for class_full_name in class_names:
        class_count += 1
        class_name = return_name_without_package(class_full_name)
        logging.info(
            "Starting retrieval of class data (excluding method instructions) for {name}, class # {current}/{total} classes.".format(
                name=class_name, current=class_count, total=total_classes
            )
        )
        class_dict = retrieve_class_data(class_full_name, class_name)
        class_dict["packageName"] = return_package_name(class_dict)
        joern_json["classes"].append(class_dict)

    class_data_end = time.time()
    class_data_diff = class_data_end - class_data_start
    logging.info(
        "The data for all classes (excluding method instructions) has been retrieved. Completed in {0} seconds.".format(
            format(class_data_diff, ".2f")
        )
    )

    if joern_json["classes"]:
        # Handle deletion of any classes which inherit from something that is external (i.e. not present within java or
        # code base)
        clean_start = time.time()
        source_code_json = clean_up_external_classes(joern_json)
        clean_end = time.time()
        clean_diff = clean_end - clean_start
        logging.info(
            "All external classes (if any) have been excluded from the source code json. Completed in {0} seconds.".format(
                format(clean_diff, ".2f")
            )
        )

        # Handle retrieval of method instructions
        method_ins_start = time.time()
        joern_json = retrieve_all_method_instructions(joern_json)
        method_ins_end = time.time()
        method_diff = method_ins_end - method_ins_start
        logging.info(
            "All method instructions for all classes have been retrieved. Completed in {0} seconds".format(
                format(method_diff, ".2f")
            )
        )

        total_query_time = (
            import_diff + name_retrieve_diff + class_data_diff + method_diff
        )
        logging.info(
            "Total elapsed time performing queries to Joern: {0} seconds.".format(
                format(total_query_time, ".2f")
            )
        )

        dict_start = time.time()
        # Create dictionaries after getting all data from joern
        source_code_json["classes"] = list(
            filter(
                None,
                list(map(create_class_dict, joern_json["classes"])),
            )
        )
        dict_end = time.time()
        dict_diff = dict_end - dict_start
        logging.info(
            "The source code json dictionary has been created. Completed in {0} seconds.".format(
                format(dict_diff, ".2f")
            )
        )

    return source_code_json


if __name__ == "__main__":
    LOG_FILE = "joern_query.log"
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s :: %(levelname)s :: %(message)s",
        datefmt="%m/%d/%Y %I:%M:%S",
        filename=LOG_FILE,
        filemode="w",
    )

    server_endpoint = "127.0.0.1:" + sys.argv[-1]
    project_dir = sys.argv[-2]
    project_name = "analyzedProject2"
    program_start_time = 0

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
        program_start_time = time.time()

    logging.info("Analyzing project_dir " + project_dir)

    if "Windows" in platform.platform():
        index = project_dir.find(":")
        win_drive = project_dir[0 : index + 1]
        project_dir = project_dir.replace(win_drive, win_drive.upper()).replace(
            "\\", "//"
        )

    # Import the source code to Joern for analyzing.
    import_start = time.time()
    query = import_code_query(project_dir, project_name)
    result = client.execute(query)
    import_end = time.time()
    import_diff = import_end - import_start

    if result["success"] and result["stdout"] != "":
        logging.info(
            "The source code has been successfully imported. Completed in {0} seconds.".format(
                format(import_diff, ".2f")
            )
        )

        # Retrieve all the class names within the source code
        name_retrieve_start = time.time()
        class_names = retrieve_all_class_names()
        total_classes = len(class_names)
        name_retrieve_end = time.time()
        name_retrieve_diff = name_retrieve_end - name_retrieve_start

        if class_names:
            logging.info(
                "The class names within the source code have been retrieved. Completed in {0} seconds.".format(
                    format(name_retrieve_diff, ".2f")
                )
            )
        else:
            logging.debug("Import Code Query Result: %s", result)
            handle_errors(
                "No class names were retrieved from Joern. Potentially empty directory."
            )

        # Create the source code json representation
        start = time.time()
        source_code_json = source_code_json_creation(class_names)
        end = time.time()

        if source_code_json["classes"]:
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
            logging.debug("Source Code JSON Dictionary: %s", source_code_json)
            handle_errors("Source code json creation failure, no classes in dictionary")

        # Close and delete the project from user's bin/joern/joern-cli/workspace
        start = time.time()
        query = delete_query(project_name)
        result = client.execute(query)
        end = time.time()
        if result["success"] and result["stdout"] != "":
            logging.info(
                "The source code has been successfully removed from Joern. Completed in {0} seconds.".format(
                    format(end - start, ".2f")
                )
            )
        else:
            logging.debug("Source Code Delete Result: %s", result)
            handle_errors(
                "Source code deletion failure, could not remove from joern",
                result["stderr"],
            )

        # Output total joern_query execution time to log file
        program_end_time = time.time()
        program_diff = program_end_time - program_start_time
        logging.info(
            "Total time taken: {0} seconds.".format(format(program_diff, ".2f"))
        )

        exit(0)
    else:
        handle_errors("Source Code Import Failure")
