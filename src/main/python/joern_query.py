import json
import os
import re
from cpgqls_client import CPGQLSClient, import_code_query


# Cleans up json output from joern so that it can be a proper JSON file.
def clean_json(joern_result):
    string_to_find = '"""'
    index = joern_result.find(string_to_find)
    joern_result = joern_result[index : len(joern_result)]
    joern_result = joern_result.replace('"""', "")
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
            return_type_pattern = re.compile("(^[a-zA-z]*\s)")
            method_name_pattern = re.compile("(^[a-zA-z]*)")

            method_with_return = re.sub(
                modifiers_pattern, "", curr_method["_1"]["code"]
            ).strip()

            # Get the return type of the method, if any.
            method_return_type = return_type_pattern.findall(method_with_return)
            return_type = ""
            if method_return_type:
                return_type = method_return_type[0].strip()

            # Get the method body with any method parameters.
            method_body = re.sub(return_type_pattern, "", method_with_return)

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
                    all_parameters = list(filter(None, all_parameters.split(",")))
                if all_parameters:
                    for param in all_parameters:
                        splitter = param.strip().split(" ")
                        param_list.append(dict(type=splitter[0], name=splitter[1]))
                return param_list

            curr_method_dict = {
                "code": curr_method["_1"]["code"],
                "methodBody": method_body,
                "parameters": get_method_parameters(method_body),
                "modifiers": modifiers_pattern.findall(curr_method["_1"]["code"]),
                "name": curr_method["_1"]["name"].replace("<init>", constructor_name),
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
                "returnType": return_type,
            }
            return curr_method_dict

    # For every class, create a dictionary and return it.
    # _1 corresponds to class name, _2 corresponds to class code declaration (i.e. "public class A")
    # _3 corresponds to class fields
    # _4 corresponds to class methods
    def create_class_dict(curr_class):
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

        curr_class_dict = {
            "name": curr_class["_1"],
            "type": "",
            "fields": list(map(create_field_dict, curr_class["_3"])),
            "methods": list(
                filter(None, list(map(create_method_dict, curr_class["_4"])))
            ),
        }
        curr_class_dict["type"] = get_type(curr_class["_2"], curr_class_dict)
        return curr_class_dict

    # Create a dictionary with all of the info about the source code and write it to a .json file.
    source_code_json = {"classes": list(map(create_class_dict, all_data))}
    joern_result = json.dumps(source_code_json, indent=4)
    write_to_file(joern_result, "sourceCode.json")


# Write the joern output of a query to a specified filename
def write_to_file(joern_result, file_name):
    directory_name = "src/main/python/joernFiles/"
    final_directory_name = directory_name + file_name
    with open(final_directory_name, "w") as f:
        f.write(joern_result)


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
    our_project_dir = (
        "C:/Users/viski/OneDrive/Documents/GitHub/CodeSmell/src/main/java/com/CodeSmell"
    )

    # Original directory of where the source code we are analyzing came from. We could use the user's
    # original dir instead of taking .java source files, or just take in .java files as store in a dir
    # originalDir = "D:/SYSC3110/Lab1"
    query = import_code_query(our_project_dir, project_name)
    result = client.execute(query)
    if result["success"]:
        print("The source code has been successfully imported.")

    # Create the source code json representation
    source_code_json_creation()

    # Close and delete the project from user's bin/joern/joern-cli/workspace
    query = 'delete ("' + project_name + '")'
    result = client.execute(query)
    if result["success"]:
        print("The source code has been successfully removed.")
