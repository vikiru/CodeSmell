package com.CodeSmell.stat;

import com.CodeSmell.model.ClassRelation;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;

import java.util.*;
import java.util.stream.Collectors;

public class ClassStat {
    /**
     * The reference to the class that this ClassStat is providing statistics on
     */
    public final CPGClass cpgClass;
    /**
     * A list containing all the attribute stats of a given class
     */
    public final List<AttributeStat> attributeStats;
    /**
     * A list containing all the method stats of a given class
     */
    public final List<MethodStat> methodStats;
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
     * Groups all the attributes of a given class by "PUBLIC", "PRIVATE" and "PACKAGE PRIVATE" modifiers.
     */
    public final Map<CPGClass.Modifier, List<CPGClass.Attribute>> modifierGroupedAttributes;
    /**
     * Groups all the methods of a given class by "PUBLIC", "PRIVATE" and "PACKAGE PRIVATE" modifiers.
     */
    public final Map<CPGClass.Modifier, List<CPGClass.Method>> modifierGroupedMethods;
    /**
     * The total distinct attribute calls that this class makes to other classes (including itself) via its methods.
     */
    public final Map<CPGClass, Integer> totalClassAttributeCalls;
    /**
     * The total distinct method calls that this class makes to other classes (including itself) via its methods.
     */
    public final Map<CPGClass, Integer> totalClassMethodCalls;

    public ClassStat(CPGClass cpgClass, CodePropertyGraph cpg, Helper helper) {
        this.cpgClass = cpgClass;
        this.attributeStats = createAttributeStat(cpgClass, helper);
        this.methodStats = createMethodStat(cpgClass, cpg, helper);
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
     * Create AttributeStats for every attribute within a CPGClass.
     *
     * @param cpgClass The class belonging to this ClassStat
     * @param helper   The helper containing useful collections of elements within cpg
     * @return A list of stats pertaining to every attribute belonging to this class
     */
    private static List<AttributeStat> createAttributeStat(CPGClass cpgClass, Helper helper) {
        List<AttributeStat> attributeStats = new ArrayList<>();
        cpgClass.getAttributes().forEach(attribute -> attributeStats.add(new AttributeStat(attribute, helper)));
        return Collections.unmodifiableList(attributeStats);
    }

    /**
     * Create MethodStats for every method within a CPGClass.
     *
     * @param cpgClass The class belonging to this ClassStat
     * @param cpg      The CodePropertyGraph containing existing classes and relations
     * @param helper   The helper containing useful collections of elements within cpg
     * @return A list of stats pertaining to every method belonging to this class
     */
    private static List<MethodStat> createMethodStat(CPGClass cpgClass, CodePropertyGraph cpg, Helper helper) {
        List<MethodStat> methodStats = new ArrayList<>();
        cpgClass.getMethods().forEach(method -> methodStats.add(new MethodStat(method, cpg, helper)));
        return Collections.unmodifiableList(methodStats);
    }

    /**
     * Determine how many times a given CPGClass was used throughout the cpg as an attribute type,
     * parameter type, the destination of an inheritance or realization relationship, the amount of times
     * its attributes and methods were called.
     *
     * <p>
     * Additionally, maintain a more detailed overview of this via usageMap.
     * </p>
     */
    private static Map<String, Integer> determineClassUsage(CPGClass cpgClass, CodePropertyGraph cpg, Helper helper,
                                                            List<AttributeStat> attributeStats,
                                                            List<MethodStat> methodStats) {
        Map<String, Integer> usageMap = new HashMap<>();
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
        var filePathResult = cpg.getClasses().stream().
                filter(cpgToFind -> cpgToFind.filePath.equals(cpgClass.filePath)).
                collect(Collectors.toList());
        boolean isRoot = cpgClass.filePath.contains(cpgClass.name);
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
    private static Map<CPGClass.Modifier, List<CPGClass.Attribute>> groupAttributesByModifiers(CPGClass cpgClass) {
        Map<CPGClass.Modifier, ArrayList<CPGClass.Attribute>> modifierGroupedAttributes = new HashMap<>();
        modifierGroupedAttributes.put(CPGClass.Modifier.PUBLIC, new ArrayList<>());
        modifierGroupedAttributes.put(CPGClass.Modifier.PRIVATE, new ArrayList<>());
        modifierGroupedAttributes.put(CPGClass.Modifier.PACKAGE_PRIVATE, new ArrayList<>());
        for (CPGClass.Attribute attribute : cpgClass.getAttributes()) {
            List<CPGClass.Modifier> modList = attribute.modifiers;
            if (modList.contains(CPGClass.Modifier.PUBLIC)) {
                modifierGroupedAttributes.get(CPGClass.Modifier.PUBLIC).add(attribute);
            } else if (modList.contains(CPGClass.Modifier.PRIVATE)) {
                modifierGroupedAttributes.get(CPGClass.Modifier.PRIVATE).add(attribute);
            } else {
                modifierGroupedAttributes.get(CPGClass.Modifier.PACKAGE_PRIVATE).add(attribute);
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
    private static Map<CPGClass.Modifier, List<CPGClass.Method>> groupMethodsByModifiers(CPGClass cpgClass) {
        HashMap<CPGClass.Modifier, ArrayList<CPGClass.Method>> modifierGroupedMethods = new HashMap<>();
        modifierGroupedMethods.put(CPGClass.Modifier.PUBLIC, new ArrayList<>());
        modifierGroupedMethods.put(CPGClass.Modifier.PRIVATE, new ArrayList<>());
        modifierGroupedMethods.put(CPGClass.Modifier.PACKAGE_PRIVATE, new ArrayList<>());
        for (CPGClass.Method method : cpgClass.getMethods()) {
            List<CPGClass.Modifier> modList = method.modifiers;
            if (modList.contains(CPGClass.Modifier.PUBLIC)) {
                modifierGroupedMethods.get(CPGClass.Modifier.PUBLIC).add(method);
            } else if (modList.contains(CPGClass.Modifier.PRIVATE)) {
                modifierGroupedMethods.get(CPGClass.Modifier.PRIVATE).add(method);
            } else {
                modifierGroupedMethods.get(CPGClass.Modifier.PACKAGE_PRIVATE).add(method);
            }
        }
        return Collections.unmodifiableMap(modifierGroupedMethods);
    }

    /**
     * Iterate through the stats of every attribute present within this class and return a total referring to
     * how many times all the attributes belonging to this class were used throughout cpg.
     *
     * @param attributeStats A list containing all the attribute stats for attributes belonging to this class
     * @return An integer value representing the total number of times the attributes of this class were used
     */
    private static int returnTotalAttributeCalls(List<AttributeStat> attributeStats) {
        int[] total = {0};
        attributeStats.forEach(attribute -> total[0] += attribute.attributeUsage);
        return total[0];
    }

    /**
     * Iterate through the stats of every method present within this class and return a total referring to how many
     * times all the methods belonging to this class were used throughout cpg.
     *
     * @param methodStats A list containing all the method stats for methods belonging to this class
     * @return An integer value representing the total number of times the methods of this class was used
     */
    private static int returnTotalMethodCalls(List<MethodStat> methodStats) {
        int[] total = {0};
        methodStats.forEach(method -> total[0] += method.methodUsage);
        return total[0];
    }

    /**
     * Determine the total number of distinct attributes of other classes (including itself) that this
     * class uses throughout its methods.
     *
     * @param methodStats A list containing the stats of every method present within a given class
     * @return A map representing how many distinct attributes of another class were used
     */
    private static Map<CPGClass, Integer> determineTotalClassAttributeCalls(List<MethodStat> methodStats) {
        Map<CPGClass, Integer> totalClassAttributeCalls = new HashMap<>();
        methodStats.
                forEach(methodStat -> methodStat.distinctAttributeCalls.
                        forEach((key, value) -> totalClassAttributeCalls.put(key,
                                totalClassAttributeCalls.getOrDefault(key, 0) + value.size())));
        return Collections.unmodifiableMap(totalClassAttributeCalls);
    }

    /**
     * Determine the total number of distinct methods of other classes (including itself) that this class uses throughout its
     * methods.
     *
     * @param methodStats A list containing the stats of every method present within a given class
     * @return A map representing how many distinct methods of another class were used
     */
    private static Map<CPGClass, Integer> determineTotalClassMethodCalls(List<MethodStat> methodStats) {
        Map<CPGClass, Integer> totalClassMethodCalls = new HashMap<>();
        methodStats.
                forEach(methodStat -> methodStat.distinctMethodCalls.
                        forEach((key, value) -> totalClassMethodCalls.put(key,
                                totalClassMethodCalls.getOrDefault(key, 0) + value.size())));
        return Collections.unmodifiableMap(totalClassMethodCalls);
    }


    @Override
    public String toString() {
        return "ClassStat for: " + cpgClass.name;
    }
}
