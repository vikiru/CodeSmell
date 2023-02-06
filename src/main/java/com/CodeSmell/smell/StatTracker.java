package com.CodeSmell.smell;

import com.CodeSmell.model.ClassRelation;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;

import com.CodeSmell.parser.Package;

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
     * Group all the relations present within cpg by their {@link com.CodeSmell.model.ClassRelation.RelationshipType}
     */
    public final HashMap<ClassRelation.RelationshipType, ArrayList<CodePropertyGraph.Relation>> distinctRelations = new HashMap<>();
    public final HashMap<Package, PackageStat> packageStats = new HashMap<>();
    public final ArrayList<ClassStat> classStat = new ArrayList<>();

    public StatTracker(CodePropertyGraph cpg) {
        helper = new Helper(cpg);
        createAllClassStats(classStat, cpg, helper);
    }

    private static void createAllClassStats(ArrayList<ClassStat> classStat,
                                            CodePropertyGraph cpg,
                                            Helper helper) {
        for (CPGClass cpgClass : cpg.getClasses()) {
            classStat.add(new ClassStat(cpgClass, cpg, helper));
        }
    }

    public static final class PackageStat {
        public final HashMap<Package, Integer> packageClassUse = new HashMap<>();
        public final HashMap<String, ArrayList<CPGClass>> distinctClassTypes = new HashMap<>();

        public PackageStat(Package pkg, CodePropertyGraph cpg, Helper helper) {

        }
    }

    public static final class ClassStat {
        public final CPGClass cpgClass;
        public final int classUsage;
        public final HashMap<String, Integer> usageMap = new HashMap<>();
        public final int totalClassLines;
        public final HashMap<String, Integer> classLineMap = new HashMap<>();
        public final ArrayList<CPGClass.Attribute> publicAttributes;
        public final ArrayList<CPGClass.Method> publicMethods;
        ArrayList<AttributeStat> attributeStats = new ArrayList<>();
        ArrayList<MethodStat> methodStats = new ArrayList<>();

        public ClassStat(CPGClass cpgClass, CodePropertyGraph cpg, Helper helper) {
            this.cpgClass = cpgClass;
            this.classUsage = determineClassUsage(cpgClass, usageMap, cpg, helper);
            this.totalClassLines = determineTotalClassLines(cpgClass, cpg, classLineMap);
            this.publicAttributes = determinePublicAttributes(cpgClass);
            this.publicMethods = determinePublicMethods(cpgClass);
            createAttributeMethodStats(cpgClass, helper, attributeStats, methodStats);
        }

        private static void createAttributeMethodStats(CPGClass cpgClass,
                                                       Helper helper,
                                                       ArrayList<AttributeStat> attributeStats,
                                                       ArrayList<MethodStat> methodStats) {
            cpgClass.getAttributes().forEach(attribute -> attributeStats.add(new AttributeStat(attribute, helper)));
            cpgClass.getMethods().forEach(method -> methodStats.add(new MethodStat(method, helper)));
        }

        /**
         * Determine how many times a given CPGClass was used throughout the cpg as an attribute type,
         * parameter type, the destination of an inheritance or realization relationship, the amount of times
         * its attributes and methods were called.
         *
         * <p>
         * Additionally, maintain a more detailed overview of this via usageMap.
         * </p>
         *
         * @param cpgClass
         * @param usageMap
         * @param cpg
         * @param helper
         * @return
         */
        private static int determineClassUsage(CPGClass cpgClass,
                                               HashMap<String, Integer> usageMap,
                                               CodePropertyGraph cpg,
                                               Helper helper) {
            // Count how many times cpgClass appears as a type
            int attributeTypeCount = Math.toIntExact(helper.allAttributes.stream().
                    filter(attribute -> (attribute.getTypeList().contains(cpgClass))).count());
            // Count how many times cpg class appears as the destination for inheritance and realization relationships
            int inheritanceCount = Math.toIntExact(cpg.getRelations().stream().
                    filter(relation -> relation.type.equals(ClassRelation.RelationshipType.INHERITANCE) &&
                            relation.destination.equals(cpgClass)).count());
            int realizationCount = Math.toIntExact(cpg.getRelations().stream().
                    filter(relation -> relation.type.equals(ClassRelation.RelationshipType.REALIZATION) &&
                            relation.destination.equals(cpgClass)).count());
            // Count how many times attributes and methods belonging to cpgClass were called within cpg
            int attributeCallCount = Math.toIntExact(helper.allAttributeCalls.stream().
                    filter(attributeCall -> attributeCall.getParent().equals(cpgClass)).count());
            int methodCallCount = Math.toIntExact(helper.allMethodCalls.stream().
                    filter(method -> method.getParent().equals(cpgClass)).count());
            // Count how many times cpgClass was used as the type of parameter
            int parameterCount = Math.toIntExact(helper.allParameters.stream().
                    filter(param -> param.getTypeList().contains(cpgClass)).count());
            // Add everything to usageMap and return the total of the values.
            usageMap.put("attributeTypeCount", attributeTypeCount);
            usageMap.put("attributeCallCount", attributeCallCount);
            usageMap.put("methodCallCount", methodCallCount);
            usageMap.put("inheritanceCount", inheritanceCount);
            usageMap.put("realizationCount", realizationCount);
            usageMap.put("parameterCount", parameterCount);
            final int[] classUsage = {0};
            usageMap.values().forEach(value -> classUsage[0] += value);
            return classUsage[0];
        }

        /**
         * @param cpgClass
         * @param cpg
         * @return
         */
        private static int determineTotalClassLines(CPGClass cpgClass,
                                                    CodePropertyGraph cpg,
                                                    HashMap<String, Integer> classLineMap) {
            var filePathResult = cpg.getClasses().stream().
                    filter(cpgToFind -> cpgToFind.filePath.equals(cpgClass.filePath)).
                    collect(Collectors.toList());
            boolean isRoot = cpgClass.filePath.contains(cpgClass.name);
            int totalSelfLines = 0;
            int totalNestedLines = 0;
            for (CPGClass classWithinFile : filePathResult) {
                int declarationLength = 2;
                final int[] attributeLength = {0};
                classWithinFile.getAttributes().stream().
                        filter(attribute -> attribute.getParent().name.equals(classWithinFile.name)).
                        forEach(attribute -> attributeLength[0] += 1);
                final int[] methodLength = {0};
                classWithinFile.getMethods().stream().
                        filter(method -> method.getParent().name.equals(classWithinFile.name)).
                        forEach(method -> methodLength[0] += method.totalMethodLength);
                if (classWithinFile.name.equals(cpgClass.name)) {
                    totalSelfLines += attributeLength[0] + declarationLength + methodLength[0];
                    classLineMap.put("attributeLineCount", attributeLength[0]);
                    classLineMap.put("declarationLineCount", declarationLength);
                    classLineMap.put("methodLineCount", methodLength[0]);
                } else if (isRoot) {
                    totalNestedLines += attributeLength[0] + declarationLength + methodLength[0];
                }
            }
            classLineMap.put("nestedLineTotal", totalNestedLines);
            return totalSelfLines + totalNestedLines;
        }

        private static ArrayList<CPGClass.Attribute> determinePublicAttributes(CPGClass cpgClass) {
            return cpgClass.getAttributes().stream().
                    filter(attribute -> Arrays.asList(attribute.modifiers).contains(CPGClass.Modifier.PUBLIC)).
                    collect(Collectors.toCollection(ArrayList::new));
        }

        private ArrayList<CPGClass.Method> determinePublicMethods(CPGClass cpgClass) {
            return cpgClass.getMethods().stream().
                    filter(method -> Arrays.asList(method.modifiers).contains(CPGClass.Modifier.PUBLIC)).
                    collect(Collectors.toCollection(ArrayList::new));
        }
    }

    public static final class AttributeStat {
        public final CPGClass.Attribute attribute;
        public final int attributeUsage;
        public final HashMap<CPGClass.Method, Integer> methodsWhichCallAttr = new HashMap<>();
        public final HashMap<CPGClass, Integer> classesWhichCallAttr = new HashMap<>();

        //HashMap<CPGClass, Integer> cpgClasses which call attr
        public AttributeStat(CPGClass.Attribute attribute, Helper helper) {
            this.attribute = attribute;
            this.attributeUsage = determineAttributeUsage(attribute, helper);
            determineAttributeUsageInMethods(attribute, helper, methodsWhichCallAttr, classesWhichCallAttr);
        }

        private static void determineAttributeUsageInMethods(CPGClass.Attribute attribute,
                                                             Helper helper,
                                                             HashMap<CPGClass.Method, Integer> methodsWhichCallAttr,
                                                             HashMap<CPGClass, Integer> classWhichCallAttr) {
            ArrayList<CPGClass.Method> allMethods = helper.allMethods;
            for (CPGClass.Method method : allMethods) {
                if (method.getAttributeCalls().contains(attribute)) {
                    int count = Math.toIntExact(Arrays.stream(method.instructions).
                            filter(instruction -> instruction.label.equals("FIELD_IDENTIFIER")
                                    && instruction.code.contains(attribute.name)).count());
                    methodsWhichCallAttr.put(method, count);
                    classWhichCallAttr.put(method.getParent(),
                            classWhichCallAttr.getOrDefault(method.getParent(), 0) + count);
                }
            }
        }

        private static int determineAttributeUsage(CPGClass.Attribute attribute, Helper helper) {
            ArrayList<CPGClass.Attribute> allAttributeCalls = helper.allAttributeCalls;
            return Math.toIntExact(allAttributeCalls.stream().
                    filter(methodCall -> methodCall == attribute).count());
        }
    }

    /**
     * MethodStat contains stats relevant to a given method pertaining to how it is used within cpg.
     */
    public static final class MethodStat {

        /**
         * The reference to the method that this MethodStat is providing information about.
         */
        public final CPGClass.Method method;
        /**
         * The total times this method was called within cpg
         */
        public final int methodUsage;
        public final HashMap<CPGClass.Method, Integer> methodsWhichCallMethod = new HashMap<>();
        public final HashMap<CPGClass, Integer> classesWhichCallMethod = new HashMap<>();
        public final HashMap<CPGClass.Method.Parameter, Integer> parameterUsage = new HashMap<>();

        public MethodStat(CPGClass.Method method, Helper helper) {
            this.method = method;
            determineParameterUsage(method, parameterUsage);
            determineMethodCallUsage(method, helper, methodsWhichCallMethod, classesWhichCallMethod);
            this.methodUsage = determineMethodUsage(methodsWhichCallMethod);
        }

        private static void determineParameterUsage(CPGClass.Method method,
                                                    HashMap<CPGClass.Method.Parameter, Integer> parameterUsage) {
            for (CPGClass.Method.Parameter parameter : method.parameters) {
                var filteredInstructions = Arrays.stream(method.instructions).filter(ins -> ins.label.equals("IDENTIFIER")
                        && ins.code.contains(parameter.name)).collect(Collectors.toList());
                parameterUsage.put(parameter, filteredInstructions.size());
            }
        }

        private static void determineMethodCallUsage(CPGClass.Method method,
                                                     Helper helper,
                                                     HashMap<CPGClass.Method, Integer> methodsWhichCallMethod,
                                                     HashMap<CPGClass, Integer> classesWhichCallMethod) {
            ArrayList<CPGClass.Method> allMethods = helper.allMethods;
            String toFind = method.getParent().name + "." + method.name;
            for (CPGClass.Method methodInCPG : allMethods) {
                if (methodInCPG.getMethodCalls().contains(method)) {
                    int count = Math.toIntExact(Arrays.stream(methodInCPG.instructions).
                            filter(instruction -> instruction.methodCall.equals(toFind)).count());
                    methodsWhichCallMethod.put(methodInCPG, count);
                    classesWhichCallMethod.put(methodInCPG.getParent(),
                            classesWhichCallMethod.getOrDefault(methodInCPG.getParent(), 0) + count);
                }
            }
        }

        private static int determineMethodUsage(HashMap<CPGClass.Method, Integer> methodsWhichCallMethod) {
            final int[] count = {0};
            methodsWhichCallMethod.forEach((key, value) -> count[0] += value);
            return count[0];
        }
    }


    /**
     * A class that contains potentially helpful lists containing info such as all attributes, all methods,
     * all method calls, all method parameters, all class names within cpg. Additionally, can retrieve class, method and
     * attribute by name.
     */
    public static final class Helper {
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
            collectAllAttributes(cpg, allAttributes);
            collectAllMethods(cpg, allMethods);
            collectAllParameters(allMethods, allParameters);
            collectAllMethodCalls(allMethods, allMethodCalls);
            collectAllAttributeCalls(allMethods, allAttributeCalls);
            collectAllClassNames(cpg, allClassNames);
            collectAllMethodNames(cpg, allMethodNames);
        }

        /**
         * Collect all the attributes that exist within cpg
         *
         * @param cpg           - The CodePropertyGraph containing all existing classes and relations
         * @param allAttributes
         */
        private static void collectAllAttributes(CodePropertyGraph cpg,
                                                 ArrayList<CPGClass.Attribute> allAttributes) {
            cpg.getClasses().forEach(cpgClass -> allAttributes.addAll(cpgClass.getAttributes()));
        }

        /**
         * Collect all the methods that exist within cpg
         *
         * @param cpg        - The CodePropertyGraph containing all existing classes and relations
         * @param allMethods
         */
        private static void collectAllMethods(CodePropertyGraph cpg,
                                              ArrayList<CPGClass.Method> allMethods) {
            cpg.getClasses().forEach(cpgClass -> allMethods.addAll(cpgClass.getMethods()));
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
            cpg.getClasses().forEach(cpgClass -> cpgClass.getMethods().forEach(method -> allMethodNames.add(method.name)));
        }

    }

}