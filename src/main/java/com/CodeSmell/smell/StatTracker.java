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
public class StatTracker {
    public final HashMap<CPGClass.Attribute, Integer> attributeUsage;
    public final HashMap<CPGClass, Integer> classUsage;
    public final HashMap<CPGClass.Method, Integer> methodUsage;
    public final TreeMap<String, ArrayList<CPGClass>> distinctClassTypes;
    public final HashMap<CPGClass.Method, HashMap<CPGClass, Integer>> distinctMethodCalls;
    public final ArrayList<Package> distinctPackages;
    public final HashMap<ClassRelation.RelationshipType, ArrayList<CodePropertyGraph.Relation>> distinctRelations;

    public StatTracker(CodePropertyGraph cpg) {
        attributeUsage = determineAttributeUsage(cpg);
        classUsage = determineClassUsage(cpg);
        methodUsage = determineMethodUsage(cpg);
        distinctClassTypes = determineDistinctClassTypes(cpg);
        distinctMethodCalls = determineDistinctMethodCalls(cpg);
        distinctPackages = determineDistinctPackages(cpg);
        distinctRelations = determineDistinctRelations(cpg);
    }

    /**
     * Iterates through the cpg to determine how many times an Attribute has been used within methods.
     *
     * @param cpg - The CodePropertyGraph containing all existing classes and relations
     * @return A hashmap indicating how many times a given Attribute has been used within the cpg
     */
    protected static HashMap<CPGClass.Attribute, Integer> determineAttributeUsage(CodePropertyGraph cpg) {
        HashMap<CPGClass.Attribute, Integer> attributeUsage = new HashMap<>();
        // Create helper lists to determine all attribute usages
        ArrayList<String> allClassNames = new ArrayList<>();
        ArrayList<CPGClass.Attribute> allAttributeWithinCPG = new ArrayList<>();
        ArrayList<CPGClass.Method> allMethodsWithinCPG = new ArrayList<>();
        cpg.getClasses().forEach(cpgClass -> allClassNames.add(cpgClass.name));
        cpg.getClasses().forEach(cpgClass -> allAttributeWithinCPG.addAll(Arrays.asList(cpgClass.attributes)));
        cpg.getClasses().forEach(cpgClass -> allMethodsWithinCPG.addAll(Arrays.asList(cpgClass.methods)));
        // Iterate through each method within CPG and determine attribute usage through method instructions
        for (CPGClass.Method method : allMethodsWithinCPG) {
            if (allClassNames.contains(method.parentClassName)) {
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
        }
        // Finally, add the counts for the unused attributes
        for (CPGClass.Attribute unusedAttribute : allAttributeWithinCPG) {
            attributeUsage.putIfAbsent(unusedAttribute, 0);
        }
        return attributeUsage;
    }

    /**
     * Iterates through the CPG to return a HashMap containing the number of times each
     * CPGClass has been used within the cpg, either through some form of association or through local instantiation.
     *
     * @param cpg - The CodePropertyGraph containing all existing classes and relations
     * @return A hashmap containing the number of times a CPGClass has been used within the cpg
     */
    protected static HashMap<CPGClass, Integer> determineClassUsage(CodePropertyGraph cpg) {
        HashMap<CPGClass, Integer> classUsageMap = new HashMap<>();
        ArrayList<CPGClass.Method> allMethodCallsWithinCPG = new ArrayList<>();
        cpg.getClasses().forEach(cpgClass -> Arrays.stream(cpgClass.methods).
                forEach(method -> allMethodCallsWithinCPG.addAll(method.getMethodCalls())));
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
            classUsageMap.put(cpgClass, count);
        }
        // Finally return a count for each CPGClass as a map, accounting for cases where there is 0 uses
        return classUsageMap;
    }


    /**
     * Iterates through the cpg to determine how many times a given Method has been used and returns a
     * hashmap mapping every Method object to an int value representing the number of times it was called.
     *
     * @param cpg - The CodePropertyGraph containing all existing classes and relations
     * @return A hashmap containing the number of times a given Method object was called within cpg
     */
    protected static HashMap<CPGClass.Method, Integer> determineMethodUsage(CodePropertyGraph cpg) {
        HashMap<CPGClass.Method, Integer> methodUsage = new HashMap<>();
        ArrayList<CPGClass.Method> allMethodsWithinCPG = new ArrayList<>();
        cpg.getClasses().forEach(cpgClass -> allMethodsWithinCPG.addAll(Arrays.asList(cpgClass.methods)));
        ArrayList<CPGClass.Method> allMethodCallsWithinCPG = new ArrayList<>();
        cpg.getClasses().forEach(cpgClass -> Arrays.stream(cpgClass.methods).
                forEach(method -> allMethodCallsWithinCPG.addAll(method.getMethodCalls())));
        // Add all the methods that exist within allMethodCalls of cpg and update the key, if it exists.
        for (CPGClass.Method method : allMethodCallsWithinCPG) {
            methodUsage.put(method, methodUsage.getOrDefault(method, 0) + 1);
        }
        // Account for unused methods by giving them a value of 0
        for (CPGClass.Method unusedMethod : allMethodsWithinCPG) {
            methodUsage.putIfAbsent(unusedMethod, 0);
        }
        // Finally, return the hashmap containing the usage of each method
        return methodUsage;
    }

    /**
     * Iterates through the cpg to determine all the distinct class types that are present such as
     * abstract class, class, enum, interface.
     *
     * @param cpg - The CodePropertyGraph containing all existing classes and relations
     * @return A TreeMap containing all the distinct class types
     */
    protected static TreeMap<String, ArrayList<CPGClass>> determineDistinctClassTypes(CodePropertyGraph cpg) {
        TreeMap<String, ArrayList<CPGClass>> distinctClassTypes = new TreeMap<>();
        for (CPGClass cpgClass : cpg.getClasses()) {
            String classType = cpgClass.classType;
            distinctClassTypes.putIfAbsent(classType, new ArrayList<>());
            distinctClassTypes.get(classType).add(cpgClass);
        }
        return distinctClassTypes;
    }

    /**
     * @param cpg - The CodePropertyGraph containing all existing classes and relations
     * @return A hashmap containing all the distinct method calls of a given method and its methodCalls object
     */
    protected static HashMap<CPGClass.Method, HashMap<CPGClass, Integer>> determineDistinctMethodCalls(CodePropertyGraph cpg) {
        HashMap<CPGClass.Method, HashMap<CPGClass, Integer>> distinctMethodCalls = new HashMap<>();
        ArrayList<CPGClass.Method> allMethodsWithinCPG = new ArrayList<>();
        ArrayList<String> allClassNames = new ArrayList<>();
        cpg.getClasses().forEach(cpgClass -> allClassNames.add(cpgClass.name));
        cpg.getClasses().forEach(cpgClass -> allMethodsWithinCPG.addAll(Arrays.asList(cpgClass.methods)));
        for (CPGClass.Method method : allMethodsWithinCPG) {
            ArrayList<CPGClass.Method> methodCalls = method.getMethodCalls();
            HashMap<CPGClass, Integer> distinctClassCalls = new HashMap<>();
            for (CPGClass.Method methodCall : methodCalls) {
                String methodParentName = methodCall.parentClassName;
                int index = allClassNames.indexOf(methodParentName);
                CPGClass methodParentClass = cpg.getClasses().get(index);
                distinctClassCalls.put(methodParentClass, distinctClassCalls.getOrDefault(methodParentClass, 0) + 1);
            }
            distinctMethodCalls.put(method, distinctClassCalls);
        }
        return distinctMethodCalls;
    }

    /**
     * Iterates through the cpg, creating Package objects corresponding to the distinct packages that exist
     * and additionally appends the corresponding sub-packages and CPGClasses that exist within each package.
     *
     * @param cpg - The CodePropertyGraph containing all existing classes and relations
     * @return An ArrayList containing all the distinct packages
     */
    protected static ArrayList<Package> determineDistinctPackages(CodePropertyGraph cpg) {
        // Get all package names, mapped to an array list of classes
        TreeMap<String, ArrayList<CPGClass>> packageNames = new TreeMap<>();
        for (CPGClass cpgClass : cpg.getClasses()) {
            String packageName = cpgClass.packageName.replace("package ", "").trim();
            packageNames.putIfAbsent(packageName, new ArrayList<>());
            packageNames.get(packageName).add(cpgClass);
        }
        // Create distinct package objects and additionally append subpackages, if present
        ArrayList<Package> distinctPackages = new ArrayList<>();
        for (Map.Entry<String, ArrayList<CPGClass>> entry : packageNames.entrySet()) {
            String packageName = entry.getKey();
            ArrayList<CPGClass> classes = entry.getValue();
            distinctPackages.add(new Package(packageName, classes));
        }
        // Add all subpackages, by checking length of packageName (greater packageName implies subPackage to current pkg)
        for (Package pkg : distinctPackages) {
            int packageNameLength = pkg.packageName.length();
            var result = distinctPackages.stream().filter(p -> p.packageName.length() > packageNameLength).
                    collect(Collectors.toList());
            if (!result.isEmpty()) {
                result.forEach(pkg::addPackage);
            }
        }
        return distinctPackages;
    }

    /**
     * Iterates through the cpg and groups all relations by RelationshipType.
     *
     * @param cpg - The CodePropertyGraph containing all existing classes and relations
     * @return A hashmap containing all relations belonging to a given RelationshipType
     */
    protected static HashMap<ClassRelation.RelationshipType, ArrayList<CodePropertyGraph.Relation>> determineDistinctRelations(CodePropertyGraph cpg) {
        HashMap<ClassRelation.RelationshipType, ArrayList<CodePropertyGraph.Relation>> distinctRelations = new HashMap<>();
        for (ClassRelation.RelationshipType relationshipType : ClassRelation.RelationshipType.values()) {
            var allMatchingRelations = cpg.getRelations().stream().
                    filter(relation -> relation.type.equals(relationshipType)).collect(Collectors.toList());
            distinctRelations.put(relationshipType, (ArrayList<CodePropertyGraph.Relation>) allMatchingRelations);
        }
        return distinctRelations;
    }

    /**
     * A class that is meant to represent that packages that exist within a given codebase.
     */
    public static class Package {
        public final String packageName;
        private final ArrayList<Package> subPackages;
        public final ArrayList<CPGClass> classes;

        public Package(String packageName, ArrayList<CPGClass> classes) {
            this.packageName = packageName;
            this.subPackages = new ArrayList<>();
            this.classes = classes;
        }

        /**
         * Return an ArrayList containing all the subpackages within the current Package
         *
         * @return - The subpackages of the current Package
         */
        public ArrayList<Package> getSubPackages() {
            return new ArrayList<>(subPackages);
        }

        /**
         * Add sub-packages to the current Package
         *
         * @param packageToAdd - The package to be added to the current Package
         */
        protected void addPackage(Package packageToAdd) {
            this.subPackages.add(packageToAdd);
        }
    }

}

