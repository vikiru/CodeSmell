import sys
import json
from json.decoder import JSONDecodeError
import platform
from timeit import default_timer as timer
import os
from pathlib import Path
from time import sleep
from cpgqls_client import CPGQLSClient, import_code_query, delete_query
from create_dictionary import *
import logging
import traceback


# Given a result["stdout"], return the resulting object with data from Joern
def clean_json(result):
    index = result.index('"')
    try:
        result_obj = json.loads(json.loads(result[index : len(result)]))
    except JSONDecodeError as e:
        logging.debug("Provided result[stdout]: %s", result)
        logging.debug("Type should be str: %s", type(result))
        handle_error(e)
    return result_obj


# Handle a query and output neccessary info to log file then return a final result object
def handle_query(query, log_dict, is_data_query=True):
    start = timer()
    result = client.execute(query)
    end = timer()
    total_time = end - start
    debug_message = log_dict["debug_msg"]
    error_message = log_dict["error_msg"]
    info_message = log_dict["info_msg"]
    if result[SUCCESS] and result[STDOUT] != "":
        info_message += " Completed in {0} seconds.".format(
            format(total_time, DEC_FORMATTER)
        )
        logging.info(info_message)
        result_obj = result["stdout"]
        if is_data_query:
            result_obj = clean_json(result[STDOUT])
    else:
        debug_message += " " + result
        logging.debug(debug_message)
        handle_error(error_message, result[STDERR])
    return result_obj


# Handle all error situations by logging the error message and joern's error message, if present.
# Followed by deleting the project from /bin/joern-cli/workspace/ and exiting with error code
def handle_error(error_message, stderr=""):
    logging.error(error_message)
    if stderr:
        logging.error(stderr.strip())
    result = client.execute(delete_query(project_name))
    if result[SUCCESS] and result[STDOUT]:
        logging.error(
            "The source code has been successfully removed from Joern, after the experienced error."
        )
    else:
        logging.debug("Delete Project Query Result: %s", result)
        logging.error(
            "Failed to remove project from Joern, after the experienced error"
        )
    exit(1)


# Execute a single query to retrieve all the class names within the source code
def retrieve_all_class_names():
    name_query = 'cpg.typeDecl.isExternal(false).filter(node => !node.name.contains("lambda")).fullName.toJson'

    info_msg = "All class names have been retrieved."
    debug_msg = "Class Name Retrieval Result: "
    error_msg = "Retrieve class names failure for {dir}".format(dir=project_dir)
    log_dict = dict(debug_msg=debug_msg, error_msg=error_msg, info_msg=info_msg)

    class_name_res = handle_query(name_query, log_dict)
    class_names = []
    if class_name_res:
        class_names = [name.replace(NESTED_SEP, DOT_SEP) for name in class_name_res]

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
    info_msg = "The class data for {name} has been retrieved.".format(name=class_name)
    debug_msg = "Class Data Retrieval Result for {name}:".format(name=class_name)
    error_msg = "Retrieve class data failure for {class_name}.".format(
        class_name=class_name
    )
    log_dict = dict(debug_msg=debug_msg, error_msg=error_msg, info_msg=info_msg)
    class_result = handle_query(class_query, log_dict)
    class_dict = []
    if class_result:
        class_dict = class_result[0]
    else:
        logging.debug(class_result)
    return class_dict


def retrieve_single_method_instruction(class_full_name, method_name):
    class_name = return_name_without_package(class_full_name)
    method_instruction_query = """cpg.typeDecl.fullName("{class_full_name}").astChildren.
                                                isMethod.name("{method_name}").ast.filter(node => node.lineNumber != None  
                                                && !node.code.equals("<empty>")).map(node => (node.code, node.label, node.lineNumber)).toJson
                                                """.format(
        class_full_name=class_full_name, method_name=method_name
    )

    info_msg = (
        "The method instructions for {method_name}() have been retrieved.".format(
            method_name=method_name,
        )
    )
    debug_msg = "Method Instruction Retrieval Result for {method_name}():".format(
        method_name=method_name
    )
    error_msg = "Retrieve method instruction data failure for {class_name}.{method_name}().".format(
        class_name=class_name, method_name=method_name
    )
    log_dict = dict(debug_msg=debug_msg, error_msg=error_msg, info_msg=info_msg)
    method_ins_result = handle_query(method_instruction_query, log_dict)
    return method_ins_result


def retrieve_all_method_instructions(class_full_name, class_dict):
    class_name = return_name_without_package(class_full_name)
    all_instruction_query = """cpg.typeDecl.fullName("{class_full_name}").astChildren.isMethod.map(node => (node.name, node.ast.
    filter(node => node.lineNumber != None).l.map(node => (node.code, node.label, node.lineNumber)))).toJson""".format(
        class_full_name=class_full_name
    )
    info_msg = "All method instructions for {class_name} have been retrieved.".format(
        class_name=class_name
    )
    debug_msg = "All Method Instruction Retrieval Result for {class_full_name}:".format(
        class_full_name=class_full_name
    )
    error_msg = "All method instruction retrieve failure for {class_full_name}".format(
        class_full_name=class_full_name
    )
    log_dict = dict(debug_msg=debug_msg, error_msg=error_msg, info_msg=info_msg)
    all_method_ins = handle_query(all_instruction_query, log_dict)
    if all_method_ins:
        method_ins_dict = all_method_ins[0]
        for method in class_dict["_8"]:
            method_name = method["_1"]
            if method_name in method_ins_dict:
                method["_7"] = method_ins_dict[method_name]


# Retrieve the method instructions for every method belonging to every class, one by one
def append_all_method_instructions(source_code_json):
    curr_count = 0
    for class_dict in source_code_json[CLASSES]:
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
            total_method_lines = return_total_method_lines(class_dict)
            if class_dict["_8"] and total_method_lines < 80:
                total_class_start = timer()
                retrieve_all_method_instructions(class_full_name, class_dict)
                total_class_end = timer()
                total_class_time += total_class_end - total_class_start
            elif total_method_lines >= 80:
                for method in class_dict["_8"]:
                    method_name = method["_1"]
                    method_code = method["_2"]
                    # Ignore abstract methods and default constructor methods ('_3' corresponds to lineNumber)
                    if ABSTRACT or NATIVE not in method_code and "_3" in method:
                        total_class_start = timer()
                        method["_7"] = retrieve_single_method_instruction(
                            class_full_name, method_name
                        )
                        total_class_end = timer()
                        total_class_time += total_class_end - total_class_start
                logging.info(
                    "All method instructions for {class_name} have been retrieved. Completed in {total_time} seconds.".format(
                        class_name=class_name,
                        total_time=format(total_class_time, DEC_FORMATTER),
                    )
                )
    return source_code_json


# Retrieve the data from joern neccessary to create a temporary dictionary
# This dictionary contains all of the information from joern as-is without modification, excluding method instructions
def create_joern_json(class_names):
    class_count = 0
    joern_json = {"relations": [], CLASSES: []}
    for class_full_name in class_names:
        class_count += 1
        class_name = return_name_without_package(class_full_name)
        logging.info(
            "Starting retrieval of class data (excluding method instructions) for {name}, class # {current}/{total} classes.".format(
                name=class_name, current=class_count, total=total_classes
            )
        )
        class_dict = retrieve_class_data(class_full_name, class_name)
        class_dict["packageName"] = return_package_name(class_full_name)
        joern_json[CLASSES].append(class_dict)
    return joern_json


def source_code_json_creation(class_names):
    source_code_json = {"relations": [], CLASSES: []}

    class_data_start = timer()
    joern_json = create_joern_json(class_names)
    class_data_end = timer()
    class_data_diff = class_data_end - class_data_start
    logging.info(
        "The data for all classes (excluding method instructions) has been retrieved. Completed in {0} seconds.".format(
            format(class_data_diff, DEC_FORMATTER)
        )
    )

    if joern_json[CLASSES]:
        # Handle deletion of any classes which inherit from something that is external (i.e. not present within java or
        # code base)
        clean_start = timer()
        source_code_json = clean_up_external_classes(joern_json)
        clean_end = timer()
        clean_diff = clean_end - clean_start
        logging.info(
            "All external classes (if any) have been excluded from the source code json. Completed in {0} seconds.".format(
                format(clean_diff, DEC_FORMATTER)
            )
        )

        # Handle retrieval of method instructions
        method_ins_start = timer()
        joern_json = append_all_method_instructions(joern_json)
        method_ins_end = timer()
        method_diff = method_ins_end - method_ins_start
        logging.info(
            "All method instructions for all classes have been retrieved. Completed in {0} seconds".format(
                format(method_diff, DEC_FORMATTER)
            )
        )

        total_query_time = (
            import_diff + name_retrieve_diff + class_data_diff + method_diff
        )
        logging.info(
            "Total elapsed time performing queries to Joern: {0} seconds.".format(
                format(total_query_time, DEC_FORMATTER)
            )
        )

        dict_start = timer()
        # Create dictionaries after getting all data from joern
        source_code_json[CLASSES] = list(
            filter(
                None,
                list(map(create_class_dict, joern_json[CLASSES])),
            )
        )
        dict_end = timer()
        dict_diff = dict_end - dict_start
        logging.info(
            "The source code json dictionary has been created. Completed in {0} seconds.".format(
                format(dict_diff, DEC_FORMATTER)
            )
        )

    return source_code_json


if __name__ == "__main__":
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
        datefmt="%m/%d/%Y %I:%M:%S",
        filename=LOG_FILE,
        filemode="w",
    )

    server_endpoint = "127.0.0.1:" + sys.argv[-1]
    project_dir = sys.argv[-2]
    project_name = "analyzedProject"
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
        program_start_time = timer()

    logging.info("Analyzing project_dir " + project_dir)

    if "Windows" in platform.platform():
        index = project_dir.find(":")
        win_drive = project_dir[0 : index + 1]
        project_dir = project_dir.replace(win_drive, win_drive.upper()).replace(
            "\\", "//"
        )

    # Import the source code to Joern for analyzing.
    import_query = import_code_query(project_dir, project_name)

    info_msg = "The source code has been successfully imported."
    debug_msg = "Source Code Import Result for {dir}:".format(dir=project_dir)
    error_msg = "Source Code Import Failure for {dir}:".format(dir=project_dir)
    import_log_dict = dict(debug_msg=debug_msg, error_msg=error_msg, info_msg=info_msg)

    import_start = timer()
    import_res = handle_query(import_query, import_log_dict, False)
    import_end = timer()
    import_diff = import_end - import_start

    try:
        # Retrieve all class names
        name_retrieve_start = timer()
        class_names = retrieve_all_class_names()
        name_retrieve_end = timer()
        name_retrieve_diff = name_retrieve_end - name_retrieve_start
        total_classes = len(class_names)

        if class_names:
            # Create the source code json representation
            source_code_json = source_code_json_creation(class_names)

            # Output all class dictionaries
            if source_code_json[CLASSES]:
                for class_dict in source_code_json[CLASSES]:
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
                handle_error(
                    "Source code json creation failure, no classes in dictionary"
                )

        # Close and delete the project from user's bin/joern/joern-cli/workspace
        delete_query = delete_query(project_name)
        info_msg = "The source code has been successfully removed from Joern."
        debug_msg = "Source Code Delete Result:"
        error_msg = "Source code deletion failure, could not remove from joern"
        del_log_dict = dict(debug_msg=debug_msg, error_msg=error_msg, info_msg=info_msg)

        del_result = handle_query(delete_query, del_log_dict, False)

        # Output total joern_query execution time to log file
        program_end_time = timer()
        program_diff = program_end_time - program_start_time
        logging.info(
            "Total time taken: {0} seconds.".format(format(program_diff, DEC_FORMATTER))
        )
        # Terminate after everything has been successfully executed
        exit(0)
    except Exception as e:
        full_trace = str(traceback.format_exc())
        handle_error(full_trace)
