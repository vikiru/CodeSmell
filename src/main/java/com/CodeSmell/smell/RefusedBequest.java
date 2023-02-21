package com.CodeSmell.smell;

import com.CodeSmell.model.ClassRelation;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.stat.AttributeStat;
import com.CodeSmell.stat.ClassStat;
import com.CodeSmell.stat.MethodStat;
import com.CodeSmell.stat.StatTracker;

import java.util.*;

/**
 * A class that does not use all of its inherited properties from its superclass.
 */
public class RefusedBequest extends Smell {
    public final LinkedList<CodeFragment> detections;

    public RefusedBequest(CodePropertyGraph cpg) {
        super("Refused Bequest", cpg);
        this.detections = new LinkedList<>();
        detectAll(new StatTracker(cpg), detections);
    }

    @Override
    public CodeFragment detectNext() {
        return detections.poll();
    }

    @Override
    public String description() {
        return "A class that does not use all of its inherited properties from its superclass.";
    }

    protected static void detectAll(StatTracker statTracker, LinkedList<CodeFragment> detections) {
        var inheritanceRelations = statTracker.distinctRelations.get(ClassRelation.RelationshipType.INHERITANCE);
        for (CodePropertyGraph.Relation relation : inheritanceRelations) {
            CPGClass subClass = relation.source;
            CPGClass superClass = relation.destination;
            ClassStat subClassStats = statTracker.classStats.get(subClass);
            ClassStat superClassStats = statTracker.classStats.get(superClass);

            int superMethodSize = superClass.getMethods().size();
            int superAttributeSize = superClass.getAttributes().size();
            int superMethodUsage = subClassStats.totalClassMethodCalls.get(superClass);
            int superAttributeUsage = subClassStats.totalClassAttributeCalls.get(superClass);

            if (superMethodUsage != superMethodSize || superAttributeUsage != superAttributeSize) {
                String description = "";
                description += subClass.name + " does not make full use of all the inherited properties of superclass: " +
                        superClass.name;
                CPGClass.Attribute[] unusedAttributes = returnUnusedAttributes(subClass, superClassStats.attributeStats);
                CPGClass.Method[] unusedMethods = returnUnusedMethods(subClass, superClassStats.methodStats);
                CPGClass[] affectedClasses = new CPGClass[]{subClass, superClass};
                detections.add(CodeFragment.makeFragment(description, affectedClasses, unusedAttributes, unusedMethods));
            }
        }
    }

    /**
     * @param subClass
     * @param attributeStats
     * @return
     */
    protected static CPGClass.Attribute[] returnUnusedAttributes(CPGClass subClass, List<AttributeStat> attributeStats) {
        Set<CPGClass.Attribute> unusedAttributes = new HashSet<>();
        attributeStats.stream().
                filter(attributeStat -> attributeStat.classesWhichCallAttr.get(subClass) == 0).
                forEach(attributeStat -> unusedAttributes.add(attributeStat.attribute));
        return unusedAttributes.toArray(new CPGClass.Attribute[0]);
    }

    /**
     * @param subClass
     * @param methodStats
     * @return
     */
    protected static CPGClass.Method[] returnUnusedMethods(CPGClass subClass, List<MethodStat> methodStats) {
        Set<CPGClass.Method> unusedMethods = new HashSet<>();
        methodStats.stream().
                filter(methodStat -> methodStat.classesWhichCallMethod.get(subClass) == 0).
                forEach(methodStat -> unusedMethods.add(methodStat.method));
        return unusedMethods.toArray(new CPGClass.Method[0]);
    }
}
