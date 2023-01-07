package com.CodeSmell;

import com.CodeSmell.CPGClass.Attribute;
import com.CodeSmell.CPGClass.Method;
import com.CodeSmell.CPGClass.Modifier;
import com.google.gson.Gson;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Parser {

    public static void main(String[] args) {
        Parser p = new Parser();
        CodePropertyGraph g = p.buildCPG("");
        p.assignAllMultiplicities(g);
    }

    /**
     * @param cpgClass
     * @param attribute
     */
    public String assignMissingAttributeType(CPGClass cpgClass, Attribute attribute) {
        String className = cpgClass.name;
        ArrayList<Method> classMethods = cpgClass.methods;
        String stringToFind = "this." + attribute.name;
        String finalAttributeType = attribute.type;
        var result = classMethods.stream().
                filter(method -> method.name.equals(className)).collect(Collectors.toList());
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
                    finalAttributeType = attributeResult.get(0).type;
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
                        finalAttributeType += type;
                    }
                }
            }
        }
        // Check all other methods apart from the constructor to determine if the desired field is used somewhere.
        if (finalAttributeType.equals(attribute.type)) {
            for (Method method : classMethods) {
                Method.Instruction[] instructions = method.instructions;
                var instructionResult = Arrays.stream(instructions)
                        .filter(instruction -> instruction.code.contains(stringToFind)
                                && instruction.label.equals("CALL")
                                && (instruction.code.contains("add") || instruction.code.contains("put"))).collect(Collectors.toList());
                if (!instructionResult.isEmpty()) {
                    int indexToFind = instructionResult.get(0).code.indexOf("(");
                    String parameter = instructionResult.get(0).code.substring(indexToFind).replace("(", "").replace(")", "");
                    var parameterResult = method.parameters.stream().
                            filter(parameterToFind -> parameterToFind.name.equals(parameter)).collect(Collectors.toList());
                    if (!parameterResult.isEmpty()) {
                        if (!parameter.contains("<")) {
                            finalAttributeType += "<" + parameterResult.get(0).type + ">";
                        } else finalAttributeType += parameterResult.get(0).type;
                    }
                }
            }
        }
        return finalAttributeType;
    }

    /**
     * @param sourceClass
     * @param destinationClass
     */
    public void compareClassMultiplicities(CodePropertyGraph cpg, CPGClass sourceClass, CPGClass destinationClass) {
        System.out.println(sourceClass.name + "---------------------" + destinationClass.name);
        String destinationType = destinationClass.name;
        ArrayList<Attribute> sourceAttributes = sourceClass.attributes;

        String cardinality = "";
        var sourceToDestResult = sourceAttributes.stream()
                .filter(attribute -> attribute.type.contains(destinationType)).collect(Collectors.toList());
        if (!sourceToDestResult.isEmpty()) {
            Attribute currentAttribute = sourceToDestResult.get(0);
            if (currentAttribute.type.contains("[]") || currentAttribute.type.contains("<")) {
                cardinality = "1..*";
            } else {
                cardinality = "1..1";
            }
        }
        System.out.println(sourceClass.name + " and " + destinationClass.name + " have a " + cardinality);
    }

    /**
     * @param cpg
     */
    public void assignAllMultiplicities(CodePropertyGraph cpg) {
        ArrayList<String> classNames = new ArrayList<>();
        cpg.getClasses().stream().forEach(cpgClass -> classNames.add(cpgClass.name));
        // Iterate through each cpgClass within cpg and determine the multiplicities between classes.
        for (CPGClass cpgClass : cpg.getClasses()) {
            ArrayList<Attribute> attributes = cpgClass.attributes;
            for (Attribute a : attributes) {
                String attributeType = a.type;
                // Check for single instance of another class
                if (classNames.contains(attributeType)) {
                    int checkIndex = classNames.indexOf(attributeType);
                    compareClassMultiplicities(cpg, cpgClass, cpg.getClasses().get(checkIndex));
                }
                // Check for presence of arrays
                else if (attributeType.contains("[]")) {
                    attributeType = attributeType.replace("[]", "");
                    int checkIndex = classNames.indexOf(attributeType);
                    if (checkIndex != -1 && !attributeType.equals(cpgClass.name)) {
                        compareClassMultiplicities(cpg, cpgClass, cpg.getClasses().get(checkIndex));
                    }
                }
                // Check for presence of Java Collections (i.e. ArrayList, Set, HashMap, etc)
                else if (attributeType.contains("<")) {
                    // Extract the type enclosed within the "< >"
                    int startIndex = attributeType.indexOf("<");
                    int endIndex = attributeType.indexOf(">");
                    attributeType = attributeType.substring(startIndex, endIndex + 1).replace("<", "").replace(">", "");
                    // Check for presence of a comma, this is useful for cases where a field is a HashMap for example.
                    if (attributeType.contains(",")) {
                        String[] types = attributeType.split(",");
                        String typeOne = types[0].trim();
                        String typeTwo = types[1].trim();
                        int firstTypeInd = classNames.indexOf(typeOne);
                        int secondTypeInd = classNames.indexOf(typeTwo);
                        // Compare the multiplicities between source (cpgClass) and
                        // dest class (the class which the type of the attribute belongs to) for both types
                        // If typeOne and typeTwo are the same (i.e. Attribute, Attribute) then only compare one instance of it
                        // Else compare both types
                        if (!typeOne.equals(typeTwo)) {
                            compareClassMultiplicities(cpg, cpgClass, cpg.getClasses().get(firstTypeInd));
                            compareClassMultiplicities(cpg, cpgClass, cpg.getClasses().get(secondTypeInd));
                        } else {
                            compareClassMultiplicities(cpg, cpgClass, cpg.getClasses().get(firstTypeInd));
                        }
                    }
                    // Other cases where a comma is not present within a Java collection such as ArrayList
                    else {
                        // Find the class which the type of the attribute belongs to.
                        int checkIndex = -1;
                        for (String name : classNames) {
                            if (name.contains(attributeType)) {
                                if (attributeType.equals(name)) {
                                    checkIndex = classNames.indexOf(name);
                                    break;
                                }
                            }
                        }
                        // If checkIndex is not -1, then the class was a valid class in the source code.
                        // Compare the multiplicities between source (cpgClass) and dest class (the class which the type of the attribute belongs to)
                        if (checkIndex != -1) {
                            compareClassMultiplicities(cpg, cpgClass, cpg.getClasses().get(checkIndex));
                        }
                    }
                }
            }
        }
    }

    /**
     * @param destination A file location for source code
     * @return cpg A code property graph of the source code containing all classes and their fields and methods
     */
    public CodePropertyGraph buildCPG(String destination) {
        Gson gson = new Gson();
        CodePropertyGraph cpg = new CodePropertyGraph();
        HashMap<CPGClass, HashMap<String, ArrayList<String>>> allMethodCallsOfClasses = new HashMap<>();
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
                    String classFullName = (String) completeClassMap.get("classFullName");
                    String type = (String) completeClassMap.get("type");
                    String filePath = (String) completeClassMap.get("filePath");
                    String packageName = (String) completeClassMap.get("packageName");
                    ArrayList methods = (ArrayList) completeClassMap.get("methods");
                    cpg.addClass(new CPGClass(name, classFullName, filePath, packageName, type));
                    classCount++;
                    allMethodCallsOfClasses.put(cpg.getClasses().get(classCount), parseSourceCodeMethods(cpg.getClasses().get(classCount), methods));
                    ArrayList<Attribute> fields = parseSourceCodeFields((ArrayList) completeClassMap.get("fields"));
                    for (Attribute field : fields) {
                        if (field.packageName.equals("java.util") && !field.type.contains("<")) {
                            String provisionalType = assignMissingAttributeType(cpg.getClasses().get(classCount), field);
                            Attribute properField = new Attribute(field.name, field.packageName, provisionalType, field.modifiers);
                            cpg.getClasses().get(classCount).attributes.add(properField);
                        } else cpg.getClasses().get(classCount).attributes.add(field);
                    }
                }
            }
            // close reader
            reader.close();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        updateMethodsAndMethodCalls(cpg, allMethodCallsOfClasses);
        return cpg;
    }

    /**
     * @param cpg
     * @param allMethodCallsOfClasses
     */
    private void updateMethodsAndMethodCalls(CodePropertyGraph cpg, HashMap<CPGClass, HashMap<String, ArrayList<String>>> allMethodCallsOfClasses) {
        ArrayList<Method> allMethodsInCPG = new ArrayList<>();
        ArrayList<String> allMethodNames = new ArrayList<>();
        cpg.getClasses().stream().forEach(cpgClass -> cpgClass.methods.stream().forEach(method -> allMethodsInCPG.add(method)));
        cpg.getClasses().stream().forEach(cpgClass -> cpgClass.methods.stream().forEach(method -> allMethodNames.add(method.name)));
        for (CPGClass cpgClass : cpg.getClasses()) {
            ArrayList<Method> originalMethodsOfClass = new ArrayList<>();
            cpgClass.methods.stream().forEach(method -> originalMethodsOfClass.add(method));
            ArrayList<Method> properMethodsOfClass = new ArrayList<>();
            HashMap<String, ArrayList<String>> allMethodCallsOfClass = allMethodCallsOfClasses.get(cpgClass);
            for (Method method : originalMethodsOfClass) {
                String methodName = method.name;
                ArrayList<Method> methodCalls = new ArrayList<>();
                if (allMethodCallsOfClass.containsKey(methodName)) {
                    ArrayList<String> allMethodCallsOfMethod = allMethodCallsOfClass.get(methodName);
                    var validMethods = allMethodCallsOfMethod.stream()
                            .filter(string -> allMethodNames.contains(string) && !string.equals("toString")).collect(Collectors.toList());
                    Set<String> uniqueMethods = new LinkedHashSet<>(validMethods);
                    ArrayList<Integer> indexes = new ArrayList<>();
                    uniqueMethods.stream().forEach(methodStr -> indexes.add(allMethodNames.indexOf(methodStr)));
                    indexes.stream().forEach(index -> methodCalls.add(allMethodsInCPG.get(index)));
                    Method properMethod = new Method(cpgClass, method.name, method.methodBody, method.instructions,
                            method.modifiers, method.parameters, method.returnType, methodCalls);
                    properMethodsOfClass.add(properMethod);
                }
            }
            cpgClass.methods = new ArrayList<>(properMethodsOfClass);
        }

    }

    /**
     * @param parentClass
     * @param methods
     * @return
     */
    private HashMap<String, ArrayList<String>> parseSourceCodeMethods(CPGClass parentClass, ArrayList methods) {
        HashMap<String, ArrayList<String>> allMethodCallsOfClass = new HashMap<>();
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
            ArrayList<String> allMethodCallInstructions = new ArrayList<String>();
            for (int i = 0; i < methodInstructions.size(); i++) {
                Map<?, ?> completeInstructionMap = (Map<?, ?>) methodInstructions.get(i);
                String label = (String) completeInstructionMap.get("_label");
                String code = (String) completeInstructionMap.get("code");
                String lineNumber = String.valueOf(completeInstructionMap.get("lineNumber")).replace(".0", "");
                String calledMethod = (String) completeInstructionMap.get("methodCall");
                if (!calledMethod.equals("")) {
                    allMethodCallInstructions.add(calledMethod);
                }
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

            Method thisMethod = new Method(parentClass, methodName, methodBody, methodInstruct, modifiers, parameters, returnType, new ArrayList<>());
            parentClass.methods.add(thisMethod);
            allMethodCallsOfClass.put(methodName, allMethodCallInstructions);
        }
        return allMethodCallsOfClass;
    }

    /**
     * @param fields
     * @return
     */
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
        JavaMethod(CPGClass parentClass, String name, String methodBody, Instruction[] instructions, Modifier[] modifiers, ArrayList<Parameter> parameters, String returnType, ArrayList<Method> methodCalls) {
            super(parentClass, name, methodBody, instructions, modifiers, parameters, returnType, methodCalls);
        }
    }
}