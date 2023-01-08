import json
import os
import re
import time
from pathlib import Path
from cpgqls_client import CPGQLSClient, import_code_query


# Cleans up json output from joern so that it can be a proper JSON file.
def clean_json(joern_result):
    string_to_find = '"""'
    index = joern_result.find(string_to_find)
    joern_result = joern_result.replace('"""', "")
    joern_result = joern_result[index: len(joern_result)]
    return joern_result


# Write the joern output of a query to a specified filename
def write_to_file(source_code_json, file_name):
    final_directory_name = (
            str(Path(__file__).parent.parent) + "/python/joernFiles/" + file_name + ".json"
    )
    with open(final_directory_name, "w") as f:
        json.dump(source_code_json, f, indent=4)


# For every attribute, create a dictionary and return it.
def create_field_dict(curr_field):
    type = curr_field["_1"]["typeFullName"]
    package_name = ""
    index = curr_field["_1"]["typeFullName"].rfind(".")
    if index != -1:
        package_name = curr_field["_1"]["typeFullName"][0: index].replace("<unresolvedNamespace>", "")
        type = curr_field["_1"]["typeFullName"][index + 1: len(curr_field["_1"]["typeFullName"])]
    curr_field_dict = {
        "name": curr_field["_1"]["name"],
        "modifiers": [modifier.lower() for modifier in curr_field["_2"]],
        "typeFullName": curr_field["_1"]["typeFullName"],
        "packageName": package_name,
        "type": type,
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
                paramater_pattern = re.compile(
                    r"(\w*\[?\]?(\<\w*\,\s\w*\>)?(\<\w*\>)?)"
                )
                all_parameter_matches = paramater_pattern.findall(all_parameters)
                all_parameters = list(
                    filter(None, [t[0] for t in all_parameter_matches])
                )
            if not all_parameters:
                return param_list
            else:
                # Append all parameters to the param_list
                count = 0
                for i in range(0, len(all_parameters)):
                    if (i + 1 + count) < len(all_parameters):
                        param_list.append(
                            dict(
                                name=all_parameters[i + 1 + count],
                                type=all_parameters[i + count],
                            )
                        )
                        count += 1
                    else:
                        break
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
                    attribute["type"]
                    for attribute in curr_class_dict["attributes"]
                    if attribute["type"] == class_name
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
        if index != -1:
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
        "classFullName": curr_class["_1"],
        "type": "",
        "filePath": curr_class["_5"],
        "packageName": get_package_name(curr_class["_5"]),
        "attributes": list(map(create_field_dict, curr_class["_3"])),
        "methods": list(
            filter(None, list(map(create_method_dict, curr_class["_4"])))
        ),
    }
    curr_class_dict["type"] = get_type(curr_class["_2"], curr_class_dict)
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
    source_code_json = {"classes": list(filter(None, list(map(create_class_dict, all_data))))}
    write_to_file(source_code_json, "sourceCode")


if __name__ == "__main__":
    server_endpoint = "localhost:8080"
    client = CPGQLSClient(server_endpoint)

    # For testing purposes. Full file paths are required for joern.
    # Get the path of src/main/java/com/CodeSmell as shown (replace '\\' with '/')
    our_project_dir = str(Path(__file__).parent.parent) + "/java/com/CodeSmell/"
    index = our_project_dir.find(":")
    win_drive = our_project_dir[0: index + 1]
    our_project_dir = our_project_dir.replace(win_drive, win_drive.upper()).replace(
        "\\", "//"
    )
    project_name = "analyzedProject"
    project_dir = our_project_dir  # Change this as needed for testing purposes. Remember to replace "\\" with "//".

    # Import the source code to Joern for analyzing.
    total_time = 0
    start = time.time()
    query = import_code_query(project_dir, project_name)
    result = client.execute(query)
    end = time.time()

    if result["success"]:
        print(
            "The source code has been successfully imported. Completed in {0} seconds.".format(
                format(end - start, ".2f"))
        )
        total_time += end - start

    # Create the source code json representation
    start = time.time()
    source_code_json_creation()
    end = time.time()
    print(
        "A .json representation of the source code has been created. Completed in {0} seconds.".format(
            format(end - start, ".2f"))
    )
    total_time += end - start

    # Close and delete the project from user's bin/joern/joern-cli/workspace
    start = time.time()
    query = 'delete ("' + project_name + '")'
    result = client.execute(query)
    end = time.time()
    if result["success"]:
        print(
            "The source code has been successfully removed. Completed in {0} seconds.".format(
                format(end - start, ".2f"))
        )
        total_time += end - start
    print("Total time taken: {0} seconds.".format(
        format(total_time, ".2f"))
    )

"""
SnakeViz and cProfile diagnostics.
import cProfile
    import pstats

    with cProfile.Profile() as pr:
        source_code_json_creation()

    stats = pstats.Stats(pr)
    stats.sort_stats(pstats.SortKey.TIME)
    # stats.print_stats()
    stats.dump_stats(filename="profiling.prof")

"""
