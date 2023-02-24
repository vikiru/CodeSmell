import os
import re

# Helper constants that are defined more than once within this file.
INIT_METHOD = "<init>"

ABSTRACT_CLASS = "abstract class"
CLASS = "class"
INTERFACE = "interface"
ENUM = "enum"

PACKAGE_PRIVATE = "package private"

# Space is needed to pickup, ABSTRACT_CLASS and not "class abstractClass" for example
MODIFIERS_PATTERN = re.compile(
    "(private |public |protected |static |final |synchronized |virtual |volatile |abstract |native )"
)

# Remove package and any nesting in a class full name to get the proper name of the class.
def return_name_without_package(class_full_name):
    name = class_full_name
    if "." in class_full_name:
        index = class_full_name.rindex(".")
        name = class_full_name[index + 1 :]
    if "$" in name:
        index = name.rindex("$")
        name = name[index + 1 :]
    return name


# Determine package name from the joern_json (prior to creation of dictionary)
def return_package_name(curr_class):
    class_full_name = curr_class["_2"]
    replace_str = "." + class_full_name
    return class_full_name.replace(replace_str, "").strip()


# Return all distinct package names as a set
def return_all_distinct_package_names(all_classes):
    package_names = {class_dict["packageName"] for class_dict in all_classes}
    return sorted(package_names, key=str)


# Read a file from the file path that Joern gives and return a list of all the lines
def read_file(file_path):
    if os.path.exists(file_path):
        java_file = open(file_path, "r")
        lines = java_file.read().splitlines()
        java_file.close()
        return lines
    else:
        return []


# Assign missing class info to each class dictionary
def assign_missing_class_info(class_dict, file_lines):
    class_name = class_dict["name"]
    class_decl_line_number = int(class_dict["lineNumber"])

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
        line_number = attribute["lineNumber"]
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
            if "public" in class_modifiers:
                attribute_modifiers.append("public")
            elif "private" in class_modifiers:
                attribute_modifiers.append("private")
            attribute_modifiers.append("final")
            attribute_modifiers.append("static")
            attribute_type = existing_type

        attribute["code"] = attribute_code
        attribute["attributeType"] = attribute_type
        attribute["modifiers"] = attribute_modifiers

    # Cleanup default constructors
    for method in class_dict["methods"]:
        if method["name"] == "" and method["returnType"] == "<empty>":
            method["name"] = class_name
            method["methodBody"] = class_name + "()"
            method["returnType"] = ""

    existing_full_name = class_dict["classFullName"].replace(package_name, "")
    new_full_name = existing_full_name.replace(".", "").replace("$", ".").strip()
    total_file_length = len(file_lines)
    empty_lines = len([line for line in file_lines if line == ""])
    non_empty_lines = total_file_length - empty_lines
    class_dict["classFullName"] = new_full_name
    class_dict["classType"] = class_type
    class_dict["code"] = class_declaration
    class_dict["fileLength"] = total_file_length
    class_dict["emptyLines"] = empty_lines
    class_dict["nonEmptyLines"] = non_empty_lines
    class_dict["importStatements"] = import_statements
    class_dict["modifiers"] = class_modifiers
    class_dict["packageName"] = package_name

    return class_dict


# For every attribute, create a dictionary and return it.
def create_attribute_dict(curr_attribute):
    attribute_name = curr_attribute["_1"]
    attribute_type_full_name = curr_attribute["_2"]
    attribute_line_number = int(curr_attribute["_3"])
    attribute_modifiers = curr_attribute["_4"]
    if not attribute_modifiers:
        attribute_modifiers = [PACKAGE_PRIVATE]

    index = attribute_type_full_name.rfind(".")
    attribute_type = attribute_type_full_name
    package_name = ""
    if index != -1:
        package_name = attribute_type_full_name.replace("[]", "").replace("$", ".")
        attribute_type = attribute_type_full_name[
            index + 1 : len(attribute_type_full_name)
        ]
    index_nested = attribute_type.rfind("$")
    if index_nested != -1:
        attribute_type = attribute_type[index_nested + 1 : len(attribute_type)]

    curr_attribute_dict = {
        "name": attribute_name,
        "parentClass": [{}],
        "packageName": package_name,
        "code": "",
        "lineNumber": attribute_line_number,
        "modifiers": [modifier.lower() for modifier in attribute_modifiers],
        "attributeType": attribute_type,
        "typeList": [],
    }

    return curr_attribute_dict


# Return all the method modifiers, combining joern and the modifiers obtained from regex
def get_method_modifiers(regex_pattern_modifiers, joern_modifiers):
    regex_pattern_modifiers = [mod.strip() for mod in regex_pattern_modifiers]
    joern_modifiers = [mod.strip() for mod in joern_modifiers]
    final_set = set(regex_pattern_modifiers).union(set(joern_modifiers))
    final_list = list(final_set)
    return final_list


# Return a list containing dictionaries for each parameter within the method body
def get_method_parameters(parameters):
    parameters_list = []
    for parameter in parameters:
        code = parameter["_1"]
        parameter_type = code.split(" ")[0]
        name = code.split(" ")[1]
        parameter_dict = {
            "code": code,
            "name": name,
            "type": parameter_type,
            "typeList": [],
        }
        parameters_list.append(parameter_dict)
    return parameters_list


# For every method, create a dictionary and return it.
def create_method_dict(curr_method):
    method_name = curr_method["_1"]
    method_code = curr_method["_2"]

    # Default values for default constructor
    method_line_number = 0
    method_line_number_end = 0
    total_lines = 0
    method_modifiers = [
        modifier.lower() for modifier in curr_method["_5"] if modifier != "CONSTRUCTOR"
    ]
    method_parameters = []
    method_instructions = []

    # Handle normal method cases
    if len(curr_method) == 7:
        method_line_number = int(curr_method["_3"])
        method_line_number_end = int(curr_method["_4"])
        total_lines = (method_line_number_end - method_line_number) + 1
        if not method_modifiers and method_name != INIT_METHOD:
            method_modifiers = [PACKAGE_PRIVATE]
        method_parameters = curr_method["_6"]
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
        "name": method_name.replace(INIT_METHOD, constructor_name),
        "parentClass": [{}],
        "methodBody": method_body,
        "modifiers": get_method_modifiers(regex_pattern_modifiers, method_modifiers),
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


# For every method's instruction, create a dictionary and return it.
def create_instruction_dict(curr_instruction):
    instruction_label = curr_instruction["_1"]
    instruction_code = curr_instruction["_2"]
    instruction_line_number = curr_instruction["_3"]
    # instruction_call_full_names = curr_instruction["_4"] #TODO: leave blank for now,

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
            index = method_call.rfind(".")
            method_call = method_call[:index] + method_call[index:].replace(".", "$")
            method_call = method_call.split(".")[-1].replace("$", ".")
            class_name, method_name = (
                method_call.split(".")[0],
                method_call.split(".")[1],
            )
            method_call = method_call.replace(INIT_METHOD, class_name)
        else:
            method_call = ""

    curr_instruction_dict = {
        "label": instruction_label,
        "code": instruction_code.replace("\r\n", ""),
        "lineNumber": int(instruction_line_number),
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
    if "$" in name:
        index = name.rindex("$")
        name = name[index + 1 : len(name)]
    return name


# For every class, create a dictionary and return it.
def create_class_dict(curr_class):
    if "_1" in curr_class:
        class_name = curr_class["_1"]
        class_full_name = curr_class["_2"]
        inherits_from_list = curr_class["_3"]
        class_declaration = curr_class["_4"]
        line_number = curr_class["_5"]
        class_attributes = curr_class["_6"]
        file_name = curr_class["_7"]
        class_methods = curr_class["_8"]

        curr_class_dict = {
            "name": get_name_without_separators(class_name),
            "code": "",
            "lineNumber": int(line_number),
            "importStatements": [],
            "modifiers": [],
            "classFullName": class_full_name,
            "inheritsFrom": [],
            "classType": get_class_type(class_declaration),
            "filePath": file_name,
            "fileLength": 0,
            "emptyLines": 0,
            "nonEmptyLines": 0,
            "packageName": "",
            "attributes": list(map(create_attribute_dict, class_attributes)),
            "methods": list(filter(None, list(map(create_method_dict, class_methods)))),
            "outwardRelations": [],
        }

        # Assign all missing info
        curr_class_dict = assign_missing_class_info(
            curr_class_dict, read_file(curr_class_dict["filePath"])
        )

        return curr_class_dict


# Remove all classes that inherit from external packages outside of the source code
def clean_up_external_classes(source_code_json):
    all_pkgs = return_all_distinct_package_names(source_code_json["classes"])
    root_pkg = ""
    if all_pkgs:
        root_pkg = all_pkgs[0]
    new_dict = {"relations": [], "classes": []}
    for class_dict in source_code_json["classes"]:
        filtered_inherits = [
            class_name
            for class_name in class_dict["_3"]
            if class_name.replace(root_pkg, "") == class_name
            and "java" not in class_name
        ]
        if not filtered_inherits:
            class_dict["_3"] = []
            new_dict["classes"].append(class_dict)
    return new_dict
