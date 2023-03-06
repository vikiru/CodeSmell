import sys
import json
import os
import platform
import re
import time
from pathlib import Path
from time import sleep
from cpgqls_client import CPGQLSClient, import_code_query
import logging


# Return all distinct package names as a set
def return_all_distinct_package_names(all_classes):
    package_names = {class_dict["packageName"] for class_dict in all_classes}
    return sorted(package_names, key=str)


# Read a file from the file path that Joern gives and return a list of all the lines
def read_file(file_path):
    if os.path.exists(file_path):
        file = open(file_path, "r")
        lines = file.read().splitlines()
        file.close()
        return lines


# Assign missing class info to each class dictionary
def assign_missing_class_info(class_dict, file_lines):
    class_name = class_dict["name"]
    class_decl_line_number = int(class_dict["lineNumber"])
    # Space is needed to pickup, "abstract class" and not "class abstractClass" for example.
    modifiers_pattern = re.compile(
        "(private |public |protected |static |final |synchronized |virtual |volatile |abstract |native )"
    )
    package_name = ""
    if file_lines[0].startswith("package"):
        package_name = file_lines[0]
    package_name = package_name.replace(";", "").replace("package ", "").strip()
    import_statements = [line for line in file_lines if "import" in line]
    class_declaration = file_lines[class_decl_line_number - 1]
    class_declaration = class_declaration.replace("{", "").strip()
    class_modifiers = [
        modifier.strip() for modifier in modifiers_pattern.findall(class_declaration)
    ]
    class_type = class_dict["classType"].strip()
    if "abstract" in class_modifiers:
        class_type = "abstract class"

    for attribute in class_dict["attributes"]:
        existing_type = attribute["attributeType"]
        line_number = attribute["lineNumber"]
        attribute_code = file_lines[line_number - 1].strip()
        attribute_modifiers = [
            modifier.strip() for modifier in modifiers_pattern.findall(attribute_code)
        ]
        attribute_type = re.sub(modifiers_pattern, "", attribute_code).strip()
        # Handle hashmap types
        attribute_type = attribute_type.replace(", ", "|")
        attribute_type = attribute_type.split()[0].replace("|", ", ")

        if not attribute_modifiers and "enum" not in class_type:
            attribute_modifiers = ["package private"]
        elif "enum" in class_type and existing_type == class_name:
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
    empty_lines = len([line for line in file_lines if line is ""])
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
    attribute_code = curr_attribute["_3"]
    attribute_line_number = int(curr_attribute["_4"])
    attribute_modifiers = curr_attribute["_5"]
    if not attribute_modifiers:
        attribute_modifiers = ["package private"]

    index = attribute_type_full_name.rfind(".")
    type = attribute_type_full_name
    package_name = ""
    if index != -1:
        package_name = attribute_type_full_name.replace("[]", "").replace("$", ".")
        type = attribute_type_full_name[index + 1: len(attribute_type_full_name)]
    index_nested = type.rfind("$")
    if index_nested != -1:
        type = type[index_nested + 1: len(type)]

    curr_attribute_dict = {
        "name": attribute_name,
        "parentClass": [{}],
        "packageName": package_name,
        "code": "",
        "lineNumber": attribute_line_number,
        "modifiers": [modifier.lower() for modifier in attribute_modifiers],
        "attributeType": type,
        "typeList": [],
    }
    return curr_attribute_dict


# For every method, create a dictionary and return it.
def create_method_dict(curr_method):
    method_name = curr_method["_1"]
    method_code = curr_method["_2"]
    # Default values for default constructor
    method_line_number = 0
    method_line_number_end = 0
    total_lines = 0
    method_modifiers = [modifier.lower() for modifier in curr_method["_5"] if modifier != "CONSTRUCTOR"]
    method_parameters = []
    method_instructions = []
    if len(curr_method) == 7:
        method_line_number = int(curr_method["_3"])
        method_line_number_end = int(curr_method["_4"])
        total_lines = (method_line_number_end - method_line_number) + 1
        if not method_modifiers and method_name != "<init>":
            method_modifiers = ["package private"]
        method_parameters = curr_method["_6"]
        method_instructions = curr_method["_7"]

    # Get the modifiers, return type and the method body from the full method body provided by Joern.
    modifiers_pattern = re.compile(
        "(private |public |protected |static |final |synchronized |virtual |volatile |abstract |native )"
    )
    regex_pattern_modifiers = modifiers_pattern.findall(method_code)

    method_with_return = re.sub(modifiers_pattern, "", method_code).strip()

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
        return_type = method_body[0: index + 1]
        if "(" in return_type or not return_type:
            return_type = ""
        if return_type in method_body:
            method_body = method_body.replace(return_type, "").strip()

    # If the method is a constructor, find the name of the class.
    constructor_name = method_name_pattern.findall(method_body)[0]

    # Return a list containing dictionaries for each parameter within the method body
    def get_method_parameters(parameters):
        parameters_list = []
        for parameter in parameters:
            code = parameter["_1"]
            type = code.split(" ")[0]
            name = code.split(" ")[1]
            parameter_dict = {
                "code": code,
                "name": name,
                "type": type,
                "typeList": [],
            }
            parameters_list.append(parameter_dict)
        return parameters_list

    def get_method_modifiers(regex_pattern_modifiers, joern_modifiers):
        regex_pattern_modifiers = [mod.strip() for mod in regex_pattern_modifiers]
        joern_modifiers = [mod.strip() for mod in joern_modifiers]
        final_set = set(regex_pattern_modifiers).union(set(joern_modifiers))
        final_list = list(final_set)
        return final_list

    curr_method_dict = {
        "name": method_name.replace("<init>", constructor_name),
        "parentClass": [{}],
        "methodBody": method_body,
        "modifiers": get_method_modifiers(
            regex_pattern_modifiers, method_modifiers
        ),
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
    instruction_call_full_names = curr_instruction["_4"]

    # Get the method call in each line of code, if any.
    method_call_pattern = re.compile(r"([a-zA-Z]*\()")
    calls = method_call_pattern.findall(instruction_code)
    method_call = ""
    acceptable_labels = ["CALL", "RETURN"]
    if calls and instruction_label in acceptable_labels:
        method_call = calls[0].replace("(", "")
        method_call = method_call.replace("super", "<init>")
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
            method_call = method_call[:index] + method_call[index:].replace(
                ".", "$"
            )
            method_call = method_call.split(".")[-1].replace("$", ".")
            class_name, method_name = method_call.split(".")[0], method_call.split(".")[1]
            method_call = method_call.replace("<init>", class_name)
        else:
            method_call = ""
    curr_instruction_dict = {
        "label": instruction_label,
        "code": instruction_code.replace("\r\n", ""),
        "lineNumber": int(instruction_line_number),
        "methodCall": method_call,
    }
    return curr_instruction_dict


# For every class, create a dictionary and return it.
def create_class_dict(curr_class):
    class_name = curr_class["_1"]
    class_full_name = curr_class["_2"]
    inherits_from_list = curr_class["_3"]
    class_declaration = curr_class["_4"]
    line_number = curr_class["_5"]
    class_modifiers = curr_class["_6"]
    class_attributes = curr_class["_7"]
    file_name = curr_class["_8"]
    class_methods = curr_class["_9"]

    # Get the type of the object, either an interface, class, enum or abstract class.
    def get_type(declaration):
        if "abstract class" in declaration:
            return "abstract class"
        elif "class" in declaration:
            return "class"
        elif "enum" in declaration:
            return "enum"
        elif "interface" in declaration:
            return "interface"

    def get_name_without_separators(name):
        if "$" in name:
            index = name.rindex("$")
            name = name[index + 1: len(name)]
        return name

    curr_class_dict = {
        "name": get_name_without_separators(class_name),
        "code": "",
        "lineNumber": int(line_number),
        "importStatements": [],
        "modifiers": [],
        "classFullName": class_full_name,
        "inheritsFrom": inherits_from_list,
        "classType": get_type(class_declaration),
        "filePath": file_name,
        "fileLength": 0,
        "emptyLines": 0,
        "nonEmptyLines": 0,
        "packageName": "",
        "attributes": list(map(create_attribute_dict, class_attributes)),
        "methods": list(filter(None, list(map(create_method_dict, class_methods)))),
        "outwardRelations": [],
    }
    curr_class_dict = assign_missing_class_info(
        curr_class_dict, read_file(curr_class_dict["filePath"])
    )
    return curr_class_dict


# Execute a single query to retrieve all the class names within the source code
def retrieve_all_class_names():
    query = 'cpg.typeDecl.isExternal(false).filter(node => !node.name.contains("lambda")).fullName.toJson'
    result = client.execute(query)
    class_names = []
    if result["success"] and result["stdout"]:
        index = result["stdout"].index('"')
        all_names = json.loads(
            json.loads(result["stdout"][index: len(result["stdout"])])
        )
        class_names = [name.replace("$", ".") for name in all_names]
    else:
        print("joern_query :: Retrieve class names failure", file=sys.stderr)
        print("joern_query :: ",result, file=sys.stderr)
        exit(1)
    return class_names


# Execute a single query to get all the data of a class
def retrieve_class_data(name):
    class_query = 'cpg.typeDecl.isExternal(false).fullName("' + name \
                  + '").map(node => (node.name, node.fullName, node.inheritsFromTypeFullName.l, node.code, ' \
                    "node.lineNumber, node.astChildren.isModifier.modifierType.l, node.astChildren.isMember.l.map(" \
                    "node => (node.name, node.typeFullName, node.code, node.lineNumber, " \
                    "node.astChildren.isModifier.modifierType.l)), node.filename," \
                    "node.astChildren.isMethod.filter(node => " \
                    '!node.code.contains("<lambda>") && ' \
                    '!node.name.contains("<clinit>")).l.map(' \
                    "node => (node.name, node.code, " \
                    "node.lineNumber, node.lineNumberEnd, " \
                    "node.astChildren.isModifier" \
                    ".modifierType.l, " \
                    "node.astChildren.isParameter.filter(" \
                    "node => !node.name.contains(" \
                    '"this")).l.map(node => (node.code, ' \
                    "node.name, node.typeFullName)), " \
                    "node.ast.filter(node => node.lineNumber != None).l.map(node => (node.label, " \
                    "node.code, node.lineNumber, " \
                    "node.ast.isCall.methodFullName.l" \
                    ")))))).toJson"
    start = time.time()
    result = client.execute(class_query)
    end = time.time()
    if result["success"] and result["stdout"] is not "":
        index = result["stdout"].index('"')
        # Returns a list of dictionaries, extract first element of that list
        joern_class_data = json.loads(
            json.loads(result["stdout"][index: len(result["stdout"])])
        )
        name = joern_class_data[0]["_1"]
        logging.info(
            "The class data for "
            + name.replace(".", "$")
            + " has been retrieved. Completed in {0} seconds.".format(
                format(end - start, ".2f")
            )
        )
        class_dict = create_class_dict(joern_class_data[0])
    else:
        print("joern_query :: Retrieve class data failure for " + name, file=sys.stderr)
        exit(1)
    return class_dict


def clean_up_external_classes(source_code_json):
    root_pkg = return_all_distinct_package_names(source_code_json["classes"])[0]
    new_dict = {"relations": [], "classes": []}
    for class_dict in source_code_json["classes"]:
        filtered_inherits = [
            class_name
            for class_name in class_dict["inheritsFrom"]
            if class_name.replace(root_pkg, "") == class_name
               and "java" not in class_name
        ]
        if not filtered_inherits:
            class_dict["inheritsFrom"] = []
            new_dict["classes"].append(class_dict)
    return new_dict


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
    project_name = "analyzedProject"

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
    print("joern_query :: project_dir " + project_dir, file=sys.stderr)

    if "Windows" in platform.platform():
        index = project_dir.find(":")
        win_drive = project_dir[0: index + 1]
        project_dir = project_dir.replace(win_drive, win_drive.upper()).replace(
            "\\", "//"
        )

    # Import the source code to Joern for analyzing.
    start = time.time()
    query = import_code_query(project_dir, project_name)
    result = client.execute(query)
    end = time.time()

    if result["success"] and not result["stderr"]:
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
            print("joern_query :: Retrieve class names failure", file=sys.stderr)
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
        if result["success"]:
            logging.info(
                "The source code has been successfully removed. Completed in {0} seconds.".format(
                    format(end - start, ".2f")
                )
            )
        logging.info("Total time taken: {0} seconds.".format(format(total_time, ".2f")))
        exit(0)
    else:
        print("joern_query :: Source Code Import Failure", result, file=sys.stderr)
        exit(1)
