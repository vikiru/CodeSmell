package com.CodeSmell.stat;

import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CPGClass.Attribute;
import com.CodeSmell.parser.CPGClass.Method;

import java.util.*;

public class AttributeStat {
    /**
     * The reference to the attribute that this AttributeStat is referring to
     */
    public final Attribute attribute;
    /**
     * An integer value representing the total number of times this attribute was throughout the methods in cpg
     */
    public final int attributeUsage;
    /**
     * A total count of how many times each method within cpg calls this attribute within their method's instructions
     */
    public final Map<Method, Integer> methodsWhichCallAttr;
    /**
     * A total count of how many times each class within cpg calls this attribute through all of their methods.
     */
    public final Map<CPGClass, Integer> classesWhichCallAttr;

    public AttributeStat(Attribute attribute, Helper helper) {
        this.attribute = attribute;
        this.methodsWhichCallAttr = determineAttributeUsage(attribute, helper);
        this.classesWhichCallAttr = determineClassAttributeUsage(methodsWhichCallAttr, attribute);
        this.attributeUsage = returnTotalUsage(methodsWhichCallAttr);
    }

    /**
     * Determine how many times this attribute was used in each method within cpg and additionally,
     * how many times it was used per class in cpg.
     *
     * @param attribute The attribute being analyzed
     * @param helper    The helper consisting of useful collections of elements within cpg
     */
    protected static Map<Method, Integer> determineAttributeUsage(Attribute attribute, Helper helper) {
        List<Method> allMethods = helper.allMethods;
        Map<Method, Integer> methodsWhichCallAttr = new HashMap<>();
        for (Method method : allMethods) {
            int count = 0;
            if (method.getAttributeCalls().contains(attribute)) {
                count = Math.toIntExact(method.instructions
                        .stream()
                        .filter(instruction -> instruction.label.equals("FIELD_IDENTIFIER")
                                && instruction.code.contains(attribute.name))
                        .count());
            }
            methodsWhichCallAttr.put(method, count);
        }
        return Collections.unmodifiableMap(methodsWhichCallAttr);
    }

    /**
     * Determine the total number of times another class has accessed this attribute via all of its methods combined.
     *
     * @param methodsWhichCallAttr A map representing how many times each method within cpg has called this attribute
     * @return A map representing how many times each class has called this attribute
     */
    private static Map<CPGClass, Integer> determineClassAttributeUsage(Map<Method, Integer> methodsWhichCallAttr, Attribute attribute) {
        Map<CPGClass, Integer> classWhichCallAttr = new HashMap<>();
        methodsWhichCallAttr
                .forEach((key, value) -> classWhichCallAttr.put(key.getParent(),
                        classWhichCallAttr.getOrDefault(key.getParent(), 0) + value));
        classWhichCallAttr.putIfAbsent(attribute.getParent(), 0);
        return Collections.unmodifiableMap(classWhichCallAttr);
    }

    /**
     * Determine the total attribute usage across all methods within cpg for this attribute.
     *
     * @param methodsWhichCallAttr The map indicating how many times each method has called this attribute
     * @return An integer representing the total number of times this attribute was used within cpg
     */
    private static int returnTotalUsage(Map<Method, Integer> methodsWhichCallAttr) {
        final int[] count = {0};
        methodsWhichCallAttr.forEach((key, value) -> count[0] += value);
        return count[0];
    }

    @Override
    public String toString() {
        return "AttributeStat for: " + attribute.toString() + " belonging to: " + attribute.getParent().name;
    }
}
