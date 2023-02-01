package com.CodeSmell.smell;

import com.CodeSmell.model.ClassRelation;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A class that is meant to contain potentially useful statistics that can aid in the detection of code smells and
 * for general use case purposes including determining potential bugs within the CodeSmell tool itself.
 */
public final class StatTracker {
    public static final HashMap<CPGClass.Attribute, Integer> attributeUsage = new HashMap<>();
    public static final HashMap<CPGClass, Integer> classUsage = new HashMap<>();
    public static final HashMap<CPGClass.Method, Integer> methodUsage = new HashMap<>();
    public static final TreeMap<String, ArrayList<CPGClass>> distinctClassTypes = new TreeMap<>();
    public static final HashMap<CPGClass.Method, HashMap<CPGClass, Integer>> distinctMethodCalls = new HashMap<>();
    public static final ArrayList<Package> distinctPackages = new ArrayList<>();
    public static final HashMap<ClassRelation.RelationshipType, ArrayList<CodePropertyGraph.Relation>> distinctRelations = new HashMap<>();

    public StatTracker(CodePropertyGraph cpg) {
        determineAttributeUsage(cpg);
        determineClassUsage(cpg);
        determineMethodUsage();
        determineDistinctClassTypes(cpg);
        determineDistinctMethodCalls(cpg);
        determineDistinctPackages(cpg);
        determineDistinctRelations(cpg);
    }

    /**
     * Iterates through the cpg to determine how many times an Attribute has been used within methods.
     *
     * @param cpg - The CodePropertyGraph containing all existing classes and relations
     */
    private static void determineAttributeUsage(CodePropertyGraph cpg) {
        // Create helper lists to determine all attribute usages
        ArrayList<String> allClassNames = Helper.allClassNames;
        ArrayList<CPGClass.Attribute> allAttributeWithinCPG = Helper.allAttributes;
        ArrayList<CPGClass.Method> allMethodsWithinCPG = Helper.allMethods;
        var filteredMethodsWithinCPG = allMethodsWithinCPG.stream().
                filter(method -> allClassNames.contains(method.parentClassName)).collect(Collectors.toList());
        // Iterate through each method within CPG and determine attribute usage through method instructions
        for (CPGClass.Method method : filteredMethodsWithinCPG) {
            int parentClassIndex = allClassNames.indexOf(method.parentClassName);
            CPGClass methodParent = cpg.getClasses().get(parentClassIndex);
            Set<String> distinctAttrNames = new HashSet<>();
            var filteredInstructions = Arrays.stream(method.instructions).
                    filter(instruction -> instruction.label.equals("FIELD_IDENTIFIER")).collect(Collectors.toList());
            filteredInstructions.forEach(ins -> distinctAttrNames.add(ins.code));
            for (String attrName : distinctAttrNames) {
                CPGClass.Attribute attr = null;
                // Check if the found attribute belongs to the method parent
                var methodParentContainsAttr = Arrays.stream(methodParent.attributes).
                        filter(attribute -> attribute.name.equals(attrName)).collect(Collectors.toList());
                if (!methodParentContainsAttr.isEmpty()) {
                    attr = methodParentContainsAttr.get(0);
                }
                // Check if the found attribute belongs to a CPGClass passed as a parameter
                else {
                    var methodParameterContainsAttr = Arrays.stream(method.parameters).
                            filter(parameter -> parameter.name.equals(attrName)).collect(Collectors.toList());
                    if (!methodParameterContainsAttr.isEmpty()) {
                        CPGClass.Method.Parameter methodParameter = methodParameterContainsAttr.get(0);
                        if (allClassNames.contains(methodParameter.type)) {
                            attr = allAttributeWithinCPG.get(allClassNames.indexOf(methodParameter.type));
                        }
                    }
                }
                // Update the count for the resultant attribute, if it is not null.
                if (attr != null) {
                    attributeUsage.put(attr, attributeUsage.getOrDefault(attr, 0) + 1);
                }
            }
        }
        // Finally, add the counts for the unused attributes
        for (CPGClass.Attribute unusedAttribute : allAttributeWithinCPG) {
            attributeUsage.putIfAbsent(unusedAttribute, 0);
        }
    }

    /**
     * Iterates through the CPG to return a HashMap containing the number of times each
     * CPGClass has been used within the cpg, either through some form of association or through local instantiation.
     *
     * @param cpg - The CodePropertyGraph containing all existing classes and relations
     */
    private static void determineClassUsage(CodePropertyGraph cpg) {
        ArrayList<CPGClass.Method> allMethodCallsWithinCPG = Helper.allMethodCalls;
        for (CPGClass cpgClass : cpg.getClasses()) {
            int count = 0;
            // Check how many association relations point towards a given cpgClass
            var filteredAssociations = cpg.getRelations().stream().
                    filter(relation -> (relation.type.equals(ClassRelation.RelationshipType.UNIDIRECTIONAL_ASSOCIATION) ||
                            relation.type.equals(ClassRelation.RelationshipType.BIDIRECTIONAL_ASSOCIATION) ||
                            relation.type.equals(ClassRelation.RelationshipType.REFLEXIVE_ASSOCIATION) ||
                            relation.type.equals(ClassRelation.RelationshipType.COMPOSITION)) &&
                            relation.destination.equals(cpgClass)).collect(Collectors.toList());
            count += filteredAssociations.size();
            // Check how many times the constructor method of a given cpgClass exists within methodCalls of cpg (local instantiation)
            var constructorResult = Arrays.stream(cpgClass.methods).
                    filter(method -> method.name.equals(cpgClass.name)).collect(Collectors.toList());
            if (!constructorResult.isEmpty()) {
                CPGClass.Method constructor = constructorResult.get(0);
                var filteredMethodCalls = allMethodCallsWithinCPG.stream().
                        filter(method -> method.equals(constructor)).collect(Collectors.toList());
                count += filteredMethodCalls.size();
            }
            classUsage.put(cpgClass, count);
        }
    }

    /**
     * Iterates through the cpg to determine how many times a given Method has been used and returns a
     * hashmap mapping every Method object to an int value representing the number of times it was called.
     */
    private static void determineMethodUsage() {
        ArrayList<CPGClass.Method> allMethodsWithinCPG = Helper.allMethods;
        ArrayList<CPGClass.Method> allMethodCallsWithinCPG = Helper.allMethodCalls;
        // Add all the methods that exist within allMethodCalls of cpg and update the key, if it exists.
        for (CPGClass.Method method : allMethodCallsWithinCPG) {
            methodUsage.put(method, methodUsage.getOrDefault(method, 0) + 1);
        }
        // Account for unused methods by giving them a value of 0
        for (CPGClass.Method unusedMethod : allMethodsWithinCPG) {
            methodUsage.putIfAbsent(unusedMethod, 0);
        }
        // Finally, return the hashmap containing the usage of each method
    }

    /**
     * Iterates through the cpg to determine all the distinct class types that are present such as
     * abstract class, class, enum, interface.
     *
     * @param cpg - The CodePropertyGraph containing all existing classes and relations
     */
    private static void determineDistinctClassTypes(CodePropertyGraph cpg) {
        for (CPGClass cpgClass : cpg.getClasses()) {
            String classType = cpgClass.classType;
            distinctClassTypes.putIfAbsent(classType, new ArrayList<>());
            distinctClassTypes.get(classType).add(cpgClass);
        }
    }

    /**
     * @param cpg - The CodePropertyGraph containing all existing classes and relations
     */
    private static void determineDistinctMethodCalls(CodePropertyGraph cpg) {
        ArrayList<CPGClass.Method> allMethodsWithinCPG = Helper.allMethods;
        ArrayList<String> allClassNames = Helper.allClassNames;
        for (CPGClass.Method method : allMethodsWithinCPG) {
            ArrayList<CPGClass.Method> methodCalls = method.getMethodCalls();
            HashMap<CPGClass, Integer> distinctClassCalls = new HashMap<>();
            for (CPGClass.Method methodCall : methodCalls) {
                String methodParentName = methodCall.parentClassName;
                if (allClassNames.contains(methodParentName)) {
                    int index = allClassNames.indexOf(methodParentName);
                    CPGClass methodParentClass = cpg.getClasses().get(index);
                    distinctClassCalls.put(methodParentClass, distinctClassCalls.getOrDefault(methodParentClass, 0) + 1);
                }
            }
            distinctMethodCalls.put(method, distinctClassCalls);
        }
    }

    /**
     * Iterates through the cpg, creating Package objects corresponding to the distinct packages that exist
     * and additionally appends the corresponding sub-packages and CPGClasses that exist within each package.
     *
     * @param cpg - The CodePropertyGraph containing all existing classes and relations
     */
    private static void determineDistinctPackages(CodePropertyGraph cpg) {
        // Get all package names, mapped to an array list of classes
        TreeMap<String, ArrayList<CPGClass>> packageNames = new TreeMap<>();
        for (CPGClass cpgClass : cpg.getClasses()) {
            String packageName = cpgClass.packageName;
            packageNames.putIfAbsent(packageName, new ArrayList<>());
            packageNames.get(packageName).add(cpgClass);
        }
        // Create distinct package objects
        for (Map.Entry<String, ArrayList<CPGClass>> entry : packageNames.entrySet()) {
            String packageName = entry.getKey();
            ArrayList<CPGClass> classes = entry.getValue();
            Package newPackage = new Package(packageName);
            classes.forEach(Package::addClass);
            distinctPackages.add(newPackage);
        }

        // Add all subpackages, by checking length of packageName (greater packageName implies subPackage to current pkg)
        for (Package pkg : distinctPackages) {
            String packageName = pkg.packageName;
            int lastOccurrence = packageName.lastIndexOf(".");
            var result = distinctPackages.stream().
                    filter(p -> p.packageName.lastIndexOf(".") > lastOccurrence).collect(Collectors.toList());
            if (!result.isEmpty()) {
                result.forEach(Package::addPackage);
            }
        }
    }

    /**
     * Iterates through the cpg and groups all relations by RelationshipType.
     *
     * @param cpg - The CodePropertyGraph containing all existing classes and relations
     */
    private static void determineDistinctRelations(CodePropertyGraph cpg) {
        for (ClassRelation.RelationshipType relationshipType : ClassRelation.RelationshipType.values()) {
            var allMatchingRelations = cpg.getRelations().stream().
                    filter(relation -> relation.type.equals(relationshipType)).collect(Collectors.toList());
            distinctRelations.put(relationshipType, (ArrayList<CodePropertyGraph.Relation>) allMatchingRelations);
        }
    }

    /**
     * A class that is meant to represent that packages that exist within a given codebase.
     */
    public static final class Package {
        public final String packageName;
        public static final ArrayList<Package> subPackages = new ArrayList<>();
        public static final ArrayList<CPGClass> classes = new ArrayList<>();

        public Package(String packageName) {
            this.packageName = packageName;
        }

        /**
         * Add sub-packages to the current Package
         *
         * @param packageToAdd - The package to be added to the current Package
         */
        private static void addPackage(Package packageToAdd) {
            subPackages.add(packageToAdd);
        }

        /**
         * Add classes to the current Package
         *
         * @param cpgClassToAdd - The class to be added to the current Package
         */
        private static void addClass(CPGClass cpgClassToAdd) {
            classes.add(cpgClassToAdd);
        }
    }

    /**
     * A class that contains potentially helpful lists containing info such as all attributes, all methods,
     * all method calls, all method parameters, all class names within cpg.
     */
    public static final class Helper {
        public static final ArrayList<CPGClass.Attribute> allAttributes = new ArrayList<>();
        public static final ArrayList<CPGClass.Method> allMethods = new ArrayList<>();
        public static final ArrayList<CPGClass.Method> allMethodCalls = new ArrayList<>();
        public static final ArrayList<CPGClass.Method.Parameter> allParameters = new ArrayList<>();
        public static final ArrayList<String> allClassNames = new ArrayList<>();

        private Helper(CodePropertyGraph cpg) {
            collectAllAttributes(cpg);
            collectAllMethods(cpg);
            collectAllMethodCalls();
            collectAllParameters();
            collectAllClassNames(cpg);
        }

        /**
         * Collect all the attributes that exist within cpg
         *
         * @param cpg - The CodePropertyGraph containing all existing classes and relations
         */
        private static void collectAllAttributes(CodePropertyGraph cpg) {
            cpg.getClasses().forEach(cpgClass -> Helper.allAttributes.addAll(Arrays.asList(cpgClass.attributes)));
        }

        /**
         * Collect all the methods that exist within cpg
         *
         * @param cpg - The CodePropertyGraph containing all existing classes and relations
         */
        private static void collectAllMethods(CodePropertyGraph cpg) {
            cpg.getClasses().forEach(cpgClass -> Helper.allMethods.addAll(Arrays.asList(cpgClass.methods)));
        }

        /**
         * Collect all the method calls that exist within cpg
         */
        private static void collectAllMethodCalls() {
            Helper.allMethods.forEach(method -> Helper.allMethodCalls.addAll(method.getMethodCalls()));
        }

        /**
         * Collect all the method parameters that exist within cpg
         */
        private static void collectAllParameters() {
            Helper.allMethods.forEach(method -> Helper.allParameters.addAll(Arrays.asList(method.parameters)));
        }

        /**
         * Collect all the class names that exist within the cpg
         *
         * @param cpg - The CodePropertyGraph containing all existing classes and relations
         */
        private static void collectAllClassNames(CodePropertyGraph cpg) {
            cpg.getClasses().forEach(cpgClass -> Helper.allClassNames.add(cpgClass.name));
        }
    }

}

