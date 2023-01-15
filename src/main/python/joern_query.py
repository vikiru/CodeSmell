import json
import os
import platform
import re
import sys
import time
from pathlib import Path
from cpgqls_client import CPGQLSClient, import_code_query
import logging

# Cleans up json output from joern so that it can be a proper JSON file.
def clean_json(joern_result):
    string_to_find = '"""'
    index = joern_result.find(string_to_find)
    joern_result = joern_result.replace('"""', "")
    joern_result = joern_result[index: len(joern_result)]
    return joern_result

# For every attribute, create a dictionary and return it.
def create_attribute_dict(curr_field):
    type = curr_field["_1"]["typeFullName"]
    package_name = ""
    index = curr_field["_1"]["typeFullName"].rfind(".")
    if index != -1:
        package_name = curr_field["_1"]["typeFullName"][0: index].replace("<unresolvedNamespace>", "")
        type = curr_field["_1"]["typeFullName"][index + 1: len(curr_field["_1"]["typeFullName"])]
    index_nested = type.rfind("$")
    if index_nested != -1:
        type = type[index_nested + 1: len(type)]

    curr_field_dict = {
        "name": curr_field["_1"]["name"],
        "code": "",
        "packageName": package_name,
        "modifiers": [modifier.lower() for modifier in curr_field["_2"]],
        "attributeType": type,
        "typeFullName": curr_field["_1"]["typeFullName"],
    }
    return curr_field_dict


# For every method, create a dictionary and return it.
def create_method_dict(curr_method):
    if curr_method["_1"]["code"] == "<empty>":
        return
    else:
        # Get the modifiers, return type and the method body from the full method body provided by Joern.
        modifiers_pattern = re.compile(
            "(private|public|protected|static|final|synchronized|volatile|abstract|native)"
        )
        method_with_return = re.sub(
            modifiers_pattern, "", curr_method["_1"]["code"]
        ).strip()

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

        # Extract the type and names of all method parameters within the method body.
        def get_method_parameters(method_body):
            param_list = []
            index = method_body.find("(")
            all_parameters = []
            if "<lambda>" not in method_body:
                all_parameters = (
                    method_body[index: len(method_body)]
                    .replace("(", "")
                    .replace(")", "")
                )
                split_params = all_parameters.split()
                maxCalls = len(split_params) / 2

                def map_parameters(split_params, start, iterations):
                    if start + 1 < len(split_params):
                        type = split_params[start].strip()
                        name = split_params[start + 1].strip()
                        param_list.append(dict(name=name, type=type))
                        if iterations - 1 != 0:
                            map_parameters(split_params, start + 2, iterations - 1)

                if split_params:
                    map_parameters(split_params, 0, maxCalls)
            return param_list

        curr_method_dict = {
            "parentClassName": "",
            "code": curr_method["_1"]["code"],
            "name": curr_method["_1"]["name"].replace("<init>", constructor_name),
            "modifiers": modifiers_pattern.findall(curr_method["_1"]["code"]),
            "returnType": return_type,
            "methodBody": method_body,
            "parameters": get_method_parameters(method_body),
            # For the instructions,
            # _2 corresponds to the labels,
            # _3 corresponds to the instructions,
            # _4 corresponds to the lineNumbers
            "instructions": list(
                filter(
                    None,
                    list(
                        map(
                            create_instruction_dict,
                            curr_method["_2"],
                            curr_method["_3"],
                            curr_method["_4"],
                        )
                    ),
                )
            ),
        }
        return curr_method_dict


# For every method's instruction, create a dictionary and return it.
def create_instruction_dict(curr_label, curr_instruction, curr_line_number):
    if curr_instruction == "<empty>":
        return
    else:
        # Get the method call in each line of code, if any.
        method_call_pattern = re.compile(r"([a-zA-Z]*\()")
        calls = method_call_pattern.findall(curr_instruction)
        method_call = ""
        if calls and curr_label == "CALL":
            method_call = calls[0].replace("(", "")

        curr_instruction_dict = {
            "label": curr_label,
            "code": curr_instruction.replace("\r\n", ""),
            "lineNumber": curr_line_number or "none",
            "methodCall": method_call,
        }
        return curr_instruction_dict


# For every class, create a dictionary and return it.
def create_class_dict(curr_class):
    if "lambda" in curr_class["_1"]:
        return
    else:
        # Get the type of the object, either a interface, class, enum or abstract class.
        def get_type(declaration, curr_class_dict):
            if "interface" in declaration:
                return "interface"
            else:
                class_name = curr_class_dict["name"]
                # Get all of the modifiers of a class's methods and combine them into a single list.
                list_method_modifiers = [
                    methods["modifiers"] for methods in curr_class_dict["methods"]
                ]
                single_list_method_modifiers = []
                for list in list_method_modifiers:
                    single_list_method_modifiers.extend(list)

                # Get all of the modifiers and types of each attribute and combine each into a single list.
                list_field_modifiers = [
                    attribute["modifiers"]
                    for attribute in curr_class_dict["attributes"]
                    if not attribute["modifiers"]
                ]
                list_field_types = [
                    attribute["attributeType"]
                    for attribute in curr_class_dict["attributes"]
                    if attribute["attributeType"] == class_name
                ]
                single_list_field_modifiers = []
                single_list_field_types = []
                for list in list_field_modifiers:
                    single_list_field_modifiers.extend(list)
                for list in list_field_types:
                    single_list_field_types.extend(list)

                set_field_types = set(list_field_types)
                if (
                        "class" in declaration
                        and not single_list_field_modifiers
                        and (
                        len(set_field_types) == 1 and class_name in set_field_types
                )
                ):
                    return "enum"
                elif (
                        "class" in declaration
                        and "abstract" in single_list_method_modifiers
                ):
                    return "abstract class"
                else:
                    return "class"

    def get_package_name(file_path):
        package_name = ""
        path_without_separators = file_path.replace(os.sep, " ").split(" ")
        index_of_src = path_without_separators.index("src")
        if index_of_src < 0:
            index_of_src = path_without_separators.index("test")
        if index_of_src < 0:
            raise Exception("joern_query could not parse folder structure. No src/test")
        full_package_name = ".".join(
            path_without_separators[index_of_src: len(path_without_separators)])
        file_name_index = full_package_name.rindex(".java")
        package_name = full_package_name[0: file_name_index]
        package_name = package_name[0: package_name.rindex(".")]

        return package_name

    def get_name_without_separators(class_name):
        name = class_name
        if "$" in class_name:
            index = class_name.rindex("$")
            name = class_name[index + 1: len(class_name)]
        return name

    # _1 corresponds to class name, _2 corresponds to class code declaration (i.e. "public class A")
    # _3 corresponds to class attribute
    # _4 corresponds to class methods
    # _5 corresponds to class filename (full path)
    curr_class_dict = {
        "name": get_name_without_separators(curr_class["_1"]),
        "code": "",
        "importStatements": [],
        "modifiers": [],
        "classFullName": curr_class["_1"],
        "classType": "",
        "filePath": curr_class["_5"],
        "packageName": get_package_name(curr_class["_5"]),
        "attributes": list(map(create_attribute_dict, curr_class["_3"])),
        "methods": list(
            filter(None, list(map(create_method_dict, curr_class["_4"])))
        ),
    }
    curr_class_dict["classType"] = get_type(curr_class["_2"], curr_class_dict)
    for method in curr_class_dict["methods"]:
        method["parentClassName"] = curr_class["_1"]
    return curr_class_dict


# Execute a single joern query to get all the data required to create a json representation of the source code.
def source_code_json_creation():
    # Obtain the classes, attribute and modifiers, methods and their instructions
    query = (
        "cpg.typeDecl.isExternal(false).map(node => (node.name, node.code, node.astChildren.isMember.l.map(node => (node, "
        "node.astChildren.isModifier.modifierType.l)), node.astChildren.isMethod.l.map(node => (node, "
        "node.ast.label.l, node.ast.code.l, node.ast.lineNumber.l)), node.filename)).toJsonPretty "
    )
    result = client.execute(query)
    all_data = json.loads(clean_json(result["stdout"]))

    # Create a dictionary with all the info about the source code and write it to a .json file.
    source_code_json = {"relations": [], "classes": list(filter(None, list(map(create_class_dict, all_data))))}
    print(source_code_json)


if __name__ == "__main__":
    server_endpoint = "localhost:8080"
    client = CPGQLSClient(server_endpoint)
    project_dir = sys.argv[-1]

    if "Windows" in platform.platform():
        index = project_dir.find(":")
        win_drive = project_dir[0: index + 1]
        project_dir = project_dir.replace(win_drive, win_drive.upper()).replace(
            "\\", "//"
        )
        
    project_name = "analyzedProject"
    # Import the source code to Joern for analyzing.
    total_time = 0
    start = time.time()
    query = import_code_query(project_dir, project_name)
    result = client.execute(query)
    end = time.time()

    if result["success"]:
        logging.info(
            "The source code has been successfully imported. Completed in {0} seconds.".format(
                format(end - start, ".2f"))
        )
        total_time += end - start
    else:
        print("import failure", file=sys.stderr)
        exit(1)

    # Create the source code json representation
    start = time.time()
    source_code_json_creation()
    end = time.time()
    logging.info(
        "A .json representation of the source code has been created. Completed in {0} seconds.".format(
            format(end - start, ".2f")))
    total_time += end - start
    
    # Close and delete the project from user's bin/joern/joern-cli/workspace
    start = time.time()
    query = 'delete ("' + project_name + '")'
    result = client.execute(query)
    end = time.time()
    if result["success"]:
        logging.info(
            "The source code has been successfully removed. Completed in "
            + format(end - start, ".2f")
            + " seconds."
        )
    total_time += end - start
