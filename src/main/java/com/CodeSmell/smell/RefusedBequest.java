package com.CodeSmell.smell;

import com.CodeSmell.model.ClassRelation;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CPGClass.Attribute;
import com.CodeSmell.parser.CPGClass.Method.*;
import com.CodeSmell.parser.CPGClass.*;
import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.parser.CodePropertyGraph.*;
import com.CodeSmell.stat.AttributeStat;
import com.CodeSmell.stat.MethodStat;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A class that does not use all of its inherited properties from its superclass.
 */
public class RefusedBequest extends Smell {
    public final LinkedList<CodeFragment> detections;

    public RefusedBequest(CodePropertyGraph cpg) {
        super("Refused Bequest", cpg);
        this.detections = new LinkedList<>();
        detectAll(detections);
    }

    @Override
    public CodeFragment detectNext() {
        return detections.poll();
    }

    @Override
    public String description() {
        return "A class that does not use all of its inherited properties from its superclass.";
    }

    @Override
    public LinkedList<CodeFragment> getDetections() {
        return this.detections;
    }

    /**
     * @param detections
     */
    protected static void detectAll(LinkedList<CodeFragment> detections) {
        StringBuilder sb = new StringBuilder();
        Map<CPGClass, List<CPGClass>> superToSubClasses = returnSuperToSubClasses(Common.stats.distinctRelations.get(ClassRelation.RelationshipType.INHERITANCE));
        for (Map.Entry<CPGClass, List<CPGClass>> entry : superToSubClasses.entrySet()) {
            CPGClass superClass = entry.getKey();
            List<CPGClass> subClasses = entry.getValue();
            for (CPGClass subClass : subClasses) {
                CPGClass[] affectedClasses = new CPGClass[]{subClass, superClass};
                Method[] affectedMethods = returnAffectedMethods(subClass, superClass);
                Attribute[] affectedAttributes = returnAffectedAttributes(subClass, superClass);
                sb.append(subClass.name).append(" does not make full use of its inherited properties from: ").append(superClass.name);
                String description = sb.toString();
                CodeFragment codeFragment = CodeFragment.makeFragment(description, affectedClasses, affectedMethods,
                        new Modifier[0], affectedAttributes, new Parameter[0], new Instruction[0]);
                detections.add(codeFragment);
            }
        }
    }

    /**
     * @param subClass
     * @param superClass
     * @return
     */
    protected static boolean hasUnusedProperties(CPGClass subClass, CPGClass superClass) {
        Set<Method> allMethods = new HashSet<>();
        Set<Attribute> allAttributes = new HashSet<>();
        int parentAttributeSize = Math.toIntExact(superClass.getAttributes()
                .stream()
                .filter(attribute -> attribute.getParent().equals(superClass))
                .count());
        int parentMethodSize = Math.toIntExact(superClass.getMethods()
                .stream()
                .filter(method -> method.getParent().equals(superClass))
                .count());
        int parentTotal = parentAttributeSize + parentMethodSize;

        List<Method> subClassMethods = subClass.getMethods()
                .stream()
                .filter(method -> method.getParent().equals(subClass))
                .collect(Collectors.toList());
        subClassMethods.forEach(method -> method.getMethodCalls()
                .stream()
                .filter(methodCall -> methodCall.getParent().equals(superClass))
                .forEach(allMethods::add));
        subClassMethods.forEach(method -> method.getAttributeCalls()
                .stream()
                .filter(attributeCall -> attributeCall.getParent().equals(superClass))
                .forEach(allAttributes::add));
        int subClassTotal = allAttributes.size() + allMethods.size();

        return subClassTotal < parentTotal;
    }

    /**
     * @param inheritanceRelations
     * @return
     */
    private static Map<CPGClass, List<CPGClass>> returnSuperToSubClasses(List<Relation> inheritanceRelations) {
        Map<CPGClass, List<CPGClass>> superToSubClasses = new HashMap<>();
        for (Relation inheritanceRelation : inheritanceRelations) {
            CPGClass subClass = inheritanceRelation.source;
            CPGClass superClass = inheritanceRelation.destination;
            if (hasUnusedProperties(subClass, superClass)) {
                superToSubClasses.putIfAbsent(superClass, new ArrayList<>());
                superToSubClasses.get(superClass).add(subClass);
            }
        }
        return Collections.unmodifiableMap(superToSubClasses);
    }

    private static Attribute[] returnAffectedAttributes(CPGClass subClass, CPGClass superClass) {
        List<Attribute> affectedAttributes = new ArrayList<>();
        List<Attribute> superAttributes = superClass.getAttributes();
        Map<Attribute, AttributeStat> attributeStats = Common.stats.attributeStats;
        for (Attribute attribute : superAttributes) {
            AttributeStat attributeStat = attributeStats.get(attribute);
            if (attributeStat.classesWhichCallAttr.get(subClass) == 0) {
                affectedAttributes.add(attribute);
            }
        }
        return affectedAttributes.toArray(Attribute[]::new);
    }

    private static Method[] returnAffectedMethods(CPGClass subClass, CPGClass superClass) {
        List<Method> affectedMethods = new ArrayList<>();
        List<Method> superMethods = superClass.getMethods();
        Map<Method, MethodStat> methodStats = Common.stats.methodStats;
        for (Method method : superMethods) {
            MethodStat methodStat = methodStats.get(method);
            if (methodStat.classesWhichCallMethod.get(subClass) == 0) {
                affectedMethods.add(method);
            }
        }
        return affectedMethods.toArray(Method[]::new);
    }
}
