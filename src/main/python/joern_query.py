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


# Return all of the unique collection types present within the code base
def return_collection_types(all_attributes):
    collection_types = {
        attribute_dict["packageName"].replace("java.util.", "").strip()
        for attribute_dict in all_attributes
        if "java.util" in attribute_dict["packageName"]
    }
    return collection_types


# Return all distinct package names as a set
def return_all_distinct_package_names(all_classes):
    package_names = {class_dict["packageName"] for class_dict in all_classes}
    return sorted(package_names, key=str)


# Read a file from the file path that Joern gives and return a list of all the lines
def read_file(file_path):
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
    class_type = class_dict["classType"]
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

        attribute["parentClassName"] = class_name
        attribute["code"] = attribute_code
        attribute["attributeType"] = attribute_type
        attribute["modifiers"] = attribute_modifiers

    for method in class_dict["methods"]:
        method["parentClassName"] = class_name

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
        "parentClassName": "",
        "name": attribute_name,
        "code": "",
        "lineNumber": attribute_line_number,
        "packageName": package_name,
        "modifiers": [modifier.lower() for modifier in attribute_modifiers],
        "attributeType": type,
        "typeList": [],
    }
    return curr_attribute_dict


# For every method, create a dictionary and return it.
def create_method_dict(curr_method):
    method_name = curr_method["_1"]
    method_code = curr_method["_2"]
    method_line_number = int(curr_method["_3"])
    method_line_number_end = int(curr_method["_4"])
    total_lines = method_line_number_end - method_line_number
    method_signature = curr_method["_5"]
    method_modifiers = [modifier.lower() for modifier in curr_method["_6"]]
    if not method_modifiers:
        method_modifiers = ["package private"]
    method_parameters = curr_method["_7"]
    method_instructions = curr_method["_8"]

    if "<empty>" in method_code or "<lambda>" in method_name:
        return
    else:
        # Get the modifiers, return type and the method body from the full method body provided by Joern.
        modifiers_pattern = re.compile(
            "(private|public|protected|static|final|synchronized|virtual|volatile|abstract|native)"
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
                evaluation_strategy = parameter["_1"]
                code = parameter["_2"]
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
            set_regex = set(regex_pattern_modifiers)
            set_joern = set(joern_modifiers)
            difference = set_regex - set_joern
            return regex_pattern_modifiers + list(difference)

        curr_method_dict = {
            "parentClassName": "",
            "code": method_code,
            "lineNumberStart": method_line_number,
            "lineNumberEnd": method_line_number_end,
            "totalMethodLength": total_lines,
            "name": method_name.replace("<init>", constructor_name),
            "modifiers": get_method_modifiers(
                regex_pattern_modifiers, method_modifiers
            ),
            "returnType": return_type,
            "methodBody": method_body,
            "parameters": get_method_parameters(method_parameters),
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
    instruction_call_type_full_name = curr_instruction["_5"]

    if "<empty>" in instruction_code:
        return
    else:
        # Get the method call in each line of code, if any.
        method_call_pattern = re.compile(r"([a-zA-Z]*\()")
        calls = method_call_pattern.findall(instruction_code)
        method_call = ""
        if calls and instruction_label == "CALL":
            method_call = calls[0].replace("(", "")
            method_call = method_call.replace("super", "<init>")
            call_list = [
                item.split(":")[0]
                for item in instruction_call_full_names
                if method_call in item and "java" not in item
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
    class_methods = curr_class["_8"]
    file_name = curr_class["_9"]

    # Get the type of the object, either a interface, class, enum or abstract class.
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
    query = 'cpg.typeDecl.isExternal(false).filter(node => !node.name.contains("lambda")).name.toJson'
    result = client.execute(query)
    class_names = []
    if result["success"]:
        print(result)
        index = result["stdout"].index('"')
        all_names = json.loads(
            json.loads(result["stdout"][index: len(result["stdout"])])
        )
        # add packages (node.fullName to make sure any classes that inherit from external libraries are removed)
        class_names = [name.replace("$", ".") for name in all_names]
    else:
        print("joern_query :: Retrieve class names failure", file=sys.stderr)
        exit(1)
    return class_names


# Execute a single query to get all the data of a class
def retrieve_class_data(name):
    class_query = (
            'cpg.typeDecl.isExternal(false).name("'
            + name
            + '").map(node => (node.name, node.fullName, '
              "node.inheritsFromTypeFullName.l, node.code, "
              "node.lineNumber,"
              "node.astChildren.isModifier.modifierType.l, "
              "node.astChildren.isMember.l.map(node => (node.name, "
              "node.typeFullName,"
              "node.code, node.lineNumber, "
              "node.astChildren.isModifier.modifierType.l)),"
              "node.astChildren.isMethod.filter(node => "
              "node.lineNumber != None"
              "&& node.lineNumberEnd != None).l.map(node => ("
              "node.name, node.code,"
              "node.lineNumber, node.lineNumberEnd, node.signature, "
              "node.astChildren.isModifier.modifierType.l, "
              "node.astChildren.isParameter.filter(node => "
              "!node.name.contains("
              '"this")).l.map(node => (node.evaluationStrategy, '
              "node.code, node.name, "
              "node.typeFullName)), node.ast.filter(node => node.lineNumber != None).l.map(node => ("
              "node.label, node.code,"
              "node.lineNumber, node.ast.isCall.methodFullName.l, node.ast.isCall.typeFullName.l)))), node.filename)).toJson"
    )
    start = time.time()
    result = client.execute(class_query)
    end = time.time()
    if result["success"] and result["stdout"] is not "":
        logging.info(
            "The class data for "
            + name.replace(".", "$")
            + " has been retrieved. Completed in {0} seconds.".format(
                format(end - start, ".2f")
            )
        )
        index = result["stdout"].index('"')
        # Returns a list of dictionaries, extract first element of that list
        joern_class_data = json.loads(
            json.loads(result["stdout"][index: len(result["stdout"])])
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
            class_dict["inheritsFrom"] = [
                className.split(".")[-1] for className in class_dict["inheritsFrom"]
            ]
            new_dict["classes"].append(class_dict)
    return new_dict


# Update all the type lists for each attribute and method parameter within cpg
def update_type_lists(source_code_json):
    all_attributes = [
        attribute
        for class_dict in source_code_json["classes"]
        for attribute in class_dict["attributes"]
    ]
    all_parameters = [
        parameter
        for class_dict in source_code_json["classes"]
        for method in class_dict["methods"]
        for parameter in method["parameters"]
    ]
    collection_types = return_collection_types(all_attributes)
    regex_str = "|".join(collection_types) + "|\[]|<|>|,"
    regex_pattern = re.compile(regex_str)

    def return_type_lists(type_to_analyze):
        type_list = [type_to_analyze]
        new_type = re.sub(regex_pattern, "", type_to_analyze)
        if len(new_type) != len(type_to_analyze):
            type_list = new_type.split()
        return type_list

    # Obtain type lists for all attributes
    for attribute in all_attributes:
        attribute["typeList"] = return_type_lists(attribute["attributeType"])
    # Obtain type lists for all method parameters
    for method_parameter in all_parameters:
        method_parameter["typeList"] = return_type_lists(method_parameter["type"])
    return source_code_json


def source_code_json_creation(class_names):
    source_code_json = {"relations": [], "classes": []}
    for class_name in class_names:
        source_code_json["classes"].append(retrieve_class_data(class_name))
    # Get all the type lists of method params & attributes within cpg (i.e. HashMap<CPGClass, Integer> should return
    # ["CPGClass", "Integer"]
    source_code_json = update_type_lists(source_code_json)
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

    server_endpoint = "localhost:" + sys.argv[-1]
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

    if result["success"]:
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
        print("joern_query :: Source Code Import Failure", file=sys.stderr)
        exit(1)
