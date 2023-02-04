package com.CodeSmell.parser;

import com.CodeSmell.model.ClassRelation;
import com.CodeSmell.smell.StatTracker;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

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
        if (codePropertyGraph.getRelations().size() == 0) {
            this.cpg = assignInheritance(codePropertyGraph);
            assignRealization(cpg);
            assignAssociation(cpg);
            assignDependency(cpg);
            assignOutwardRelations(cpg);
        } else {
            this.cpg = codePropertyGraph;
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
        StatTracker.Helper helper = new StatTracker.Helper(cpg);
        ArrayList<String> allClassNames = helper.allClassNames;
        for (CPGClass cpgClass : cpg.getClasses()) {
            HashMap<String, Long> typeCountMap = new HashMap<>();
            Set<CPGClass> uniqueDestinationClasses = new HashSet<>();
            Set<CPGClass.Attribute> filteredAttribute = new HashSet<>();
            Arrays.stream(cpgClass.attributes).filter(attr -> (attr.parentClassName.equals(cpgClass.name)
                            || attr.parentClassName.equals(cpgClass.classFullName))).
                    forEach(attribute -> attribute.typeList.stream().
                            filter(allClassNames::contains).
                            forEach(type -> uniqueDestinationClasses.add(helper.getClassFromName(type))));
            Arrays.stream(cpgClass.attributes).filter(attr -> (attr.parentClassName.equals(cpgClass.name)
                            || attr.parentClassName.equals(cpgClass.classFullName))).
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
        Set<CPGClass.Attribute> allSourceTypes = Arrays.stream(sourceClass.attributes).
                filter(attr -> (attr.typeList.contains(destinationClass.name) || attr.typeList.contains(destinationClass.classFullName)) &&
                        attr.parentClassName.equals(sourceClass.name)).
                collect(Collectors.toSet());
        Set<CPGClass.Attribute> allDestinationTypes = Arrays.stream(destinationClass.attributes).
                filter(attr -> (attr.typeList.contains(sourceClass.name) || attr.typeList.contains(sourceClass.classFullName)) &&
                        attr.parentClassName.equals(destinationClass.name)).
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
        var constructorResult = Arrays.stream(sourceClass.methods).
                filter(method -> method.name.equals(sourceClass.name) &&
                        method.parentClassName.equals(sourceClass.name) &&
                        !method.methodBody.contains(destinationClass.name)).collect(Collectors.toList());
        var filteredAttributes = Arrays.stream(sourceClass.attributes).
                filter(attribute -> (attribute.typeList.contains(destinationClass.classFullName)
                        || attribute.typeList.contains(destinationClass.name)
                        && attribute.parentClassName.equals(sourceClass.name))
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
        ArrayList<String> allClassNames = helper.allClassNames;
        for (CPGClass.Method method : helper.allMethods) {
            CPGClass methodParent = helper.getClassFromName(method.parentClassName);
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
            Arrays.stream(methodParent.inheritsFrom).filter(allClassNames::contains).
                    forEach(classToIgnore -> classesToIgnore.add(helper.getClassFromName(classToIgnore)));
            classesToIgnore.add(methodParent);
            // Create a single set containing all classes that exist within method params and method calls
            Set<CPGClass> classesToAddDependencies = new HashSet<>();
            var methodCallResult = method.getMethodCalls().stream().
                    filter(methodCall -> helper.allClassNames.contains(methodCall.parentClassName)
                            && !methodCall.parentClassName.equals(methodParent.name)).collect(Collectors.toSet());
            // Add all classes that exist within cpg and exist as parameters to the classesToAddDependencies set.
            Arrays.stream(method.parameters).
                    forEach(methodParameter -> methodParameter.typeList.stream().
                            filter(allClassNames::contains).
                            forEach(typeToAdd -> classesToAddDependencies.add(helper.getClassFromName(typeToAdd))));
            // Add all classes that exist within a method's methodCalls list.
            methodCallResult.forEach(methodToAdd -> classesToAddDependencies.add(helper.getClassFromName(methodToAdd.parentClassName)));
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
     * Iterates through the cpg, assigning inheritance relations and updating the respective subclass to
     * possess the inherited properties of the superclass.
     *
     * @param cpg - The CodePropertyGraph containing all existing classes and relations
     * @return An updated CodePropertyGraph object with the updated subclasses
     */
    protected static CodePropertyGraph assignInheritance(CodePropertyGraph cpg) {
        CodePropertyGraph updatedGraph = new CodePropertyGraph();
        StatTracker.Helper helper = new StatTracker.Helper(cpg);
        ArrayList<String> allClassNames = helper.allClassNames;
        Set<CPGClass> allClasses = new HashSet<>();
        var allInterfaces = cpg.getClasses().stream().
                filter(cpgClass -> cpgClass.classType.equals("interface")).collect(Collectors.toList());
        ArrayList<String> allInterfaceNames = new ArrayList<>();
        allInterfaces.forEach(interfaceClass -> allInterfaceNames.add(interfaceClass.name));
        var allExtenders = cpg.getClasses().stream().
                filter(cpgClass -> cpgClass.code.contains("extends")).collect(Collectors.toList());
        // Iterate through all of cpg
        for (CPGClass cpgClass : allExtenders) {
            // Account for cases where Joern may return classes such as Object, which would not be present within CPG
            // Joern provides both interfaces and superclasses within inheritsFrom, account for this case
            var filteredInheritFrom = Arrays.stream(cpgClass.inheritsFrom).
                    filter(name -> allClassNames.contains(name) && !allInterfaceNames.contains(name)).collect(Collectors.toList());
            if (!filteredInheritFrom.isEmpty()) {
                String className = filteredInheritFrom.get(0);
                CPGClass destinationClass = helper.getClassFromName(className);
                boolean subClassAlreadyUpdated = subClassAlreadyUpdated(cpgClass, destinationClass);
                // Subclass not updated
                if (!subClassAlreadyUpdated) {
                    CPGClass properSubClass = appendSuperClassProperties(cpgClass, destinationClass);
                    allClasses.add(properSubClass);
                }
                // Subclass already updated
                else {
                    allClasses.add(cpgClass);
                }
            }
            // cpgClass extends a class not present within CPG (i.e. Application)
            else {
                allClasses.add(cpgClass);
            }
        }
        Set<CPGClass> updatedExtendersOnly = new HashSet<>(allClasses);
        // Add all the classes that do not extend and then add the classes to cpg and return the updated graph
        var allNonExtenders = cpg.getClasses().stream().
                filter(cpgClass -> !cpgClass.code.contains("extends")).collect(Collectors.toList());
        allClasses.addAll(allNonExtenders);
        allClasses.forEach(updatedGraph::addClass);
        // Add relations to the updatedGraph
        for (CPGClass cpgClass : updatedExtendersOnly) {
            var filteredInheritFrom = Arrays.stream(cpgClass.inheritsFrom).
                    filter(name -> allClassNames.contains(name) && !allInterfaceNames.contains(name)).collect(Collectors.toList());
            if (!filteredInheritFrom.isEmpty()) {
                String className = filteredInheritFrom.get(0);
                CPGClass destinationClass = helper.getClassFromName(className);
                CodePropertyGraph.Relation relationToAdd = new
                        CodePropertyGraph.Relation(cpgClass, destinationClass,
                        ClassRelation.RelationshipType.INHERITANCE, "");
                if (!checkRelationExists(updatedGraph, relationToAdd)) {
                    updatedGraph.addRelation(relationToAdd);
                }
            }
        }

        return updatedGraph;
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
        // Add all super class properties
        ArrayList<CPGClass.Attribute> allSuperClassAttr = new ArrayList<>(List.of(superClass.attributes));
        ArrayList<CPGClass.Method> allSuperClassMethods = new ArrayList<>(List.of(superClass.methods));
        allSubClassAttr.addAll(allSuperClassAttr);
        allSubClassMethods.addAll(allSuperClassMethods);
        // Return updated subclass
        return new CPGClass(subClass.name, subClass.classFullName, subClass.packageName, subClass.importStatements,
                subClass.code, subClass.lineNumber, subClass.modifiers, subClass.classType, subClass.filePath, subClass.fileLength,
                subClass.nonEmptyLines, subClass.emptyLines, subClass.inheritsFrom,
                allSubClassAttr.toArray(new CPGClass.Attribute[allSuperClassAttr.size()]),
                allSubClassMethods.toArray(new CPGClass.Method[allSuperClassMethods.size()]));
    }

    /**
     * Create sets of sub and superclasses methods and attributes and check to see if
     * all superclass properties are present, if yes then subClass has already been updated.
     *
     * @param existingSubclass
     * @param superClass
     * @return
     */
    protected static boolean subClassAlreadyUpdated(CPGClass existingSubclass, CPGClass superClass) {
        boolean updated = false;
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
     * Iterates through the cpg and assigns realization relationships.
     */
    protected static void assignRealization(CodePropertyGraph cpg) {
        var allRealizations = cpg.getClasses().stream().
                filter(cpgClass -> cpgClass.code.contains("implements")).collect(Collectors.toList());
        var allInterfaces = cpg.getClasses().stream().
                filter(cpgClass -> cpgClass.classType.equals("interface")).collect(Collectors.toList());
        ArrayList<String> allInterfaceNames = new ArrayList<>();
        allInterfaces.forEach(interfaceClass -> allInterfaceNames.add(interfaceClass.name));
        // Iterate through the filtered allRealizations list and assign realization relationships
        for (CPGClass cpgClass : allRealizations) {
            var filteredInheritsFrom = Arrays.stream(cpgClass.inheritsFrom).
                    filter(allInterfaceNames::contains).collect(Collectors.toList());
            if (!filteredInheritsFrom.isEmpty()) {
                var interfaceToFind = allInterfaces.stream().
                        filter(cpgInterface -> cpgInterface.name.equals(filteredInheritsFrom.get(0))).collect(Collectors.toList());
                CPGClass interfaceClass = interfaceToFind.get(0);
                CodePropertyGraph.Relation relationToAdd = new
                        CodePropertyGraph.Relation(cpgClass, interfaceClass,
                        ClassRelation.RelationshipType.REALIZATION, "");
                if (!checkRelationExists(cpg, relationToAdd)) {
                    cpg.addRelation(relationToAdd);
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
