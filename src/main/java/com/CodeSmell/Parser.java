package com.CodeSmell;

import com.CodeSmell.CPGClass.Attribute;
import com.CodeSmell.CPGClass.Method;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Parser {

    public static void main(String[] args) {
        Parser p = new Parser();
        CodePropertyGraph g = p.initializeCPG("src/main/python/joernFiles/sourceCode.json");
        p.assignAllMultiplicities(g);
    }

    /**
     * @param destination
     * @return
     */
    public CodePropertyGraph initializeCPG(String destination) {
        GsonBuilder builder = new GsonBuilder();
        builder.excludeFieldsWithoutExposeAnnotation();
        Gson gson = builder.create();
        CodePropertyGraph cpg = new CodePropertyGraph();
        try {
            Reader reader = Files.newBufferedReader(Paths.get(destination));
            cpg = gson.fromJson(reader, CodePropertyGraph.class);
            // todo comment - explaining why this needs 2 calls.
            CodePropertyGraph tempGraph = assignProperFieldsAndMethods(cpg);
            cpg = assignProperFieldsAndMethods(tempGraph);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return cpg;
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
        Method[] classMethods = cpgClass.methods;
        String stringToFind = "this." + attribute.name;
        String finalAttributeType = attribute.type;
        var result = Arrays.stream(classMethods).
                filter(method -> method.name.equals(className)).collect(Collectors.toList());
        if (!result.isEmpty()) {
            Method constructor = result.get(0);
            var instructionResult = Arrays.stream(constructor.instructions).
                    filter(instruction -> instruction.label.equals("METHOD"))
                    .collect(Collectors.toList());
            String constructorBody = instructionResult.get(0).code;
            // Check the constructor, if the desired field is a parameter, this will obtain the type
            if (constructorBody.contains(attribute.name)) {
                var attributeResult = Arrays.stream(constructor.parameters).
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
                    var parameterResult = Arrays.stream(method.parameters).
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
     * @param methodToUpdate
     * @return
     */
    public Method updateMethodWithMethodCalls(CodePropertyGraph cpg, Method methodToUpdate) {
        ArrayList<Method> allMethodsInCPG = new ArrayList<>();
        ArrayList<String> allMethodNames = new ArrayList<>();
        cpg.getClasses().stream().forEach(cpgClass -> Arrays.stream(cpgClass.methods).forEach(method -> allMethodsInCPG.add(method)));
        cpg.getClasses().stream().forEach(cpgClass -> Arrays.stream(cpgClass.methods).forEach(method -> allMethodNames.add(method.name)));

        // Get the indexes of the names of each Method called by methodToUpdate
        ArrayList<Integer> indexes = new ArrayList<>();
        for (Method.Instruction instruction : methodToUpdate.instructions) {
            String label = instruction.label;
            String methodCall = instruction.methodCall;
            if (label.equals("CALL") && (!methodCall.equals("") || !methodCall.equals("toString"))) {
                int indexOfMethodCalled = allMethodNames.indexOf(methodCall);
                int lastIndexOfMethodCalled = allMethodNames.lastIndexOf(methodCall);
                if (indexOfMethodCalled != lastIndexOfMethodCalled) {
                    Method firstMethod = allMethodsInCPG.get(indexOfMethodCalled);
                    Method secondMethod = allMethodsInCPG.get(lastIndexOfMethodCalled);
                    List<CPGClass> filteredClasses = cpg.getClasses().stream().
                            filter(cpgClass -> cpgClass.name.equals(firstMethod.parentClassName) ||
                                    cpgClass.name.equals(secondMethod.parentClassName)).collect(Collectors.toList());
                    var result = filteredClasses.stream().
                            filter(cpgClass -> cpgClass.type.equals("interface")).collect(Collectors.toList());
                    if (!result.isEmpty()) {
                        if (firstMethod.parentClassName.equals(result.get(0).name)) {
                            indexes.add(lastIndexOfMethodCalled);
                        } else if (secondMethod.parentClassName.equals(result.get(0).name)) {
                            indexes.add(indexOfMethodCalled);
                        }
                    }
                } else {
                    if (indexOfMethodCalled != -1) {
                        indexes.add(indexOfMethodCalled);
                    }
                }
            }
        }
        ArrayList<Method> methodCalls = new ArrayList<>();
        Set<Integer> uniqueIndexes = new LinkedHashSet<>(indexes);
        uniqueIndexes.stream().forEach(index -> methodCalls.add(allMethodsInCPG.get(index)));
        Method[] methodsCalled = methodCalls.toArray(new Method[methodCalls.size()]);

        return new Method(methodToUpdate.parentClassName,
                methodToUpdate.code, methodToUpdate.name, methodToUpdate.modifiers, methodToUpdate.returnType,
                methodToUpdate.methodBody, methodToUpdate.parameters, methodToUpdate.instructions,
                methodsCalled);
    }

    /**
     * @param cpg
     * @return
     */
    public CodePropertyGraph assignProperFieldsAndMethods(CodePropertyGraph cpg) {
        CodePropertyGraph graph = new CodePropertyGraph();
        for (CPGClass cpgClass : cpg.getClasses()) {
            ArrayList<Attribute> properAttributes = new ArrayList<>();
            ArrayList<Method> properMethods = new ArrayList<>();
            for (Attribute a : cpgClass.attributes) {
                if (a.packageName.equals("java.util") && !a.type.contains("<")) {
                    String provisionalType = assignMissingAttributeType(cpgClass, a);
                    properAttributes.add(new Attribute(a.name, a.packageName, provisionalType, a.modifiers));
                } else {
                    properAttributes.add(a);
                }
            }
            for (Method m : cpgClass.methods) {
                Method properMethod = updateMethodWithMethodCalls(cpg, m);
                properMethods.add(properMethod);
            }
            CPGClass properClass = new CPGClass(cpgClass.name, cpgClass.classFullName,
                    cpgClass.type, cpgClass.filePath, cpgClass.packageName,
                    properAttributes.toArray(new Attribute[properAttributes.size()]),
                    properMethods.toArray(new Method[properMethods.size()]));
            graph.addClass(properClass);
        }
        return graph;
    }

    //todo HAS A, IS A , USES A

    CodePropertyGraph.Relation determineAssociationRelationshipType(CodePropertyGraph cpg, CPGClass sourceClass, CPGClass destinationClass, String sourceToDestCardinality) {
        Method[] sourceClassMethods = sourceClass.methods;
        Method[] destinationClassMethods = destinationClass.methods;


        // Get the constructors of both the source and destination classes, if present.
        //List<Method> constructorSrc = sourceClassMethods.stream().filter(method -> method.name.contains(sourceClass.name)).collect(Collectors.toList());
        //List<Method> constructorDst = destinationClassMethods.stream().filter(method -> method.name.contains(destinationClass.name)).collect(Collectors.toList());

        // todo fix
        return null;
    }

    /**
     * @param sourceClass
     * @param destinationClass
     */
    // TODO
    public String compareClassMultiplicity(CodePropertyGraph cpg, CPGClass sourceClass, CPGClass destinationClass) {
        String destinationType = destinationClass.name;
        Attribute[] sourceAttributes = sourceClass.attributes;

        // Determine the multiplicity of the source class to the dest class
        // (i.e. how many instance of the dest class are present within the source class)
        String cardinality = "";
        ArrayList<String> types = new ArrayList<>();
        Arrays.stream(sourceClass.attributes)
                .filter(attributes -> attributes.type.contains(destinationClass.name)).
                collect(Collectors.toList()).
                stream().forEach(attribute -> types.add(attribute.type));
        Set<String> uniqueTypes = new HashSet<>(types);
        if (!uniqueTypes.isEmpty()) {
            ArrayList<String> newTypesList = new ArrayList<>(uniqueTypes);
            String currentType = newTypesList.get(0);
            if (currentType.contains("<") || currentType.contains("[]")) {
                cardinality = "1..*";
            } else {
                cardinality = "1..1";
            }
        }
        System.out.println("Source Class: " + sourceClass.name + " has a " + cardinality + " with Dest Class: " + destinationClass.name);
        return cardinality;
    }

    /**
     * @param cpg
     */
    public void assignAllMultiplicities(CodePropertyGraph cpg) {
        ArrayList<String> classNames = new ArrayList<>();
        cpg.getClasses().stream().forEach(cpgClass -> classNames.add(cpgClass.name));
        // Iterate through each cpgClass within cpg and determine the multiplicities between classes.
        for (CPGClass cpgClass : cpg.getClasses()) {
            Attribute[] attributes = cpgClass.attributes;
            for (Attribute a : attributes) {
                String attributeType = a.type;
                String cardinality = "";
                // Check for single instance of another class
                if (classNames.contains(attributeType)) {
                    int checkIndex = classNames.indexOf(attributeType);
                    cardinality = compareClassMultiplicity(cpg, cpgClass, cpg.getClasses().get(checkIndex));
                }
                // Check for presence of arrays
                else if (attributeType.contains("[]")) {
                    attributeType = attributeType.replace("[]", "");
                    int checkIndex = classNames.indexOf(attributeType);
                    if (checkIndex != -1 && !attributeType.equals(cpgClass.name)) {
                        cardinality = compareClassMultiplicity(cpg, cpgClass, cpg.getClasses().get(checkIndex));
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
                            compareClassMultiplicity(cpg, cpgClass, cpg.getClasses().get(firstTypeInd));
                            compareClassMultiplicity(cpg, cpgClass, cpg.getClasses().get(secondTypeInd));
                        } else {
                            cardinality = compareClassMultiplicity(cpg, cpgClass, cpg.getClasses().get(firstTypeInd));
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
                            cardinality = compareClassMultiplicity(cpg, cpgClass, cpg.getClasses().get(checkIndex));
                        }
                    }
                }
            }
        }
    }


    private class JavaAttribute extends Attribute {
        JavaAttribute(String name, String packageName, String type, CPGClass.Modifier[] m) {
            super(name, packageName, type, m);
        }
    }

    private class JavaMethod extends Method {
        JavaMethod(String parentClassName, String code, String name, CPGClass.Modifier[] modifiers, String returnType, String methodBody, Parameter[] parameters, Instruction[] instructions, Method[] methodCalls) {
            super(parentClassName, code, name, modifiers, returnType, methodBody, parameters, instructions, methodCalls);
        }
    }
}