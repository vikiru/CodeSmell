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
public final class StatTracker {
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

    public final Map<String, Integer> packageUse;
    public final List<CPGClass.Method> longParameterMethod;
    public final List<CPGClass.Method> longMethods;

    public StatTracker(CodePropertyGraph cpg) {
        helper = new Helper(cpg);
        this.distinctClassTypes = determineDistinctClassTypes(cpg);
        this.distinctRelations = determineDistinctRelations(cpg);
        this.classStats = createStatObjects(cpg, helper);
        this.packageUse = determinePackageUsage(classStats);
        this.longParameterMethod = findLongParameterMethods(helper, 4);
        this.longMethods = findLongMethods(helper, 30);
    }

    /**
     * Iterates through the cpg to determine all the distinct class types that are present such as
     * abstract class, class, enum, interface.
     *
     * @param cpg The CodePropertyGraph containing all existing classes and relations
     * @return
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
     * @return
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
     * @return
     */
    private static Map<CPGClass, ClassStat> createStatObjects(CodePropertyGraph cpg, Helper helper) {
        Map<CPGClass, ClassStat> classStats = new HashMap<>();
        for (CPGClass cpgClass : cpg.getClasses()) {
            classStats.put(cpgClass, new ClassStat(cpgClass, cpg, helper));
        }
        return Collections.unmodifiableMap(classStats);
    }
    
    private static Map<String, Integer> determinePackageUsage(Map<CPGClass, ClassStat> classStats) {
        Map<String, Integer> packageUse = new HashMap<>();
        List<ClassStat> classStatVals = new ArrayList<>(classStats.values());
        classStatVals.forEach(classStat ->
                packageUse.put(classStat.cpgClass.packageName,
                        packageUse.getOrDefault((classStat.cpgClass.packageName), 0) + classStat.classUsage));
        return Collections.unmodifiableMap(packageUse);
    }

    private static List<CPGClass.Method> findLongParameterMethods(Helper helper, int limit) {
        return helper.allMethods.stream().
                filter(method -> method.parameters.size() >= limit).distinct().collect(Collectors.toUnmodifiableList());
    }

    private static List<CPGClass.Method> findLongMethods(Helper helper, int limit) {
        return helper.allMethods.stream().
                filter(method -> method.totalMethodLength >= limit).distinct().collect(Collectors.toUnmodifiableList());
    }

}