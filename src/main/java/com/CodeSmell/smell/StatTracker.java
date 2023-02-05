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

    public final HashMap<CPGClass, ClassStat> classStats = new HashMap<>();
    public final HashMap<Package, PackageStat> packageStats = new HashMap<>();

    // determine total calls (Attribute and methodCalls) that a class makes to various packages including its own package
    // hashmap<cpgclass, hashmap<package, int>

    public StatTracker(CodePropertyGraph cpg) {
        helper = new Helper(cpg);
    }

    public static final class PackageStat {
    }

    public static final class ClassStat {
        public final int classUsage;
        public final int totalClassLines;
        public final HashMap<CPGClass.Attribute, Integer> attributesUsage = new HashMap<>();
        public final HashMap<CPGClass.Method, Integer> methodsUsage = new HashMap<>();
        public final HashMap<CPGClass, ArrayList<CPGClass.Attribute>> totalClassAttributeCalls = new HashMap<>();
        public final HashMap<CPGClass, ArrayList<CPGClass.Method>> totalClassMethodCalls = new HashMap<>();

        public ClassStat(CPGClass cpgClass, int classUsage, int totalClassLines) {
            this.classUsage = classUsage;
            this.totalClassLines = totalClassLines;
        }
    }

    /**
     * @param cpgClass
     * @param cpg
     * @param helper
     * @return
     */
    private static int determineClassUsage(CPGClass cpgClass, CodePropertyGraph cpg, Helper helper) {
        var filteredRelations = cpg.getRelations().stream().
                filter(relation -> (relation.type.equals(ClassRelation.RelationshipType.INHERITANCE) ||
                        relation.type.equals(ClassRelation.RelationshipType.REALIZATION))).collect(Collectors.toList());
        var attributeTypes = helper.allAttributes.stream().
                filter(attribute -> (attribute.typeList.contains(cpgClass.name) || attribute.typeList.contains(cpgClass.classFullName))).
                collect(Collectors.toList());
        var allAttributeCalls = helper.allAttributeCalls.stream().
                filter(attributeCall -> attributeCall.parentClassName.equals(cpgClass.name)).collect(Collectors.toList());
        var allMethodCalls = helper.allMethodCalls.stream().
                filter(method -> method.parentClassName.equals(cpgClass.name)).collect(Collectors.toList());
        var allParameters = helper.allParameters.stream().filter(param -> param.typeList.contains(cpgClass.name)).
                collect(Collectors.toList());
        return filteredRelations.size() + attributeTypes.size() +
                allAttributeCalls.size() + allMethodCalls.size() + allParameters.size();
    }

    /**
     * @param cpgClass
     * @param cpg
     * @return
     */
    private static int determineTotalClassLines(CPGClass cpgClass, CodePropertyGraph cpg) {
        var filePathResult = cpg.getClasses().stream().filter(cpgToFind -> cpgToFind.filePath.equals(cpgClass.filePath)).
                collect(Collectors.toList());
        int totalSelfLines = 0;
        int totalNestedLines = 0;
        System.out.println(cpgClass.name);
        for (CPGClass classWithinFile : filePathResult) {
            int declarationLength = 2;
            final int[] attributeLength = {0};
            Arrays.stream(classWithinFile.attributes).
                    filter(attribute -> attribute.parentClassName.equals(classWithinFile.name)).
                    forEach(attribute -> attributeLength[0] += 1);
            final int[] methodLength = {0};
            Arrays.stream(classWithinFile.methods).
                    filter(method -> method.parentClassName.equals(classWithinFile.name)).
                    forEach(method -> methodLength[0] += method.totalMethodLength);
            if (classWithinFile.name.equals(cpgClass.name)) {
                totalSelfLines += attributeLength[0] + declarationLength + methodLength[0];
            } else {
                totalNestedLines += attributeLength[0] + declarationLength + methodLength[0];
            }
        }
        int totalLines = totalSelfLines + totalNestedLines;
        return totalLines;
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