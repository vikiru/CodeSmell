package com.CodeSmell.smell;

import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.stat.ClassStat;
import com.CodeSmell.stat.MethodStat;
import com.CodeSmell.stat.StatTracker;

import java.util.*;
import java.util.stream.Collectors;

public class InappropriateIntimacy extends Smell {
    public final LinkedList<CodeFragment> detections;

    protected InappropriateIntimacy(CodePropertyGraph cpg) {
        super("Inappropriate Intimacy", cpg);
        this.detections = new LinkedList<>();
        detectAll(new StatTracker(cpg), detections);
    }

    @Override
    public CodeFragment detectNext() {
        return detections.poll();
    }

    @Override
    public String description() {
        return "One classes use the internal fields and methods of another class";
    }

    protected static void detectAll(StatTracker statTracker,
                                    LinkedList<CodeFragment> detections) {
        for (ClassStat classStat : statTracker.classStats.values()) {
            List<CPGClass.Attribute> filteredAttributes = classStat.modifierGroupedAttributes.entrySet().stream().
                    filter(entry -> !entry.getKey().equals(CPGClass.Modifier.PUBLIC)).
                    map(Map.Entry::getValue).flatMap(Collection::stream).collect(Collectors.toList());
            List<CPGClass.Method> getters = returnGetters(classStat.cpgClass, filteredAttributes);
            if (!filteredAttributes.isEmpty() && !getters.isEmpty()) {
                List<MethodStat> filteredMethodStat = classStat.methodStats.stream().
                        filter(methodStat -> getters.contains(methodStat.method)).
                        collect(Collectors.toList());
                CPGClass.Attribute[] affectedAttributes = returnAffectedAttributes(getters);
                CPGClass.Method[] affectedMethods = returnAffectedMethods(classStat.cpgClass, filteredMethodStat);
                CPGClass[] affectedClasses = returnAffectedClasses(classStat.cpgClass, affectedMethods);
                String description = classStat.cpgClass.name + " is exposing its private fields to other classes";
                if (affectedMethods.length > 0 && affectedAttributes.length > 0 && affectedClasses.length > 1) {
                    detections.add(CodeFragment.makeFragment(description, affectedClasses, affectedAttributes, affectedMethods));
                }
            }

        }
    }

    protected static List<CPGClass.Method> returnGetters(CPGClass cpgClass,
                                                         List<CPGClass.Attribute> filteredAttributes) {
        Set<String> attributeTypes = new HashSet<>();
        filteredAttributes.forEach(attribute -> attributeTypes.add(attribute.attributeType));
        return cpgClass.getMethods().stream().
                filter(method -> method.parameters.size() == 0 && attributeTypes.contains(method.returnType) &&
                        method.getMethodCalls().size() == 0 && method.getAttributeCalls().size() == 1
                        && filteredAttributes.contains(method.getAttributeCalls().get(0))).
                collect(Collectors.toList());
    }

    protected static CPGClass.Attribute[] returnAffectedAttributes(List<CPGClass.Method> getters) {
        Set<CPGClass.Attribute> affectedAttributes = new HashSet<>();
        getters.forEach(method -> affectedAttributes.addAll(method.getAttributeCalls()));
        return affectedAttributes.toArray(new CPGClass.Attribute[0]);
    }

    protected static CPGClass.Method[] returnAffectedMethods(CPGClass cpgClass,
                                                             List<MethodStat> filteredMethodStat) {
        Set<CPGClass.Method> affectedMethods = new HashSet<>();
        filteredMethodStat.
                forEach(methodStat -> methodStat.methodsWhichCallMethod.entrySet().stream().
                        filter(entry -> entry.getKey().getParent() != cpgClass
                                && methodStat.methodsWhichCallMethod.get(entry.getKey()) > 0).
                        forEach(entry -> affectedMethods.add(entry.getKey())));
        return affectedMethods.toArray(new CPGClass.Method[0]);
    }

    protected static CPGClass[] returnAffectedClasses(CPGClass cpgClass,
                                                      CPGClass.Method[] affectedMethods) {
        Set<CPGClass> affectedClasses = new HashSet<>();
        affectedClasses.add(cpgClass);
        Arrays.stream(affectedMethods).
                forEach(method -> affectedClasses.add(method.getParent()));
        return affectedClasses.toArray(new CPGClass[0]);
    }
}
