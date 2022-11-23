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
        "cpg.typeDecl.isExternal(false).map(node => (node.name, node.astChildren.isMember.l.map(node => (node, "
        "node.astChildren.isModifier.modifierType.l)), node.astChildren.isMethod.l.map(node => (node, "
        "node.ast.label.l, node.ast.code.l, node.ast.lineNumber.l)))).toJsonPretty "
    )
    result = client.execute(query)
    all_data = json.loads(clean_json(result["stdout"]))

    # For every field, create a dictionary and return it.
    def create_field_dict(curr_field):
        curr_field_dict = {
            "name": curr_field["_1"]["name"],
            "modifiers": [x.lower() for x in curr_field["_2"]],
            "type": curr_field["_1"]["typeFullName"],
        }
        return curr_field_dict

    # For every method, create a dictionary and return it.
    def create_method_dict(curr_method):
        # For every method's instruction, create a dictionary and return it.
        def create_instruction_dict(curr_label, curr_instructions, curr_line_number):
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
            method_return_type = return_type_pattern.findall(method_with_return)
            return_type = ""

            if method_return_type:
                return_type = method_return_type[0].strip()
            method_body = re.sub(return_type_pattern, "", method_with_return)
            constructor_name = method_name_pattern.findall(method_body)[0]

            curr_method_dict = {
                "code": curr_method["_1"]["code"],
                "methodBody": method_body,
                "modifiers": modifiers_pattern.findall(curr_method["_1"]["code"]),
                "name": curr_method["_1"]["name"].replace("<init>", constructor_name),
                # For the instructions,
                # _2 corresponds to the labels,
                # _3 corresponds to the code,
                # _4 corresponds to the lineNumbers
                "instructions": list(
                    map(
                        create_instruction_dict,
                        curr_method["_2"],
                        curr_method["_3"],
                        curr_method["_4"],
                    )
                ),
                "returnType": return_type,
            }
            return curr_method_dict

    # For every class, create a dictionary and return it.
    def create_class_dict(curr_class):
        curr_class_dict = {
            "name": curr_class["_1"],
            "fields": list(map(create_field_dict, curr_class["_2"])),
            "methods": list(
                filter(None, list(map(create_method_dict, curr_class["_3"])))
            ),
        }
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
