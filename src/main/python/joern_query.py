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

# Threshold Constants (current config allows each class to have its lowest time for class data + method ins)
PROJECT_AST_SIZE_THRESHOLD = 1000
CLASS_AST_SIZE_THRESHOLD = 1100
METHOD_AST_SIZE_THRESHOLD = 460  # Potential Numbers: 100, 300, 460, 900 (lower threshold means more classes need to retrieve ins separately)
METHOD_LINES_THRESHOLD = 160

# Error types
DELETE_ERROR = "[DELETE ERROR]"
EMPTY_DIRECTORY_ERROR = "[EMPTY DIR ERROR]"
IMPORT_QUERY_ERROR = "[IMPORT ERROR]"
PORT_ERROR = "[PORT ERROR]"
PYTHON_ERROR = "[PYTHON ERROR]"
QUERY_ERROR = "[QUERY ERROR]"


def clean_json(result: str):
    """Given a result["stdout"], return the resulting object with data from Joern"""

    if '"' in result:
        index = result.index('"')
        try:
            result_obj = json.loads(json.loads(result[index : len(result)]))
        except JSONDecodeError as e:
            debug_logger.debug("Provided result[stdout]: %s", result)
            debug_logger.debug("Type should be str: %s", type(result))
            handle_error(PYTHON_ERROR, e)
        return result_obj


def handle_query(query: str, log_dict: dict, is_data_query: bool = True):
    """Handle a query and output neccessary info to log file then return a final result object"""

    start = timer()
    result = client.execute(query)
    end = timer()
    total_time = end - start

    info_message = log_dict["info_msg"]
    debug_message = log_dict["debug_msg"]
    error_message = log_dict["error_msg"]

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
        if not is_data_query and "import" in query:
            handle_error(IMPORT_QUERY_ERROR, error_message, result[STDERR])
        if not is_data_query and "delete" in query:
            handle_error(DELETE_ERROR, error_message, result[STDERR])
        else:
            handle_error(QUERY_ERROR, error_message, result[STDERR])
    return result_obj


def handle_error(error_type: str, error_message: str, stderr: str = ""):
    """Handle all error situations by logging the error message and joern's error message, if present.
    Followed by deleting the project from /bin/joern-cli/workspace/ and exiting with error code."""

    final_error_msg = error_type + ": " + error_message
    main_logger.error(final_error_msg)
    if stderr:
        main_logger.error(stderr.strip())

    result = client.execute(delete_query(project_name))
    if result[SUCCESS] and result[STDOUT]:
        main_logger.info(
            "The source code has been successfully removed from Joern, after the experienced error."
        )
    else:
        debug_logger.debug("Delete Project Query Result: %s", result)
        error_msg = "Failed to remove project from Joern, after the experienced error"
        final_error_msg = DELETE_ERROR + ": " + error_msg
        main_logger.error(final_error_msg)
    exit(1)


def retrieve_all_class_names():
    """Execute a single query to retrieve all the class names within the source code. Additionally, retrieve
    the total number of attributes, the total ast size of the class and the total method ast size
    of each class."""

    name_query = """cpg.typeDecl.isExternal(false).filter(node => !node.name.contains("lambda$")).
    map(node => (node.fullName, node.ast.size, node.astChildren.size, 
    node.ast.isMethod.isExternal(false).filter(node => node.lineNumber != None).
    l.map(node => (node.ast.size)))).toJson"""

    info_msg = "All class name and class sizes have been retrieved."
    debug_msg = "Class Name Retrieval Result: "
    error_msg = "Retrieve class names failure for {dir}".format(dir=project_dir)
    log_dict = create_log_dictionary(info_msg, debug_msg, error_msg)

    class_name_res = handle_query(name_query, log_dict)
    class_bundles = []
    if class_name_res:
        # Create class bundles for every class present in the directory (full name, class ast size, attribute ast size, method ast size,
        # real ast size (attribute + method ast size))
        class_bundles = create_class_bundles(class_name_res)
    return class_bundles


def construct_query(class_bundle: dict):
    """Construct a class data retrieval query, determining if attributes, methods
    and additionally method instructions should be queried for, if a class posesses
    attributes and methods and if the ast size and method ast size of a class is
    within a threshold."""

    full_name = class_bundle["classFullName"].replace(NESTED_SEP, DOT_SEP)
    class_ast_size = class_bundle["totalAstSize"]
    attribute_ast_size = class_bundle["attributeAstSize"]
    method_ast_size = class_bundle["methodAstSize"]
    total_methods = class_bundle["totalMethods"]

    # Determine if attributes should be retrieved or not
    attribute_retrieve = ""
    if attribute_ast_size:
        attribute_retrieve = """, node.astChildren.isMember.l.map(node => (node.name, node.typeFullName, node.lineNumber, 
        node.astChildren.isModifier.modifierType.l))"""

    # Determine if method instructions should be retrieved or not
    method_ins_retrieve = ""
    if (
        class_ast_size < CLASS_AST_SIZE_THRESHOLD
        and method_ast_size < METHOD_AST_SIZE_THRESHOLD
    ):
        method_ins_retrieve = """, node.ast.isCall.filter(node => !node.methodFullName.contains("<operator>") && 
        !node.code.contains("<") || node.methodFullName.contains("Exception")).l.map(node => (node.methodFullName, 
        node.lineNumber)), node.ast.filter(node => node.lineNumber != None).l.map(node => (node.code, node.label, node.lineNumber))"""

    # Determine if methods should be retrieved or not
    method_retrieve = ""
    if total_methods:
        method_retrieve = """, node.astChildren.isMethod.isExternal(false).filter(node => !node.code.contains("<lambda>") && 
        !node.name.contains("<clinit>")).l.map(node => (node.name, node.fullName, node.code, node.lineNumber, node.lineNumberEnd, 
        node.astChildren.isModifier.modifierType.l{method_ins_retrieve}))""".format(
            method_ins_retrieve=method_ins_retrieve
        )

    # Construct the query, combining everything together
    class_query = """cpg.typeDecl.fullName("{full_name}").map(node => (node.name, 
    node.fullName, node.filename, node.inheritsFromTypeFullName.l, node.code, node.lineNumber{attribute_retrieve}{method_retrieve})).
    toJson""".format(
        full_name=full_name,
        attribute_retrieve=attribute_retrieve,
        method_retrieve=method_retrieve,
    )

    if method_retrieve and not method_ins_retrieve:
        class_bundle["withoutIns"] = True
    else:
        class_bundle["withoutIns"] = False

    return class_query


def retrieve_class_data(class_bundle: dict):
    """Execute a single query to get all the data of a class. If retrieve_ins is True, additionally
    retrieve the instructions of all methods of the class, if not class data without instructions is retrieved."""

    class_query = construct_query(class_bundle)
    full_name = class_bundle["classFullName"].replace(NESTED_SEP, DOT_SEP)
    needs_ins = class_bundle["withoutIns"]
    without_ins = ""
    if needs_ins:
        without_ins = " (excluding instructions) "
    class_name = return_name_without_package(full_name)

    # Handle query and log neccesarry information
    info_msg = "The class data for {name}{without_ins} has been retrieved.".format(
        name=class_name, without_ins=without_ins
    )
    debug_msg = "Class Data Retrieval Result for {name}:".format(name=class_name)
    error_msg = "Retrieve class data failure for {class_name}.".format(
        class_name=class_name
    )
    log_dict = create_log_dictionary(info_msg, debug_msg, error_msg)
    class_result = handle_query(class_query, log_dict)

    class_dict = []
    if class_result:
        class_dict = class_result[0]
        class_dict["needsInstructions"] = needs_ins
    else:
        debug_logger.debug(class_result)
    return class_dict


def retrieve_all_method_instruction(class_full_name: str, class_dict: dict):
    """Given the full name of a class, retrieve the method instructions for all of its methods."""

    class_full_name = class_full_name.replace(NESTED_SEP, DOT_SEP)
    class_name = return_name_without_package(class_full_name)
    all_instruction_query = """cpg.typeDecl.fullName("{class_full_name}").
    astChildren.isMethod.isExternal(false).filter(node => node.lineNumber != None).map(node => 
    (node.fullName, node.ast.isCall.filter(node => !node.methodFullName.contains("<operator>") 
    && !node.name.contains("<") || node.methodFullName.contains("Exception")).l.map(node => (node.methodFullName, node.lineNumber)), 
    node.ast.filter(node => node.lineNumber != None).l.map(node => (node.code, node.label, node.lineNumber)))).toJson""".format(
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
    log_dict = create_log_dictionary(info_msg, debug_msg, error_msg)
    all_method_ins = handle_query(all_instruction_query, log_dict)

    if all_method_ins:
        method_ins_dict = all_method_ins[0]
        for method in class_dict["_8"]:
            method_full_name = method["_2"]
            if method_full_name in method_ins_dict:
                method["_7"] = method_ins_dict["_2"]
                method["_8"] = method_ins_dict["_3"]
            else:
                method["_7"] = []
                method["_8"] = []


def retrieve_single_method_instruction(
    class_full_name: str, method_name: str, method: dict
):
    """Given the full name of a class and the name of a method, retrieve all of its method instructions."""

    class_full_name = class_full_name.replace(NESTED_SEP, DOT_SEP)
    class_name = return_name_without_package(class_full_name)

    method_instruction_query = """cpg.typeDecl.fullName("{class_full_name}").astChildren.isMethod.name("{method_name}").map(node => 
    (node.ast.isCall.filter(node => !node.methodFullName.contains("<operator>") 
    && !node.name.contains("<") || node.methodFullName.contains("Exception")).l.map(node => (node.methodFullName, node.lineNumber)),  
    node.ast.filter(node => node.lineNumber != None).l.map(node => (node.code, node.label, node.lineNumber)))).toJson""".format(
        class_full_name=class_full_name, method_name=method_name
    )

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
    log_dict = create_log_dictionary(info_msg, debug_msg, error_msg)
    method_ins_result = handle_query(method_instruction_query, log_dict)

    method_ins = []
    if method_ins_result:
        method_ins = method_ins_result[0]
        method["_7"] = method_ins["_1"]
        method["_8"] = method_ins["_2"]


def handle_multiple_instructions(class_full_name: str, class_methods: list):
    """Retrieve instructions for every method present within a class one by one and append
    these instructions to the appropriate method."""

    class_name = return_name_without_package(class_full_name)
    current_method = 0
    total_methods = len(class_methods)
    ins_start = timer()
    for method in class_methods:
        current_method += 1
        method_name = method["_1"]
        if method["totalLength"] != 0 and ABSTRACT or NATIVE not in method:
            main_logger.info(
                "Starting retrieval of method instructions for {name}, method #{current}/{total} methods in class.".format(
                    name=method_name,
                    current=current_method,
                    total=total_methods,
                )
            )
            retrieve_single_method_instruction(class_full_name, method_name, method)
    ins_end = timer()
    ins_total = ins_end - ins_start
    main_logger.info(
        "All method instructions for {class_name} have been retrieved. Completed in {time} seconds".format(
            class_name=class_name, time=format(format(ins_total, DEC_FORMATTER))
        )
    )


def append_all_instructions(source_code_json):
    """Iterate through all classes within the directory that still need instructions and append
    the approriate instructions to the appropriate methods."""

    # Ignore interfaces and classes that already have instructions
    class_ins_reqs = [
        cd
        for cd in source_code_json
        if cd["needsInstructions"] and INTERFACE not in cd["_4"]
    ]

    total = len(class_ins_reqs)
    current = 0
    for class_dict in class_ins_reqs:
        class_full_name = class_dict["_2"]
        class_name = return_name_without_package(class_full_name)
        current += 1
        main_logger.info(
            "Retrieving method instructions for {class_name}, class #{current}/{total} classes needing instructions.".format(
                class_name=class_name, current=current, total=total
            )
        )
        class_ast_size = class_dict["classAstSize"]
        method_lines = class_dict["methodLines"]
        class_methods = class_dict["_8"]
        if (
            class_ast_size <= CLASS_AST_SIZE_THRESHOLD
            and method_lines <= METHOD_LINES_THRESHOLD
        ):
            retrieve_all_method_instruction(class_full_name, class_dict)
        else:
            class_methods.sort(key=lambda entry: entry["totalLength"])
            handle_multiple_instructions(class_full_name, class_methods)
    return source_code_json


def handle_large_project(class_bundles: list):
    """Handle larger projects (total ast size > 1000) by retrieving the data for each class
    individually (with/without method instructions) based on the ast size of the class,
    following a Shortest Task First approach.

    Classes without instructions will have their instructions retrieved either all at once or one by one depending
    on the ast size of the classs. Methods are additionally sorted in ascending order based on total length prior to instruction retrieval
    process.
    """

    joern_json = {CLASSES: []}
    # Retrieve the data for all classes (with/without instructions based on their ast size)
    class_data_start = timer()
    current = 0
    for class_bundle in class_bundles:
        current += 1
        class_ast_size = class_bundle["totalAstSize"]
        total_attribute_ast_size = class_bundle["attributeAstSize"]
        total_method_ast_size = class_bundle["methodAstSize"]
        full_name = class_bundle["classFullName"]
        class_name = return_name_without_package(full_name)
        main_logger.info(
            "Starting retrieval of class data for {name}, class #{current}/{total} classes.".format(
                name=class_name, current=current, total=total_classes
            )
        )
        class_dict = retrieve_class_data(class_bundle)
        class_dict["packageName"] = return_package_name(full_name)
        class_dict["classAstSize"] = class_ast_size
        class_dict["attributeAstSize"] = total_attribute_ast_size
        class_dict["methodAstSize"] = total_method_ast_size
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
    assign_total_method_lines(filtered_classes[CLASSES])

    # Handle retrieval of method instructions for classes which still need them
    method_ins_start = timer()
    joern_json[CLASSES] = append_all_instructions(filtered_classes[CLASSES])
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

    Small Projects can be defined as having a total AST size of < 1000 (AST size is summed up from all the classes within given directory).
    """

    query = """cpg.typeDecl.isExternal(false).filter(node => !node.name.contains("lambda$")).
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

    main_logger.info("Server Endpoint: %s", server_endpoint)
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
        index = project_dir.find(COLON_SEP)
        win_drive = project_dir[0 : index + 1]
        project_dir = project_dir.replace(win_drive, win_drive.upper()).replace(
            "\\", "//"
        )

    # Import the source code to Joern for analyzing.
    import_query = import_code_query(project_dir, project_name)
    info_msg = "The source code has been successfully imported."
    debug_msg = "Source Code Import Result for {dir}:".format(dir=project_dir)
    error_msg = "Source Code Import Failure for {dir}:".format(dir=project_dir)
    log_dict = create_log_dictionary(info_msg, debug_msg, error_msg)
    import_start = timer()
    main_logger.info(
        "Importing source code into Joern (may take a while for larger projects)."
    )
    import_res = handle_query(import_query, log_dict, False)
    import_end = timer()
    import_diff = import_end - import_start

    try:
        # Retrieve all class names
        name_retrieve_start = timer()
        class_bundles = retrieve_all_class_names()
        name_retrieve_end = timer()
        name_retrieve_diff = name_retrieve_end - name_retrieve_start
        total_classes = len(class_bundles)

        # Determine the total AST size of the given directory (used to determine how data retrieval should perform)
        total_ast_size = 0
        for class_bundle in class_bundles:
            total_ast_size += class_bundle["totalAstSize"]

        # Retrieve class data for all classes within the source code, handle projects of
        # different sizes differently.
        if total_ast_size <= PROJECT_AST_SIZE_THRESHOLD:
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
            source_code_json = handle_large_project(class_bundles)

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
            error_msg = "Source code json creation failure, no classes in dictionary"
            handle_error(EMPTY_DIRECTORY_ERROR, error_msg)

        # Close and delete the project from user's bin/joern/joern-cli/workspace
        delete_query = delete_query(project_name)
        info_msg = "The source code has been successfully removed from Joern."
        debug_msg = "Source Code Delete Result:"
        error_msg = "Source code deletion failure, could not remove from joern"
        log_dict = create_log_dictionary(info_msg, debug_msg, error_msg)
        del_result = handle_query(delete_query, log_dict, False)

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
        handle_error(PYTHON_ERROR, full_trace)
