package com.CodeSmell.parser;

import com.CodeSmell.model.ClassRelation;
import com.CodeSmell.smell.StatTracker;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The RelationshipManager is responsible for adding relations to the CodePropertyGraph object
 * and adding relationships to each CPGClass's outwardRelations attribute.
 */
public class RelationshipManager {
    /**
     * Create a RelationshipManager object which will assign all possible relations, provided a valid CPG and
     * will return a new CPG with relations.
     *
     * @param cpg - The CodePropertyGraph containing all existing classes and relations
     */
    public RelationshipManager(CodePropertyGraph cpg) {
        if (cpg.getRelations().size() == 0) {
            assignInheritance(cpg);
            assignRealization(cpg);
            assignAssociation(cpg);
            assignDependency(cpg);
            assignOutwardRelations(cpg);
        }
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
        for (CPGClass cpgClass : cpg.getClasses()) {
            HashMap<String, Long> typeCountMap = new HashMap<>();
            Set<CPGClass> uniqueDestinationClasses = new HashSet<>();
            Set<CPGClass.Attribute> filteredAttribute = new HashSet<>();
            cpgClass.getAttributes().stream().filter(attr -> (attr.getParent().equals(cpgClass))).
                    forEach(attribute -> uniqueDestinationClasses.addAll(attribute.getTypeList()));
            cpgClass.getAttributes().stream().filter(attr -> (attr.getParent().equals(cpgClass))).
                    forEach(filteredAttribute::add);
            for (CPGClass.Attribute attr : filteredAttribute) {
                typeCountMap.put(attr.attributeType, typeCountMap.getOrDefault(attr.attributeType, 0L) + 1L);
            }
            for (CPGClass destClass : uniqueDestinationClasses) {
                Map<String, Long> filtered = typeCountMap.entrySet().stream()
                        .filter(entry -> entry.getKey().contains(destClass.name) || entry.getKey().contains(destClass.classFullName))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                String highestMultiplicity = returnHighestMultiplicity(filtered);
                ClassRelation.RelationshipType type = null;
                boolean isReflexive = cpgClass.name.equals(destClass.name);
                boolean isBidirectional = determineBidirectionalAssociation(cpgClass, destClass);
                boolean isComposition = determineCompositionRelationship(cpgClass, destClass);
                if (isReflexive) {
                    type = ClassRelation.RelationshipType.REFLEXIVE_ASSOCIATION;
                } else {
                    if (isBidirectional) {
                        type = ClassRelation.RelationshipType.BIDIRECTIONAL_ASSOCIATION;
                    }
                    if (isComposition && !isBidirectional) {
                        type = ClassRelation.RelationshipType.COMPOSITION;
                    }
                    if (!isBidirectional && !isComposition) {
                        type = ClassRelation.RelationshipType.UNIDIRECTIONAL_ASSOCIATION;
                    }
                }
                CodePropertyGraph.Relation relationToAdd = new
                        CodePropertyGraph.Relation(cpgClass, destClass, type, highestMultiplicity);
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
        boolean bidirectionalAssociationExists = false;
        Set<CPGClass.Attribute> allSourceTypes = sourceClass.getAttributes().stream().
                filter(attr -> attr.getTypeList().contains(destinationClass) && attr.getParent().equals(sourceClass)).
                collect(Collectors.toSet());
        Set<CPGClass.Attribute> allDestinationTypes = destinationClass.getAttributes().stream().
                filter(attr -> (attr.getTypeList().contains(sourceClass)) && attr.getParent().equals(destinationClass)).
                collect(Collectors.toSet());
        if (!allSourceTypes.isEmpty() && !allDestinationTypes.isEmpty()) {
            bidirectionalAssociationExists = true;
        }
        return bidirectionalAssociationExists;
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
        var constructorResult = sourceClass.getMethods().stream().
                filter(method -> method.name.equals(sourceClass.name) &&
                        method.getParent().equals(sourceClass) &&
                        !method.methodBody.contains(destinationClass.name)).collect(Collectors.toList());
        var filteredAttributes = sourceClass.getAttributes().stream().
                filter(attribute -> attribute.getTypeList().contains(destinationClass)
                        && attribute.getParent().equals(sourceClass)
                        && !attribute.code.contains("static")).collect(Collectors.toList());
        String codeToFind = "= new " + destinationClass.name;
        // A constructor does not exist, but attributes matching destination class exist,
        // filter these attributes such that they contain "new" and are not static, or the attribute contains final
        if (constructorResult.isEmpty() && !filteredAttributes.isEmpty()) {
            var compositionAttribute = filteredAttributes.stream().filter(attribute ->
                    attribute.code.contains(codeToFind)
                            || attribute.code.contains("final")).collect(Collectors.toList());
            if (!compositionAttribute.isEmpty()) {
                compositionExists = true;
            }
        }
        // A constructor exists and destination class does not appear within parameters
        // The constructor's instruction contains "= new (destination class name)"
        else if (!constructorResult.isEmpty() && !filteredAttributes.isEmpty()) {
            CPGClass.Method sourceConstructor = constructorResult.get(0);
            var constructorIns = Arrays.stream(sourceConstructor.instructions).
                    filter(instruction -> instruction.code.contains(codeToFind)).collect(Collectors.toList());
            if (!constructorIns.isEmpty()) {
                compositionExists = true;
            }
        }
        return compositionExists;
    }

    /**
     * Iterates through the cpg, assigning dependencies based off of method parameters and method calls.
     */
    protected static void assignDependency(CodePropertyGraph cpg) {
        StatTracker.Helper helper = new StatTracker.Helper(cpg);
        for (CPGClass.Method method : helper.allMethods) {
            CPGClass methodParent = method.getParent();
            var filteredRelations = cpg.getRelations().stream().
                    filter(relation -> (relation.type.equals(ClassRelation.RelationshipType.UNIDIRECTIONAL_ASSOCIATION) ||
                            relation.type.equals(ClassRelation.RelationshipType.BIDIRECTIONAL_ASSOCIATION) ||
                            relation.type.equals(ClassRelation.RelationshipType.REFLEXIVE_ASSOCIATION) ||
                            relation.type.equals(ClassRelation.RelationshipType.COMPOSITION)) &&
                            relation.source.equals(methodParent)).collect(Collectors.toList());
            // Create a set which contains all the classes which srcClass has some kind of association relation to
            Set<CPGClass> classesToIgnore = new HashSet<>();
            filteredRelations.forEach(relation -> classesToIgnore.add(relation.destination));
            // Ignore the class itself and classes it inherits from (interfaces and superclass)
            classesToIgnore.addAll(methodParent.getInheritsFrom());
            classesToIgnore.add(methodParent);
            // Create a single set containing all classes that exist within method params and method calls
            Set<CPGClass> classesToAddDependencies = new HashSet<>();
            method.getMethodCalls().stream().
                    filter(methodCall -> !methodCall.getParent().equals(methodParent)).
                    forEach(methodCall -> classesToAddDependencies.add(methodCall.getParent()));
            // Add all classes that exist within cpg and exist as parameters to the classesToAddDependencies set.
            Arrays.stream(method.parameters).
                    forEach(methodParameter -> classesToAddDependencies.addAll(methodParameter.getTypeList()));
            // Remove all occurrences of elements within classesToIgnore and add dependencies accordingly
            classesToAddDependencies.removeAll(classesToIgnore);
            for (CPGClass destinationClass : classesToAddDependencies) {
                CodePropertyGraph.Relation relationToAdd = new CodePropertyGraph.Relation
                        (methodParent, destinationClass, ClassRelation.RelationshipType.DEPENDENCY, "");
                if (!checkRelationExists(cpg, relationToAdd)) {
                    cpg.addRelation(relationToAdd);
                }
            }
        }
    }


    /**
     * Iterates through the cpg, assigning inheritance relations
     *
     * @param cpg - The CodePropertyGraph containing all existing classes and relations
     */
    protected static void assignInheritance(CodePropertyGraph cpg) {
        for (CPGClass cpgClass : cpg.getClasses()) {
            var filteredInherits = cpgClass.getInheritsFrom().stream().
                    filter(cpgToFind -> !cpgToFind.classType.equals("interface")).collect(Collectors.toList());
            if (!filteredInherits.isEmpty()) {
                for (CPGClass dest : filteredInherits) {
                    CodePropertyGraph.Relation relationToAdd =
                            new CodePropertyGraph.Relation(cpgClass, dest, ClassRelation.RelationshipType.INHERITANCE, "");
                    if (!checkRelationExists(cpg, relationToAdd)) {
                        cpg.addRelation(relationToAdd);
                    }
                }
            }
        }
    }

    /**
     * Iterates through the cpg and assigns realization relationships.
     */
    protected static void assignRealization(CodePropertyGraph cpg) {
        for (CPGClass cpgClass : cpg.getClasses()) {
            var filteredInherits = cpgClass.getInheritsFrom().stream().
                    filter(cpgToFind -> cpgToFind.classType.equals("interface")).collect(Collectors.toList());
            if (!filteredInherits.isEmpty()) {
                for (CPGClass dest : filteredInherits) {
                    CodePropertyGraph.Relation relationToAdd =
                            new CodePropertyGraph.Relation(cpgClass, dest, ClassRelation.RelationshipType.REALIZATION, "");
                    if (!checkRelationExists(cpg, relationToAdd)) {
                        cpg.addRelation(relationToAdd);
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
}
