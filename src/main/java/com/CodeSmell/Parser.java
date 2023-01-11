package com.CodeSmell;

import com.CodeSmell.CPGClass.Attribute;
import com.CodeSmell.CPGClass.Method;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Parser {

    public static void main(String[] args) {
        Parser p = new Parser();
        CodePropertyGraph g = p.initializeCPG("src/main/python/joernFiles/sourceCode.json");
    }

    /**
     * @param cpgClass
     * @return
     */
    private CPGClass assignMissingClassInfo(CPGClass cpgClass) {

        System.out.println("CLASS: " + cpgClass.name);
        System.out.println("ATTRIBUTES: " + Arrays.toString(cpgClass.attributes));
        // KNOWN INFO
        File classFile = new File(cpgClass.filePath);
        String className = cpgClass.name;
        String constructorBody = "";
        var constructorResult = Arrays.stream(cpgClass.methods).
                filter(method -> method.name.equals(className)).collect(Collectors.toList());
        if (!constructorResult.isEmpty()) {
            constructorBody = constructorResult.get(0).code;
        }
        // HELPER VARIABLES
        TreeMap<Integer, String> crucialIndexes = new TreeMap<>();
        HashSet<Integer> attributeIndexes = new HashSet<>();
        HashSet<Integer> methodIndexes = new HashSet<>();
        ArrayList<String> nonEmptyLines = new ArrayList<>();

        // INFO TO FIND
        String classType = "";
        String classDeclaration = "";
        String packageName = "";
        ArrayList<CPGClass.Modifier> classModifiers = new ArrayList<>();
        HashSet<Attribute> updatedAttributes = new HashSet<>();

        // Read in Java file and append non-empty lines to an ArrayList
        try {
            try (Scanner scanner = new Scanner(classFile)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (!line.equals("")) {
                        nonEmptyLines.add(line);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        // get package
        var packageResult = nonEmptyLines.stream().filter(line -> line.startsWith("package ") && line.contains(";")).collect(Collectors.toList());

        // get all imports
        var importStatements = nonEmptyLines.stream().filter(line -> line.startsWith("import ") && line.contains(";")).collect(Collectors.toList());

        // get class declaration
        var classDeclResult = nonEmptyLines.stream().filter(line -> line.contains(cpgClass.name) && line.contains("{")).collect(Collectors.toList());

        if (!packageResult.isEmpty()) {
            packageName = packageResult.get(0).replace("package ", "").replace(";", "").trim();
        }

        // extract missing info from class decl
        if (!classDeclResult.isEmpty()) {
            classDeclaration = classDeclResult.get(0).replace("{", "").trim();
            String[] types = {"class", "enum", "abstract class", "interface"};
            String line = classDeclaration.trim();
            line = line.replace(className, "").replace("{", "").trim();
            for (CPGClass.Modifier m : CPGClass.Modifier.values()) {
                if (line.contains(m.modString)) {
                    line = line.replace(m.modString, "").trim();
                    classModifiers.add(m);
                }
            }
            if (!classModifiers.contains(CPGClass.Modifier.ABSTRACT)) {
                for (String typeStr : types) {
                    if (line.contains(typeStr)) {
                        classType = typeStr;
                    }
                }
            } else {
                classType = "abstract class";
            }
        }

        // extract missing info from attributes
        for (Attribute a : cpgClass.attributes) {
            String name = a.name;
            String type = a.type;
            if (type.contains("$")) {
                type = getProperTypeName(type);
            }
            String toFind = type + " " + name;
            var attributeResult = nonEmptyLines.stream().filter(line -> (line.contains(toFind) || line.contains(name)) && !line.contains("{")).collect(Collectors.toList());
            if (!attributeResult.isEmpty()) {
                ArrayList<CPGClass.Modifier> fieldModifiers = new ArrayList<>();
                String code = attributeResult.get(0).trim();
                String line = code.replace(a.name, "").trim();
                for (CPGClass.Modifier modStr : CPGClass.Modifier.values()) {
                    if (line.contains(modStr.modString)) {
                        line = line.replace(modStr.modString, "").trim();
                        fieldModifiers.add(modStr);
                    }
                }
                Attribute properAttribute = new Attribute(name, code, a.packageName,
                        type, fieldModifiers.toArray(new CPGClass.Modifier[fieldModifiers.size()]));
                updatedAttributes.add(properAttribute);
            }
        }


        return new CPGClass(className, classDeclaration,
                importStatements.toArray(new String[importStatements.size()]),
                classModifiers.toArray(new CPGClass.Modifier[classModifiers.size()]),
                cpgClass.classFullName, classType, cpgClass.filePath, packageName,
                updatedAttributes.toArray(new Attribute[updatedAttributes.size()]),
                cpgClass.methods);
    }


    /**
     * @param destination
     * @return
     */
    public CodePropertyGraph initializeCPG(String destination) {
        GsonBuilder builder = new GsonBuilder();
        builder.excludeFieldsWithoutExposeAnnotation();
        Gson gson = builder.create();
        CodePropertyGraph tempCPG = new CodePropertyGraph();
        CodePropertyGraph cpg = new CodePropertyGraph();
        try {
            Reader reader = Files.newBufferedReader(Paths.get(destination));
            tempCPG = gson.fromJson(reader, CodePropertyGraph.class);
            tempCPG = assignProperFieldsAndMethods(tempCPG, 2);
            // temp
            for (CPGClass cpgClass : tempCPG.getClasses()) {
                cpg.addClass(assignMissingClassInfo(cpgClass));
            }
            this.assignAssociationRelationships(cpg);
            this.assignDependencyRelationships(cpg);
            this.assignRealizationRelationships(cpg);
            this.assignInheritanceRelationship(cpg);
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
     * <p>
     * 1) Check the constructor parameters
     * <p>
     * 2) Check the instructions of the constructor
     * <p>
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
        cpg.getClasses().forEach(cpgClass -> allMethodsInCPG.addAll(Arrays.asList(cpgClass.methods)));
        cpg.getClasses().forEach(cpgClass -> Arrays.stream(cpgClass.methods).forEach(method -> allMethodNames.add(method.name)));

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
        uniqueIndexes.forEach(index -> methodCalls.add(allMethodsInCPG.get(index)));

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
                    properAttributes.add(new Attribute(a.name, "", a.packageName, provisionalType, a.modifiers));
                } else {
                    properAttributes.add(a);
                }
            }
            for (Method m : cpgClass.methods) {
                int iterationNumber = (iterations - 1) == 0 ? 2 : 1;
                Method properMethod = updateMethodWithMethodCalls(cpg, m, iterationNumber);
                properMethods.add(properMethod);
            }

            CPGClass properClass = new CPGClass(cpgClass.name, "", new String[]{}, new CPGClass.Modifier[]{}, cpgClass.classFullName,
                    cpgClass.type, cpgClass.filePath, cpgClass.packageName,
                    properAttributes.toArray(new Attribute[properAttributes.size()]),
                    properMethods.toArray(new Method[properMethods.size()]));
            graph.addClass(properClass);
        }
        if (iterations - 1 == 0) {
            return graph;
        } else return assignProperFieldsAndMethods(graph, iterations - 1);
    }

    /**
     * @param cpg
     */
    private void assignAssociationRelationships(CodePropertyGraph cpg) {
        ArrayList<String> allClassNames = new ArrayList<>();
        cpg.getClasses().stream().forEach(cpgClass -> allClassNames.add(cpgClass.name));
        for (CPGClass cpgClass : cpg.getClasses()) {
            ArrayList<String> allAttributeNames = new ArrayList<>();
            for (Attribute classAttribute : cpgClass.attributes) {
                String properName = getProperTypeName(classAttribute.type);
                if (properName.contains(",")) {
                    String[] types = properName.split(",");
                    types[0] = types[0].trim();
                    types[1] = types[1].trim();
                    for (String t : types) {
                        // Handle HashMaps
                        if (allClassNames.contains(t)) {
                            int destClassIndex = allClassNames.indexOf(t);
                            CodePropertyGraph.Relation relationToAdd = new CodePropertyGraph.Relation
                                    (cpgClass, cpg.getClasses().get(destClassIndex),
                                            ClassRelation.Type.ASSOCIATION, "1..*");
                            if (!checkExistingRelation(cpg, relationToAdd)) {
                                cpg.addRelation(relationToAdd);
                            }
                        }
                    }
                } else {
                    if (allClassNames.contains(getProperTypeName(classAttribute.type))) {
                        allAttributeNames.add(classAttribute.type);
                    }
                }
            }
            Map<String, Long> result
                    = allAttributeNames.stream().collect(
                    Collectors.groupingBy(
                            Function.identity(),
                            Collectors.counting()));
            if (!result.isEmpty()) {
                for (Map.Entry<String, Long> entry : result.entrySet()) {
                    String attributeType = entry.getKey();
                    Long count = entry.getValue();
                    int destClassIndex = allClassNames.indexOf(getProperTypeName(attributeType));
                    CodePropertyGraph.Relation relationToAdd = new
                            CodePropertyGraph.Relation(cpgClass, cpg.getClasses().get(destClassIndex),
                            ClassRelation.Type.ASSOCIATION, obtainMultiplicity(attributeType, count));
                    if (!checkExistingRelation(cpg, relationToAdd)) {
                        cpg.addRelation(relationToAdd);
                    }
                }
            }
        }
    }

    /**
     * @param cpg
     */
    private void assignRealizationRelationships(CodePropertyGraph cpg) {
        ArrayList<String> allClassNames = new ArrayList<>();
        ArrayList<Method> allMethodsInCPG = new ArrayList<>();
        ArrayList<String> allMethodNames = new ArrayList<>();
        cpg.getClasses().forEach(cpgClass -> Arrays.stream(cpgClass.methods).forEach(method -> allMethodsInCPG.add(method)));
        cpg.getClasses().stream().forEach(cpgClass -> Arrays.stream(cpgClass.methods).forEach(method -> allMethodNames.add(method.name)));
        cpg.getClasses().stream().forEach(cpgClass -> allClassNames.add(cpgClass.name));

        var interfacesResult = cpg.getClasses().stream().
                filter(cpgClass -> cpgClass.type.equals("interface")).collect(Collectors.toList());

        CodePropertyGraph.Relation relationToAdd;
        if (!interfacesResult.isEmpty()) {
            for (CPGClass interfaceClass : interfacesResult) {
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
                                ClassRelation.Type.REALIZATION, "");
                    } else {
                        relationToAdd = new CodePropertyGraph.
                                Relation(parentSecondClass, parentFirstClass,
                                ClassRelation.Type.REALIZATION, "");
                    }
                    if (!checkExistingRelation(cpg, relationToAdd)) {
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
                                    ClassRelation.Type.DEPENDENCY, "");
                            if (!checkExistingRelation(cpg, relationToAdd)) {
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
     */
    private void assignInheritanceRelationship(CodePropertyGraph cpg) {
        ArrayList<String> allClassNames = new ArrayList<>();
        cpg.getClasses().stream().forEach(cpgClass -> allClassNames.add(cpgClass.name));

        for (CPGClass cpgClass : cpg.getClasses()) {
            String code = cpgClass.code;
            if (code.contains("extends")) {
                int startIndex = code.indexOf(cpgClass.name);
                String destClass = code.substring(startIndex).replace(cpgClass.name, "").replace("extends", "").trim();
                if (allClassNames.contains(destClass)) {
                    int destClassIndex = allClassNames.indexOf(destClass);
                    CodePropertyGraph.Relation relationToAdd = new CodePropertyGraph.Relation(cpgClass,
                            cpg.getClasses().get(destClassIndex), ClassRelation.Type.INHERITANCE, "");
                    if (!checkExistingRelation(cpg, relationToAdd)) {
                        cpg.addRelation(relationToAdd);
                    }
                }
            }
        }
    }

    /**
     * @param cpg
     * @param relationToAdd
     * @return
     */
    private boolean checkExistingRelation(CodePropertyGraph cpg, CodePropertyGraph.Relation relationToAdd) {
        var result = cpg.getRelations().stream().
                filter(relation -> relation.source.equals(relationToAdd.source)
                        && relation.destination.equals(relationToAdd.destination)
                        && relation.type.equals(relationToAdd.type)
                        && relation.multiplicity.equals(relationToAdd.multiplicity)).collect(Collectors.toList());
        if (result.isEmpty()) {
            return false;
        } else return true;
    }


    /**
     * @param attribute
     * @param count
     * @return
     */
    private String obtainMultiplicity(String attribute, Long count) {
        String multiplicityToReturn = "";
        if (attribute.contains("[]") || attribute.contains("<")) {
            multiplicityToReturn = "1..*";
        } else {
            if (count == 1) {
                multiplicityToReturn = "1..1";
            } else if (count > 1) {
                multiplicityToReturn = "1.." + count;
            }
        }
        return multiplicityToReturn;
    }

    /**
     * @param typeName
     * @return
     */
    private String getProperTypeName(String typeName) {
        if (typeName.contains("$")) {
            int index = typeName.lastIndexOf("$");
            typeName = typeName.substring(index + 1);
        } else if (typeName.contains("[]")) {
            typeName = typeName.replace("[]", "");
        } else if (typeName.contains("<")) {
            int startingIndex = typeName.indexOf("<");
            int endingIndex = typeName.indexOf(">");
            typeName = typeName.substring(startingIndex, endingIndex + 1).trim().replace("<", "").replace(">", "");
        }
        return typeName;
    }

    private class JavaAttribute extends Attribute {
        JavaAttribute(String name, String code, String packageName, String type, CPGClass.Modifier[] m) {
            super(name, code, packageName, type, m);
        }
    }

    private class JavaMethod extends Method {
        JavaMethod(String parentClassName, String code, String name, CPGClass.Modifier[] modifiers, String returnType, String methodBody, Parameter[] parameters, Instruction[] instructions) {
            super(parentClassName, code, name, modifiers, returnType, methodBody, parameters, instructions);
        }
    }
}