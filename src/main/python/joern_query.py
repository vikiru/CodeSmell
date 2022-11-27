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
    joern_result = joern_result[index : len(joern_result)]
    return joern_result


# Execute a single joern query to get all the data required to create a json representation of the source code.
def source_code_json_creation():
    # Obtain the classes, fields and modifiers, methods and their instructions
    query = (
        "cpg.typeDecl.isExternal(false).map(node => (node.name, node.code, node.astChildren.isMember.l.map(node => (node, "
        "node.astChildren.isModifier.modifierType.l)), node.astChildren.isMethod.l.map(node => (node, "
        "node.ast.label.l, node.ast.code.l, node.ast.lineNumber.l)))).toJsonPretty "
    )
    result = client.execute(query)
    all_data = json.loads(clean_json(result["stdout"]))

    # For every field, create a dictionary and return it.
    def create_field_dict(curr_field):
        type = curr_field["_1"]["typeFullName"]
        index = curr_field["_1"]["typeFullName"].rfind(".")
        if index != -1:
            type = curr_field["_1"]["typeFullName"][
                index + 1 : len(curr_field["_1"]["typeFullName"])
            ]

        curr_field_dict = {
            "name": curr_field["_1"]["name"],
            "modifiers": [x.lower() for x in curr_field["_2"]],
            "type": type,
        }
        return curr_field_dict

    # For every method, create a dictionary and return it.
    def create_method_dict(curr_method):
        # For every method's instruction, create a dictionary and return it.
        def create_instruction_dict(curr_label, curr_instructions, curr_line_number):
            if curr_instructions == "<empty>":
                return
            else:
                # Get the method call in each line of code, if any.
                method_call_pattern = re.compile("([a-zA-Z]*\()")
                calls = method_call_pattern.findall(curr_instructions)
                method_call = ""
                if calls and curr_label == "CALL":
                    method_call = calls[0].replace("(", "")

                curr_instruction_dict = {
                    "_label": curr_label,
                    "code": curr_instructions.replace("\r\n", ""),
                    "lineNumber": curr_line_number or "none",
                    "methodCall": method_call,
                }
                return curr_instruction_dict

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

            method_name_pattern = re.compile("(^[a-zA-z]*)")

            # Get the return type of the method, if any.
            return_type_pattern = re.compile("(^[a-zA-z]*\s)")
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

            # Extract the type and names of all method parameters within the method body.
            def get_method_parameters(method_body):
                param_list = []
                index = method_body.find("(")
                all_parameters = []
                if "<lambda>" not in method_body:
                    all_parameters = (
                        method_body[index : len(method_body)]
                        .replace("(", "")
                        .replace(")", "")
                    )
                    paramater_pattern = re.compile(
                        "(\w*\[?\]?(\<\w*\,\s\w*\>)?(\<\w*\>)?)"
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
                        if (i + 1 + count) <= len(all_parameters):
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
                "parentClass": "",
                "code": curr_method["_1"]["code"],
                "name": curr_method["_1"]["name"].replace("<init>", constructor_name),
                "modifiers": modifiers_pattern.findall(curr_method["_1"]["code"]),
                "returnType": return_type,
                "methodBody": method_body,
                "parameters": get_method_parameters(method_body),
                # For the instructions,
                # _2 corresponds to the labels,
                # _3 corresponds to the code,
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
                    # Get all of the modifiers of a class's methods and combine them into a single list.
                    list_method_modifiers = [
                        methods["modifiers"] for methods in curr_class_dict["methods"]
                    ]
                    single_list_method_modifiers = []
                    for list in list_method_modifiers:
                        single_list_method_modifiers.extend(list)

                    if "class" in declaration and not curr_class_dict["methods"]:
                        return "enum"
                    elif (
                        "class" in declaration
                        and "abstract" in single_list_method_modifiers
                    ):
                        return "abstract class"
                    else:
                        return "class"

            # _1 corresponds to class name, _2 corresponds to class code declaration (i.e. "public class A")
            # _3 corresponds to class fields
            # _4 corresponds to class methods
            curr_class_dict = {
                "name": curr_class["_1"],
                "type": "",
                "fields": list(map(create_field_dict, curr_class["_3"])),
                "methods": list(
                    filter(None, list(map(create_method_dict, curr_class["_4"])))
                ),
            }
            curr_class_dict["type"] = get_type(curr_class["_2"], curr_class_dict)
            for method in curr_class_dict["methods"]:
                method["parentClass"] = curr_class["_1"]
            return curr_class_dict

    # Create a dictionary with all the info about the source code and write it to a .json file.
    source_code_json = {"classes": list(map(create_class_dict, all_data))}
    write_to_file(source_code_json, "sourceCode")


# Write the joern output of a query to a specified filename
def write_to_file(source_code_json, file_name):
    final_directory_name = (
        str(Path(__file__).parent.parent) + "/python/joernFiles/" + file_name + ".json"
    )
    with open(final_directory_name, "w") as f:
        json.dump(source_code_json, f, indent=4)


if __name__ == "__main__":
    server_endpoint = "localhost:8080"
    client = CPGQLSClient(server_endpoint)

    # Eventually replace dirName with a directory which we decide upon
    # which will always store the imported source code
    directory = os.getcwd()
    directory = directory.replace("\\", "//")
    directory_name = directory + "/src/main/python/joernTestProject/src"
    project_name = "analyzedProject"

    # For testing purposes. Full file paths are required for joern.
    # Get the path of src/main/java/com/CodeSmell as shown (replace '\\' with '/')
    our_project_dir = str(Path(__file__).parent.parent) + "/java/com/CodeSmell/"
    index = our_project_dir.find(":")
    winDrive = our_project_dir[0 : index + 1]
    our_project_dir = our_project_dir.replace(winDrive, winDrive.upper()).replace(
        "\\", "//"
    )
            
    # Original directory of where the source code we are analyzing came from. We could use the user's
    # original dir instead of taking .java source files, or just take in .java files as store in a dir
    # originalDir = "D:/SYSC3110/Lab1"
    start = time.time()
    query = import_code_query(our_project_dir, project_name)
    result = client.execute(query)
    end = time.time()

    if result["success"]:
        print(
            "The source code has been successfully imported. Completed in "
            + format(end - start, ".2f")
            + " seconds."
        )

    start = time.time()
    # Create the source code json representation
    source_code_json_creation()
    end = time.time()

    print(
        "A .json representation of the source code has been created. Completed in "
        + format(end - start, ".2f")
        + " seconds."
    )

    # Close and delete the project from user's bin/joern/joern-cli/workspace
    start = time.time()
    query = 'delete ("' + project_name + '")'
    result = client.execute(query)
    end = time.time()
    if result["success"]:
        print(
            "The source code has been successfully removed. Completed in "
            + format(end - start, ".2f")
            + " seconds."
        )
"""
    import cProfile
    import pstats

    with cProfile.Profile() as pr:
        source_code_json_creation(all_data)

    stats = pstats.Stats(pr)
    stats.sort_stats(pstats.SortKey.TIME)
    # stats.print_stats()
    stats.dump_stats(filename="profiling.prof")
"""
