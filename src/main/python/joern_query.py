import json
import os
import platform
import re
import sys
import time
from pathlib import Path
from time import sleep
from cpgqls_client import CPGQLSClient, import_code_query
import logging


def read_file(file_path):
    file = open(file_path, "r")
    lines = file.read().splitlines()
    file.close()
    return lines


def assign_missing_class_info(class_dict, file_lines):
    class_name = class_dict["name"]
    # Space is needed to pickup, "abstract class" and not "class abstractClass" for example.
    modifiers_pattern = re.compile(
        "(private |public |protected |static |final |synchronized |virtual |volatile |abstract |native )"
    )
    package_name = "".join(filter(lambda line: ("package" in line), file_lines))
    package_name = package_name.replace(";", "")
    import_statements = [line for line in file_lines if "import" in line]
    class_declaration = "".join(filter(lambda line: ("class" and "{" in line and "(" not in line), file_lines))
    class_declaration = class_declaration.replace("{", "").strip()
    class_modifiers = [modifier.strip() for modifier in modifiers_pattern.findall(class_declaration)]
    class_type = class_declaration.replace(class_name, "").replace("{", "").strip()
    class_type = re.sub(modifiers_pattern, "", class_type).strip()

    for attribute in class_dict["attributes"]:
        attribute_name = attribute["name"]
        line_number = attribute["lineNumber"]
        attribute_code = file_lines[line_number - 1].strip()
        attribute_modifiers = [modifier.strip() for modifier in modifiers_pattern.findall(attribute_code)]
        if not attribute_modifiers and "enum" not in class_type:
            attribute_modifiers = ["package private"]
        attribute_type = re.sub(modifiers_pattern, "", attribute_code).strip()
        attribute_type = attribute_type.split()[0]
        attribute["parentClassName"] = class_name
        attribute["code"] = attribute_code
        attribute["attributeType"] = attribute_type
        attribute["modifiers"] = attribute_modifiers
        attribute["packageName"] = attribute["typeFullName"].replace("[]", "")

    class_dict["code"] = class_declaration
    class_dict["importStatements"] = import_statements
    class_dict["modifiers"] = class_modifiers
    class_dict["classType"] = class_type
    class_dict["packageName"] = package_name

    return class_dict


# For every attribute, create a dictionary and return it.
def create_attribute_dict(curr_attribute):
    attribute_name = curr_attribute["_1"]
    attribute_type_full_name = curr_attribute["_2"]
    attribute_code = curr_attribute["_3"]
    attribute_line_number = curr_attribute["_4"]
    attribute_modifiers = curr_attribute["_5"]

    index = attribute_type_full_name.rfind(".")
    type = attribute_type_full_name
    package_name = ""
    if index != -1:
        package_name = attribute_type_full_name
        type = attribute_type_full_name[index + 1: len(attribute_type_full_name)]
    index_nested = type.rfind("$")
    if index_nested != -1:
        type = type[index_nested + 1: len(type)]

    curr_attribute_dict = {
        "parentClassName": "",
        "name": attribute_name,
        "code": "",
        "lineNumber": int(attribute_line_number),
        "packageName": package_name,
        "modifiers": [modifier.lower() for modifier in attribute_modifiers],
        "attributeType": type,
        "typeFullName": attribute_type_full_name,
    }
    return curr_attribute_dict


# For every method, create a dictionary and return it.
def create_method_dict(curr_method):
    method_name = curr_method["_1"]
    method_code = curr_method["_2"]
    method_line_number = curr_method["_3"]
    method_line_number_end = curr_method["_4"]
    method_signature = curr_method["_5"]
    method_modifiers = [modifier.lower() for modifier in curr_method["_6"]]
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
                    "evaluationStrategy": evaluation_strategy,
                    "name": name,
                    "type": type,
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
            "lineNumberStart": int(method_line_number),
            "lineNumberEnd": int(method_line_number_end),
            "name": method_name.replace("<init>", constructor_name),
            "modifiers": get_method_modifiers(
                regex_pattern_modifiers, method_modifiers
            ),
            "signature": method_signature,
            "returnType": return_type,
            "methodBody": method_body,
            "parameters": get_method_parameters(method_parameters),
            "instructions": list(
                filter(
                    None,
                    list(map(create_instruction_dict, method_instructions)),
                )
            ),
        }
        return curr_method_dict


# For every method's instruction, create a dictionary and return it.
def create_instruction_dict(curr_instruction):
    instruction_line_number = ""
    instruction_label = curr_instruction["_1"]
    instruction_code = curr_instruction["_2"]

    if len(curr_instruction) == 3:
        instruction_line_number = curr_instruction["_3"]
    else:
        instruction_line_number = "0"

    if "<empty>" in instruction_code:
        return
    else:
        # Get the method call in each line of code, if any.
        method_call_pattern = re.compile(r"([a-zA-Z]*\()")
        calls = method_call_pattern.findall(instruction_code)
        method_call = ""
        if calls and instruction_label == "CALL":
            method_call = calls[0].replace("(", "")

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
    def get_type(declaration, class_dictionary):
        if "interface" in declaration:
            return "interface"
        else:
            # Get all the modifiers of a class's methods and combine them into a single list.
            list_method_modifiers = [
                methods["modifiers"] for methods in class_dictionary["methods"]
            ]
            single_list_method_modifiers = []
            for list in list_method_modifiers:
                single_list_method_modifiers.extend(list)

            # Get all the modifiers and types of each attribute and combine each into a single list.
            list_attribute_modifiers = [
                attribute["modifiers"]
                for attribute in class_dictionary["attributes"]
                if not attribute["modifiers"]
            ]
            list_attribute_types = [
                attribute["attributeType"]
                for attribute in class_dictionary["attributes"]
                if attribute["attributeType"] == class_name
            ]
            single_list_attribute_modifiers = []
            single_list_attribute_types = []
            for list in list_attribute_modifiers:
                single_list_attribute_modifiers.extend(list)
            for list in list_attribute_types:
                single_list_attribute_types.extend(list)

            set_attribute_types = set(list_attribute_types)
            if (
                    "class" in declaration
                    and not single_list_attribute_modifiers
                    and (len(set_attribute_types) == 1 and class_name in set_attribute_types)
            ):
                return "enum"
            elif "class" in declaration and "abstract" in single_list_method_modifiers:
                return "abstract class"
            else:
                return "class"

    def get_package_name(file_path):
        path_without_separators = file_path.replace(os.sep, " ").split(" ")
        index_of_src = path_without_separators.index("src")
        if index_of_src < 0:
            index_of_src = path_without_separators.index("test")
        if index_of_src < 0:
            raise Exception("joern_query could not parse folder structure. No src/test")
        full_package_name = ".".join(
            path_without_separators[index_of_src: len(path_without_separators)]
        )
        file_name_index = full_package_name.rindex(".java")
        package_name = full_package_name[0:file_name_index]
        package_name = package_name[0: package_name.rindex(".")]
        return package_name

    def get_name_without_separators(name):
        if "$" in name:
            index = name.rindex("$")
            name = name[index + 1: len(name)]
        return name

    curr_class_dict = {
        "name": get_name_without_separators(class_name),
        "code": "",  # keep empty for now
        "lineNumber": int(line_number),
        "importStatements": [],  # keep empty for now
        "modifiers": [],  # keep these empty for now
        "classFullName": class_full_name,
        "inheritsFrom": [className.split('.')[-1] for className in inherits_from_list],
        "classType": "",
        "filePath": file_name,
        "packageName": get_package_name(file_name),
        "attributes": list(map(create_attribute_dict, class_attributes)),
        "methods": list(filter(None, list(map(create_method_dict, class_methods)))),
        "outwardRelations": []
    }
    curr_class_dict["classType"] = get_type(class_declaration, curr_class_dict)
    for method in curr_class_dict["methods"]:
        method["parentClassName"] = curr_class["_1"]

    curr_class_dict = assign_missing_class_info(curr_class_dict, read_file(curr_class_dict["filePath"]))
    return curr_class_dict


# Execute a single query to retrieve all the class names within the source code
def retrieve_all_class_names():
    query = 'cpg.typeDecl.isExternal(false).filter(node => !node.name.contains("lambda")).name.toJson'
    result = client.execute(query)
    class_names = []
    if result["success"]:
        index = result["stdout"].index('"')
        all_names = json.loads(
            json.loads(result["stdout"][index: len(result["stdout"])])
        )
        class_names = [name.replace("$", ".") for name in all_names]
    return class_names


# Execute a single query to get all the data of a class
def retrieve_class_data(name):
    class_query = (
            'cpg.typeDecl.name("' + name + '").map(node => (node.name, node.fullName, '
                                           "node.inheritsFromTypeFullName.l, node.code, node.lineNumber, "
                                           "node.astChildren.isModifier.modifierType.l, "
                                           "node.astChildren.isMember.l.map(node => (node.name, node.typeFullName, "
                                           "node.code, node.lineNumber, node.astChildren.isModifier.modifierType.l)), "
                                           "node.astChildren.isMethod.filter(node => node.lineNumber != None "
                                           "&& node.lineNumberEnd != None).l.map(node => (node.name, node.code, "
                                           "node.lineNumber, node.lineNumberEnd, node.signature, "
                                           "node.astChildren.isModifier.modifierType.l, "
                                           "node.astChildren.isParameter.filter(node => !node.name.contains("
                                           '"this")).l.map(node => (node.evaluationStrategy, node.code, node.name, '
                                           "node.typeFullName)), node.ast.l.map(node => (node.label, node.code, "
                                           "node.lineNumber)))), node.filename)).toJson"
    )
    start = time.time()
    result = client.execute(class_query)
    end = time.time()
    if result["success"]:
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


def source_code_json_creation(class_names):
    source_code_json = {"relations": [], "classes": []}
    for class_name in class_names:
        source_code_json["classes"].append(retrieve_class_data(class_name))
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

    server_endpoint = "localhost:8080"
    client = CPGQLSClient(server_endpoint)
    if client:
        logging.info("joern_query is starting and connected to CPGQLSClient.")
    project_dir = sys.argv[-1]

    if "Windows" in platform.platform():
        index = project_dir.find(":")
        win_drive = project_dir[0: index + 1]
        project_dir = project_dir.replace(win_drive, win_drive.upper()).replace(
            "\\", "//"
        )
    project_name = "analyzedProject"

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
                    4, byteorder=sys.byteorder, signed=True)
                print("class content size: ", len(class_contents), file=sys.stderr)
                print("size bytes size: ", file=sys.stderr)
                sys.stdout.buffer.write(size_bytes)
                sys.stdout.buffer.write(class_contents)

            sys.stdout.buffer.write((-1).to_bytes(
                4, byteorder=sys.byteorder, signed=True))
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
