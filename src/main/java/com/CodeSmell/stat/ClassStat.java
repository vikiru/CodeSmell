package com.CodeSmell.stat;

import com.CodeSmell.model.ClassRelation;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;

import java.util.*;
import java.util.stream.Collectors;

public final class ClassStat {
    /**
     * The reference to the class that this ClassStat is providing statistics on
     */
    public final CPGClass cpgClass;
    /**
     * The total number of times this class was used within cpg
     */
    public final int classUsage;
    /**
     * A detailed overview indicating how the classUsage was determined by showing how many times
     * a class was used as a parameter type, inheritance or realization relation target, how many times it was used
     * as an attribute type, and how many times its attributes and methods were called
     */
    public final Map<String, Integer> usageMap = new HashMap<>();
    /**
     * The total number of non-empty lines present within a file (excludes package and import statements)
     */
    public final int totalClassLines;
    /**
     * A detailed overview indicating how totalClassLines was determined showing how many lines were associated with
     * attributes, methods, class declaration, and all nested classes
     */
    public final Map<String, Integer> classLineMap = new HashMap<>();
    /**
     * Groups all the attributes of a given class by "PUBLIC", "PRIVATE" and "PACKAGE PRIVATE" modifiers.
     */
    public final Map<CPGClass.Modifier, ArrayList<CPGClass.Attribute>> modifierGroupedAttributes;
    /**
     * Groups all the methods of a given class by "PUBLIC", "PRIVATE" and "PACKAGE PRIVATE" modifiers.
     */
    public final Map<CPGClass.Modifier, ArrayList<CPGClass.Method>> modifierGroupedMethods;
    /**
     * A list containing all the attribute stats of a given class
     */
    public final ArrayList<AttributeStat> attributeStats = new ArrayList<>();
    /**
     * A list containing all the method stats of a given class
     */
    public final ArrayList<MethodStat> methodStats = new ArrayList<>();

    public ClassStat(CPGClass cpgClass, CodePropertyGraph cpg, Helper helper) {
        this.cpgClass = cpgClass;
        this.classUsage = returnTotalUsage(cpgClass, cpg, helper, attributeStats, methodStats, usageMap);
        this.totalClassLines = determineTotalClassLines(cpgClass, cpg, classLineMap);
        this.modifierGroupedAttributes = groupAttributesByModifiers(cpgClass);
        this.modifierGroupedMethods = groupMethodsByModifiers(cpgClass);
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
    private static int returnTotalUsage(CPGClass cpgClass, CodePropertyGraph cpg, Helper helper,
                                        ArrayList<AttributeStat> attributeStats,
                                        ArrayList<MethodStat> methodStats,
                                        Map<String, Integer> usageMap) {
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
        final int[] classUsage = {0};
        usageMap.values().forEach(value -> classUsage[0] += value);
        return classUsage[0];
    }

    /**
     * Given a CPGClass object, return an int representing the total lines present within that file. Accounting for
     * nested classes and classes that inherit from a superclass. Additionally, update the classLineMap with a detailed
     * overview of how the final returned value was calculated.
     *
     * @param cpgClass     - The class being analyzed
     * @param cpg          - The CodePropertyGraph containing the classes present in the codebase
     * @param classLineMap - A hashmap containing string keys and integer values representing the total contribution that
     *                     each key makes to the final returned integer value from this method
     * @return An integer value representing the total amount of non-empty lines present within a class.
     */
    private static int determineTotalClassLines(CPGClass cpgClass,
                                                CodePropertyGraph cpg,
                                                Map<String, Integer> classLineMap) {
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

    /**
     * Group all the attributes present within a class by 'PUBLIC', 'PRIVATE' and 'PACKAGE PRIVATE' modifiers.
     *
     * @param cpgClass The class being analyzed
     * @return
     */
    private static Map<CPGClass.Modifier, ArrayList<CPGClass.Attribute>> groupAttributesByModifiers(CPGClass cpgClass) {
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
     */
    private static Map<CPGClass.Modifier, ArrayList<CPGClass.Method>> groupMethodsByModifiers(CPGClass cpgClass) {
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
    private static int returnTotalAttributeCalls(ArrayList<AttributeStat> attributeStats) {
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
    private static int returnTotalMethodCalls(ArrayList<MethodStat> methodStats) {
        int[] total = {0};
        methodStats.forEach(method -> total[0] += method.methodUsage);
        return total[0];
    }

    @Override
    public String toString() {
        return "ClassStat for: " + cpgClass.name;
    }
}
