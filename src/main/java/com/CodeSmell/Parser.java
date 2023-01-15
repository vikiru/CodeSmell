package com.CodeSmell;

import com.CodeSmell.CPGClass.Attribute;
import com.CodeSmell.CPGClass.Method;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Parser class that reads in the JSON source code of the project that is being analysed and then
 * converts the JSON code to a code property object that can be
 */
public class Parser {

    public static final String CPG_BACKUP_JSON = "cpg_backup.json";

    /**
     * Given a filePath and a CodePropertyGraph object, serialize the cpg into a .json file with
     * pretty printing and write to the given path.
     *
     * @param cpg      - The CodePropertyGraph containing all the classes and relations of the source code
     * @param filePath - The filePath to where the .json will be outputted
     */
    public void writeToJson(CodePropertyGraph cpg, String filePath) {
        GsonBuilder builder = new GsonBuilder();
        builder.excludeFieldsWithoutExposeAnnotation();
        builder.disableHtmlEscaping();
        builder.setPrettyPrinting();
        Gson gson = builder.create();
        try {
            try (Writer writer = new FileWriter(filePath)) {
                gson.toJson(cpg, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Reads in a .json file to create an initial CodePropertyGraph and then calls methods to obtain missing information
     * and update neccessary fields of every element within cpg. Finally, adds relationships to the cpg object and then
     * serializes it into a .json file.
     *
     * @param destination - The filePath containing the .json file
     * @param complete - if true read as CPG.json output file, if false read as 
     * joern_query.py  standard output
     * @return A CodePropertyGraph object containing the source code classes and all relations
     */
    public CodePropertyGraph initializeCPG(Reader joernReader, boolean complete) {
        GsonBuilder builder = new GsonBuilder();
        builder.excludeFieldsWithoutExposeAnnotation();
        Gson gson = builder.create();

        System.out.println("Reading in CPG json. " + complete);
        CodePropertyGraph cpg = new CodePropertyGraph();

        if (!complete) {
            CodePropertyGraph tempCPG = gson.fromJson(joernReader,
                CodePropertyGraph.class);
            if (tempCPG == null) {
                throw new RuntimeException("Bad JSON read by parser.");
            }
            // get missing info for CPGClasses and their fields and methods.
            cpg = assignProperAttributesAndMethods(tempCPG, 2);
            System.out.println("Parser　processed joern_query.py output");
            // assign all relations (association of diff types, composition, realization, inheritance, dependency)
            cpg = this.assignInheritanceRelationship(cpg);
            this.assignRealizationRelationships(cpg);
            this.assignAssociationRelationships(cpg);
            this.assignDependencyRelationships(cpg);
            // finally write the resulting cpg to a json at a given filePath
            // this.writeToJson(cpg, CPG_BACKUP_JSON);
            for (CodePropertyGraph.Relation r : cpg.getRelations()) {
                r.source.addOutwardRelation(r);
            }
            try {
                FileOutputStream fos = new FileOutputStream(CPG_BACKUP_JSON);
                ObjectOutputStream oos = new ObjectOutputStream(fos);
                oos.writeObject(cpg);
                oos.flush();
                oos.close();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } else {
            System.out.println("Parser reading backup file");
            try {
                FileInputStream fis = new FileInputStream(CPG_BACKUP_JSON);
                ObjectInputStream ois = new ObjectInputStream(fis);
                cpg = (CodePropertyGraph) ois.readObject();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        System.out.printf("Project read: %d classes, %d relations\n",
            cpg.getClasses().size(), cpg.getRelations().size());
        return cpg;
    }

    /**
     * Iterates through the cpg and iterates through every attribute of every class assigining association
     * relationships.
     * <p>
     * BIDIRECTIONAL_ASSOCIATION assigned if the source and dest class both store each other as fields.
     * </p>
     * <p>
     * UNIDIRECTIONAL_ASSOCIATION assigned if only one class has the other as a field.
     * </p>
     * <p>
     * REFLEXIVE_ASSOCIATION assigned if the source and dest class are the same CPGClass object.
     * </p>
     *
     * @param cpg The CodePropertyGraph containing classes and existing relations.
     */
    void assignAssociationRelationships(CodePropertyGraph cpg) {
        ArrayList<String> allClassNames = new ArrayList<>();
        cpg.getClasses().stream().forEach(cpgClass -> allClassNames.add(cpgClass.name));
        for (CPGClass cpgClass : cpg.getClasses()) {
            ArrayList<String> allAttributeTypes = new ArrayList<>();
            for (Attribute classAttribute : cpgClass.attributes) {
                String properName = getProperTypeName(classAttribute.attributeType);
                if (properName.contains(",")) {
                    String[] types = properName.split(",");
                    types[0] = types[0].trim();
                    types[1] = types[1].trim();
                    for (String t : types) {
                        // Handle HashMaps
                        if (allClassNames.contains(t)) {
                            int destClassIndex = allClassNames.indexOf(t);
                            CPGClass destClass = cpg.getClasses().get(destClassIndex);
                            String multiplicity = "1..*";
                            ClassRelation.RelationshipType type;
                            var destResult = Arrays.stream(destClass.attributes).
                                    filter(attribute -> attribute.attributeType.contains(cpgClass.name))
                                    .collect(Collectors.toList());
                            if (!destResult.isEmpty()) {
                                String destMultiplicity = obtainMultiplicity(destResult.get(0).attributeType, 0L);
                                if (destMultiplicity.equals("1..*")) {
                                    multiplicity = "*..*";
                                }
                                type = ClassRelation.RelationshipType.BIDIRECTIONAL_ASSOCIATION;
                            } else {
                                type = ClassRelation.RelationshipType.UNIDIRECTIONAL_ASSOCIATION;
                            }
                            if (cpgClass == destClass) {
                                type = ClassRelation.RelationshipType.REFLEXIVE_ASSOCIATION;
                            }
                            if (determineCompositionRelationship(cpgClass, destClass)) {
                                type = ClassRelation.RelationshipType.COMPOSITION;
                            }
                            CodePropertyGraph.Relation relationToAdd = new CodePropertyGraph.Relation
                                    (cpgClass, destClass,
                                            type, multiplicity);
                            if (!checkExistingRelation(cpg, relationToAdd)) {
                                cpg.addRelation(relationToAdd);
                            }
                        }
                    }
                } else {
                    if (allClassNames.contains(properName)) {
                        allAttributeTypes.add(classAttribute.attributeType);
                    }
                }
            }
            Map<String, Long> result
                    = allAttributeTypes.stream().collect(
                    Collectors.groupingBy(
                            Function.identity(),
                            Collectors.counting()));
            if (!result.isEmpty()) {
                for (Map.Entry<String, Long> entry : result.entrySet()) {
                    String attributeType = entry.getKey();
                    Long count = entry.getValue();
                    int destClassIndex = allClassNames.indexOf(getProperTypeName(attributeType));
                    CPGClass destClass = cpg.getClasses().get(destClassIndex);
                    String multiplicity = obtainMultiplicity(attributeType, count);
                    // Handle many-to-many
                    ClassRelation.RelationshipType type;
                    var destResult = Arrays.stream(destClass.attributes).
                            filter(attribute -> attribute.attributeType.contains(cpgClass.name)).collect(Collectors.toList());
                    if (!destResult.isEmpty()) {
                        String destMultiplicity = obtainMultiplicity(destResult.get(0).attributeType, 0L);
                        if (destMultiplicity.equals("1..*")) {
                            multiplicity = "*..*";
                        }
                        type = ClassRelation.RelationshipType.BIDIRECTIONAL_ASSOCIATION;
                    } else {
                        type = ClassRelation.RelationshipType.UNIDIRECTIONAL_ASSOCIATION;
                    }
                    if (cpgClass == destClass) {
                        type = ClassRelation.RelationshipType.REFLEXIVE_ASSOCIATION;
                    }
                    if (determineCompositionRelationship(cpgClass, destClass)) {
                        type = ClassRelation.RelationshipType.COMPOSITION;
                    }
                    CodePropertyGraph.Relation relationToAdd = new
                            CodePropertyGraph.Relation(cpgClass, destClass,
                            type, multiplicity);
                    if (!checkExistingRelation(cpg, relationToAdd)) {
                        var additionalCheck = cpg.getRelations().stream().
                                filter(relation -> relation.source.equals(cpgClass)
                                        && relation.destination.equals(destClass)).collect(Collectors.toList());
                        if (additionalCheck.isEmpty()) {
                            cpg.addRelation(relationToAdd);
                        }
                    }
                }
            }
        }
    }

    /**
     * Given a source and destination CPGClass, determine if the source class has a composition relation
     * with the destination class.
     * <p>
     * Checks for the presence of a constructor and if yes, then checks that the constructor parameters does not
     * contain a parameter matching the type of destination class.
     * <p>
     * If no constructor is present, checks the attribute's code to see if the attribute was declared and initialized on the same line.
     * </p>
     *
     * @param source      - The source class containing an attribute matching the destination class.
     * @param destination - The destination class which is being compared with to determine if source has composition relation with it.
     * @return True or False, depending on if a composition relation exists.
     */
    boolean determineCompositionRelationship(CPGClass source, CPGClass destination) {
        boolean compositionExists = false;
        var constructorResult = Arrays.stream(source.methods).
                filter(method -> method.name.equals(source.name)).collect(Collectors.toList());
        var matchingAttribute = Arrays.stream(source.attributes).
                filter(attribute -> attribute.attributeType.contains(destination.name)).collect(Collectors.toList());
        // Check for presence of a constructor
        if (!constructorResult.isEmpty()) {
            // Check that the destination class's type is not present within the constructor
            Method constructor = constructorResult.get(0);
            if (!constructor.methodBody.contains(destination.name)) {
                var constructorInstructions = Arrays.stream(constructor.instructions).
                        filter(instruction -> instruction.label.equals("CALL")
                                && instruction.code.contains(destination.name)
                                && instruction.code.contains("=")).collect(Collectors.toList());
                if (!constructorInstructions.isEmpty()) {
                    compositionExists = true;
                }
            }
        } else {
            // Check if the field is declared and initialized in the same line
            // Additonally ensure the field is not static.
            if (matchingAttribute.get(0).code.contains("= new") && !matchingAttribute.get(0).code.contains("static")) {
                compositionExists = true;
            }
        }
        return compositionExists;
    }

    /**
     * Iterates through the cpg and assigns dependency relationships if the source class uses another class within
     * a method and the source class does not have the dest class as one of the types for its fields.
     *
     * @param cpg The CodePropertyGraph containing source code and existing relations.
     */
    private void assignDependencyRelationships(CodePropertyGraph cpg) {
        ArrayList<String> allClassNames = new ArrayList<>();
        cpg.getClasses().stream().forEach(cpgClass -> allClassNames.add(cpgClass.name));
        for (CPGClass cpgClass : cpg.getClasses()) {
            for (Method method : cpgClass.methods) {
                if (!method.name.equals(cpgClass.name)) {
                    // Assign dependencies based off of method parameters
                    for (Method.Parameter parameter : method.parameters) {
                        String typeProperName = getProperTypeName(parameter.type);
                        if (allClassNames.contains(typeProperName)) {
                            int classIndex = allClassNames.indexOf(typeProperName);
                            CPGClass destClass = cpg.getClasses().get(classIndex);
                            var checkAttributes = Arrays.stream(cpgClass.attributes).
                                    filter(attribute -> attribute.attributeType.contains(destClass.name)).collect(Collectors.toList());
                            // Only add the dependency relationship if the destination class is not present within the source class's
                            // attributes.
                            if (checkAttributes.isEmpty()) {
                                CodePropertyGraph.Relation relationToAdd = new CodePropertyGraph.
                                        Relation(cpgClass, destClass,
                                        ClassRelation.RelationshipType.DEPENDENCY, "");
                                if (!checkExistingRelation(cpg, relationToAdd)) {
                                    var additionalCheck = cpg.getRelations().stream().
                                            filter(relation -> relation.source.equals(cpgClass)
                                                    && relation.destination.equals(destClass)).collect(Collectors.toList());
                                    if (additionalCheck.isEmpty()) {
                                        cpg.addRelation(relationToAdd);
                                    }
                                }
                            }
                        }
                    }
                    // Assign dependencies off of method calls
                    if (!method.getMethodCalls().isEmpty()) {
                        for (Method calls : method.getMethodCalls()) {
                            String parentClass = calls.parentClassName;
                            int indexParent = allClassNames.indexOf(parentClass);
                            if (indexParent != -1) {
                                CPGClass destClass = cpg.getClasses().get(indexParent);
                                if (destClass != cpgClass) {
                                    CodePropertyGraph.Relation relationToAdd = new CodePropertyGraph.
                                            Relation(cpgClass, destClass,
                                            ClassRelation.RelationshipType.DEPENDENCY, "");
                                    if (!checkExistingRelation(cpg, relationToAdd)) {
                                        var additionalCheck = cpg.getRelations().stream().
                                                filter(relation -> relation.source.equals(cpgClass)
                                                        && relation.destination.equals(destClass)).collect(Collectors.toList());
                                        if (additionalCheck.isEmpty()) {
                                            cpg.addRelation(relationToAdd);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Iterates through the CodePropertyGraph and assigns inheritance relations for every class
     * which extends another class. Additionally, call a helper method so that the super class's methods are
     * appended to the subclass.
     * <p>
     * Once this is done, a new class is created and appended to a new CodePropertyGraph object. After all classes
     * are iterated through, relations are added and finally, the updatedGraph is returned.
     *
     * @param cpg - The CodePropertyGraph containing existing CPGClass and Relations.
     * @return A CodePropertyGraph containing the updated CPGClasses and Relations.
     */
    private CodePropertyGraph assignInheritanceRelationship(CodePropertyGraph cpg) {
        ArrayList<String> allClassNames = new ArrayList<>();
        cpg.getClasses().stream().forEach(cpgClass -> allClassNames.add(cpgClass.name));
        CodePropertyGraph updatedGraph = new CodePropertyGraph();
        // Handle adding superclass methods to sub class
        for (CPGClass cpgClass : cpg.getClasses()) {
            String code = cpgClass.code;
            if (code.contains("extends")) {
                int startingIndex = code.indexOf("extends");
                String destClassName = code.substring(startingIndex).replace("extends", "").trim();
                if (allClassNames.contains(destClassName)) {
                    int destClassIndex = allClassNames.indexOf(destClassName);
                    CPGClass destClass = cpg.getClasses().get(destClassIndex);
                    CPGClass properClass = appendSuperClassMethods(cpgClass, destClass);
                    updatedGraph.addClass(properClass);
                } else {
                    updatedGraph.addClass(cpgClass);
                }
            } else updatedGraph.addClass(cpgClass);
        }
        // Handle adding inheritance relations
        for (CPGClass newClass : updatedGraph.getClasses()) {
            String code = newClass.code;
            if (code.contains("extends")) {
                int startingIndex = code.indexOf("extends");
                String destClassName = code.substring(startingIndex).replace("extends", "").trim();
                int destClassIndex = allClassNames.indexOf(destClassName);
                if (destClassIndex != -1) {
                    CPGClass destClass = updatedGraph.getClasses().get(destClassIndex);
                    CodePropertyGraph.Relation relationToAdd = new CodePropertyGraph.Relation
                            (newClass, destClass, ClassRelation.RelationshipType.INHERITANCE, "");
                    if (!checkExistingRelation(updatedGraph, relationToAdd)) {
                        updatedGraph.addRelation(relationToAdd);
                    }
                }
            }
        }
        return updatedGraph;
    }

    /**
     * Adds all the existing methods of a superclass to a subclass and returns a new CPGClass object for the subclass.
     *
     * @param subClass   The CPGClass which inherits from the superclass
     * @param superClass The CPGClass which is inherited by the subclass
     * @return The updated CPGClass object for the subclass, with the super class's methods added.
     */
    CPGClass appendSuperClassMethods(CPGClass subClass, CPGClass superClass) {
        ArrayList<Method> methods = new ArrayList<>(Arrays.asList(subClass.methods));
        ArrayList<String> existingMethodNames = new ArrayList<>();
        methods.forEach(method -> existingMethodNames.add(method.name));
        ArrayList<Method> superClassMethods = new ArrayList<>(Arrays.asList(superClass.methods));
        for (Method method : superClassMethods) {
            String name = method.name;
            if (!existingMethodNames.contains(name)) {
                methods.add(method);
            }
        }
        return new CPGClass(subClass.name, subClass.code,
                subClass.importStatements, subClass.modifiers, subClass.classFullName,
                subClass.classType, subClass.filePath, subClass.packageName, subClass.attributes,
                methods.toArray(new Method[methods.size()]));
    }


    /**
     * Iterates through the provided CodePropertyGraph to determine whether a realization relationship exists.
     * This is determined by first checking for the presence of interfaces within the cpg and collecting them within a list.
     * <p>
     * If the list (List<CPGClass>) is not empty, it is iterated and for every method within that interface,
     * it is compared with other existing method names to determine if there is more than one instance of a method name.
     * <p>
     * Ignoring toString() methods, if there is more than one method match, a comparison is made between the parentClass of both
     * methods and whichever is not an interface is the source of the realization relationship.
     *
     * @param cpg -The CodePropertyGraph containing source code classes and existing relations
     */
    void assignRealizationRelationships(CodePropertyGraph cpg) {
        ArrayList<String> allClassNames = new ArrayList<>();
        ArrayList<Method> allMethodsInCPG = new ArrayList<>();
        ArrayList<String> allMethodNames = new ArrayList<>();
        cpg.getClasses().forEach(cpgClass -> Arrays.stream(cpgClass.methods).forEach(method -> allMethodsInCPG.add(method)));
        cpg.getClasses().stream().forEach(cpgClass -> Arrays.stream(cpgClass.methods).forEach(method -> allMethodNames.add(method.name)));
        cpg.getClasses().stream().forEach(cpgClass -> allClassNames.add(cpgClass.name));

        var interfacesResult = cpg.getClasses().stream().
                filter(cpgClass -> cpgClass.classType.equals("interface")).collect(Collectors.toList());

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
                    if (!parentFirstClass.classType.equals("interface")) {
                        relationToAdd = new CodePropertyGraph.
                                Relation(parentFirstClass, parentSecondClass,
                                ClassRelation.RelationshipType.REALIZATION, "");
                    } else {
                        relationToAdd = new CodePropertyGraph.
                                Relation(parentSecondClass, parentFirstClass,
                                ClassRelation.RelationshipType.REALIZATION, "");
                    }
                    if (!checkExistingRelation(cpg, relationToAdd)) {
                        cpg.addRelation(relationToAdd);
                    }
                }
            }
        }
    }

    /**
     * Determines whether a relation exists within the CodePropertyGraph (matching the exact source, target, type
     * and multiplicity)
     * <p>
     * If the relation exists, return true and if it does not exist, return false.
     *
     * @param cpg           The CodePropertyGraph object containing all the classes and existing relations
     * @param relationToAdd The relation to be added to the provided CodePropertyGraph object.
     * @return boolean - True or False, depending on if the relation exists within cpg
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
     * Iterates through the provided CodePropertyGraph object, calling helper
     * methods to update the attributes and methods. Once each attribute and method is updated,
     * a new CPGClass is created containing these new Attribute and Method objects. This class is then added to a
     * new CodePropertyGraph which will finally be returned at the end.
     *
     * @param cpg        - The CodePropertyGraph object containing the updated Attribute and Methods
     * @param iterations - The number of iterations that the method must be called in order to properly update fields and methods.
     * @return
     */
    CodePropertyGraph assignProperAttributesAndMethods(CodePropertyGraph cpg, int iterations) {
        CodePropertyGraph graph = new CodePropertyGraph();
        for (CPGClass cpgClass : cpg.getClasses()) {
            ArrayList<Attribute> properAttributes = new ArrayList<>(Arrays.asList(cpgClass.attributes));
            ArrayList<Method> properMethods = new ArrayList<>();
            for (Method m : cpgClass.methods) {
                int iterationNumber = (iterations - 1) == 0 ? 2 : 1;
                // ignore lambdas for now
                if (!m.name.contains("lambda")) {
                    Method properMethod = updateMethodWithMethodCalls(cpg, m, iterationNumber);
                    properMethods.add(properMethod);
                }
            }

            CPGClass properClass = new CPGClass(cpgClass.name, "", new String[]{}, new CPGClass.Modifier[]{},
                    cpgClass.classFullName, cpgClass.classType, cpgClass.filePath, cpgClass.packageName,
                    properAttributes.toArray(new Attribute[properAttributes.size()]),
                    properMethods.toArray(new Method[properMethods.size()]));
            properClass = assignMissingClassInfo(properClass);
            graph.addClass(properClass);
        }
        if (iterations - 1 == 0) {
            return graph;
        } else return assignProperAttributesAndMethods(graph, iterations - 1);
    }

    /**
     * <p>
     * Given a CPGClass object, this method will read in the .java file by reading the file
     * based on the filePath field of the class. This method is necessary to address certain
     * limitations of Joern.
     * </p>
     *
     * <p>
     * This method will update the CPGClass's code, packageName, classType, classModifiers, importStatements fields.
     * Additionally, updating each the type, code, and modifiers fields of each Attribute within the class as needed.
     * <p>
     * Finally, a new CPGClass object is returned.
     * </p>
     *
     * @param cpgClass - The existing CPGClass object which will be updated.
     * @return A new CPGClass containing the updated CPGClass fields and Attribute objects.
     */
    CPGClass assignMissingClassInfo(CPGClass cpgClass) {
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
        var packageResult = nonEmptyLines.stream().
                filter(line -> line.startsWith("package ") && line.contains(";")).collect(Collectors.toList());

        // get all imports
        var importStatements = nonEmptyLines.stream().
                filter(line -> line.startsWith("import ") && line.contains(";")).collect(Collectors.toList());

        // get class declaration
        var classDeclResult = nonEmptyLines.stream().
                filter(line -> line.contains(cpgClass.name) && line.endsWith("{") && !line.contains("(") && !line.contains(")")).collect(Collectors.toList());

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
            String type = a.attributeType;
            if (type.contains("$")) {
                type = getProperTypeName(type);
            }
            String toFind = type + " " + name;
            var attributeResult = nonEmptyLines.stream().
                    filter(line -> (line.contains(toFind) || line.contains(name))
                            && !line.contains("{") && !line.contains("//") && !line.contains("*")).collect(Collectors.toList());
            // add all modifiers
            if (!attributeResult.isEmpty()) {
                ArrayList<CPGClass.Modifier> fieldModifiers = new ArrayList<>();
                String code = attributeResult.get(0).trim();
                String line = code.replace(a.name, "").trim();
                if (!type.equals(cpgClass.name) && !classType.equals("enum")) {
                    for (CPGClass.Modifier modStr : CPGClass.Modifier.values()) {
                        if (line.contains(modStr.modString)) {
                            line = line.replace(modStr.modString, "").trim();
                            fieldModifiers.add(modStr);
                        }
                    }
                }
                // enum constants are implicitly declared as public static final.
                else {
                    fieldModifiers.add(CPGClass.Modifier.PUBLIC);
                    fieldModifiers.add(CPGClass.Modifier.STATIC);
                    fieldModifiers.add(CPGClass.Modifier.FINAL);
                }
                // get proper type of attribute
                if (a.packageName.equals("java.util") && !type.contains("<")) {
                    int startIndex = line.indexOf("<");
                    int endIndex = line.lastIndexOf(">");
                    if (startIndex != -1 && endIndex != -1) {
                        type += line.substring(startIndex, endIndex + 1).trim();
                    }
                }
                String attrPackage = a.packageName;
                if (a.typeFullName.contains(packageName) && attrPackage.equals("")) {
                    String properType = getProperTypeName(type).trim();
                    if (!type.contains(",")) {
                        attrPackage = packageName + "." + properType;
                    }
                } else if (a.typeFullName.contains("<unresolvedNameSpace>") && attrPackage.equals("")) {
                    attrPackage = packageName;
                }
                // create new attribute with proper info and add to updatedAttributes list
                Attribute properAttribute = new Attribute(name, code, attrPackage,
                        type, fieldModifiers.toArray(new CPGClass.Modifier[fieldModifiers.size()]), a.typeFullName);
                updatedAttributes.add(properAttribute);
            }
        }

        // return the CPGClass with updated info
        return new CPGClass(className, classDeclaration,
                importStatements.toArray(new String[importStatements.size()]),
                classModifiers.toArray(new CPGClass.Modifier[classModifiers.size()]),
                cpgClass.classFullName, classType, cpgClass.filePath, packageName,
                updatedAttributes.toArray(new Attribute[updatedAttributes.size()]),
                cpgClass.methods);
    }

    /**
     * Updates a given method with its proper methodCalls, requires two iterations to get all the proper method
     * calls of each method called within this list.
     *
     * @param cpg            - The CodePropertyGraph containing the source code information
     * @param methodToUpdate - The method that is having its methodCalls field updated
     * @return The updated method with its methodCalls
     */
    Method updateMethodWithMethodCalls(CodePropertyGraph cpg, Method methodToUpdate, int iterationNumber) {
        ArrayList<Method> allMethodsInCPG = new ArrayList<>();
        ArrayList<String> allMethodNames = new ArrayList<>();
        ArrayList<String> allClassNames = new ArrayList<>();
        ArrayList<Method.Instruction> allMethodInstructions = new ArrayList<>(Arrays.asList(methodToUpdate.instructions));
        cpg.getClasses().forEach(cpgClass -> allMethodsInCPG.addAll(Arrays.asList(cpgClass.methods)));
        cpg.getClasses().forEach(cpgClass -> Arrays.stream(cpgClass.methods).forEach(method -> allMethodNames.add(method.name)));
        cpg.getClasses().forEach(cpgClass -> allClassNames.add(cpgClass.name));

        // Get the indexes of the names of each Method called by methodToUpdate
        ArrayList<Integer> indexes = new ArrayList<>();
        for (Method.Instruction instruction : methodToUpdate.instructions) {
            String label = instruction.label;
            String code = instruction.code;
            String lineNumber = instruction.lineNumber;
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
                            filter(cpgClass -> cpgClass.classType.equals("interface")).collect(Collectors.toList());
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
        var identifierResult = allMethodInstructions.stream().
                filter(ins -> ins.label.equals("IDENTIFIER")
                        && allClassNames.contains(ins.code)).collect(Collectors.toList());
        var methodCallResult = allMethodInstructions.stream().
                filter(ins -> ins.label.equals("CALL") && !ins.methodCall.equals("")).collect(Collectors.toList());
        var localResult = allMethodInstructions.stream().
                filter(ins -> ins.label.equals("LOCAL")).collect(Collectors.toList());

        // Check for identifier and calls
        for (Method.Instruction identifier : identifierResult) {
            String parentName = identifier.code;
            for (Method.Instruction mc : methodCallResult) {
                String methodCalled = mc.methodCall;
                // Determine the number of method parameters provided by the instruction code
                String code = mc.code;
                int startIndex = code.indexOf("(");
                code = code.substring(startIndex).replace("(", "").replace(")", "").trim();
                int paramCount = 0;
                if (code.contains(",")) {
                    String[] params = code.split(",");
                    paramCount = params.length;
                } else paramCount = 1;
                int finalParamCount = paramCount;
                // Find the method matching parentClassName, methodName, and parameter count
                var methodResult = allMethodsInCPG.stream().
                        filter(method -> method.name.equals(methodCalled)
                                && method.parentClassName.equals(parentName)
                                && method.parameters.length == finalParamCount).collect(Collectors.toList());
                if (!methodResult.isEmpty()) {
                    indexes.add(allMethodsInCPG.indexOf(methodResult.get(0)));
                }
            }
        }
        // Check for local variable creation
        for (Method.Instruction localIns : localResult) {
            String[] localInstanceVar = localIns.code.split(" ");
            if (localInstanceVar.length == 2) {
                String type = localInstanceVar[0];
                String name = localInstanceVar[1];
                if (allClassNames.contains(type)) {
                    var checkCreation = methodCallResult.stream().
                            filter(i -> i.methodCall.contains(type) && i.code.contains("=")).collect(Collectors.toList());
                    for (Method.Instruction creation : checkCreation) {
                        String methodCalled = creation.methodCall;
                        int index = allMethodNames.indexOf(methodCalled);
                        if (index != -1) {
                            indexes.add(index);
                        }
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
     * Returns the multiplicity given an attribute type and a count of how many instances that attribute
     * has within a given class.
     *
     * @param attribute - A String representation of the attribute type
     * @param count     - A Long representing the count of how many instances of this attribute exist within source class
     * @return A string representing the multiplicity
     */
    String obtainMultiplicity(String attribute, Long count) {
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
     * Returns a String containing the type name after removing extra characters such as "[]", "< >" so
     * that the resulting string can be used in comparisons within cpg.
     *
     * @param typeName The name of the attribute's type.
     * @return The name of the attribute's type without special characters and extra info.
     */
    String getProperTypeName(String typeName) {
        if (typeName.contains("$")) {
            int index = typeName.lastIndexOf("$");
            typeName = typeName.substring(index + 1);
        } else if (typeName.contains("[]")) {
            typeName = typeName.replace("[]", "");
        } else if (typeName.contains("<")) {
            int startingIndex = typeName.indexOf("<");
            int endingIndex = typeName.lastIndexOf(">");
            typeName = typeName.substring(startingIndex, endingIndex + 1).
                    replace("<", " ").
                    replace(">", "").
                    replace(", ", " ").trim();
        }
        return typeName;
    }

    private class JavaAttribute extends Attribute {
        JavaAttribute(String name, String code, String packageName, String type, CPGClass.Modifier[] m, String typeFullName) {
            super(name, code, packageName, type, m, typeFullName);
        }
    }

    private class JavaMethod extends Method {
        JavaMethod(String parentClassName, String code, String name, CPGClass.Modifier[] modifiers, String returnType, String methodBody, Parameter[] parameters, Instruction[] instructions) {
            super(parentClassName, code, name, modifiers, returnType, methodBody, parameters, instructions);
        }
    }
}