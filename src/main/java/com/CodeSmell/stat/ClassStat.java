package com.CodeSmell.stat;

import com.CodeSmell.model.ClassRelation;
import com.CodeSmell.model.ClassRelation.*;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CPGClass.*;
import com.CodeSmell.parser.CodePropertyGraph;

import java.util.*;
import java.util.stream.Collectors;

public class ClassStat {
    /**
     * The reference to the class that this ClassStat is providing statistics on
     */
    public final CPGClass cpgClass;
    /**
     * A map containing all the attribute stats of a given class
     */
    public final Map<Attribute, AttributeStat> attributeStats;
    /**
     * A map containing all the method stats of a given class
     */
    public final Map<Method, MethodStat> methodStats;
    /**
     * The total number of times this class was used within cpg
     */
    public final int classUsage;
    /**
     * A detailed overview indicating how the classUsage was determined by showing how many times
     * a class was used as a parameter type, inheritance or realization relation target, how many times it was used
     * as an attribute type, and how many times its attributes and methods were called
     */
    public final Map<String, Integer> usageMap;
    /**
     * The total number of non-empty lines present within a file (excludes package and import statements)
     */
    public final int totalClassLines;
    /**
     * A detailed overview indicating how totalClassLines was determined showing how many lines were associated with
     * attributes, methods, class declaration, and all nested classes
     */
    public final Map<String, Integer> classLineMap;
    /**
     * Groups all the attributes of a given class by "PUBLIC", "PROTECTED", "PRIVATE" and "PACKAGE PRIVATE" modifiers.
     */
    public final Map<Modifier, List<Attribute>> modifierGroupedAttributes;
    /**
     * Groups all the methods of a given class by "PUBLIC", "PROTECTED", "PRIVATE" and "PACKAGE PRIVATE" modifiers.
     */
    public final Map<Modifier, List<Method>> modifierGroupedMethods;
    /**
     * The total distinct attribute calls that this class makes to other classes (including itself) via its methods.
     */
    public final Map<CPGClass, Integer> totalClassAttributeCalls;
    /**
     * The total distinct method calls that this class makes to other classes (including itself) via its methods.
     */
    public final Map<CPGClass, Integer> totalClassMethodCalls;

    public ClassStat(CPGClass cpgClass, CodePropertyGraph cpg, Helper helper,
                     Map<Attribute, AttributeStat> attributeStats,
                     Map<Method, MethodStat> methodStats) {
        this.cpgClass = cpgClass;
        this.attributeStats = Collections.unmodifiableMap(attributeStats);
        this.methodStats = Collections.unmodifiableMap(methodStats);
        this.usageMap = determineClassUsage(cpgClass, cpg, helper, attributeStats, methodStats);
        this.classUsage = returnTotalUsage(usageMap);
        this.classLineMap = determineTotalClassLines(cpgClass, cpg);
        this.totalClassLines = returnTotalClassLines(classLineMap);
        this.modifierGroupedAttributes = groupAttributesByModifiers(cpgClass);
        this.modifierGroupedMethods = groupMethodsByModifiers(cpgClass);
        this.totalClassAttributeCalls = determineTotalClassAttributeCalls(methodStats);
        this.totalClassMethodCalls = determineTotalClassMethodCalls(methodStats);
    }

    /**
     * Determine how many times a given CPGClass was used throughout the cpg as an attribute type,
     * parameter type, the destination of an inheritance or realization relationship, the amount of times
     * its attributes and methods were called.
     */
    private static Map<String, Integer> determineClassUsage(CPGClass cpgClass, CodePropertyGraph cpg, Helper helper,
                                                            Map<Attribute, AttributeStat> attributeStats,
                                                            Map<Method, MethodStat> methodStats) {
        Map<String, Integer> usageMap = new HashMap<>();
        // Count how many times cpgClass appears as a type
        int attributeTypeCount = Math.toIntExact(helper.allAttributes
                .stream()
                .filter(attribute -> (attribute.getTypeList().contains(cpgClass)))
                .count());
        // Count how many times cpg class appears as the destination for inheritance and realization relationships
        int inheritanceCount = Math.toIntExact(cpg.getRelations()
                .stream()
                .filter(relation -> relation.type.equals(RelationshipType.INHERITANCE)
                        && relation.destination.equals(cpgClass))
                .count());
        int realizationCount = Math.toIntExact(cpg.getRelations()
                .stream()
                .filter(relation -> relation.type.equals(RelationshipType.REALIZATION)
                        && relation.destination.equals(cpgClass))
                .count());
        // Count how many times attributes and methods belonging to cpgClass were called within cpg
        int attributeCallCount = returnTotalAttributeCalls(attributeStats);
        int methodCallCount = returnTotalMethodCalls(methodStats);
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
        return Collections.unmodifiableMap(usageMap);
    }

    /**
     * Iterate through all the key, value pairs of the usageMap to return an integer value representing the total
     * times a given class was used within cpg.
     *
     * @param usageMap The map containing a detailed overview of where this class was used
     * @return An integer value representing the total number of times this class was used
     */
    private static int returnTotalUsage(Map<String, Integer> usageMap) {
        final int[] count = {0};
        usageMap.forEach((key, value) -> count[0] += value);
        return count[0];
    }

    /**
     * Given a CPGClass object, return a map representing the total lines present within that file. Accounting for
     * nested classes and classes that inherit from a superclass.
     *
     * @param cpgClass - The class being analyzed
     * @param cpg      - The CodePropertyGraph containing the classes present in the codebase
     * @return An integer value representing the total amount of non-empty lines present within a class.
     */
    private static Map<String, Integer> determineTotalClassLines(CPGClass cpgClass, CodePropertyGraph cpg) {
        Map<String, Integer> classLineMap = new HashMap<>();
        var filePathResult = cpg.getClasses()
                .stream()
                .filter(cpgToFind -> cpgToFind.filePath.equals(cpgClass.filePath))
                .collect(Collectors.toList());
        boolean isRoot = cpgClass.filePath.contains(cpgClass.name);
        int totalNestedLines = 0;
        for (CPGClass classWithinFile : filePathResult) {
            int declarationLength = 2;
            final int[] attributeLength = {0};
            classWithinFile.getAttributes()
                    .stream()
                    .filter(attribute -> attribute.getParent().name.equals(classWithinFile.name))
                    .forEach(attribute -> attributeLength[0] += 1);
            final int[] methodLength = {0};
            classWithinFile.getMethods()
                    .stream()
                    .filter(method -> method.getParent().name.equals(classWithinFile.name))
                    .forEach(method -> methodLength[0] += method.totalMethodLength);
            if (classWithinFile.name.equals(cpgClass.name)) {
                classLineMap.put("attributeLineCount", attributeLength[0]);
                classLineMap.put("declarationLineCount", declarationLength);
                classLineMap.put("methodLineCount", methodLength[0]);
            } else if (isRoot) {
                totalNestedLines += attributeLength[0] + declarationLength + methodLength[0];
            }
        }
        classLineMap.put("nestedLineTotal", totalNestedLines);
        return Collections.unmodifiableMap(classLineMap);
    }

    /**
     * Iterate through the key, value pairs of the classLineMap and return an integer value representing the
     * total number of lines present within a class.
     *
     * @param classLineMap A map containing an overview of the factors contributing to the total number of lines in a class
     * @return An integer value representing the total number of lines in a class
     */
    private static int returnTotalClassLines(Map<String, Integer> classLineMap) {
        final int[] count = {0};
        classLineMap.forEach((key, value) -> count[0] += value);
        return count[0];
    }

    /**
     * Group all the attributes present within a class by 'PUBLIC', 'PRIVATE' and 'PACKAGE PRIVATE' modifiers.
     *
     * @param cpgClass The class being analyzed
     * @return A map containing attributes grouped by their modifier
     */
    private static Map<Modifier, List<Attribute>> groupAttributesByModifiers(CPGClass cpgClass) {
        Map<Modifier, ArrayList<Attribute>> modifierGroupedAttributes = new HashMap<>();
        modifierGroupedAttributes.put(Modifier.PUBLIC, new ArrayList<>());
        modifierGroupedAttributes.put(Modifier.PROTECTED, new ArrayList<>());
        modifierGroupedAttributes.put(Modifier.PRIVATE, new ArrayList<>());
        modifierGroupedAttributes.put(Modifier.PACKAGE_PRIVATE, new ArrayList<>());
        for (Attribute attribute : cpgClass.getAttributes()) {
            List<Modifier> modList = attribute.modifiers;
            if (modList.contains(Modifier.PUBLIC)) {
                modifierGroupedAttributes.get(Modifier.PUBLIC).add(attribute);
            } else if (modList.contains(Modifier.PROTECTED)) {
                modifierGroupedAttributes.get(Modifier.PROTECTED).add(attribute);
            } else if (modList.contains(Modifier.PRIVATE)) {
                modifierGroupedAttributes.get(Modifier.PRIVATE).add(attribute);
            } else {
                modifierGroupedAttributes.get(Modifier.PACKAGE_PRIVATE).add(attribute);
            }
        }
        return Collections.unmodifiableMap(modifierGroupedAttributes);
    }

    /**
     * Group all the methods present within a class by 'PUBLIC', 'PRIVATE' and 'PACKAGE PRIVATE' modifiers.
     *
     * @param cpgClass The class being analyzed
     * @return A map containing methods grouped by their modifiers
     */
    private static Map<Modifier, List<Method>> groupMethodsByModifiers(CPGClass cpgClass) {
        HashMap<Modifier, ArrayList<Method>> modifierGroupedMethods = new HashMap<>();
        modifierGroupedMethods.put(Modifier.PUBLIC, new ArrayList<>());
        modifierGroupedMethods.put(Modifier.PROTECTED, new ArrayList<>());
        modifierGroupedMethods.put(Modifier.PRIVATE, new ArrayList<>());
        modifierGroupedMethods.put(Modifier.PACKAGE_PRIVATE, new ArrayList<>());
        for (Method method : cpgClass.getMethods()) {
            List<Modifier> modList = method.modifiers;
            if (modList.contains(Modifier.PUBLIC)) {
                modifierGroupedMethods.get(Modifier.PUBLIC).add(method);
            } else if (modList.contains(Modifier.PROTECTED)) {
                modifierGroupedMethods.get(Modifier.PROTECTED).add(method);
            } else if (modList.contains(Modifier.PRIVATE)) {
                modifierGroupedMethods.get(Modifier.PRIVATE).add(method);
            } else {
                modifierGroupedMethods.get(Modifier.PACKAGE_PRIVATE).add(method);
            }
        }
        return Collections.unmodifiableMap(modifierGroupedMethods);
    }

    /**
     * Iterate through the stats of every attribute present within this class and return a total referring to
     * how many times all the attributes belonging to this class were used throughout cpg.
     *
     * @param attributeStats A map containing all the attribute stats for attributes belonging to this class
     * @return An integer value representing the total number of times the attributes of this class were used
     */
    private static int returnTotalAttributeCalls(Map<Attribute, AttributeStat> attributeStats) {
        int[] total = {0};
        attributeStats.forEach((key, value) -> total[0] += value.attributeUsage);
        return total[0];
    }

    /**
     * Iterate through the stats of every method present within this class and return a total referring to how many
     * times all the methods belonging to this class were used throughout cpg.
     *
     * @param methodStats A map containing all the method stats for methods belonging to this class
     * @return An integer value representing the total number of times the methods of this class was used
     */
    private static int returnTotalMethodCalls(Map<Method, MethodStat> methodStats) {
        int[] total = {0};
        methodStats.forEach((key, value) -> total[0] += value.methodUsage);
        return total[0];
    }

    /**
     * Determine the total number of distinct attributes of other classes (including itself) that this
     * class uses throughout its methods.
     *
     * @param methodStats A list containing the stats of every method present within a given class
     * @return A map representing how many distinct attributes of another class were used
     */
    private static Map<CPGClass, Integer> determineTotalClassAttributeCalls(Map<Method, MethodStat> methodStats) {
        Map<CPGClass, Integer> totalClassAttributeCalls = new HashMap<>();
        methodStats
                .forEach((key, value) -> value.distinctAttributeCalls
                        .forEach((attrKey, attrVal) -> totalClassAttributeCalls.put(attrKey,
                                totalClassAttributeCalls.getOrDefault(attrKey, 0) + attrVal.size())));
        return Collections.unmodifiableMap(totalClassAttributeCalls);
    }

    /**
     * Determine the total number of distinct methods of other classes (including itself) that this class uses throughout its
     * methods.
     *
     * @param methodStats A list containing the stats of every method present within a given class
     * @return A map representing how many distinct methods of another class were used
     */
    private static Map<CPGClass, Integer> determineTotalClassMethodCalls(Map<Method, MethodStat> methodStats) {
        Map<CPGClass, Integer> totalClassMethodCalls = new HashMap<>();
        methodStats
                .forEach((key, value) -> value.distinctMethodCalls
                        .forEach((methodKey, methodValue) -> totalClassMethodCalls.put(methodKey,
                                totalClassMethodCalls.getOrDefault(methodKey, 0) + methodValue.size())));
        return Collections.unmodifiableMap(totalClassMethodCalls);
    }


    @Override
    public String toString() {
        return "ClassStat for: " + cpgClass.name;
    }
}
