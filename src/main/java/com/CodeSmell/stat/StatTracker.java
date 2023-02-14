package com.CodeSmell.stat;

import com.CodeSmell.model.ClassRelation;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A class that is meant to contain potentially useful statistics that can aid in the detection of code smells and
 * for general use case purposes including determining potential bugs within the CodeSmell tool itself.
 */
public final class StatTracker {
    public final Helper helper;

    /**
     * Group all {@link CPGClass} by their classType.
     */
    public final TreeMap<String, ArrayList<CPGClass>> distinctClassTypes = new TreeMap<>();

    /**
     * Group all the relations present within cpg by their {@link com.CodeSmell.model.ClassRelation.RelationshipType}
     */
    public final HashMap<ClassRelation.RelationshipType, ArrayList<CodePropertyGraph.Relation>> distinctRelations = new HashMap<>();

    /**
     * Group all the {@link CPGClass} with a {@link ClassStat} containing statistics about that class and its
     * attributes and methods via {@link AttributeStat} and {@link MethodStat} respectively
     */
    public final HashMap<CPGClass, ClassStat> classStats = new HashMap<>();

    public final HashMap<String, Integer> packageUse = new HashMap<>();
    public final ArrayList<CPGClass.Method> longParameterMethod = new ArrayList<>();
    public final ArrayList<CPGClass.Method> longMethods = new ArrayList<>();

    public StatTracker(CodePropertyGraph cpg) {
        helper = new Helper(cpg);
        createStatObjects(cpg, helper, classStats);
        determineDistinctClassTypes(cpg, distinctClassTypes);
        determineDistinctRelations(cpg, distinctRelations);
        determinePackageUsage(classStats, packageUse);
        findLongParameterMethods(helper, longParameterMethod, 4);
        findLongMethods(helper, longMethods, 30);
    }

    /**
     * Iterate through the cpg to create stat objects corresponding to elements within the graph.
     *
     * @param cpg        The CodePropertyGraph containing all existing classes and relations
     * @param helper     The helper consisting of useful collections of elements within cpg
     * @param classStats A map containing the statistics of each class within cpg
     */
    private static void createStatObjects(CodePropertyGraph cpg, Helper helper, HashMap<CPGClass, ClassStat> classStats) {
        for (CPGClass cpgClass : cpg.getClasses()) {
            classStats.put(cpgClass, new ClassStat(cpgClass, cpg, helper));
        }
    }

    /**
     * Iterates through the cpg and groups all relations by RelationshipType.
     *
     * @param cpg               The CodePropertyGraph containing all existing classes and relations
     * @param distinctRelations The map containing all the relations grouped by their type
     */
    private static void determineDistinctRelations(CodePropertyGraph cpg,
                                                   HashMap<ClassRelation.RelationshipType,
                                                           ArrayList<CodePropertyGraph.Relation>> distinctRelations) {
        for (ClassRelation.RelationshipType relationshipType : ClassRelation.RelationshipType.values()) {
            var allMatchingRelations = cpg.getRelations().stream().
                    filter(relation -> relation.type.equals(relationshipType)).collect(Collectors.toList());
            distinctRelations.put(relationshipType, (ArrayList<CodePropertyGraph.Relation>) allMatchingRelations);
        }
    }

    /**
     * Iterates through the cpg to determine all the distinct class types that are present such as
     * abstract class, class, enum, interface.
     *
     * @param cpg                The CodePropertyGraph containing all existing classes and relations
     * @param distinctClassTypes The map containing all the classes grouped by their type
     */
    private static void determineDistinctClassTypes(CodePropertyGraph cpg,
                                                    TreeMap<String, ArrayList<CPGClass>> distinctClassTypes) {
        for (CPGClass cpgClass : cpg.getClasses()) {
            String classType = cpgClass.classType;
            distinctClassTypes.putIfAbsent(classType, new ArrayList<>());
            distinctClassTypes.get(classType).add(cpgClass);
        }
    }

    private static void determinePackageUsage(HashMap<CPGClass, ClassStat> classStats, HashMap<String, Integer> packageUse) {
        ArrayList<ClassStat> classStatVals = new ArrayList<>(classStats.values());
        classStatVals.forEach(classStat ->
                packageUse.put(classStat.cpgClass.packageName,
                        packageUse.getOrDefault((classStat.cpgClass.packageName), 0) + classStat.classUsage));
    }

    private static void findLongParameterMethods(Helper helper, ArrayList<CPGClass.Method> longParameterMethod, int limit) {
        var longParamResult = helper.allMethods.stream().
                filter(method -> method.parameters.size() >= limit).distinct().
                collect(Collectors.toList());
        longParameterMethod.addAll(longParamResult);
    }

    private static void findLongMethods(Helper helper, ArrayList<CPGClass.Method> longMethods, int limit) {
        var longMethodsResult = helper.allMethods.stream().
                filter(method -> method.totalMethodLength >= limit).distinct().
                collect(Collectors.toList());
        longMethods.addAll(longMethodsResult);
    }

}