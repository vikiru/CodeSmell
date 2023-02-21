package com.CodeSmell.stat;

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
    /**
     * A helper object consisting of potentially useful collections of elements withing cpg such as
     * all attributes, all methods, method parameters, method calls, attribute calls, and names of classes,
     * attributes and methods within cpg.
     */
    public final Helper helper;
    /**
     * Group all {@link CPGClass} by their classType.
     */
    public final Map<String, List<CPGClass>> distinctClassTypes;
    /**
     * Group all the relations present within cpg by their {@link com.CodeSmell.model.ClassRelation.RelationshipType}
     */
    public final Map<ClassRelation.RelationshipType, List<CodePropertyGraph.Relation>> distinctRelations;
    /**
     * Group all the {@link CPGClass} with a {@link ClassStat} containing statistics about that class and its
     * attributes and methods via {@link AttributeStat} and {@link MethodStat} respectively
     */
    public final Map<CPGClass, ClassStat> classStats;
    /**
     * Group all the {@link CPGClass.Attribute} with a {@link AttributeStat} containing statistics about that
     * attribute
     */
    public final Map<CPGClass.Attribute, AttributeStat> attributeStats;
    /**
     * Group all the {@link CPGClass.Method} with a {@link MethodStat} containing statistics about that method
     */
    public final Map<CPGClass.Method, MethodStat> methodStats;
    /**
     * Group all classes to their respective packages and maintain a sum of the usage of each class within that package
     * as the total package usage for that package
     */
    public final Map<String, Integer> packageUse;
    /**
     * Group all methods with a parameter length being greater than a provided limit, as of this moment - 4 parameters.
     */
    public final List<CPGClass.Method> longParameterMethod;
    /**
     * Group all methods with a total method length being greater than a provided limit, as of this moment - 30 lines.
     */
    public final List<CPGClass.Method> longMethods;

    public StatTracker(CodePropertyGraph cpg) {
        helper = new Helper(cpg);
        this.distinctClassTypes = determineDistinctClassTypes(cpg);
        this.distinctRelations = determineDistinctRelations(cpg);
        this.classStats = createClassStats(cpg, helper);
        this.attributeStats = createAttributeStats(classStats);
        this.methodStats = createMethodStats(classStats);
        this.packageUse = determinePackageUsage(classStats);
        this.longParameterMethod = findLongParameterMethods(helper, 4);
        this.longMethods = findLongMethods(helper, 30);
    }

    /**
     * Iterates through the cpg to determine all the distinct class types that are present such as
     * abstract class, class, enum, interface.
     *
     * @param cpg The CodePropertyGraph containing all existing classes and relations
     * @return A map containing all the distinct class types within cpg
     */
    private static Map<String, List<CPGClass>> determineDistinctClassTypes(CodePropertyGraph cpg) {
        Map<String, List<CPGClass>> distinctClassTypes = new TreeMap<>();
        for (CPGClass cpgClass : cpg.getClasses()) {
            String classType = cpgClass.classType;
            distinctClassTypes.putIfAbsent(classType, new ArrayList<>());
            distinctClassTypes.get(classType).add(cpgClass);
        }
        return Collections.unmodifiableMap(distinctClassTypes);
    }

    /**
     * Iterates through the cpg and groups all relations by RelationshipType.
     *
     * @param cpg The CodePropertyGraph containing all existing classes and relations
     * @return A map containing all the distinct relations within cpg
     */
    private static Map<ClassRelation.RelationshipType, List<CodePropertyGraph.Relation>> determineDistinctRelations(CodePropertyGraph cpg) {
        Map<ClassRelation.RelationshipType, List<CodePropertyGraph.Relation>> distinctRelations = new HashMap<>();
        for (ClassRelation.RelationshipType relationshipType : ClassRelation.RelationshipType.values()) {
            var allMatchingRelations = cpg.getRelations().stream().
                    filter(relation -> relation.type.equals(relationshipType)).collect(Collectors.toList());
            distinctRelations.put(relationshipType, allMatchingRelations);
        }
        return Collections.unmodifiableMap(distinctRelations);
    }

    /**
     * Iterate through the cpg to create stat objects corresponding to elements within the graph.
     *
     * @param cpg    The CodePropertyGraph containing all existing classes and relations
     * @param helper The helper consisting of useful collections of elements within cpg
     * @return A map containing a stat object for every class
     */
    private static Map<CPGClass, ClassStat> createClassStats(CodePropertyGraph cpg, Helper helper) {
        Map<CPGClass, ClassStat> classStats = new HashMap<>();
        for (CPGClass cpgClass : cpg.getClasses()) {
            classStats.put(cpgClass, new ClassStat(cpgClass, cpg, helper));
        }
        return Collections.unmodifiableMap(classStats);
    }

    /**
     * @param classStats
     * @return
     */
    private static Map<CPGClass.Attribute, AttributeStat> createAttributeStats(Map<CPGClass, ClassStat> classStats) {
        Map<CPGClass.Attribute, AttributeStat> attributeStats = new HashMap<>();
        classStats.values().forEach(classStat -> attributeStats.putAll(classStat.attributeStats));
        return Collections.unmodifiableMap(attributeStats);
    }

    /**
     * @param classStats
     * @return
     */
    private static Map<CPGClass.Method, MethodStat> createMethodStats(Map<CPGClass, ClassStat> classStats) {
        Map<CPGClass.Method, MethodStat> methodStats = new HashMap<>();
        classStats.values().forEach(classStat -> methodStats.putAll(classStat.methodStats));
        return Collections.unmodifiableMap(methodStats);
    }

    /**
     * Determine the usage of each package by summing the class usage of every class within that package.
     *
     * @param classStats A map containing a class stat for every class
     * @return A map indicating how many times each package was used
     */
    private static Map<String, Integer> determinePackageUsage(Map<CPGClass, ClassStat> classStats) {
        Map<String, Integer> packageUse = new HashMap<>();
        List<ClassStat> classStatVals = new ArrayList<>(classStats.values());
        classStatVals.forEach(classStat ->
                packageUse.put(classStat.cpgClass.packageName,
                        packageUse.getOrDefault((classStat.cpgClass.packageName), 0) + classStat.classUsage));
        return Collections.unmodifiableMap(packageUse);
    }

    /**
     * Group all methods with a total number of parameters greater than or equal to a specified limit value into a single list.
     *
     * @param helper The helper consisting of useful collections of elements within cpg
     * @param limit  The minimum number of parameters a method should have to be considered a long parameter method
     * @return A list of all methods within cpg that satisfy this criteria
     */
    private static List<CPGClass.Method> findLongParameterMethods(Helper helper, int limit) {
        return helper.allMethods.stream().
                filter(method -> method.parameters.size() >= limit).distinct().collect(Collectors.toUnmodifiableList());
    }

    /**
     * Group all methods with a total method length greater than or equal to a specified limit value into a single list.
     *
     * @param helper The helper consisting of useful collections of elements within cpg
     * @param limit  The minimum length that a method should be to be considered a long method
     * @return A list of methods within cpg that satisfy this criteria
     */
    private static List<CPGClass.Method> findLongMethods(Helper helper, int limit) {
        return helper.allMethods.stream().
                filter(method -> method.totalMethodLength >= limit).distinct().collect(Collectors.toUnmodifiableList());
    }

}