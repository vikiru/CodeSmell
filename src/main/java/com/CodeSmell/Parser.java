package com.CodeSmell;

import com.CodeSmell.CPGClass.Attribute;
import com.CodeSmell.CPGClass.Method;
import com.CodeSmell.CPGClass.Modifier;
import com.google.gson.Gson;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Parser {

    public static void main(String[] args) {
        Parser p = new Parser();
        CodePropertyGraph g = p.buildCPG("");
        p.getAllMultiplicities(g);
    }

    /**
     * @param cpgClass
     * @param attribute
     */
    public void getMissingAttributeType(CPGClass cpgClass, Attribute attribute) {
        String className = cpgClass.name;
        // Accounting for nested classes (i.e. CPGClass$Method)
        int separatorIndex = cpgClass.name.indexOf("$");
        if (separatorIndex != -1) {
            className = className.substring(separatorIndex).replace("$", "");
        }
        ArrayList<Method> classMethods = cpgClass.methods;
        String finalClassName = className;
        String stringToFind = "this." + attribute.name;
        String originalType = attribute.type;
        var result = classMethods.stream().
                filter(method -> method.name.equals(finalClassName)).collect(Collectors.toList());
        if (!result.isEmpty()) {
            Method constructor = result.get(0);
            var instructionResult = Arrays.stream(constructor.instructions).
                    filter(instruction -> instruction.label.equals("METHOD"))
                    .collect(Collectors.toList());
            String constructorBody = instructionResult.get(0).code;
            // Check the constructor, if the desired field is a parameter, this will obtain the type
            if (constructorBody.contains(attribute.name)) {
                var attributeResult = constructor.parameters.stream().
                        filter(parameter -> parameter.name.equals(attribute.name)).collect(Collectors.toList());
                if (!attributeResult.isEmpty()) {
                    attribute.type = attributeResult.get(0).type;
                }
            }
            // Check the constructor's instructions, if the desired field is defined within the constructor, this will obtain the
            // type
            else {
                instructionResult = Arrays.stream(constructor.instructions).
                        filter(instruction -> instruction.label.equals("CALL") &&
                                instruction.code.contains("<") && !instruction.code.contains("<>")
                                && instruction.code.contains(stringToFind)).collect(Collectors.toList());
                if (!instructionResult.isEmpty()) {
                    int startingIndex = instructionResult.get(0).code.indexOf("<");
                    int endingIndex = instructionResult.get(0).code.indexOf(">");
                    if (startingIndex != -1) {
                        String type = instructionResult.get(0).code.substring(startingIndex, endingIndex + 1);
                        attribute.type += type;
                    }
                }
            }
        }
        // Check all other methods apart from the constructor to determine if the desired field is used somewhere.
        if (originalType.equals(attribute.type)) {
            for (Method method : classMethods) {
                Method.Instruction[] instructions = method.instructions;
                var instructionResult = Arrays.stream(instructions)
                        .filter(instruction -> instruction.code.contains(stringToFind)
                                && instruction.label.equals("CALL")
                                && (instruction.code.contains("add") || instruction.code.contains("put"))).collect(Collectors.toList());
                if (!instructionResult.isEmpty()) {
                    int indexToFind = instructionResult.get(0).code.indexOf("(");
                    String parameter = instructionResult.get(0).code.substring(indexToFind).replace("(", "").replace(")", "");
                    ArrayList<Method.Parameter> parameters = new ArrayList<>();
                    var parameterResult = method.parameters.stream().
                            filter(parameterToFind -> parameterToFind.name.equals(parameter)).collect(Collectors.toList());
                    if (!parameterResult.isEmpty()) {
                        if (!parameter.contains("<")) {
                            attribute.type += "<" + parameterResult.get(0).type + ">";
                        } else attribute.type += parameterResult.get(0).type;
                    }
                }
            }
        }
    }

    /**
     * @param cpg
     */
    public void getAllMissingInfo(CodePropertyGraph cpg) {
        // Get all the fields that are a part of Collection (i.e. contain "< >" in their declaration)
        for (CPGClass cpgClass : cpg.getClasses()) {
            for (Attribute attribute : cpgClass.attributes) {
                if (attribute.packageName.equals("java.util") && !attribute.type.contains("<")) {
                    this.getMissingAttributeType(cpgClass, attribute);
                }
            }
        }
    }

    /**
     * @param cpg
     */
    public void getAllMultiplicities(CodePropertyGraph cpg) {
        for (CPGClass cpgClass : cpg.getClasses()) {
            ArrayList<CPGClass> classesToCheck = new ArrayList<>();
            for (Attribute attribute : cpgClass.attributes) {
                if (!attribute.type.equals(cpgClass.name) && attribute.packageName.contains("java.util")) {
                    String attributeType = attribute.type;
                    int index = attributeType.indexOf("<");
                    //String attributeGenerics = attributeType.substring(index).replace("<", "").replace(">", "");
                    String attributeGenerics = "";
                    ArrayList<String> types = new ArrayList<>();
                    if (attributeGenerics.contains(",")) {
                        String[] splittedString = attributeGenerics.split(",");
                        types.add(splittedString[0].trim());
                        types.add(splittedString[1].trim());
                    } else {
                        types.add(attributeGenerics);
                    }
                    for (String currentType : types) {
                        for (CPGClass currentClass : cpg.getClasses()) {
                            String className = currentClass.name;
                            int separatorIndex = currentClass.name.indexOf("$");
                            if (separatorIndex != -1) {
                                className = className.substring(separatorIndex).replace("$", "");
                            }
                            if (className.equals(currentType)) {
                                classesToCheck.add(currentClass);
                            }
                        }
                    }
                }
            }
//////
        }

    }

    /**
     * @param destination A file location for source code
     * @return cpg A code property graph of the source code containing all classes and their fields and methods
     */
    public CodePropertyGraph buildCPG(String destination) {
        Gson gson = new Gson();
        CodePropertyGraph cpg = new CodePropertyGraph();
        HashMap<Method, String> calls = new HashMap<>();
        try {
            Reader reader = Files.newBufferedReader(Paths.get("src/main/python/joernFiles/sourceCode.json"));
            // convert JSON file to map
            Map<?, ?> map = gson.fromJson(reader, Map.class);
            Object[] entryValues;
            entryValues = map.values().toArray();
            ArrayList classes = (ArrayList) entryValues[0];
            int classCount = -1;
            for (Object classMap : classes) {
                Map<?, ?> completeClassMap = (Map<?, ?>) classMap;
                if (completeClassMap != null) {
                    String name = (String) completeClassMap.get("name");
                    String type = (String) completeClassMap.get("type");
                    String filePath = (String) completeClassMap.get("filePath");
                    String packageName = (String) completeClassMap.get("packageName");
                    cpg.addClass(new CPGClass(name, filePath, packageName, type));
                    classCount++;
                    ArrayList<Method> methods = parseSourceCodeMethods(cpg.getClasses().get(classCount), (ArrayList) completeClassMap.get("methods"), calls);
                    for (Method method : methods) {
                        cpg.getClasses().get(classCount).methods.add(method);
                    }
                    ArrayList<Attribute> fields = parseSourceCodeFields((ArrayList) completeClassMap.get("fields"));
                    for (Attribute field : fields) {
                        cpg.getClasses().get(classCount).attributes.add(field);
                    }
                }
            }
            // close reader
            reader.close();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        HashMap<String, Method> allMethods = new HashMap();
        for (CPGClass cpgClass : cpg.getClasses()) {
            for (Method method : cpgClass.methods) {
                allMethods.put(method.name, method);
            }
        }
        updateMethodCalls(allMethods, cpg, calls);
        getAllMissingInfo(cpg);

        return cpg;
    }

    private void updateMethodCalls(HashMap<String, Method> allMethods, CodePropertyGraph cpg, HashMap<Method, String> calls) {
        for (CPGClass cpgClass : cpg.getClasses()) {
            for (Method method : cpgClass.methods) {
                for (String calledMethod : calls.values()) {
                    if (allMethods.containsKey(calledMethod)) {
                        method.methodCalls.add(allMethods.get(calledMethod));
                    }
                }
            }
        }
    }

    private ArrayList<Method> parseSourceCodeMethods(CPGClass parentClass, ArrayList methods, HashMap<Method, String> calls) {
        ArrayList<Method> parsedMethods = new ArrayList<>();
        String calledMethod = "";
        for (Object method : methods) {
            Map<?, ?> completeMethodMap = (Map<?, ?>) method;
            String methodName = (String) completeMethodMap.get("name");
            ArrayList methodModifiers = (ArrayList) completeMethodMap.get("modifiers");
            String methodBody = (String) completeMethodMap.get("methodBody");
            String returnType = (String) completeMethodMap.get("returnType");
            ArrayList methodParameters = (ArrayList) completeMethodMap.get("parameters");
            ArrayList methodInstructions = (ArrayList) completeMethodMap.get("instructions");

            // Add all the instructions of a method
            Method.Instruction[] methodInstruct = new Method.Instruction[methodInstructions.size()];
            for (int i = 0; i < methodInstructions.size(); i++) {
                Map<?, ?> completeInstructionMap = (Map<?, ?>) methodInstructions.get(i);
                String label = (String) completeInstructionMap.get("_label");
                String code = (String) completeInstructionMap.get("code");
                String lineNumber = String.valueOf(completeInstructionMap.get("lineNumber")).replace(".0", "");
                calledMethod = (String) completeInstructionMap.get("methodCall");
                methodInstruct[i] = new Method.Instruction(label, code, lineNumber);
            }

            // Add all the parameters of a method
            ArrayList<Method.Parameter> parameters = new ArrayList<>();
            for (Object parametersTree : methodParameters) {
                Map<?, ?> completeParametersMap = (Map<?, ?>) parametersTree;
                String name = (String) completeParametersMap.get("name");
                String type = (String) completeParametersMap.get("type");
                parameters.add(new Method.Parameter(name, type));
            }

            // Add all the modifiers of a method
            Modifier[] modifiers = new Modifier[methodModifiers.size()];
            if (!methodModifiers.isEmpty()) {
                for (int i = 0; i < methodModifiers.size(); i++) {
                    for (Modifier m : Modifier.values()) {
                        if (methodModifiers.get(i).equals(m.modString)) {
                            modifiers[i] = m;
                        }
                    }
                }
            } else {
                modifiers = new Modifier[]{};
            }

            Method thisMethod = new Method(parentClass, methodName, methodBody, methodInstruct, modifiers, parameters, returnType);
            if (!(calledMethod.equals(""))) {
                calls.put(thisMethod, calledMethod);
            }
            parsedMethods.add(thisMethod);
        }
        return parsedMethods;
    }

    private ArrayList<Attribute> parseSourceCodeFields(ArrayList fields) {
        ArrayList<Attribute> parsedFields = new ArrayList<>();
        for (Object field : fields) {
            Map<?, ?> completeFieldMap = (Map<?, ?>) field;
            String fieldName = (String) completeFieldMap.get("name");
            String fieldType = (String) completeFieldMap.get("type");
            String packageName = (String) completeFieldMap.get("packageName");
            ArrayList fieldModifiers = (ArrayList) completeFieldMap.get("modifiers");

            // Add all modifiers of a field
            Modifier[] modifiers = new Modifier[fieldModifiers.size()];
            if (!fieldModifiers.isEmpty()) {
                for (int i = 0; i < fieldModifiers.size(); i++) {
                    for (Modifier m : Modifier.values()) {
                        if (fieldModifiers.get(i).equals(m.modString)) {
                            modifiers[i] = m;
                        }
                    }
                }
            } else {
                modifiers = new Modifier[]{};
            }
            parsedFields.add(new Attribute(fieldName, packageName, fieldType, modifiers));
        }
        return parsedFields;
    }

    private class JavaAttribute extends Attribute {
        JavaAttribute(String name, String packageName, String type, Modifier[] m) {
            super(name, packageName, type, m);
        }
    }

    private class JavaMethod extends Method {
        JavaMethod(CPGClass parentClass, String name, String methodBody, Instruction[] instructions, Modifier[] modifiers, ArrayList<Parameter> parameters, String returnType) {
            super(parentClass, name, methodBody, instructions, modifiers, parameters, returnType);
        }
    }
}