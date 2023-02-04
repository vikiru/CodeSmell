package com.CodeSmell.smell;

import com.CodeSmell.model.ClassRelation;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;

import com.CodeSmell.parser.Package;
import com.CodeSmell.parser.Package.File;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

/**
 * A class that is meant to contain potentially useful statistics that can aid in the detection of code smells and
 * for general use case purposes including determining potential bugs within the CodeSmell tool itself.
 */
public final class StatTracker {
    public final Helper helper;

    /**
     * For every {@link CPGClass} within cpg, maintain a count of many times it was used (how many instances of it are
     * present as an attributeType, parameter type, how many of its methods appear within methodCalls, and any inheritance/realization
     * relations that exist with the class as the destination).
     */
    public final HashMap<CPGClass, Integer> classUsage = new HashMap<>();

    /**
     * For every {@link com.CodeSmell.parser.CPGClass.Attribute} within cpg, maintain a count of how many times it was used
     * within the body of a method.
     */
    public final HashMap<CPGClass.Attribute, Integer> attributeUsage = new HashMap<>();

    /**
     * For every {@link com.CodeSmell.parser.CPGClass.Method}, maintain a count of how many times within cpg it is used.
     */
    public final HashMap<CPGClass.Method, Integer> methodUsage = new HashMap<>();

    /**
     * For every {@link com.CodeSmell.parser.CPGClass.Method.Parameter}, maintain a count of how many times within a method
     * it was used
     */
    public final HashMap<CPGClass.Method, HashMap<CPGClass.Method.Parameter, Integer>> parameterUsage = new HashMap<>();

    /**
     * Group all {@link CPGClass} by their classType.
     */
    public final TreeMap<String, ArrayList<CPGClass>> distinctClassTypes = new TreeMap<>();

    /**
     * For every {@link com.CodeSmell.parser.CPGClass.Method}, determine the distinct method calls
     * that are made to each {@link CPGClass}.
     */
    public final HashMap<CPGClass.Method, HashMap<CPGClass, Integer>> distinctMethodCalls = new HashMap<>();

    /**
     * For every {@link com.CodeSmell.parser.CPGClass.Method}, determine the distinct attribute calls
     * that are made to each {@link CPGClass}.
     */
    public final HashMap<CPGClass.Method, HashMap<CPGClass, Integer>> distinctAttributeCalls = new HashMap<>();

    /**
     * Determine all the distinct packages, sub-packages and files which contain classes, within cpg.
     */
    public final ArrayList<Package> distinctPackages = new ArrayList<>();

    /**
     * Group all the relations present within cpg by their {@link com.CodeSmell.model.ClassRelation.RelationshipType}
     */
    public final HashMap<ClassRelation.RelationshipType, ArrayList<CodePropertyGraph.Relation>> distinctRelations = new HashMap<>();

    /**
     * Determine the total non-empty lines used by each class (accounts for nested classes)
     */
    public final HashMap<CPGClass, Integer> totalClassLines = new HashMap<>();

    /**
     * Determine the total method calls that a class makes to other classes within cpg
     */
    public final HashMap<CPGClass, HashMap<CPGClass, Integer>> totalClassMethodCalls = new HashMap<>();

    /**
     * Determine the total attribute calls that a class makes to other classes within cpg
     */
    public final HashMap<CPGClass, HashMap<CPGClass, Integer>> totalClassAttributeCalls = new HashMap<>();

    public StatTracker(CodePropertyGraph cpg) {
        helper = new Helper(cpg);
        determineAttributeUsage(attributeUsage, helper);
        determineClassUsage(cpg, classUsage, helper);
        determineMethodUsage(methodUsage, helper);
        determineParameterUsage(parameterUsage, helper);
        determineDistinctClassTypes(cpg, distinctClassTypes);
        determineDistinctAttributeCalls(distinctAttributeCalls, helper);
        determineDistinctMethodCalls(distinctMethodCalls, helper);
        determineDistinctPackages(cpg, distinctPackages);
        determineDistinctRelations(cpg, distinctRelations);
        determineTotalClassLines(cpg, totalClassLines);
        determineTotalClassAttributeCalls(cpg, distinctAttributeCalls, totalClassAttributeCalls);
        determineTotalClassMethodCalls(cpg, distinctMethodCalls, totalClassMethodCalls);
    }

    /**
     * Iterates through the cpg to determine how many times an Attribute has been used within methods.
     *
     * @param attributeUsage
     * @param helper
     */
    private static void determineAttributeUsage(HashMap<CPGClass.Attribute, Integer> attributeUsage,
                                                Helper helper) {
        ArrayList<CPGClass.Attribute> allAttributes = helper.allAttributes;
        ArrayList<CPGClass.Attribute> allAttributeCalls = helper.allAttributeCalls;
        for (CPGClass.Attribute attribute : allAttributeCalls) {
            attributeUsage.put(attribute, attributeUsage.getOrDefault(attribute, 0) + 1);
        }
        allAttributes.forEach(attr -> attributeUsage.putIfAbsent(attr, 0));
    }

    /**
     * For every class within the cpg, return a count of how many times it was used as an attribute type,
     * inheritance/realization relation, parameter or its methods were used
     *
     * @param cpg        The CodePropertyGraph containing all existing classes and relations
     * @param classUsage
     * @param helper
     */
    private static void determineClassUsage(CodePropertyGraph cpg, HashMap<CPGClass, Integer> classUsage, Helper helper) {
        ArrayList<CPGClass.Method> allMethodCallsWithinCPG = helper.allMethodCalls;
        ArrayList<CPGClass.Attribute> allAttributesWithinCPG = helper.allAttributes;
        ArrayList<CPGClass.Method.Parameter> allParametersWithinCPG = helper.allParameters;
        for (CPGClass cpgClass : cpg.getClasses()) {
            var attributeUsageCount = allAttributesWithinCPG.stream().filter(attribute ->
                    attribute.attributeType.contains(cpgClass.name) ||
                            attribute.attributeType.contains(cpgClass.classFullName)).collect(Collectors.toList());
            var relationCount = cpg.getRelations().stream().
                    filter(relation -> (relation.type.equals(ClassRelation.RelationshipType.INHERITANCE) ||
                            relation.type.equals(ClassRelation.RelationshipType.REALIZATION))
                            && relation.destination.equals(cpgClass)).collect(Collectors.toList());
            var methodCallsCount = allMethodCallsWithinCPG.stream().
                    filter(method -> method.parentClassName.equals(cpgClass.name)).collect(Collectors.toList());
            var parameterUsageCount = allParametersWithinCPG.stream().
                    filter(parameter -> (parameter.typeList.contains(cpgClass.name)
                            || parameter.typeList.contains(cpgClass.classFullName))).collect(Collectors.toList());
            int totalClassUsage = attributeUsageCount.size() + relationCount.size() + methodCallsCount.size() + parameterUsageCount.size();
            classUsage.put(cpgClass, totalClassUsage);
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
    }

    /**
     * For every method within cpg, determine how many times each method parameter was used within the method's instructions
     *
     * @param parameterUsage
     * @param helper
     */
    private static void determineParameterUsage(HashMap<CPGClass.Method, HashMap<CPGClass.Method.Parameter, Integer>> parameterUsage,
                                                Helper helper) {
        ArrayList<CPGClass.Method> allMethods = helper.allMethods;
        for (CPGClass.Method method : allMethods) {
            HashMap<CPGClass.Method.Parameter, Integer> parameterUsageMap = new HashMap<>();
            for (CPGClass.Method.Parameter parameter : method.parameters) {
                var filteredInstructions = Arrays.stream(method.instructions).filter(ins -> ins.label.equals("IDENTIFIER")
                        && ins.code.contains(parameter.name)).collect(Collectors.toList());
                parameterUsageMap.put(parameter, filteredInstructions.size());
            }
            parameterUsage.put(method, parameterUsageMap);
        }
    }

    /**
     * Iterates through the cpg to determine all the distinct class types that are present such as
     * abstract class, class, enum, interface.
     *
     * @param cpg                The CodePropertyGraph containing all existing classes and relations
     * @param distinctClassTypes
     */
    private static void determineDistinctClassTypes(CodePropertyGraph cpg,
                                                    TreeMap<String, ArrayList<CPGClass>> distinctClassTypes) {
        for (CPGClass cpgClass : cpg.getClasses()) {
            String classType = cpgClass.classType;
            distinctClassTypes.putIfAbsent(classType, new ArrayList<>());
            distinctClassTypes.get(classType).add(cpgClass);
        }
    }

    /**
     * @param distinctAttributeCalls
     * @param helper
     */
    private static void determineDistinctAttributeCalls(HashMap<CPGClass.Method, HashMap<CPGClass, Integer>> distinctAttributeCalls, Helper helper) {
        ArrayList<CPGClass.Method> allMethods = helper.allMethods;
        for (CPGClass.Method method : allMethods) {
            HashMap<CPGClass, Integer> attributeCallClassMap = new HashMap<>();
            method.getAttributeCalls().
                    forEach(attrCall -> attributeCallClassMap.put(helper.getClassFromName(attrCall.parentClassName),
                            attributeCallClassMap.getOrDefault(helper.getClassFromName(attrCall.parentClassName), 0) + 1));
            distinctAttributeCalls.put(method, attributeCallClassMap);
        }
    }

    /**
     * @param distinctMethodCalls
     * @param helper
     */
    private static void determineDistinctMethodCalls(HashMap<CPGClass.Method, HashMap<CPGClass, Integer>> distinctMethodCalls, Helper helper) {
        ArrayList<CPGClass.Method> allMethods = helper.allMethods;
        for (CPGClass.Method method : allMethods) {
            HashMap<CPGClass, Integer> methodCallClassMap = new HashMap<>();
            method.getMethodCalls().
                    forEach(methodCall -> methodCallClassMap.put(helper.getClassFromName(methodCall.parentClassName),
                            methodCallClassMap.getOrDefault(helper.getClassFromName(methodCall.parentClassName), 0) + 1));
            distinctMethodCalls.put(method, methodCallClassMap);
        }
    }

    /**
     * Iterate through the cpg to determine all the distinct packages and sub-packages, additionally creating
     * files which possess 1 or more classes each (accounts for nested classes).
     *
     * @param cpg              The CodePropertyGraph containing all existing classes and relations
     * @param distinctPackages
     */
    private static void determineDistinctPackages(CodePropertyGraph cpg,
                                                  ArrayList<Package> distinctPackages) {
        // Get all package names, mapped to an array list of files
        TreeMap<String, ArrayList<File>> packageNames = new TreeMap<>();
        for (CPGClass cpgClass : cpg.getClasses()) {
            String packageName = cpgClass.packageName;
            String filePath = cpgClass.filePath;
            String fileName = cpgClass.name + ".java";
            packageNames.putIfAbsent(packageName, new ArrayList<>());
            if (filePath.contains(fileName)) {
                // Handle adding of new files and classes within those files
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
     * @param cpg               The CodePropertyGraph containing all existing classes and relations
     * @param distinctRelations
     */
    private static void determineDistinctRelations(CodePropertyGraph cpg,
                                                   HashMap<ClassRelation.RelationshipType, ArrayList<CodePropertyGraph.Relation>> distinctRelations) {
        for (ClassRelation.RelationshipType relationshipType : ClassRelation.RelationshipType.values()) {
            var allMatchingRelations = cpg.getRelations().stream().
                    filter(relation -> relation.type.equals(relationshipType)).collect(Collectors.toList());
            distinctRelations.put(relationshipType, (ArrayList<CodePropertyGraph.Relation>) allMatchingRelations);
        }
    }

    /**
     * For each class within the cpg, determine how many non-empty lines are occupied by that class. Additionally, accounting
     * for any nested classes within the same file.
     *
     * @param cpg             The CodePropertyGraph containing all existing classes and relations
     * @param totalClassLines The map containing all the classes within cpg and all of their total class line length
     */
    private static void determineTotalClassLines(CodePropertyGraph cpg,
                                                 HashMap<CPGClass, Integer> totalClassLines) {
        for (CPGClass cpgClass : cpg.getClasses()) {
            String filePath = cpgClass.filePath;
            boolean isRootClass = filePath.contains(cpgClass.name);
            int selfAttributeLength = cpgClass.attributes.length;
            final int[] selfMethodLines = {0};
            Arrays.stream(cpgClass.methods).forEach(method -> {
                selfMethodLines[0] += method.totalMethodLength;
            });
            int selfDeclarationLines = 2;
            int selfTotal = selfDeclarationLines + selfAttributeLength + selfMethodLines[0];
            var nestedCheck = cpg.getClasses().stream().
                    filter(cpgToFind -> cpgToFind.filePath.equals(filePath) &&
                            !cpgToFind.name.equals(cpgClass.name)).collect(Collectors.toList());
            if (nestedCheck.isEmpty()) {
                totalClassLines.put(cpgClass, selfTotal);
            } else {
                int nestedTotal = 0;
                for (CPGClass nestedClass : nestedCheck) {
                    int attrLength = nestedClass.attributes.length;
                    final int[] methodLength = {0};
                    Arrays.stream(nestedClass.methods).forEach(method -> {
                        methodLength[0] += method.totalMethodLength;
                    });
                    int declLines = 2;
                    nestedTotal += declLines + attrLength + methodLength[0];
                }
                if (isRootClass) {
                    int total = nestedTotal + selfTotal;
                    totalClassLines.put(cpgClass, total);
                } else {
                    totalClassLines.put(cpgClass, selfTotal);
                }
            }
        }
    }

    /**
     * @param cpg
     * @param distinctAttributeCalls
     * @param totalClassAttributeCalls
     */
    private static void determineTotalClassAttributeCalls(CodePropertyGraph cpg,
                                                          HashMap<CPGClass.Method, HashMap<CPGClass, Integer>> distinctAttributeCalls,
                                                          HashMap<CPGClass, HashMap<CPGClass, Integer>> totalClassAttributeCalls) {
        for (CPGClass cpgClass : cpg.getClasses()) {
            HashMap<CPGClass, Integer> classIntegerHashMap = new HashMap<>();
            for (CPGClass.Method method : cpgClass.methods) {
                distinctAttributeCalls.get(method).forEach((key, value) -> classIntegerHashMap.
                        put(key, classIntegerHashMap.getOrDefault(key, 0) + value));
            }
            totalClassAttributeCalls.put(cpgClass, classIntegerHashMap);
        }
    }

    /**
     * @param cpg
     * @param distinctMethodCalls
     * @param totalClassMethodCalls
     */
    private static void determineTotalClassMethodCalls(CodePropertyGraph cpg,
                                                       HashMap<CPGClass.Method, HashMap<CPGClass, Integer>> distinctMethodCalls,
                                                       HashMap<CPGClass, HashMap<CPGClass, Integer>> totalClassMethodCalls) {
        for (CPGClass cpgClass : cpg.getClasses()) {
            HashMap<CPGClass, Integer> classIntegerHashMap = new HashMap<>();
            for (CPGClass.Method method : cpgClass.methods) {
                distinctMethodCalls.get(method).forEach((key, value) -> classIntegerHashMap.
                        put(key, classIntegerHashMap.getOrDefault(key, 0) + value));
            }
            totalClassMethodCalls.put(cpgClass, classIntegerHashMap);
        }
    }


    /**
     * A class that contains potentially helpful lists containing info such as all attributes, all methods,
     * all method calls, all method parameters, all class names within cpg. Additionally, can retrieve class, method and
     * attribute by name.
     */
    public static final class Helper {
        /**
         * The CodePropertyGraph containing all existing classes and relations
         */
        private final CodePropertyGraph cpg;

        /**
         * All the {@link com.CodeSmell.parser.CPGClass.Attribute} that exist within the cpg
         */
        public final ArrayList<CPGClass.Attribute> allAttributes = new ArrayList<>();

        /**
         * All the {@link com.CodeSmell.parser.CPGClass.Method} that exist within the cpg
         */
        public final ArrayList<CPGClass.Method> allMethods = new ArrayList<>();

        /**
         * All the {@link com.CodeSmell.parser.CPGClass.Method} calls that exist within the cpg
         */
        public final ArrayList<CPGClass.Method> allMethodCalls = new ArrayList<>();

        /**
         * All the {@link com.CodeSmell.parser.CPGClass.Attribute} calls that exist within the cpg
         */
        public final ArrayList<CPGClass.Attribute> allAttributeCalls = new ArrayList<>();

        /**
         * All the {@link com.CodeSmell.parser.CPGClass.Method.Parameter} that exist within the cpg
         */
        public final ArrayList<CPGClass.Method.Parameter> allParameters = new ArrayList<>();

        /**
         * All the class names (both name and classFullName) that exist within the cpg
         */
        public final ArrayList<String> allClassNames = new ArrayList<>();

        /**
         * All the method names that exist within the cpg
         */
        public final ArrayList<String> allMethodNames = new ArrayList<>();

        public Helper(CodePropertyGraph cpg) {
            this.cpg = cpg;
            collectAllAttributes(cpg, allAttributes);
            collectAllMethods(cpg, allMethods);
            collectAllParameters(allMethods, allParameters);
            collectAllMethodCalls(allMethods, allMethodCalls);
            collectAllAttributeCalls(allMethods, allAttributeCalls);
            collectAllClassNames(cpg, allClassNames);
            collectAllMethodNames(cpg, allMethodNames);
        }

        /**
         * @param className
         * @return
         */
        public CPGClass getClassFromName(String className) {
            CPGClass classToReturn = null;
            var classResult = cpg.getClasses().stream().
                    filter(cpgClass -> (cpgClass.name.equals(className) || cpgClass.classFullName.equals(className))).
                    limit(2).
                    collect(Collectors.toList());
            if (!classResult.isEmpty()) {
                classToReturn = classResult.get(0);
            }
            return classToReturn;
        }

        /**
         * @param attributeName
         * @param attributeType
         * @param parentClassName
         * @return
         */
        public CPGClass.Attribute getAttributeFromName(String attributeName,
                                                       String attributeType,
                                                       String parentClassName) {
            CPGClass.Attribute attributeToReturn = null;
            var attributeResult = allAttributes.stream().
                    filter(attribute -> attribute.name.equals(attributeName) &&
                            attribute.attributeType.equals(attributeType) &&
                            attribute.parentClassName.equals(parentClassName)).
                    limit(2).
                    collect(Collectors.toList());
            if (!attributeResult.isEmpty()) {
                attributeToReturn = attributeResult.get(0);
            }
            return attributeToReturn;
        }

        /**
         * @param methodName
         * @param parentClassName
         * @return
         */
        public CPGClass.Method getMethodFromName(String methodName,
                                                 String parentClassName) {
            CPGClass.Method methodToReturn = null;
            var methodResult = allMethods.stream().
                    filter(method -> method.name.equals(methodName) &&
                            method.parentClassName.equals(parentClassName)).
                    limit(2).
                    collect(Collectors.toList());
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
        private static void collectAllAttributes(CodePropertyGraph cpg,
                                                 ArrayList<CPGClass.Attribute> allAttributes) {
            cpg.getClasses().forEach(cpgClass -> allAttributes.addAll(Arrays.asList(cpgClass.attributes)));
        }

        /**
         * Collect all the methods that exist within cpg
         *
         * @param cpg        - The CodePropertyGraph containing all existing classes and relations
         * @param allMethods
         */
        private static void collectAllMethods(CodePropertyGraph cpg,
                                              ArrayList<CPGClass.Method> allMethods) {
            cpg.getClasses().forEach(cpgClass -> allMethods.addAll(Arrays.asList(cpgClass.methods)));
        }

        /**
         * Collect all the method calls that exist within cpg
         */
        private static void collectAllMethodCalls(ArrayList<CPGClass.Method> allMethods,
                                                  ArrayList<CPGClass.Method> allMethodCalls) {
            allMethods.forEach(method -> allMethodCalls.addAll(method.getMethodCalls()));
        }

        private static void collectAllAttributeCalls(ArrayList<CPGClass.Method> allMethods,
                                                     ArrayList<CPGClass.Attribute> allAttributeCalls) {
            allMethods.forEach(method -> allAttributeCalls.addAll(method.getAttributeCalls()));
        }

        /**
         * Collect all the method parameters that exist within cpg
         */
        private static void collectAllParameters(ArrayList<CPGClass.Method> allMethods,
                                                 ArrayList<CPGClass.Method.Parameter> allParameters) {
            allMethods.forEach(method -> allParameters.addAll(Arrays.asList(method.parameters)));
        }

        /**
         * Collect all the class names that exist within the cpg (both name and classFullName)
         *
         * @param cpg           The CodePropertyGraph containing all existing classes and relations
         * @param allClassNames
         */
        private static void collectAllClassNames(CodePropertyGraph cpg,
                                                 ArrayList<String> allClassNames) {
            HashSet<String> uniqueNames = new HashSet<>();
            cpg.getClasses().forEach(cpgClass -> uniqueNames.add(cpgClass.name));
            cpg.getClasses().forEach(cpgClass -> uniqueNames.add(cpgClass.classFullName));
            allClassNames.addAll(uniqueNames);
        }

        /**
         * Collect all the method names that exist within the cpg
         *
         * @param cpg            The CodePropertyGraph containing all existing classes and relations
         * @param allMethodNames
         */
        private static void collectAllMethodNames(CodePropertyGraph cpg,
                                                  ArrayList<String> allMethodNames) {
            cpg.getClasses().forEach(cpgClass -> Arrays.stream(cpgClass.methods).forEach(method -> allMethodNames.add(method.name)));
        }

    }

}