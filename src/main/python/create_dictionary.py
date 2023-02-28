import os
import re
import logging
from timeit import default_timer as timer

# Helper constants
INIT_METHOD = "<init>"
NESTED_SEP = "$"
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
LOG_FORMATTER = "%(asctime)s %(levelname)s %(message)s"
LOG_FILE = "joern_query.log"

NAME = "name"
CODE = "code"
MODIFIERS = "modifiers"
LINE_NUM = "lineNumber"

# Space is needed to pickup, ABSTRACT_CLASS and not "class abstractClass" for example
MODIFIERS_PATTERN = re.compile(
    "(private |public |protected |static |final |synchronized |virtual |volatile |abstract |native )"
)


def sorted_dictionary(list_d):
    """Given a list of dictionaries, sort by value and combine into a single sorted dictionary."""

    unsorted_dict = {key: val for name in list_d for key, val in name.items()}
    sorted_tuple = sorted(unsorted_dict.items(), key=lambda entry: entry[1])
    sorted_dict = dict((key, val) for key, val in sorted_tuple)
    return sorted_dict


def return_method_name(full_name):
    """Return the name of a method without the full name + method signature. Additionally return the class it belongs to"""

    index_signature = full_name.index(":")
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
    name = class_full_name
    if DOT_SEP in class_full_name:
        index = class_full_name.rindex(DOT_SEP)
        name = class_full_name[index + 1 :]
    if NESTED_SEP in name:
        index = name.rindex(NESTED_SEP)
        name = name[index + 1 :]
    return name


def return_package_name(class_full_name):
    """Determine package name from the joern_json (prior to creation of dictionary)"""

    package_name = ""
    if DOT_SEP in class_full_name:
        index = class_full_name.rindex(".")
        package_name = class_full_name[:index].strip()
    return package_name


def return_all_distinct_package_names(all_classes):
    """Return all distinct package names as a set"""

    package_names = {class_dict["packageName"] for class_dict in all_classes}
    return sorted(package_names, key=str)


def read_file(file_path):
    """Read a file from the file path that Joern gives and return a list of all the lines"""
    if os.path.exists(file_path):
        java_file = open(file_path, "r")
        lines = java_file.read().splitlines()
        java_file.close()
        return lines
    else:
        return []


def assign_missing_class_info(class_dict, file_lines):
    """Assign missing class info to each class dictionary"""

    class_name = class_dict[NAME]
    class_decl_line_number = int(class_dict[LINE_NUM])

    package_name = ""
    if file_lines[0].startswith("package"):
        package_name = file_lines[0]
    package_name = package_name.replace(";", "").replace("package ", "").strip()
    import_statements = [line for line in file_lines if "import" in line]
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
        "parentClass": [{}],
        "packageName": package_name,
        CODE: "",
        LINE_NUM: attribute_line_number,
        MODIFIERS: [modifier.lower() for modifier in attribute_modifiers],
        "attributeType": attribute_type,
        "typeList": [],
    }

    return curr_attribute_dict


def get_method_modifiers(regex_pattern_modifiers, joern_modifiers):
    """Return all the method modifiers, combining joern and the modifiers obtained from regex"""
    regex_pattern_modifiers = [mod.strip() for mod in regex_pattern_modifiers]
    joern_modifiers = [mod.strip() for mod in joern_modifiers]
    final_set = set(regex_pattern_modifiers).union(set(joern_modifiers))
    final_list = list(final_set)
    return final_list


def get_method_parameters(parameters):
    """Return a list containing dictionaries for each parameter within the method body"""
    parameters_list = []
    for parameter in parameters:
        code = parameter["_1"]
        parameter_type = code.split(" ")[0]
        name = code.split(" ")[1]
        parameter_dict = {
            CODE: code,
            NAME: name,
            "type": parameter_type,
            "typeList": [],
        }
        parameters_list.append(parameter_dict)
    return parameters_list


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

    # Handle normal method cases
    if len(curr_method) == 6:
        method_line_number = int(curr_method["_4"])
        method_line_number_end = int(curr_method["_"])
        total_lines = (method_line_number_end - method_line_number) + 1
        if not method_modifiers and method_name != INIT_METHOD:
            method_modifiers = [PACKAGE_PRIVATE]
        method_instructions = curr_method["_7"]

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
        index = method_body.find(">")
        return_type = method_body[0 : index + 1]
        if "(" in return_type or not return_type:
            return_type = ""
        if return_type in method_body:
            method_body = method_body.replace(return_type, "").strip()

    # If the method is a constructor, find the name of the class.
    constructor_name = method_name_pattern.findall(method_body)[0]

    curr_method_dict = {
        NAME: method_name.replace(INIT_METHOD, constructor_name),
        "parentClass": [{}],
        "methodBody": method_body,
        MODIFIERS: get_method_modifiers(regex_pattern_modifiers, method_modifiers),
        "parameters": get_method_parameters(method_parameters),
        "returnType": return_type,
        "lineNumberStart": method_line_number,
        "lineNumberEnd": method_line_number_end,
        "totalMethodLength": total_lines,
        "instructions": list(
            filter(
                None,
                list(map(create_instruction_dict, method_instructions)),
            )
        ),
        "methodCalls": [],
        "attributeCalls": [],
    }

    return curr_method_dict


def create_instruction_dict(curr_instruction):
    """For every method's instruction, create a dictionary and return it."""

    instruction_label = curr_instruction["_1"]
    instruction_code = curr_instruction["_2"]
    instruction_line_number = curr_instruction["_3"]
    instruction_call_full_names = curr_instruction["_4"]

    # Get the method call in each line of code, if any.
    method_call_pattern = re.compile(r"([a-zA-Z]*\()")
    calls = method_call_pattern.findall(instruction_code)
    method_call = ""
    acceptable_labels = ["CALL", "RETURN"]
    if calls and instruction_label in acceptable_labels:
        method_call = calls[0].replace("(", "")
        method_call = method_call.replace("super", INIT_METHOD)
        call_list = [
            item.split(":")[0]
            for item in instruction_call_full_names
            if method_call in item
        ]
        # Account for cases where two classes could have the same method names (additionally exclude names
        # matching java): ClassA.getA() and ClassB.getA() so the returned method_call would be able to tell:
        # "ClassA.getA" was called. instead of just "getA"
        if call_list and method_call:
            method_call = call_list[0]
            index = method_call.rfind(DOT_SEP)
            method_call = method_call[:index] + method_call[index:].replace(
                DOT_SEP, NESTED_SEP
            )
            method_call = method_call.split(DOT_SEP)[-1].replace(NESTED_SEP, DOT_SEP)
            class_name, method_name = (
                method_call.split(DOT_SEP)[0],
                method_call.split(DOT_SEP)[1],
            )
            method_call = method_call.replace(INIT_METHOD, class_name)
        else:
            method_call = ""

    curr_instruction_dict = {
        "label": instruction_label,
        CODE: instruction_code.replace("\r\n", ""),
        LINE_NUM: int(instruction_line_number),
        "methodCall": method_call,
    }

    return curr_instruction_dict


# Get the type of the object, either an interface, class, enum or abstract class.
def get_class_type(declaration):
    if ABSTRACT_CLASS in declaration:
        return ABSTRACT_CLASS
    elif CLASS in declaration:
        return CLASS
    elif ENUM in declaration:
        return ENUM
    elif INTERFACE in declaration:
        return INTERFACE


# Return the name of a class without the separators, this is used mainly for nested classes.
def get_name_without_separators(name):
    if NESTED_SEP in name:
        index = name.rindex(NESTED_SEP)
        name = name[index + 1 : len(name)]
    return name


def create_class_dict(curr_class):
    """For every class, create a dictionary and return it."""

    class_name = curr_class["_1"]
    class_full_name = curr_class["_2"]
    file_name = curr_class["_3"]
    inherits_from_list = curr_class["_4"]
    class_declaration = curr_class["_5"]
    line_number = curr_class["_6"]
    class_attributes = curr_class["_7"]
    class_methods = curr_class["_8"]

    curr_class_dict = {
        NAME: get_name_without_separators(class_name),
        CODE: "",
        LINE_NUM: int(line_number),
        "importStatements": [],
        MODIFIERS: [],
        "classFullName": class_full_name,
        "inheritsFrom": [],
        "classType": get_class_type(class_declaration),
        "filePath": file_name,
        "fileLength": 0,
        "emptyLines": 0,
        "nonEmptyLines": 0,
        "packageName": "",
        "attributes": list(
            filter(None, list(map(create_attribute_dict, class_attributes)))
        ),
        "methods": list(filter(None, list(map(create_method_dict, class_methods)))),
        "outwardRelations": [],
    }

    # Assign all missing info
    curr_class_dict = assign_missing_class_info(
        curr_class_dict, read_file(curr_class_dict["filePath"])
    )

    return curr_class_dict


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
