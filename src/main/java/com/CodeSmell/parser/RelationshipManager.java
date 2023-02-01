package com.CodeSmell.parser;

import com.CodeSmell.model.ClassRelation;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The RelationshipManager is responsible for adding relations to the CodePropertyGraph object
 * and adding relationships to each CPGClass's outwardRelations attribute.
 */
public class RelationshipManager {
    public final CodePropertyGraph cpg;

    /**
     * Create a RelationshipManager object which will assign all possible relations, provided a valid CPG and
     * will return a new CPG with relations.
     *
     * @param codePropertyGraph - The CodePropertyGraph containing all existing classes and relations
     */
    public RelationshipManager(CodePropertyGraph codePropertyGraph) {
        this.cpg = assignInheritance(codePropertyGraph);
        assignAssociation(cpg);
        assignRealization(cpg);
        assignDependency(cpg);
        assignOutwardRelations(cpg);
    }

    /**
     * Iterates through all the added relations within cpg to add the respective outward relations to the sourceClass.
     */
    protected static void assignOutwardRelations(CodePropertyGraph cpg) {
        for (CodePropertyGraph.Relation relation : cpg.getRelations()) {
            CPGClass source = relation.source;
            source.addOutwardRelation(relation);
        }
    }

    /**
     * Iterates through the cpg assigning association relationships.
     *
     * <p>
     * BIDIRECTIONAL_ASSOCIATION assigned if the source and dest class both store each other as attributes.
     * </p>
     *
     * <p>
     * UNIDIRECTIONAL_ASSOCIATION assigned if only one class has the other as a attribute.
     * </p>
     *
     * <p>
     * REFLEXIVE_ASSOCIATION assigned if the source and dest class are the same CPGClass object.
     * </p>
     **/
    protected static void assignAssociation(CodePropertyGraph cpg) {
        final String[] allClassNamePattern = {""};
        cpg.getClasses().forEach(cpgClass -> allClassNamePattern[0] += cpgClass.name + "|");
        String allNameRegex = allClassNamePattern[0];
        Pattern regexPattern = Pattern.compile(allNameRegex, Pattern.COMMENTS);
        // Iterate through cpg and match all attributes that are a valid class within cpg
        for (CPGClass sourceClass : cpg.getClasses()) {
            ArrayList<String> allAttributeTypes = new ArrayList<>();
            Set<CPGClass> properDestClass = new HashSet<>();
            for (CPGClass.Attribute attribute : sourceClass.attributes) {
                String attributeType = attribute.attributeType;
                Matcher matcher = regexPattern.matcher(attributeType);
                while (matcher.find()) {
                    if (!matcher.group().equals("") && attribute.parentClassName.equals(sourceClass.name)) {
                        allAttributeTypes.add(attributeType);
                        String className = matcher.group();
                        ArrayList<String> allClassNames = getAllClassNames(cpg);
                        if (allClassNames.contains(className)) {
                            int index = allClassNames.indexOf(className);
                            properDestClass.add(cpg.getClasses().get(index));
                        }
                    }
                }
            }
            Map<String, Long> result = allAttributeTypes.stream().
                    collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
            // Add associations for unique destination classes
            for (CPGClass destinationClass : properDestClass) {
                Map<String, Long> filteredResult = result.entrySet().stream().
                        filter(entry -> entry.getKey().contains(destinationClass.name)).
                        collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                String multiplicity = returnHighestMultiplicity(filteredResult);
                ClassRelation.RelationshipType type;
                boolean sameClass = sourceClass == destinationClass;
                // Handle Reflexive Association
                if (sameClass) {
                    type = ClassRelation.RelationshipType.REFLEXIVE_ASSOCIATION;
                } else {
                    // Handle Bi-Directional Association
                    if (determineBidirectionalAssociation(sourceClass, destinationClass)) {
                        type = ClassRelation.RelationshipType.BIDIRECTIONAL_ASSOCIATION;
                    }
                    // Handle Unidirectional Association
                    else {
                        type = ClassRelation.RelationshipType.UNIDIRECTIONAL_ASSOCIATION;
                    }
                    if (determineCompositionRelationship(sourceClass, destinationClass)) {
                        type = ClassRelation.RelationshipType.COMPOSITION;
                    }
                }
                CodePropertyGraph.Relation relationToAdd = new
                        CodePropertyGraph.Relation(sourceClass, destinationClass, type, multiplicity);
                // Add relation if it does not exist and there is no conflict with inheritance
                if (!checkRelationExists(cpg, relationToAdd)) {
                    cpg.addRelation(relationToAdd);
                }
            }
        }
    }

    /**
     * Determines if a bidirectional association exists between two classes.
     *
     * @param sourceClass      - The source CPGClass object
     * @param destinationClass - The destination CPGClass object
     * @return True or False, depending on if bidirectional association exists or not
     */
    protected static boolean determineBidirectionalAssociation(CPGClass sourceClass, CPGClass destinationClass) {
        var destResult = Arrays.stream(destinationClass.attributes).
                filter(attribute -> attribute.attributeType.contains(sourceClass.name)
                        && attribute.parentClassName.equals(destinationClass.name)).collect(Collectors.toList());
        return !destResult.isEmpty();
    }

    /**
     * Determines if a composition relation exists between two classes.
     *
     * @param sourceClass      - The source CPGClass object
     * @param destinationClass - The destination CPGClass object
     * @return True or False, depending on if composition exists or not
     */
    protected static boolean determineCompositionRelationship(CPGClass sourceClass, CPGClass destinationClass) {
        boolean compositionExists = false;
        var constructorResult = Arrays.stream(sourceClass.methods).
                filter(method -> method.name.equals(sourceClass.name)).collect(Collectors.toList());
        var matchingAttribute = Arrays.stream(sourceClass.attributes).
                filter(attribute -> attribute.attributeType.contains(destinationClass.name)
                        && attribute.parentClassName.equals(sourceClass.name)).collect(Collectors.toList());
        // Check for presence of a constructor
        if (!constructorResult.isEmpty()) {
            CPGClass.Method constructor = constructorResult.get(0);
            // Ensure destination class is not within parameters of constructor
            if (!constructor.methodBody.contains(destinationClass.name)) {
                var constructorInstructions = Arrays.stream(constructor.instructions).
                        filter(instruction -> instruction.label.equals("CALL")
                                && instruction.code.contains(destinationClass.name)
                                && instruction.code.contains("new")).collect(Collectors.toList());
                if (!constructorInstructions.isEmpty()) {
                    compositionExists = true;
                }
            }
        }
        // Case where constructor is not present
        else {
            // Check if the attribute is declared and initialized in the same line
            // Additionally ensure the attribute is not static.
            if (matchingAttribute.get(0).code.contains("= new") && !matchingAttribute.get(0).code.contains("static")) {
                compositionExists = true;
            }
        }
        return compositionExists;
    }

    /**
     * Iterates through the cpg, assigning dependencies based off of method parameters and method calls.
     */
    protected static void assignDependency(CodePropertyGraph cpg) {
        ArrayList<String> allClassNames = getAllClassNames(cpg);
        for (CPGClass cpgClass : cpg.getClasses()) {
            for (CPGClass.Method method : cpgClass.methods) {
                // Add dependency relations for parameters that do not have an existing relation of any kind with source class
                if (!method.name.equals(cpgClass.name)) {
                    for (CPGClass.Method.Parameter parameter : method.parameters) {
                        if (allClassNames.contains(parameter.type)) {
                            int index = allClassNames.indexOf(parameter.type);
                            CPGClass destinationClass = cpg.getClasses().get(index);
                            var additionalCheck = cpg.getRelations().stream().
                                    filter(relation -> relation.source.equals(cpgClass) &&
                                            relation.destination.equals(destinationClass))
                                    .collect(Collectors.toList());
                            CodePropertyGraph.Relation relationToAdd = new
                                    CodePropertyGraph.Relation(cpgClass, destinationClass,
                                    ClassRelation.RelationshipType.DEPENDENCY, "");
                            if (additionalCheck.isEmpty() && !checkRelationExists(cpg, relationToAdd)) {
                                cpg.addRelation(relationToAdd);
                            }
                        }
                    }
                    // Add dependency relations based on a method's method calls
                    for (CPGClass.Method methodCall : method.getMethodCalls()) {
                        int index = allClassNames.indexOf(methodCall.parentClassName);
                        CPGClass methodParent = cpg.getClasses().get(index);
                        var additionalCheck = cpg.getRelations().stream().
                                filter(relation -> relation.source.equals(cpgClass) &&
                                        relation.destination.equals(methodParent))
                                .collect(Collectors.toList());
                        CodePropertyGraph.Relation relationToAdd = new
                                CodePropertyGraph.Relation(cpgClass, methodParent,
                                ClassRelation.RelationshipType.DEPENDENCY, "");
                        if (additionalCheck.isEmpty() && !checkRelationExists(cpg, relationToAdd)) {
                            cpg.addRelation(relationToAdd);
                        }
                    }
                }
            }
        }
    }

    /**
     * Iterates through the cpg, assigning inheritance relations and updating the respective subclass to
     * possess the inherited properties of the superclass.
     *
     * @param codePropertyGraph - The CodePropertyGraph containing all existing classes and relations
     * @return An updated CodePropertyGraph object with the updated subclasses
     */
    protected static CodePropertyGraph assignInheritance(CodePropertyGraph codePropertyGraph) {
        CodePropertyGraph updatedGraph = new CodePropertyGraph();
        List<CPGClass> allClasses = codePropertyGraph.getClasses().stream().
                filter(cpgClass -> !cpgClass.code.contains("extends")).collect(Collectors.toList());
        List<CPGClass> allInheritances = codePropertyGraph.getClasses().stream().
                filter(cpgClass -> cpgClass.code.contains("extends")).collect(Collectors.toList());
        // Iterate through the filtered allInheritances list and assign inheritance relationships
        for (CPGClass subClass : allInheritances) {
            for (String className : subClass.inheritsFrom) {
                ArrayList<String> allClassNames = getAllClassNames(codePropertyGraph);
                // Account for cases where Joern may return classes such as Object, which would not be present within CPG
                if (allClassNames.contains(className)) {
                    int index = allClassNames.indexOf(className);
                    CPGClass destinationClass = codePropertyGraph.getClasses().get(index);
                    // Joern provides both interfaces and superclasses within inheritsFrom, account for this case
                    if (!destinationClass.classType.equals("interface") && !subClassAlreadyUpdated(subClass, destinationClass)) {
                        CPGClass properSubClass = appendSuperClassProperties(subClass, destinationClass);
                        allClasses.add(properSubClass);
                        CodePropertyGraph.Relation relationToAdd = new
                                CodePropertyGraph.Relation(properSubClass, destinationClass,
                                ClassRelation.RelationshipType.INHERITANCE, "");
                        if (!checkRelationExists(updatedGraph, relationToAdd)) {
                            updatedGraph.addRelation(relationToAdd);
                        }
                    }
                    // Subclass has already been updated
                    else {
                        allClasses.add(subClass);
                    }
                }
            }
        }
        allClasses.forEach(updatedGraph::addClass);
        codePropertyGraph.getRelations().forEach(updatedGraph::addRelation);
        return updatedGraph;
    }

    /**
     * @param existingSubclass
     * @param superClass
     * @return
     */
    protected static boolean subClassAlreadyUpdated(CPGClass existingSubclass, CPGClass superClass) {
        boolean updated = false;
        // Create sets of sub and superclasses methods and attributes and check to see if
        // all superclass properties are present, if yes then subClass has already been updated.
        Set<CPGClass.Attribute> subAttrSet = new HashSet<>(List.of(existingSubclass.attributes));
        Set<CPGClass.Method> subMethodSet = new HashSet<>(List.of(existingSubclass.methods));
        Set<CPGClass.Attribute> superClassAttrSet = new HashSet<>(List.of(superClass.attributes));
        Set<CPGClass.Method> superClassMethodSet = new HashSet<>(List.of(superClass.methods));
        superClassMethodSet.removeAll(subMethodSet);
        superClassAttrSet.removeAll(subAttrSet);
        if (superClassMethodSet.isEmpty() && superClassAttrSet.isEmpty()) {
            updated = true;
        }
        return updated;
    }

    /**
     * Add all the properties belonging to a given superclass to its subclass and return
     * the updated subclass.
     *
     * <p>
     * This means that subclass will have all the attributes and methods of a superclass.
     * </p>
     *
     * @param subClass   - The subclass CPGClass object
     * @param superClass - The superclass CPGClass object
     * @return The updated subclass with all of its inherited properties
     */
    protected static CPGClass appendSuperClassProperties(CPGClass subClass, CPGClass superClass) {
        // Get all existing subclass properties
        ArrayList<CPGClass.Attribute> allSubClassAttr = new ArrayList<>(List.of(subClass.attributes));
        ArrayList<CPGClass.Method> allSubClassMethods = new ArrayList<>(List.of(subClass.methods));
        List<CPGClass.Method> subClassConstructor = Arrays.stream(subClass.methods).
                filter(method -> method.name.equals(subClass.name)).collect(Collectors.toList());
        List<CPGClass.Method> superClassConstructor = Arrays.stream(superClass.methods).
                filter(method -> method.name.equals(superClass.name)).collect(Collectors.toList());
        // Append superclass constructor to subclass constructor's method call,
        // if 'super' exists in constructor instructions
        if (!subClassConstructor.isEmpty()) {
            CPGClass.Method existingConstructor = subClassConstructor.get(0);
            int index = allSubClassMethods.indexOf(existingConstructor);
            ArrayList<CPGClass.Method> newMethodCalls = new ArrayList<>(existingConstructor.getMethodCalls());
            var superCallResult = Arrays.stream(existingConstructor.instructions).
                    filter(instruction -> instruction.label.equals("CALL") && instruction.methodCall.equals("super")).
                    collect(Collectors.toList());
            if (!superCallResult.isEmpty() && !superClassConstructor.isEmpty()) {
                allSubClassMethods.remove(index);
                CPGClass.Method superMethod = superClassConstructor.get(0);
                newMethodCalls.add(superMethod);
                CPGClass.Method properSubClassConstructor =
                        new CPGClass.Method(
                                existingConstructor.parentClassName,
                                existingConstructor.code,
                                existingConstructor.lineNumberStart,
                                existingConstructor.lineNumberEnd,
                                existingConstructor.name,
                                existingConstructor.modifiers,
                                existingConstructor.returnType,
                                existingConstructor.methodBody,
                                existingConstructor.parameters,
                                existingConstructor.instructions);
                properSubClassConstructor.setMethodCalls(newMethodCalls);
                allSubClassMethods.add(index, properSubClassConstructor);
            }
        }
        // Add all super class properties
        ArrayList<CPGClass.Attribute> allSuperClassAttr = new ArrayList<>(List.of(superClass.attributes));
        ArrayList<CPGClass.Method> allSuperClassMethods = new ArrayList<>(List.of(superClass.methods));
        allSubClassAttr.addAll(allSuperClassAttr);
        allSubClassMethods.addAll(allSuperClassMethods);
        // Return updated subclass
        return new CPGClass(subClass.name, subClass.code,
                subClass.lineNumber, subClass.importStatements, subClass.modifiers,
                subClass.classFullName, subClass.inheritsFrom, subClass.classType, subClass.filePath, subClass.packageName,
                allSubClassAttr.toArray(new CPGClass.Attribute[allSuperClassAttr.size()]),
                allSubClassMethods.toArray(new CPGClass.Method[allSuperClassMethods.size()]));
    }

    /**
     * Iterates through the cpg and assigns realization relationships.
     */
    protected static void assignRealization(CodePropertyGraph cpg) {
        var allRealizations = cpg.getClasses().stream().
                filter(cpgClass -> cpgClass.code.contains("implements")).collect(Collectors.toList());
        ArrayList<String> allClassNames = getAllClassNames(cpg);
        // Iterate through the filtered allRealizations list and assign realization relationships
        for (CPGClass cpgClass : allRealizations) {
            for (String className : cpgClass.inheritsFrom) {
                if (allClassNames.contains(className)) {
                    int index = allClassNames.indexOf(className);
                    CPGClass destinationClass = cpg.getClasses().get(index);
                    if (destinationClass.classType.equals("interface")) {
                        CodePropertyGraph.Relation relationToAdd = new
                                CodePropertyGraph.Relation(cpgClass, destinationClass,
                                ClassRelation.RelationshipType.REALIZATION, "");
                        if (!checkRelationExists(cpg, relationToAdd)) {
                            cpg.addRelation(relationToAdd);
                        }
                    }
                }
            }
        }
    }

    /**
     * Determines whether a relation exists within the CodePropertyGraph (matching the exact source, target, type
     * and multiplicity)
     *
     * <p>
     * If the relation exists, return true and if it does not exist, return false.
     * </p>
     *
     * @param relationToAdd The relation to be added to the provided CodePropertyGraph object.
     * @return boolean - True or False, depending on if the relation exists within cpg
     */
    protected static boolean checkRelationExists(CodePropertyGraph codePropertyGraph, CodePropertyGraph.Relation relationToAdd) {
        var result = codePropertyGraph.getRelations().stream().
                filter(relation -> relation.source.equals(relationToAdd.source)
                        && relation.destination.equals(relationToAdd.destination)
                        && relation.type.equals(relationToAdd.type)
                        && relation.multiplicity.equals(relationToAdd.multiplicity)).collect(Collectors.toList());
        return !result.isEmpty();
    }

    /**
     * Returns the multiplicity given an attribute type and a count of how many instances that attribute
     * has within a given class.
     *
     * @param attribute - A String representation of the attribute type
     * @param count     - A Long representing the count of how many instances of this attribute exist within source class
     * @return A string representing the multiplicity
     */
    protected static String obtainMultiplicity(String attribute, Long count) {
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
     * Return a String representing the highest multiplicity from a map of attribute types.
     *
     * <p>
     * This means that if both 1..1 and 1..* are present, the value returned will be 1..*.
     * </p>
     *
     * @param filteredAttributes - A map containing attribute type's and a count of how many times this attribute type appeared
     *                           within a specific CPGClass
     * @return A String representing the highest multiplicity
     */
    protected static String returnHighestMultiplicity(Map<String, Long> filteredAttributes) {
        ArrayList<String> multiplicityList = new ArrayList<>();
        for (Map.Entry<String, Long> entry : filteredAttributes.entrySet()) {
            String attribute = entry.getKey();
            Long count = entry.getValue();
            multiplicityList.add(obtainMultiplicity(attribute, count));
        }
        // Return the highest multiplicity (account for cases where both 1 to 1 and 1 to many exist, for a given destination class type)
        var result = multiplicityList.stream().filter(multiplicity -> multiplicity.equals("1..*")).collect(Collectors.toList());
        if (!result.isEmpty()) {
            return "1..*";
        } else return multiplicityList.get(0);
    }

    /**
     * Returns an ArrayList containing all the class names within the cpg.
     *
     * @param codePropertyGraph - The CodePropertyGraph containing all existing classes and relations
     * @return An ArrayList containing all the class names
     */
    protected static ArrayList<String> getAllClassNames(CodePropertyGraph codePropertyGraph) {
        ArrayList<String> allClassNames = new ArrayList<>();
        codePropertyGraph.getClasses().forEach(cpgClass -> allClassNames.add(cpgClass.name));
        return allClassNames;
    }
}
