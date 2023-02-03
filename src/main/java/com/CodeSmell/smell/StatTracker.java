package com.CodeSmell.smell;

import com.CodeSmell.model.ClassRelation;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

/**
 * A class that is meant to contain potentially useful statistics that can aid in the detection of code smells and
 * for general use case purposes including determining potential bugs within the CodeSmell tool itself.
 */
public final class StatTracker {
    public final Helper helper;
    public final HashMap<CPGClass.Attribute, Integer> attributeUsage = new HashMap<>();
    public final HashMap<CPGClass, Integer> classUsage = new HashMap<>();
    public final HashMap<CPGClass.Method, Integer> methodUsage = new HashMap<>();
    public final TreeMap<String, ArrayList<CPGClass>> distinctClassTypes = new TreeMap<>();
    public final HashMap<CPGClass.Method, HashMap<CPGClass, Integer>> distinctMethodCalls = new HashMap<>();
    public final ArrayList<Package> distinctPackages = new ArrayList<>();
    public final HashMap<ClassRelation.RelationshipType, ArrayList<CodePropertyGraph.Relation>> distinctRelations = new HashMap<>();
    public final HashMap<CPGClass, Integer> totalClassLines = new HashMap<>();

    public StatTracker(CodePropertyGraph cpg) {
        helper = new Helper(cpg);
        determineAttributeUsage(cpg, attributeUsage, helper);
        determineClassUsage(cpg, classUsage, helper);
        determineMethodUsage(methodUsage, helper);
        determineDistinctClassTypes(cpg, distinctClassTypes);
        determineDistinctMethodCalls(cpg, distinctMethodCalls, helper);
        determineDistinctPackages(cpg, distinctPackages);
        determineDistinctRelations(cpg, distinctRelations);
        determineTotalClassLines(cpg, totalClassLines);
    }


    /**
     * Iterates through the cpg to determine how many times an Attribute has been used within methods.
     *
     * @param cpg            - The CodePropertyGraph containing all existing classes and relations
     * @param attributeUsage
     * @param helper
     */
    private static void determineAttributeUsage(CodePropertyGraph cpg, HashMap<CPGClass.Attribute, Integer> attributeUsage, Helper helper) {
        // Create helper lists to determine all attribute usages
        ArrayList<String> allClassNames = helper.allClassNames;
        ArrayList<CPGClass.Attribute> allAttributeWithinCPG = helper.allAttributes;
        ArrayList<CPGClass.Method> allMethodsWithinCPG = helper.allMethods;
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
     * @param cpg        - The CodePropertyGraph containing all existing classes and relations
     * @param classUsage
     * @param helper
     */
    private static void determineClassUsage(CodePropertyGraph cpg, HashMap<CPGClass, Integer> classUsage, Helper helper) {
        ArrayList<CPGClass.Method> allMethodCallsWithinCPG = helper.allMethodCalls;
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
    private static void determineMethodUsage(HashMap<CPGClass.Method, Integer> methodUsage, Helper helper) {
        ArrayList<CPGClass.Method> allMethodsWithinCPG = helper.allMethods;
        ArrayList<CPGClass.Method> allMethodCallsWithinCPG = helper.allMethodCalls;
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
     * @param cpg                - The CodePropertyGraph containing all existing classes and relations
     * @param distinctClassTypes
     */
    private static void determineDistinctClassTypes(CodePropertyGraph cpg, TreeMap<String, ArrayList<CPGClass>> distinctClassTypes) {
        for (CPGClass cpgClass : cpg.getClasses()) {
            String classType = cpgClass.classType;
            distinctClassTypes.putIfAbsent(classType, new ArrayList<>());
            distinctClassTypes.get(classType).add(cpgClass);
        }
    }

    /**
     * @param cpg                 - The CodePropertyGraph containing all existing classes and relations
     * @param distinctMethodCalls
     * @param helper
     */
    private static void determineDistinctMethodCalls(CodePropertyGraph cpg,
                                                     HashMap<CPGClass.Method, HashMap<CPGClass, Integer>> distinctMethodCalls,
                                                     Helper helper) {
        ArrayList<CPGClass.Method> allMethodsWithinCPG = helper.allMethods;
        ArrayList<String> allClassNames = helper.allClassNames;
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
     * @param cpg              - The CodePropertyGraph containing all existing classes and relations
     * @param distinctPackages
     */
    private static void determineDistinctPackages(CodePropertyGraph cpg, ArrayList<Package> distinctPackages) {
        // Get all package names, mapped to an array list of files
        TreeMap<String, ArrayList<File>> packageNames = new TreeMap<>();
        for (CPGClass cpgClass : cpg.getClasses()) {
            String packageName = cpgClass.packageName;
            String filePath = cpgClass.filePath;
            String fileName = cpgClass.name + ".java";
            packageNames.putIfAbsent(packageName, new ArrayList<>());
            if (filePath.contains(fileName)) {
                // Handle adding of new files and classes within those files, starting with root class.
                File newFile = new File(fileName, filePath);
                var fileClasses = cpg.getClasses().stream().
                        filter(nestedClasses -> nestedClasses.filePath.equals(filePath)).collect(Collectors.toList());
                fileClasses.sort(comparing(classes -> classes.classFullName));
                if (!fileClasses.isEmpty()) {
                    fileClasses.forEach(fileClass -> File.addClass(newFile.classes, fileClass));
                }
                packageNames.get(packageName).add(newFile);
            }
        }
        // Create distinct package objects
        for (Map.Entry<String, ArrayList<File>> entry : packageNames.entrySet()) {
            String packageName = entry.getKey();
            ArrayList<File> files = entry.getValue();
            files.sort(comparing(file -> file.fileName.toLowerCase()));
            Package newPackage = new Package(packageName);
            files.forEach(file -> Package.addFile(newPackage.files, file));
            distinctPackages.add(newPackage);
        }
        // Add all subpackages, by checking length of packageName (greater packageName implies subPackage to current pkg)
        for (Package pkg : distinctPackages) {
            String packageName = pkg.packageName;
            int lastOccurrence = packageName.lastIndexOf(".");
            var result = distinctPackages.stream().
                    filter(p -> p.packageName.lastIndexOf(".") > lastOccurrence).collect(Collectors.toList());
            if (!result.isEmpty()) {
                result.forEach(pkgToAdd -> Package.addPackage(pkg.subPackages, pkgToAdd));
            }
        }
    }

    /**
     * Iterates through the cpg and groups all relations by RelationshipType.
     *
     * @param cpg               - The CodePropertyGraph containing all existing classes and relations
     * @param distinctRelations
     */
    private static void determineDistinctRelations(CodePropertyGraph cpg, HashMap<ClassRelation.RelationshipType, ArrayList<CodePropertyGraph.Relation>> distinctRelations) {
        for (ClassRelation.RelationshipType relationshipType : ClassRelation.RelationshipType.values()) {
            var allMatchingRelations = cpg.getRelations().stream().
                    filter(relation -> relation.type.equals(relationshipType)).collect(Collectors.toList());
            distinctRelations.put(relationshipType, (ArrayList<CodePropertyGraph.Relation>) allMatchingRelations);
        }
    }

    /**
     * @param cpg
     * @param totalClassLines
     */
    private static void determineTotalClassLines(CodePropertyGraph cpg, HashMap<CPGClass, Integer> totalClassLines) {
        for (CPGClass cpgClass : cpg.getClasses()) {
            int selfAttributeLength = cpgClass.attributes.length;
            final int[] selfMethodLines = {0};
            Arrays.stream(cpgClass.methods).forEach(method -> {
                selfMethodLines[0] += method.totalMethodLength;
            });
            int declarationLines = 2;
            // This is the total amount of lines without package and import statements taken into account,
            // purely just shows how many lines of non-empty code are occupied by each class
            int totalLines = selfAttributeLength + selfMethodLines[0] + declarationLines;
            totalClassLines.put(cpgClass, totalLines);
        }
    }

    /**
     * A class that is meant to represent that packages that exist within a given codebase.
     */
    public static final class Package {
        public final String packageName;
        public final ArrayList<File> files = new ArrayList<>();
        public final ArrayList<Package> subPackages = new ArrayList<>();

        public Package(String packageName) {
            this.packageName = packageName;
        }

        /**
         * Add sub-packages to the current Package
         *
         * @param packageToAdd - The package to be added to the current Package
         */
        private static void addPackage(ArrayList<Package> subPackages, Package packageToAdd) {
            subPackages.add(packageToAdd);
        }

        /**
         * Add files to the current Package
         */
        private static void addFile(ArrayList<File> classes, File fileToAdd) {
            classes.add(fileToAdd);
        }

    }

    /**
     *
     */
    public static final class File {
        public final String fileName;
        public final String filePath;
        public final ArrayList<CPGClass> classes = new ArrayList<>();

        public File(String fileName, String filePath) {
            this.fileName = fileName;
            this.filePath = filePath;
        }

        /**
         * Add classes to the current File
         *
         * @param cpgClassToAdd - The class to be added to the current Package
         */
        private static void addClass(ArrayList<CPGClass> classes, CPGClass cpgClassToAdd) {
            classes.add(cpgClassToAdd);
        }
    }

    /**
     * A class that contains potentially helpful lists containing info such as all attributes, all methods,
     * all method calls, all method parameters, all class names within cpg.
     */
    public static final class Helper {
        private final CodePropertyGraph cpg;
        public final ArrayList<CPGClass.Attribute> allAttributes = new ArrayList<>();
        public final ArrayList<CPGClass.Method> allMethods = new ArrayList<>();
        public final ArrayList<CPGClass.Method> allMethodCalls = new ArrayList<>();
        public final ArrayList<CPGClass.Method.Parameter> allParameters = new ArrayList<>();
        public final ArrayList<String> allClassNames = new ArrayList<>();
        public final ArrayList<String> allMethodNames = new ArrayList<>();

        public Helper(CodePropertyGraph cpg) {
            this.cpg = cpg;
            collectAllAttributes(cpg, allAttributes);
            collectAllMethods(cpg, allMethods);
            collectAllMethodCalls(allMethods, allMethodCalls);
            collectAllParameters(allMethods, allParameters);
            collectAllClassNames(cpg, allClassNames);
            collectAllMethodNames(cpg, allMethodNames);
        }

        public CPGClass getClassFromName(String name) {
            CPGClass classToReturn = null;
            var classResult = cpg.getClasses().stream().
                    filter(cpgClass -> cpgClass.name.equals(name) || cpgClass.classFullName.equals(name)).collect(Collectors.toList());
            if (!classResult.isEmpty()) {
                classToReturn = classResult.get(0);
            }
            return classToReturn;
        }

        public CPGClass.Attribute getAttributeFromName(String name, String attributeType, String parentClassName) {
            CPGClass.Attribute attributeToReturn = null;
            var attributeResult = allAttributes.stream().
                    filter(attribute -> attribute.name.equals(name) &&
                            attribute.attributeType.equals(attributeType) &&
                            attribute.parentClassName.equals(parentClassName)).collect(Collectors.toList());
            if (!attributeResult.isEmpty()) {
                attributeToReturn = attributeResult.get(0);
            }
            return attributeToReturn;
        }

        public CPGClass.Method getMethodFromName(String name, String parentClassName) {
            CPGClass.Method methodToReturn = null;
            var methodResult = allMethods.stream().
                    filter(method -> method.name.equals(name) &&
                            method.parentClassName.equals(parentClassName)).collect(Collectors.toList());
            if (!methodResult.isEmpty()) {
                methodToReturn = methodResult.get(0);
            }
            return methodToReturn;
        }


        /**
         * Collect all the attributes that exist within cpg
         *
         * @param cpg           - The CodePropertyGraph containing all existing classes and relations
         * @param allAttributes
         */
        private static void collectAllAttributes(CodePropertyGraph cpg, ArrayList<CPGClass.Attribute> allAttributes) {
            cpg.getClasses().forEach(cpgClass -> allAttributes.addAll(Arrays.asList(cpgClass.attributes)));
        }

        /**
         * Collect all the methods that exist within cpg
         *
         * @param cpg        - The CodePropertyGraph containing all existing classes and relations
         * @param allMethods
         */
        private static void collectAllMethods(CodePropertyGraph cpg, ArrayList<CPGClass.Method> allMethods) {
            cpg.getClasses().forEach(cpgClass -> allMethods.addAll(Arrays.asList(cpgClass.methods)));
        }

        /**
         * Collect all the method calls that exist within cpg
         */
        private static void collectAllMethodCalls(ArrayList<CPGClass.Method> allMethods, ArrayList<CPGClass.Method> allMethodCalls) {
            allMethods.forEach(method -> allMethodCalls.addAll(method.getMethodCalls()));
        }

        /**
         * Collect all the method parameters that exist within cpg
         */
        private static void collectAllParameters(ArrayList<CPGClass.Method> allMethods, ArrayList<CPGClass.Method.Parameter> allParameters) {
            allMethods.forEach(method -> allParameters.addAll(Arrays.asList(method.parameters)));
        }

        /**
         * Collect all the class names that exist within the cpg
         *
         * @param cpg           - The CodePropertyGraph containing all existing classes and relations
         * @param allClassNames
         */
        private static void collectAllClassNames(CodePropertyGraph cpg, ArrayList<String> allClassNames) {
            cpg.getClasses().forEach(cpgClass -> allClassNames.add(cpgClass.name));
        }

        private static void collectAllMethodNames(CodePropertyGraph cpg, ArrayList<String> allMethodNames) {
            cpg.getClasses().forEach(cpgClass -> Arrays.stream(cpgClass.methods).forEach(method -> allMethodNames.add(method.name)));
        }

    }

}