package com.CodeSmell.smell;

import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.stat.AttributeStat;
import com.CodeSmell.stat.ClassStat;
import com.CodeSmell.stat.StatTracker;

import java.util.*;
import java.util.stream.Collectors;

/**
 * An Orphan Variable/Constant Class can be defined as a collection of constants
 * that belong elsewhere than the class they are defined within.
 */
public class OrphanVariable extends Smell {
    public final LinkedList<CodeFragment> detections;

    public OrphanVariable(CodePropertyGraph cpg) {
        super("Orphan Variable", cpg);
        this.detections = new LinkedList<>();
        detectAll(new StatTracker(cpg), detections);
    }

    @Override
    public CodeFragment detectNext() {
        return detections.poll();
    }

    @Override
    public String description() {
        return "A collection of constants that belong in a different class than the class they are defined within";
    }

    protected static void detectAll(StatTracker statTracker, LinkedList<CodeFragment> detections) {
        List<String> primitiveTypeList = List.of(new String[]{"boolean", "byte", "char", "double",
                "float", "int", "long", "short"});
        for (ClassStat classStat : statTracker.classStats.values()) {
            List<CPGClass.Attribute> classConstants = returnClassConstants(classStat.cpgClass, primitiveTypeList);
            if (!classConstants.isEmpty()) {
                List<AttributeStat> classConstantStats = classStat.attributeStats.stream().
                        filter(attributeStat -> classConstants.contains(attributeStat.attribute)).
                        collect(Collectors.toList());
                List<AttributeStat> filteredAttributeStats = returnFilteredStats(classStat.cpgClass, classConstantStats);
                List<CPGClass.Attribute> filteredAttributes = new ArrayList<>();
                filteredAttributeStats.forEach(attributeStat -> filteredAttributes.add(attributeStat.attribute));
                CPGClass[] affectedClasses = returnAffectedClasses(classStat.cpgClass, filteredAttributeStats);
                if (affectedClasses.length > 1) {
                    CPGClass.Attribute[] affectedAttributes = filteredAttributes.toArray(new CPGClass.Attribute[0]);
                    String description = classStat.cpgClass + " has a collection of constants that belong elsewhere.";
                    detections.add(CodeFragment.makeFragment(description, affectedClasses, affectedAttributes));
                }
            }
        }
    }

    /**
     * Given a CPGClass, return a list of attributes matching the conditions necessary to be considered a constant
     * within that class.
     *
     * <br><br>
     * Such conditions include being of a primitive type and possessing public static final modifiers.
     *
     * @param cpgClass          The class being analyzed
     * @param primitiveTypeList A list containing Strings matching all primitive types within Java
     * @return A list of attributes matching the conditions needed to be a constant
     */
    protected static List<CPGClass.Attribute> returnClassConstants(CPGClass cpgClass,
                                                                   List<String> primitiveTypeList) {
        return cpgClass.getAttributes().stream().
                filter(attribute -> attribute.modifiers.contains(CPGClass.Modifier.PUBLIC) &&
                        attribute.modifiers.contains(CPGClass.Modifier.STATIC) &&
                        attribute.modifiers.contains(CPGClass.Modifier.FINAL) &&
                        primitiveTypeList.contains(attribute.attributeType)).collect(Collectors.toList());
    }

    /**
     * Return a list of AttributeStats such that the class that owns these Attributes does not use them at all
     * throughout its methods.
     *
     * @param cpgClass       The class being analyzed
     * @param attributeStats A list of all the stats pertaining to all the attributes present within the class
     * @return A list of filtered AttributeStats in which the class does not use these attributes
     */
    protected static List<AttributeStat> returnFilteredStats(CPGClass cpgClass,
                                                             List<AttributeStat> attributeStats) {
        return attributeStats.stream().
                filter(attributeStat -> attributeStat.classesWhichCallAttr.get(cpgClass) == 0).
                collect(Collectors.toList());
    }

    /**
     * Return an array consisting of all the classes (including the class that owns the constants) which
     * make use of the constants.
     *
     * @param cpgClass               The class possessing the constants
     * @param filteredAttributeStats The stats pertaining to the constants
     * @return An array of all the classes which make use of the constants and additionally the class that owns the
     * constants but does not use them
     */
    protected static CPGClass[] returnAffectedClasses(CPGClass cpgClass,
                                                      List<AttributeStat> filteredAttributeStats) {
        Set<CPGClass> affectedClasses = new HashSet<>();
        affectedClasses.add(cpgClass);
        for (AttributeStat attributeStat : filteredAttributeStats) {
            Map<CPGClass, Integer> classesWhichCallAttr = attributeStat.classesWhichCallAttr;
            classesWhichCallAttr.entrySet().stream().
                    filter(entry -> classesWhichCallAttr.get(entry.getKey()) > 0 && entry.getKey() != cpgClass).
                    forEach(entry -> affectedClasses.add(entry.getKey()));
        }
        return affectedClasses.toArray(new CPGClass[0]);
    }
}