import os
import re
import logging
import logging.handlers
from timeit import default_timer as timer

# Helper Constants
INIT_METHOD = "<init>"
NESTED_SEP = "$"
COLON_SEP = ":"
DOT_SEP = "."

ABSTRACT_CLASS = "abstract class"
CLASS = "class"
INTERFACE = "interface"
ENUM = "enum"

PUBLIC = "public"
PRIVATE = "private"
PACKAGE_PRIVATE = "package private"
ABSTRACT = "abstract"
NATIVE = "native"

CLASSES = "classes"

SUCCESS = "success"
STDOUT = "stdout"
STDERR = "stderr"
DEC_FORMATTER = ".2f"

NAME = "name"
CODE = "code"
MODIFIERS = "modifiers"
LINE_NUM = "lineNumber"

# Space is needed to pickup, ABSTRACT_CLASS and not "class abstractClass" for example
MODIFIERS_PATTERN = re.compile(
    "(private |public |protected |static |final |synchronized |virtual |volatile |abstract |native )"
)


def assign_total_method_lines(all_classes):
    """Iterate through each class and sum up the total number of lines from their methods."""

    for class_dict in all_classes:
        class_total = 0
        if "_8" in class_dict:
            for method in class_dict["_8"]:
                end = 0
                start = 0
                add = 0
                if "_4" and "_5" in method:
                    end = method["_5"]
                    start = method["_4"]
                    add = 1
                method["totalLength"] = (end - start) + add
                class_total += method["totalLength"]
            class_dict["methodLines"] = class_total


def clean_method_full_name(method_full_name):
    """Given the full name of a method consisting of the package, class and signature of the method. Return the name separating
    the package, class and method name by a dollar sign.
    """

    str_to_return = method_full_name
    if COLON_SEP in method_full_name:
        index_signature = str_to_return.index(COLON_SEP)
        str_to_return = str_to_return[:index_signature]
        for _ in range(0, 2):
            if DOT_SEP in str_to_return:
                index_sep = str_to_return.rindex(DOT_SEP)
                str_to_return = """{start}{new_char}{end}""".format(
                    start=str_to_return[:index_sep],
                    new_char=str_to_return[index_sep].replace(DOT_SEP, NESTED_SEP),
                    end=str_to_return[index_sep + 1 : len(str_to_return)],
                )
    return str_to_return


def return_method_name(full_name):
    """Return the name of a method without the full name + method signature. Additionally return the class it belongs to"""

    index_signature = full_name.index(COLON_SEP)
    full_method_name = full_name[:index_signature]
    index = full_method_name.rindex(DOT_SEP)
    full_method_name = full_method_name.replace(NESTED_SEP, DOT_SEP)
    method_name = full_method_name[:index] + full_method_name[index:].replace(
        DOT_SEP, NESTED_SEP
    )
    split = method_name.split(DOT_SEP)
    method_name = [name for name in split if NESTED_SEP in name][0].replace(
        NESTED_SEP, DOT_SEP
    )
    class_name, method_name = (
        method_name.split(DOT_SEP)[0],
        method_name.split(DOT_SEP)[1],
    )
    return class_name, method_name


def return_name_without_package(class_full_name):
    """Remove package and any nesting in a class full name to get the proper name of the class."""

    class_full_name = class_full_name.replace(NESTED_SEP, DOT_SEP)
    class_full_name = class_full_name.split(DOT_SEP)[-1]
    return class_full_name


def return_attribute_package_name(attribute_full_name: str):
    """Given the full name of an attribute, return its package name"""

    package_name = attribute_full_name
    if DOT_SEP in package_name:
        index_sep = package_name.rindex(DOT_SEP)
        package_name = """{start}{new_char}{end}""".format(
            start=package_name[:index_sep],
            new_char=package_name[index_sep].replace(DOT_SEP, NESTED_SEP),
            end=package_name[index_sep + 1 : len(package_name)],
        )
        splitted = package_name.split(NESTED_SEP)
        if splitted:
            package_name = splitted[0]
    else:
        package_name = ""
    return package_name


def return_package_name(class_full_name):
    """Given the full name of a class, return its package name. Mainly used to filter out
    external classes prior to appending instructions."""

    package_name = ""
    if DOT_SEP in class_full_name:
        index = class_full_name.rindex(DOT_SEP)
        package_name = class_full_name[:index].strip()
    return package_name


def return_all_distinct_package_names(all_classes):
    """Return all distinct package names as a set, sorted in ascending order"""

    package_names = {class_dict["packageName"] for class_dict in all_classes}
    return sorted(package_names, key=str)


def read_file(file_path):
    """Read a file from the file path that Joern gives and return a list of all the lines"""

    lines = []
    if os.path.exists(file_path):
        java_file = open(file_path, "r")
        lines = java_file.read().splitlines()
        java_file.close()
    return lines


def create_attribute_dict(curr_attribute):
    """For every attribute, create a dictionary and return it."""

    attribute_name = curr_attribute["_1"]
    attribute_type_full_name = curr_attribute["_2"]
    attribute_line_number = int(curr_attribute["_3"])
    attribute_modifiers = curr_attribute["_4"]
    if not attribute_modifiers:
        attribute_modifiers = [PACKAGE_PRIVATE]

    index = attribute_type_full_name.rfind(DOT_SEP)
    attribute_type = attribute_type_full_name
    package_name = ""
    if index != -1:
        package_name = attribute_type_full_name.replace("[]", "").replace(
            NESTED_SEP, DOT_SEP
        )
        attribute_type = attribute_type_full_name[
            index + 1 : len(attribute_type_full_name)
        ]
    index_nested = attribute_type.rfind(NESTED_SEP)
    if index_nested != -1:
        attribute_type = attribute_type[index_nested + 1 : len(attribute_type)]

    curr_attribute_dict = {
        NAME: attribute_name,
        "parentClass": [{}],  # Handled by Parser
        "packageName": return_attribute_package_name(attribute_type_full_name),
        CODE: "",  # Handled within assign_missing_class_info
        LINE_NUM: attribute_line_number,
        MODIFIERS: [modifier.lower() for modifier in attribute_modifiers],
        "attributeType": attribute_type,  # Handled within assign_missing_class_info
        "typeList": [],  # Handled by Parser
    }

    return curr_attribute_dict


def get_method_modifiers(regex_pattern_modifiers, joern_modifiers):
    """Return all the method modifiers, combining joern and the modifiers obtained from regex"""

    regex_pattern_modifiers = [mod.strip() for mod in regex_pattern_modifiers]
    joern_modifiers = [mod.strip() for mod in joern_modifiers]
    final_set = set(regex_pattern_modifiers).union(set(joern_modifiers))
    final_list = list(final_set)
    return final_list


def create_method_parameters(parameters):
    """Return a list containing dictionaries for each parameter within the method body"""

    parameters_list = []
    for parameter in parameters:
        splitted_str = parameter.split()
        if len(splitted_str) == 2:
            parameter_type = splitted_str[0]
            name = splitted_str[1]
            parameter_dict = {
                CODE: parameter,
                NAME: name,
                "type": parameter_type,
                "typeList": [],  # Handled by Parser
            }
            parameters_list.append(parameter_dict)
    return parameters_list


def assign_method_calls(method_ins, method_calls):
    """Assign method calls to all instructions which call either an Exception or a method
    belonging to a class present in the given directory. Additionally, ensure that duplicates are cleaned after assignment.
    """

    method_names = set()
    for call in method_calls:
        for key, value in call.items():
            cleaned_name = clean_method_full_name(key)
            split_name = cleaned_name.split(NESTED_SEP)
            package_name = " "
            class_name = ""
            method_name = ""
            if len(split_name) == 3:
                package_name = split_name[0]
                class_name = split_name[1]
                method_name = split_name[2]
            elif len(split_name) == 2:
                class_name = split_name[0]
                method_name = split_name[1]

            method_name = method_name.replace(INIT_METHOD, class_name)
            method_names.add(method_name)
            filtered_calls = [
                ins
                for ins in method_ins
                if ins["lineNumber"] == value
                and ins["methodCall"] == method_name
                and NESTED_SEP not in ins["methodCall"]
            ]
            if filtered_calls:
                first_call = filtered_calls[0]
                method_call = (
                    package_name + NESTED_SEP + class_name + NESTED_SEP + method_name
                )
                first_call["methodCall"] = method_call

    # Clean after assigning method calls
    for ins in method_ins:
        call = ins["methodCall"]
        if call in method_names:
            ins["methodCall"] = ""


def create_instruction_dict(curr_instruction):
    """For every method's instruction, create a dictionary and return it."""

    instruction_code = curr_instruction["_1"]
    instruction_label = curr_instruction["_2"]
    instruction_line_number = curr_instruction["_3"]

    # Get the method call in each line of code, if any.
    method_call_pattern = re.compile(r"([a-zA-Z]*\()")
    calls = method_call_pattern.findall(instruction_code)
    method_call = ""
    acceptable_labels = ["CALL", "RETURN"]
    if calls and instruction_label in acceptable_labels:
        method_call = calls[0].replace("(", "")
        method_call = method_call.replace("super", INIT_METHOD)

    curr_instruction_dict = {
        "label": instruction_label,
        CODE: instruction_code.replace("\r\n", ""),
        LINE_NUM: int(instruction_line_number),
        "methodCall": method_call,
    }

    return curr_instruction_dict


def create_method_dict(curr_method):
    """For every method, create a dictionary and return it."""

    method_name = curr_method["_1"]
    method_full_name = curr_method["_2"]
    method_code = curr_method["_3"]

    # Default values for default constructor
    method_line_number = 0
    method_line_number_end = 0
    total_lines = 0
    method_modifiers = [
        modifier.lower() for modifier in curr_method["_6"] if modifier != "CONSTRUCTOR"
    ]
    method_parameters = []
    method_instructions = []
    method_calls = []
    parameter_codes = set()

    # Handle normal method cases
    if len(curr_method) == 8 or len(curr_method) == 9:
        method_line_number = int(curr_method["_4"])
        method_line_number_end = int(curr_method["_5"])
        total_lines = (method_line_number_end - method_line_number) + 1
        if not method_modifiers and method_name != INIT_METHOD:
            method_modifiers = [PACKAGE_PRIVATE]
        method_calls = curr_method["_7"]
        if "_8" in curr_method and type(curr_method["_8"]) == list:
            method_instructions = curr_method["_8"]
            for ins in method_instructions:
                if len(ins) == 3:
                    code = ins["_1"]
                    label = ins["_2"]
                    if "METHOD_PARAMETER" in label and code != "this":
                        parameter_codes.add(code)

    # Get the modifiers, return type and the method body from the full method body provided by Joern.
    regex_pattern_modifiers = MODIFIERS_PATTERN.findall(method_code)
    method_with_return = re.sub(MODIFIERS_PATTERN, "", method_code).strip()
    method_name_pattern = re.compile(r"(^[a-zA-z]*)")

    # Get the return type of the method, if any.
    return_type_pattern = re.compile(r"(^[a-zA-z]*\s)")
    method_return_type = return_type_pattern.findall(method_with_return)
    return_type = ""
    if method_return_type:
        return_type = method_return_type[0].strip()

    # Get the method body with any method parameters.
    method_body = re.sub(return_type_pattern, "", method_with_return)
    if not return_type:
        # Handle all Collection types (Set, HashMap, ArrayList, etc)
        if ">" in method_body:
            index = method_body.find(">")
            return_type = method_body[0 : index + 1]
        if "(" in return_type or not return_type:
            return_type = ""
        if return_type in method_body:
            method_body = method_body.replace(return_type, "").strip()

    # If the method is a constructor, find the name of the class.
    constructor_name = method_name_pattern.findall(method_body)[0]

    # Get method parameters, if any.
    method_parameters = create_method_parameters(parameter_codes)

    # Get method instructions, if any.
    method_instructions = list(
        filter(
            None,
            list(map(create_instruction_dict, method_instructions)),
        )
    )

    curr_method_dict = {
        NAME: method_name.replace(INIT_METHOD, constructor_name),
        "parentClass": [{}],  # Handled by Parser
        "methodBody": method_body,
        MODIFIERS: get_method_modifiers(regex_pattern_modifiers, method_modifiers),
        "parameters": method_parameters,
        "returnType": return_type,
        "lineNumberStart": method_line_number,
        "lineNumberEnd": method_line_number_end,
        "totalMethodLength": total_lines,
        "instructions": method_instructions,
        "methodCalls": [],  # Handled by Parser
        "attributeCalls": [],  # Handled by Parser
    }

    # Assign method calls to all instructions which call either an Exception or a method belonging to a class in the source code
    # in the following format: <package_name>$<class_name>$<method_name>
    # (this is to account for same class name, same method name, different package)
    assign_method_calls(curr_method_dict["instructions"], method_calls)

    return curr_method_dict


"""
# TODO: Add to CPGClass (removes 5 parameters from CPGClass which it does not need)
# Will most probably be a final 1 element array similar to parent for attr/method.
def create_file_dict(curr_file_contents):

    curr_file_dict = {
        "filePath": "",
        "fileLength": 0,
        "importStatements": [],
        "fileLength": 0,
        "nonEmptyLines": 0,
        "emptyLines": 0,
    }
    return curr_file_dict
"""

"""
#TODO: Add to File, imported classes, attrs, methods will be private. importLine can be public final (implementation idea ready)
def create_import_dict(curr_import_statement):
    curr_import_dict = {
        "importLine": "",
        "importedClasses": [],
        "importedAttributes": [],
        "importedMethods": []
    }
"""


def create_class_dict(curr_class):
    """For every class, create a dictionary and return it."""

    class_name = curr_class["_1"]
    class_full_name = curr_class["_2"]
    file_name = curr_class["_3"]
    inherits_from_list = curr_class["_4"]
    class_declaration = curr_class["_5"]
    line_number = curr_class["_6"]

    # Create attribute dictionaries, if present
    class_attributes = []
    if "_7" in curr_class:
        class_attributes = curr_class["_7"]
        class_attributes = list(
            filter(None, list(map(create_attribute_dict, class_attributes)))
        )

    # Create method dictionaries, if present
    class_methods = []
    if "_8" in curr_class:
        class_methods = curr_class["_8"]
        class_methods = list(filter(None, list(map(create_method_dict, class_methods))))

    curr_class_dict = {
        NAME: return_name_without_package(class_full_name),
        CODE: "",  # Handled in assign_missing_class_info
        LINE_NUM: int(line_number),
        "importStatements": [],  # Handled in assign_missing_class_info
        MODIFIERS: [],  # Handled in assign_missing_class_info
        "classFullName": class_full_name,
        "inheritsFrom": [],  # Handled in Parser
        "classType": get_class_type(class_declaration),
        "filePath": file_name,
        "fileLength": 0,  # Handled in assign_missing_class_info
        "emptyLines": 0,  # Handled in assign_missing_class_info
        "nonEmptyLines": 0,  # Handled in assign_missing_class_info
        "packageName": "",  # Handled in assign_missing_class_info
        "attributes": class_attributes,
        "methods": class_methods,
        "outwardRelations": [],  # Handled in RelationshipManager
    }

    # Assign all missing info
    curr_class_dict = assign_missing_class_info(
        curr_class_dict, read_file(curr_class_dict["filePath"])
    )

    # Sort attributes and methods by line number
    curr_class_dict["attributes"] = sorted(
        curr_class_dict["attributes"], key=lambda a: a["lineNumber"]
    )
    curr_class_dict["methods"] = sorted(
        curr_class_dict["methods"], key=lambda m: m["lineNumberStart"]
    )

    return curr_class_dict


def get_class_type(declaration):
    """Get the type of the object, either an interface, class, enum or abstract class."""

    if ABSTRACT_CLASS in declaration:
        return ABSTRACT_CLASS
    elif CLASS in declaration:
        return CLASS
    elif ENUM in declaration:
        return ENUM
    elif INTERFACE in declaration:
        return INTERFACE


def assign_missing_class_info(class_dict, file_lines):
    """Assign missing class info to each class dictionary. This includes the following:
    - package name
    - import statements
    - class declaration
    - class modifiers
    - class type
    - attribute declaration and type
    - attribute modifiers
    - total lines and empty and non-empty lines in class
    """

    class_name = class_dict[NAME]
    class_decl_line_number = int(class_dict[LINE_NUM])

    package_name = ""
    if file_lines[0].startswith("package"):
        package_name = file_lines[0]
    package_name = package_name.replace(";", "").replace("package ", "").strip()
    import_statements = [line for line in file_lines if line.startswith("import")]
    class_declaration = file_lines[class_decl_line_number - 1]
    class_declaration = class_declaration.replace("{", "").strip()
    class_modifiers = [
        modifier.strip() for modifier in MODIFIERS_PATTERN.findall(class_declaration)
    ]
    class_type = class_dict["classType"].strip()
    if "abstract" in class_modifiers:
        class_type = ABSTRACT_CLASS

    for attribute in class_dict["attributes"]:
        existing_type = attribute["attributeType"]
        line_number = attribute[LINE_NUM]
        attribute_code = file_lines[line_number - 1].strip()
        attribute_modifiers = [
            modifier.strip() for modifier in MODIFIERS_PATTERN.findall(attribute_code)
        ]
        attribute_type = re.sub(MODIFIERS_PATTERN, "", attribute_code).strip()
        # Handle hashmap types
        attribute_type = attribute_type.replace(", ", "|")
        attribute_type = attribute_type.split()[0].replace("|", ", ")

        if not attribute_modifiers and ENUM not in class_type:
            attribute_modifiers = [PACKAGE_PRIVATE]
        elif ENUM in class_type and existing_type == class_name:
            if PUBLIC in class_modifiers:
                attribute_modifiers.append(PUBLIC)
            elif PRIVATE in class_modifiers:
                attribute_modifiers.append(PRIVATE)
            attribute_modifiers.append("final")
            attribute_modifiers.append("static")
            attribute_type = existing_type

        attribute[CODE] = attribute_code
        attribute["attributeType"] = attribute_type
        attribute[MODIFIERS] = attribute_modifiers

    # Cleanup default constructors
    for method in class_dict["methods"]:
        if method[NAME] == "" and method["returnType"] == "<empty>":
            method[NAME] = class_name
            method["methodBody"] = class_name + "()"
            method["returnType"] = ""

    existing_full_name = class_dict["classFullName"].replace(package_name, "")
    new_full_name = (
        existing_full_name.replace(DOT_SEP, "").replace(NESTED_SEP, DOT_SEP).strip()
    )
    total_file_length = len(file_lines)
    empty_lines = len([line for line in file_lines if line == ""])
    non_empty_lines = total_file_length - empty_lines
    class_dict["classFullName"] = new_full_name
    class_dict["classType"] = class_type
    class_dict[CODE] = class_declaration
    class_dict["fileLength"] = total_file_length
    class_dict["emptyLines"] = empty_lines
    class_dict["nonEmptyLines"] = non_empty_lines
    class_dict["importStatements"] = import_statements
    class_dict[MODIFIERS] = class_modifiers
    class_dict["packageName"] = package_name

    return class_dict


def clean_up_external_classes(source_code_json):
    """Remove all classes that inherit from external classes outside of the source code"""

    clean_start = timer()
    all_pkgs = return_all_distinct_package_names(source_code_json[CLASSES])
    root_pkg = ""
    if all_pkgs:
        root_pkg = all_pkgs[0]

    # Construct a new dictionary without external classes.
    new_dict = {"relations": [], CLASSES: []}
    for class_dict in source_code_json[CLASSES]:
        filtered_inherits = [
            class_name
            for class_name in class_dict["_4"]
            if class_name.replace(root_pkg, "") == class_name
            and "java" not in class_name
        ]
        if not filtered_inherits:
            class_dict["_4"] = []
            new_dict[CLASSES].append(class_dict)

    clean_end = timer()
    clean_diff = clean_end - clean_start
    logging.info(
        "All external classes (if any) have been excluded from the source code json. Completed in {0} seconds.".format(
            format(clean_diff, DEC_FORMATTER)
        )
    )
    return new_dict


def create_log_dictionary(info_msg="", debug_msg="", error_msg=""):
    return dict(info_msg=info_msg, debug_msg=debug_msg, error_msg=error_msg)


def create_loggers():
    """Create two loggers, one for handling info and error messages and will be read by JoernServer (main_logger) which outputs
    logs to `joern_query.log` and another logger for debug information outputted to `debug.log`"""

    LOG_FORMAT = "%(asctime)s %(levelname)s %(message)s"
    LOG_DATE_FORMAT = "%m/%d/%Y %I:%M:%S"
    MAIN_LOG_FILE = "joern_query.log"
    DEBUG_LOG_FILE = "debug.log"
    LOG_FORMATTER = logging.Formatter(fmt=LOG_FORMAT, datefmt=LOG_DATE_FORMAT)

    # Configure main logger
    main_logger = logging.getLogger()
    main_handler = logging.FileHandler(filename=MAIN_LOG_FILE, mode="w")
    main_handler.setFormatter(LOG_FORMATTER)
    main_handler.setLevel(logging.INFO)
    main_logger.addHandler(main_handler)
    main_logger.setLevel(logging.INFO)

    # Configure debug logger
    debug_logger = logging.getLogger()
    debug_handler = logging.FileHandler(filename=DEBUG_LOG_FILE, mode="w")
    debug_handler.setFormatter(LOG_FORMATTER)
    debug_handler.setLevel(logging.DEBUG)
    debug_logger.addHandler(debug_handler)
    debug_logger.setLevel(logging.DEBUG)

    return main_logger, debug_logger


def create_class_bundles(name_ast_size_res):
    """Create class bundles for every class within the provided directory.

    These class bundles contain the class's full name, total ast size given by Joern,
    attribute ast size, method ast size and the real ast size which is a combination of attribute and method ast size.
    """

    all_classes = []
    for entry in name_ast_size_res:
        full_name = entry["_1"]
        class_ast_size = entry["_2"]
        attribute_ast_size = entry["_3"]
        method_ast_sizes = entry["_4"]
        total_methods = len(method_ast_sizes)
        total_method_size = sum(method_ast_sizes)
        real_ast_size = attribute_ast_size + total_method_size
        class_bundle = {
            "classFullName": full_name,
            "totalAstSize": class_ast_size,
            "attributeAstSize": attribute_ast_size,
            "totalMethods": total_methods,
            "methodAstSize": total_method_size,
            "realAstTotal": real_ast_size,
        }
        all_classes.append(class_bundle)
    all_classes.sort(
        key=lambda entry: (
            entry["methodAstSize"],
            entry["totalAstSize"],
            entry["totalMethods"],
            entry["attributeAstSize"],
        )
    )
    return all_classes
