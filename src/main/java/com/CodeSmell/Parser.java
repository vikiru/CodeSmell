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
        p.assignDependencyRelationships(g);
        p.assignInheritanceRelationships(g);
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
            cpg = assignProperFieldsAndMethods(cpg, 2);
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
    private String assignMissingAttributeType(CPGClass cpgClass, Attribute attribute) {
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
    private Method updateMethodWithMethodCalls(CodePropertyGraph cpg, Method methodToUpdate, int iterationNumber) {
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

        if (iterationNumber == 1) {
            Method properMethod = new Method(methodToUpdate.parentClassName,
                    methodToUpdate.code, methodToUpdate.name, methodToUpdate.modifiers, methodToUpdate.returnType,
                    methodToUpdate.methodBody, methodToUpdate.parameters, methodToUpdate.instructions);
            properMethod.setMethodCalls(methodCalls);
            return properMethod;
        } else {
            ArrayList<Method> fixMethodReferences = new ArrayList<>();
            for (Method m : methodToUpdate.getMethodCalls()) {
                var result = allMethodsInCPG.stream().
                        filter(method -> method.name.equals(m.name) && method.parentClassName.equals(m.parentClassName))
                        .collect(Collectors.toList());
                fixMethodReferences.add(result.get(0));
            }
            methodToUpdate.setMethodCalls(fixMethodReferences);
            return methodToUpdate;
        }
    }

    /**
     * @param cpg
     * @param iterations
     * @return
     */
    private CodePropertyGraph assignProperFieldsAndMethods(CodePropertyGraph cpg, int iterations) {
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
                int iterationNumber = (iterations - 1) == 0 ? 2 : 1;
                Method properMethod = updateMethodWithMethodCalls(cpg, m, iterationNumber);
                properMethods.add(properMethod);
            }
            CPGClass properClass = new CPGClass(cpgClass.name, cpgClass.classFullName,
                    cpgClass.type, cpgClass.filePath, cpgClass.packageName,
                    properAttributes.toArray(new Attribute[properAttributes.size()]),
                    properMethods.toArray(new Method[properMethods.size()]));
            graph.addClass(properClass);
        }
        if (iterations - 1 == 0) {
            return graph;
        } else return assignProperFieldsAndMethods(graph, iterations - 1);
    }

    private void assignInheritanceRelationships(CodePropertyGraph cpg) {
        ArrayList<String> allClassNames = new ArrayList<String>();
        ArrayList<Method> allMethodsInCPG = new ArrayList<>();
        ArrayList<String> allMethodNames = new ArrayList<>();
        cpg.getClasses().stream().forEach(cpgClass -> Arrays.stream(cpgClass.methods).forEach(method -> allMethodsInCPG.add(method)));
        cpg.getClasses().stream().forEach(cpgClass -> Arrays.stream(cpgClass.methods).forEach(method -> allMethodNames.add(method.name)));
        cpg.getClasses().stream().forEach(cpgClass -> allClassNames.add(cpgClass.name));

        var interfacesResult = cpg.getClasses().stream().
                filter(cpgClass -> cpgClass.type.equals("interface")).collect(Collectors.toList());

        CodePropertyGraph.Relation relationToAdd;
        if (!interfacesResult.isEmpty()) {
            for (CPGClass interfaceClass : interfacesResult) {
                System.out.println(interfaceClass.name);
                for (Method method : interfaceClass.methods) {
                    int firstIndex = allMethodNames.indexOf(method.name);
                    int lastIndex = allMethodNames.lastIndexOf(method.name);
                    Method firstMethod = allMethodsInCPG.get(firstIndex);
                    Method secondMethod = allMethodsInCPG.get(lastIndex);
                    int parentFirst = allClassNames.indexOf(firstMethod.parentClassName);
                    int parentSecond = allClassNames.indexOf(secondMethod.parentClassName);
                    CPGClass parentFirstClass = cpg.getClasses().get(parentFirst);
                    CPGClass parentSecondClass = cpg.getClasses().get(parentSecond);
                    if (!parentFirstClass.type.equals("interface")) {
                        relationToAdd = new CodePropertyGraph.
                                Relation(parentFirstClass, parentSecondClass,
                                ClassRelation.Type.REALIZATION, ClassRelation.Multiplicity.NONE);
                    } else {
                        relationToAdd = new CodePropertyGraph.
                                Relation(parentSecondClass, parentFirstClass,
                                ClassRelation.Type.REALIZATION, ClassRelation.Multiplicity.NONE);
                    }
                    CodePropertyGraph.Relation finalRelationToAdd = relationToAdd;
                    var result = cpg.getRelations().stream().
                            filter(relation -> relation.source.equals(finalRelationToAdd.source)
                                    && relation.destination.equals(finalRelationToAdd.destination)
                                    && relation.type.equals(finalRelationToAdd.type)
                                    && relation.multiplicity.equals(finalRelationToAdd.multiplicity)).collect(Collectors.toList());
                    if (result.isEmpty()) {
                        cpg.addRelation(relationToAdd);
                    }
                }
            }
        }
    }


    /**
     * @param cpg
     */
    private void assignDependencyRelationships(CodePropertyGraph cpg) {
        //DEPENDENCY
        ArrayList<String> allClassNames = new ArrayList<String>();
        cpg.getClasses().stream().forEach(cpgClass -> allClassNames.add(cpgClass.name));
        for (CPGClass cpgClass : cpg.getClasses()) {
            for (Method method : cpgClass.methods) {
                for (Method.Parameter parameter : method.parameters) {
                    String typeProperName = parameter.type;
                    if (typeProperName.contains("[]")) {
                        typeProperName.replace("[]", "");
                    } else if (typeProperName.contains("<")) {
                        int startIndex = typeProperName.indexOf("<");
                        int endIndex = typeProperName.indexOf(">");
                        typeProperName = typeProperName.substring(startIndex, endIndex + 1);
                    }
                    if (allClassNames.contains(typeProperName)) {
                        int classIndex = allClassNames.indexOf(typeProperName);
                        CPGClass destinationClass = cpg.getClasses().get(classIndex);
                        var checkAttributes = Arrays.stream(cpgClass.attributes).
                                filter(attribute -> attribute.type.contains(destinationClass.name)).collect(Collectors.toList());
                        // Only add the dependency relationship if the destination class is not present within the source class's
                        // attributes.
                        if (checkAttributes.isEmpty()) {
                            CodePropertyGraph.Relation relationToAdd = new CodePropertyGraph.
                                    Relation(cpgClass, destinationClass,
                                    ClassRelation.Type.DEPENDENCY, ClassRelation.Multiplicity.NONE);

                            var result = cpg.getRelations().stream().
                                    filter(relation -> relation.source.equals(relationToAdd.source)
                                            && relation.destination.equals(relationToAdd.destination)
                                            && relation.type.equals(relationToAdd.type)
                                            && relation.multiplicity.equals(relationToAdd.multiplicity)).collect(Collectors.toList());
                            if (result.isEmpty()) {
                                cpg.addRelation(relationToAdd);
                            }
                        }
                    }

                }
            }
        }
    }

    /**
     * @param cpg
     * @param sourceClass
     * @param destinationClass
     * @param multiplicity
     */
    private void assignAssociationRelationshipType(CodePropertyGraph cpg, CPGClass sourceClass, CPGClass destinationClass, ClassRelation.Multiplicity multiplicity) {
        // ASSOCIATION, AGGREGATION, COMPOSITION
        CodePropertyGraph.Relation relationToAdd;
        ClassRelation.Type type;

        // ASSOCIATION (1..1 OR 1..N)
        if (!multiplicity.getCardinality().equals("1..*") && !multiplicity.getCardinality().equals("*..*")) {
            type = ClassRelation.Type.ASSOCIATION;
        }
        // AGGREGATION OR COMPOSITION (1..* OR *..*)
        else {
            var attributeResult = Arrays.stream(sourceClass.attributes).
                    filter(attribute -> attribute.type.contains(destinationClass.name)).collect(Collectors.toList());
            String attributeToFind = attributeResult.get(0).name;
            var constructorResult = Arrays.stream(sourceClass.methods).
                    filter(method -> method.name.equals(sourceClass.name)).collect(Collectors.toList());
            // Check for the prescence of a constructor
            if (!constructorResult.isEmpty()) {
                Method constructor = constructorResult.get(0);
                var parameterResult = Arrays.stream(constructor.parameters).
                        filter(parameter -> parameter.name.equals(attributeToFind)).collect(Collectors.toList());
                // Check if the destination class is within the constructor params, if so then this is aggregation.
                if (!parameterResult.isEmpty()) {
                    type = ClassRelation.Type.AGGREGATION;
                }
                // If not, check the constructor's instructions.
                else {
                    String stringToFind = "this." + attributeToFind;
                    var instructionResult = Arrays.stream(constructor.instructions).
                            filter(instruction -> instruction.label.equals("CALL")
                                    && instruction.code.contains(stringToFind) && instruction.code.contains("= new")).collect(Collectors.toList());
                    // Check if the field is declared within the constructor, if yes - composition and
                    // if no - that means it is declared elsewhere
                    // Since it was not passed within the constructor, it is not aggregation. Therefore, composition is only option.
                    if (!instructionResult.isEmpty()) {
                        type = ClassRelation.Type.COMPOSITION;
                    } else type = ClassRelation.Type.COMPOSITION;
                }
            }
            // Constructor does not exist
            else {
                var methodResult = Arrays.stream(sourceClass.methods).
                        filter(method -> method.name.contains("set") && method.methodBody.contains(destinationClass.name)).collect(Collectors.toList());
                // Check for the prescence of a setter, if it exists then this is an aggregation
                if (!methodResult.isEmpty()) {
                    type = ClassRelation.Type.AGGREGATION;
                }
                // If setter does not exist, this means relation is a composition
                else {
                    type = ClassRelation.Type.COMPOSITION;
                }
            }
        }

        // Finally, create the relation and determine if it is possible to add it (i.e. the relation does not already exist)
        relationToAdd = new CodePropertyGraph.Relation(sourceClass, destinationClass, type, multiplicity);

        var result = cpg.getRelations().stream().
                filter(relation -> relation.source.equals(relationToAdd.source)
                        && relation.destination.equals(relationToAdd.destination)
                        && relation.type.equals(relationToAdd.type)
                        && relation.multiplicity.equals(relationToAdd.multiplicity)).collect(Collectors.toList());
        if (result.isEmpty()) {
            cpg.addRelation(relationToAdd);
        }

    }

    /**
     * @param sourceClass
     * @param destinationClass
     */
    // TODO
    public void compareClassMultiplicity(CodePropertyGraph cpg, CPGClass sourceClass, CPGClass destinationClass) {
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
                assignAssociationRelationshipType(cpg, sourceClass, destinationClass, ClassRelation.Multiplicity.ONE_TO_MANY);
            } else {
                if (types.size() > 1) {
                    cardinality = "1.." + types.size();
                    ClassRelation.Multiplicity multiplicity = ClassRelation.Multiplicity.ONE_TO_N;
                    multiplicity.setCardinality(cardinality);
                    assignAssociationRelationshipType(cpg, sourceClass, destinationClass, multiplicity);
                } else if (types.size() == 1) {
                    cardinality = "1..1";
                    assignAssociationRelationshipType(cpg, sourceClass, destinationClass, ClassRelation.Multiplicity.ONE_TO_ONE);
                }
            }
        }
        System.out.println("Source Class: " + sourceClass.name + " has a " + cardinality + " with Dest Class: " + destinationClass.name);
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
                    compareClassMultiplicity(cpg, cpgClass, cpg.getClasses().get(checkIndex));
                }
                // Check for presence of arrays
                else if (attributeType.contains("[]")) {
                    attributeType = attributeType.replace("[]", "");
                    int checkIndex = classNames.indexOf(attributeType);
                    if (checkIndex != -1 && !attributeType.equals(cpgClass.name)) {
                        compareClassMultiplicity(cpg, cpgClass, cpg.getClasses().get(checkIndex));
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
                            compareClassMultiplicity(cpg, cpgClass, cpg.getClasses().get(firstTypeInd));
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
                            compareClassMultiplicity(cpg, cpgClass, cpg.getClasses().get(checkIndex));
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
        JavaMethod(String parentClassName, String code, String name, CPGClass.Modifier[] modifiers, String returnType, String methodBody, Parameter[] parameters, Instruction[] instructions) {
            super(parentClassName, code, name, modifiers, returnType, methodBody, parameters, instructions);
        }
    }
}