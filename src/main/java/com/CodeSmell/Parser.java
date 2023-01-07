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
        CodePropertyGraph g = p.buildCPG("src/main/python/joernFiles/sourceCode.json");
        p.assignAllMultiplicities(g);
    }

    /**
     * Given a CPGClass and an Attribute belonging to that class, assign the proper type to the Attribute.
     * This is for cases where an Attribute may be a part of Java Collections (i.e. HashMap, ArrayList, Set, etc).
     * <p>
     * Performs 3 checks to determine the attribute's type:
     * 1) Check the constructor parameters
     * 2) Check the instructions of the constructor
     * 3) Check the instructions of all other methods of that class
     *
     * @param cpgClass  The parent CPGClass containing the Attribute
     * @param attribute The attribute that is missing a proper type (i.e. a type given as "ArrayList")
     * @return A String containing the proper Attribute type
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
     * @param cpg
     * @param sourceClass
     * @param destinationClass
     * @return
     */
    public CodePropertyGraph.Relation determineAssociationRelationshipType(CodePropertyGraph cpg, CPGClass sourceClass, CPGClass destinationClass, String sourceToDestCardinality) {
        ArrayList<Method> sourceClassMethods = sourceClass.methods;
        ArrayList<Method> destinationClassMethods = destinationClass.methods;

        // Get the constructors of both the source and destination classes, if present.
        List<Method> constructorSrc = sourceClassMethods.stream().filter(method -> method.name.contains(sourceClass.name)).collect(Collectors.toList());
        List<Method> constructorDst = destinationClassMethods.stream().filter(method -> method.name.contains(destinationClass.name)).collect(Collectors.toList());
    }

    /**
     * @param sourceClass
     * @param destinationClass
     */
    public void compareClassMultiplicities(CodePropertyGraph cpg, CPGClass sourceClass, CPGClass destinationClass) {
        String destinationType = destinationClass.name;
        ArrayList<Attribute> sourceAttributes = sourceClass.attributes;

        // Determine the multiplicity of the source class to the dest class (i.e. how many instance of the dest class are present within the source class?)
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
            Reader reader = Files.newBufferedReader(Paths.get(destination));
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
                    // Add the methods to each class and additionally create a HashMap to store a class and
                    // each of the methods that each method within that class calls
                    allMethodCallsOfClasses.put(cpg.getClasses().get(classCount), parseSourceCodeMethods(cpg.getClasses().get(classCount), methods));
                    // Add the fields of a class, accounting for any missing attribute types
                    ArrayList<Attribute> fields = parseSourceCodeAttributes((ArrayList) completeClassMap.get("fields"));
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
        // Obtain the proper Method references for each method called by a method and replace the Method objects of each class,
        // with the same fields and the only difference will be the methodCalls array
        updateMethodsAndMethodCalls(cpg, allMethodCallsOfClasses);
        return cpg;
    }

    /**
     * Iterate trough each CPGClass within the CodePropertyGraph and update each class's Method's so that
     * they no longer have an empty ArrayList for the methodCalls field.
     *
     * @param cpg                     The CodePropertyGraph representation of the source code
     * @param allMethodCallsOfClasses A HashMap containing Every Method and Each Method Called by Every Method for Every CPGClass
     */
    private void updateMethodsAndMethodCalls(CodePropertyGraph cpg, HashMap<CPGClass, HashMap<String, ArrayList<String>>> allMethodCallsOfClasses) {
        // Obtain lists of all the Methods and names of Method's within the cpg.
        ArrayList<Method> allMethodsInCPG = new ArrayList<>();
        ArrayList<String> allMethodNames = new ArrayList<>();
        cpg.getClasses().stream().forEach(cpgClass -> cpgClass.methods.stream().forEach(method -> allMethodsInCPG.add(method)));
        cpg.getClasses().stream().forEach(cpgClass -> cpgClass.methods.stream().forEach(method -> allMethodNames.add(method.name)));
        // Iterate through each CPGClass within cpg and create the proper methodCalls field of Methods within each class and then
        // create a new proper Method for that class and append this to properMethodsOfClass which will replace
        // the original methods of the CPGClass.
        for (CPGClass cpgClass : cpg.getClasses()) {
            // Create two lists, one which will be used to traverse the original methods of a class and
            // another which will be used to store the proper methods of a class (with a non-empty methodCalls ArrayList, if possible).
            ArrayList<Method> originalMethodsOfClass = cpgClass.methods;
            ArrayList<Method> properMethodsOfClass = new ArrayList<>();
            // Extract the HashMap which contains each Method and the list of Methods that Method calls.
            HashMap<String, ArrayList<String>> allMethodCallsOfClass = allMethodCallsOfClasses.get(cpgClass);
            // Traverse through each Method present within cpgClass and determine the Methods that it calls
            // Create a new Method based on the original Method fields and a new updated methodCalls ArrayList.
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
            // Finally, repalce the original methods of the cpgClass with the new proper methods.
            cpgClass.methods = new ArrayList<>(properMethodsOfClass);
        }

    }

    /**
     * Iterates through a given ArrayList to create and append Method's to a given parent CPGClass object.
     * Finally, returning a HashMap containing the parent CPGClass alongside each Method and each Method called
     * by each of the methods present within the parent class.
     *
     * @param parentClass The parent CPGClass which posseses the method
     * @param methods     An ArrayList of Objects parsed from the sourceCode json
     * @return a HashMap containing the CPGClass alongside each of the Method's within that class and each of
     * the Method calls within each Method body.
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
            // Create a new Method and add it to the parent CPGClass
            Method thisMethod = new Method(parentClass, methodName, methodBody, methodInstruct, modifiers, parameters, returnType, new ArrayList<>());
            parentClass.methods.add(thisMethod);
            // Additionally, add this to the HashMap which contains the Method calls the current Method makes within its body.
            allMethodCallsOfClass.put(methodName, allMethodCallInstructions);
        }
        // Finally, return the HashMap containing the CPGClass alongside each of the Method's within that class and each of
        // the Method calls within each Method body.
        return allMethodCallsOfClass;
    }

    /**
     * Iterates through a given ArrayList to create Attributes and finally
     * returns an ArrayList containing all the Attributes.
     *
     * @param fields An ArrayList of Objects parsed from the sourceCode json
     * @return An ArrayList containing all the parsed and created Attributes
     */
    private ArrayList<Attribute> parseSourceCodeAttributes(ArrayList fields) {
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