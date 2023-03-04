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


def clean_json(result):
    """Given a result["stdout"], return the resulting object with data from Joern"""

    if '"' in result:
        index = result.index('"')
        try:
            result_obj = json.loads(json.loads(result[index : len(result)]))
        except JSONDecodeError as e:
            debug_logger.debug("Provided result[stdout]: %s", result)
            debug_logger.debug("Type should be str: %s", type(result))
            handle_error(e)
        return result_obj


def handle_query(query, log_dict, is_data_query=True):
    """Handle a query and output neccessary info to log file then return a final result object"""

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
        main_logger.info(info_message)
        result_obj = result["stdout"]
        if is_data_query:
            result_obj = clean_json(result[STDOUT])
    else:
        debug_logger.debug("Query used: \n{query}".format(query=query))
        debug_message += " {result}".format(result=result)
        debug_logger.debug(debug_message)
        handle_error(error_message, result[STDERR])
    return result_obj


def handle_error(error_message, stderr=""):
    """Handle all error situations by logging the error message and joern's error message, if present.
    Followed by deleting the project from /bin/joern-cli/workspace/ and exiting with error code."""

    main_logger.error(error_message)
    if stderr:
        main_logger.error(stderr.strip())
    result = client.execute(delete_query(project_name))
    if result[SUCCESS] and result[STDOUT]:
        main_logger.info(
            "The source code has been successfully removed from Joern, after the experienced error."
        )
    else:
        debug_logger.debug("Delete Project Query Result: %s", result)
        main_logger.error(
            "Failed to remove project from Joern, after the experienced error"
        )
    exit(1)


def retrieve_all_class_names():
    """Execute a single query to retrieve all the class names within the source code"""

    name_query = """cpg.typeDecl.isExternal(false).filter(node => !node.name.contains("lambda")).
    map(node => (node.fullName, node.ast.size)).toJson"""

    info_msg = "All class names have been retrieved."
    debug_msg = "Class Name Retrieval Result: "
    error_msg = "Retrieve class names failure for {dir}".format(dir=project_dir)
    log_dict = dict(debug_msg=debug_msg, error_msg=error_msg, info_msg=info_msg)

    class_name_res = handle_query(name_query, log_dict)
    class_names = []
    if class_name_res:
        # Sort by the size of ast of each class (ascending order)
        unsorted_dict = {
            key: val for name in class_name_res for key, val in name.items()
        }
        sorted_tuple = sorted(unsorted_dict.items(), key=lambda entry: entry[1])
        name_ast_dict = dict((key, val) for key, val in sorted_tuple)
        class_names = [*name_ast_dict]
    return class_names, name_ast_dict


def retrieve_class_data(full_name, retrieve_ins=False):
    """Execute a single query to get all the data of a class. If retrieve_ins is True, additionally
    retrieve the instructions of all methods of the class, if not class data without instructions is retrieved."""

    name_without_sep = full_name.replace(NESTED_SEP, DOT_SEP)
    ins_retrieve = ""
    without_ins = ""
    if retrieve_ins:
        ins_retrieve = """, node.ast.filter(node => node.lineNumber != None).l.
        map(node => (node.code, node.label, node.lineNumber))"""
    else:
        without_ins = " (excluding instructions)"

    class_query = """cpg.typeDecl.isExternal(false).fullName("{full_name}").map(node => (node.name, 
    node.fullName, node.filename, node.inheritsFromTypeFullName.l, node.code, node.lineNumber, 
    node.astChildren.isMember.l.map(node => (node.name, node.typeFullName, 
    node.lineNumber, node.astChildren.isModifier.modifierType.l)), 
    node.astChildren.isMethod.isExternal(false).filter(node => !node.code.contains("<lambda>") && 
    !node.name.contains("<clinit>")).l.map(node => (node.name, node.fullName, node.code, node.lineNumber, 
    node.lineNumberEnd, node.astChildren.isModifier.modifierType.l, node.ast.isCall.filter(node => 
    !node.methodFullName.contains("<operator>") && !node.code.contains("<lambda>") && 
    !node.code.contains("<empty>") || node.name.contains("Exception")).l.
    map(node => (node.methodFullName, node.lineNumber)){ins_retrieve})))).toJson""".format(
        full_name=name_without_sep, ins_retrieve=ins_retrieve
    )
    class_name = return_name_without_package(full_name)
    info_msg = "The class data for {name}{without_ins} has been retrieved.".format(
        name=class_name, without_ins=without_ins
    )
    debug_msg = "Class Data Retrieval Result for {name}:".format(name=class_name)
    error_msg = "Retrieve class data failure for {class_name}.".format(
        class_name=class_name
    )
    log_dict = dict(debug_msg=debug_msg, error_msg=error_msg, info_msg=info_msg)
    class_result = handle_query(class_query, log_dict)
    class_dict = []
    if class_result:
        class_dict = class_result[0]
        class_dict["hasInstructions"] = retrieve_ins
    else:
        debug_logger.debug(class_result)
    return class_dict


def retrieve_all_method_instruction(class_full_name, class_dict):
    """Given the full name of a class, retrieve the method instructions for all of its methods."""

    class_name = return_name_without_package(class_full_name)
    all_instruction_query = """cpg.typeDecl.fullName("{class_full_name}").astChildren.isMethod.map(node => (node.fullName, node.ast.
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
            method_full_name = method["_2"]
            if method_full_name in method_ins_dict:
                method["_8"] = method_ins_dict[method_full_name]


def retrieve_single_method_instruction(full_name):
    """Given the full name of a method, retrieve all of its method instructions."""

    method_instruction_query = """cpg.method.filter(node => node.fullName.equals("{method_name}")).ast.
    filter(node => node.lineNumber != None).map(node => (node.code, node.label, node.lineNumber)).toJson""".format(
        method_name=full_name
    )

    class_name, method_name = return_method_name(full_name)

    info_msg = "The method instructions for {class_name}.{method_name}() have been retrieved.".format(
        class_name=class_name,
        method_name=method_name,
    )
    debug_msg = (
        "Method Instruction Retrieval Result for {class_name}.{method_name}():".format(
            class_name=class_name, method_name=method_name
        )
    )
    error_msg = "Retrieve method instruction data failure for {method_name}().".format(
        method_name=method_name
    )
    log_dict = dict(debug_msg=debug_msg, error_msg=error_msg, info_msg=info_msg)
    method_ins_result = handle_query(method_instruction_query, log_dict)
    method_ins = []
    if method_ins_result:
        method_ins = method_ins_result[0]
    return method_ins


def append_all_instructions(source_code_json):
    """Iterate through all classes within the directory that still need instructions and append
    the approriate instructions to the appropriate methods."""

    # Ignore interfaces and classes that already have instructions
    class_ins_reqs = [
        cd
        for cd in source_code_json
        if not cd["hasInstructions"] and INTERFACE not in cd["_4"]
    ]

    total = len(class_ins_reqs)
    current = 0
    for class_dict in class_ins_reqs:
        class_name = class_dict["_1"]
        class_full_name = class_dict["_2"]
        ins_start = timer()
        current += 1
        main_logger.info(
            "Retrieving method instructions for {class_name}, class #{current}/{total} classes needing instructions.".format(
                class_name=class_name, current=current, total=total
            )
        )
        class_ast_size = name_ast_dict[class_full_name]
        method_lines = class_dict["methodLines"]
        total_methods = len(class_dict["_8"])
        if method_lines <= 140:
            retrieve_all_method_instruction(class_full_name, class_dict)
        else:
            current_method = 0
            for method in class_dict["_8"]:
                current_method += 1
                method_name = method["_1"]
                method_full_name = method["_2"]
                if method["totalLength"] != 0 and ABSTRACT or NATIVE not in method:
                    main_logger.info(
                        "Starting retrieval of method instructions for {name}, method #{current}/{total} methods in class.".format(
                            name=method_name,
                            current=current_method,
                            total=total_methods,
                        )
                    )
                    method["_8"] = retrieve_single_method_instruction(
                        full_name=method_full_name
                    )
            ins_end = timer()
            ins_total = ins_end - ins_start
            main_logger.info(
                "All method instructions for {class_name} have been retrieved. Completed in {time} seconds".format(
                    class_name=class_name, time=format(format(ins_total, DEC_FORMATTER))
                )
            )
    return source_code_json


def handle_large_project(class_names):
    """Handle larger projects (total ast size > 2000) by retrieving the data for each class individually (with/without method instructions)
    based on the ast size of the class, following a Shortest Task First approach.

    Classes without instructions will have their instructions retrieved either all at once or one by one depending
    on the ast size of the classs. Methods are additionally sorted in ascending order based on total length prior to instruction retrieval
    process.
    """

    joern_json = {CLASSES: []}
    # Retrieve the data for all classes (with/without instructions based on their ast size)
    class_data_start = timer()
    current = 0

    for name in class_names:
        current += 1
        ast_size = name_ast_dict[name]
        class_name = return_name_without_package(name)
        main_logger.info(
            "Starting retrieval of class data for {name}, class #{current}/{total} classes.".format(
                name=class_name, current=current, total=total_classes
            )
        )
        if ast_size <= 1000:
            class_dict = retrieve_class_data(name, True)
        else:
            class_dict = retrieve_class_data(name)
        class_dict["_8"] = sort_methods(methods=class_dict["_8"])
        class_dict["packageName"] = return_package_name(name)
        joern_json[CLASSES].append(class_dict)

    class_data_end = timer()
    class_data_diff = class_data_end - class_data_start
    main_logger.info(
        "The data for all classes has been retrieved. Completed in {0} seconds.".format(
            format(class_data_diff, DEC_FORMATTER)
        )
    )

    # Filter out external classes and add all method instructions
    filtered_classes = clean_up_external_classes(joern_json)
    sorted_classes = sort_classes(filtered_classes[CLASSES])

    # Handle retrieval of method instructions for classes which still need them
    method_ins_start = timer()
    joern_json[CLASSES] = append_all_instructions(sorted_classes)
    method_ins_end = timer()
    method_diff = method_ins_end - method_ins_start
    main_logger.info(
        "All method instructions for classes needing instructions have been retrieved. Completed in {0} seconds.".format(
            format(method_diff, DEC_FORMATTER)
        )
    )

    total_query_time = import_diff + name_retrieve_diff + class_data_diff + method_diff
    main_logger.info(
        "Total elapsed time performing queries to Joern: {0} seconds.".format(
            format(total_query_time, DEC_FORMATTER)
        )
    )

    # Create the source code json
    source_code_json = {"relations": [], CLASSES: []}
    dict_start = timer()
    source_code_json[CLASSES] = list(
        filter(None, list(map(create_class_dict, joern_json[CLASSES]))),
    )
    dict_end = timer()
    dict_diff = dict_end - dict_start
    main_logger.info(
        "The source code json dictionary has been created. Completed in {0} seconds.".format(
            format(dict_diff, DEC_FORMATTER)
        )
    )

    return source_code_json


def handle_small_project():
    """
    Retrieve all the info for every class within the source code (including method instructions) for small projects.

    Small Projects can be defined as having a total AST size of < 2000 (AST size is summed up from all the classes within given directory).
    """

    query = """cpg.typeDecl.isExternal(false).filter(node => !node.name.contains("lambda")).
    map(node => (node.name, node.fullName, node.filename, node.inheritsFromTypeFullName.l, 
    node.code, node.lineNumber, 
    node.astChildren.isMember.l.map(node => (node.name, node.typeFullName, 
    node.lineNumber, node.astChildren.isModifier.modifierType.l)), 
    node.astChildren.isMethod.isExternal(false).filter(node => !node.code.contains("<lambda>") 
    && !node.name.contains("<clinit>")).l.
    map(node => (node.name, node.fullName, node.code, node.lineNumber, node.lineNumberEnd, 
    node.astChildren.isModifier.modifierType.l, node.ast.isCall.filter(node => 
    !node.methodFullName.contains("<operator>") && !node.code.contains("<lambda>") && 
    !node.code.contains("<empty>") || node.name.contains("Exception")).l.
    map(node => (node.methodFullName, node.lineNumber)),
    node.ast.filter(node => node.lineNumber != None).l.
    map(node => (node.code, node.label, node.lineNumber)))))).toJson"""

    # Retrieve the data for all classes
    info_msg = "The data for every class has been retrieved."
    debug_msg = "All class data result:"
    error_msg = "All class data retrieve fail"
    log_dict = dict(debug_msg=debug_msg, error_msg=error_msg, info_msg=info_msg)
    data_start = timer()
    class_result = handle_query(query, log_dict)
    data_end = timer()
    data_diff = data_end - data_start

    total_query_time = import_diff + data_diff
    main_logger.info(
        "Total elapsed time performing queries to Joern: {0} seconds.".format(
            format(total_query_time, DEC_FORMATTER)
        )
    )

    # Filter out external classes
    joern_json = {CLASSES: []}
    for class_dict in class_result:
        class_full_name = class_dict["_2"]
        class_dict["packageName"] = return_package_name(class_full_name)
        joern_json[CLASSES].append(class_dict)
    filtered_classes = clean_up_external_classes(joern_json)

    source_code_json = {"relations": [], CLASSES: []}

    # Create class dictionaries after getting all data from joern
    dict_start = timer()
    source_code_json[CLASSES] = list(
        filter(
            None,
            list(map(create_class_dict, filtered_classes[CLASSES])),
        )
    )
    dict_end = timer()
    dict_diff = dict_end - dict_start
    main_logger.info(
        "The source code json dictionary has been created. Completed in {0} seconds.".format(
            format(dict_diff, DEC_FORMATTER)
        )
    )
    return source_code_json


if __name__ == "__main__":
    main_logger, debug_logger = create_loggers()

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
        main_logger.info("joern_query is starting and connected to CPGQLSClient.")
        program_start_time = timer()

    main_logger.info("Analyzing project_dir " + project_dir)

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
        class_names, name_ast_dict = retrieve_all_class_names()
        name_retrieve_end = timer()
        name_retrieve_diff = name_retrieve_end - name_retrieve_start
        total_classes = len(class_names)

        # Determine the total AST size of the given directory (used to determine how data retrieval should perform)
        total_ast_size = 0
        for entry in name_ast_dict:
            total_ast_size += name_ast_dict[entry]

        # Retrieve class data for all classes within the source code, handle projects of
        # different sizes differently.
        if total_ast_size <= 1400:
            main_logger.info(
                "The provided directory is considered small (AST Size = {ast_size}), retrieving data for all classes at once.".format(
                    ast_size=total_ast_size
                )
            )
            source_code_json = handle_small_project()
        else:
            main_logger.info(
                "The provided directory is considered large (AST Size = {ast_size}), retrieving data for all classes one by one.".format(
                    ast_size=total_ast_size
                )
            )
            source_code_json = handle_large_project(class_names)

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
            debug_logger.debug("Source Code JSON Dictionary: %s", source_code_json)
            handle_error("Source code json creation failure, no classes in dictionary")

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
        main_logger.info(
            "Total time taken: {0} seconds.".format(format(program_diff, DEC_FORMATTER))
        )
        # Terminate after everything has been successfully executed
        exit(0)
    except Exception as e:
        full_trace = str(traceback.format_exc())
        handle_error(full_trace)
