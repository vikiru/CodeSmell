package com.CodeSmell.stat;

import com.CodeSmell.parser.CPGClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class AttributeStat {
    /**
     * The reference to the attribute that this AttributeStat is referring to
     */
    public final CPGClass.Attribute attribute;
    /**
     * An integer value representing the total number of times this attribute was throughout the methods in cpg
     */
    public final int attributeUsage;
    /**
     * A total count of how many times each method within cpg calls this attribute within their method's instructions
     */
    public final Map<CPGClass.Method, Integer> methodsWhichCallAttr;
    /**
     * A total count of how many times each class within cpg calls this attribute through all of their methods.
     */
    public final Map<CPGClass, Integer> classesWhichCallAttr;

    public AttributeStat(CPGClass.Attribute attribute, Helper helper) {
        this.attribute = attribute;
        this.methodsWhichCallAttr = determineAttributeUsage(attribute, helper);
        this.classesWhichCallAttr = determineClassAttributeUsage(methodsWhichCallAttr);
        this.attributeUsage = returnTotalUsage(methodsWhichCallAttr);
    }

    /**
     * Determine how many times this attribute was used in each method within cpg and additionally,
     * how many times it was used per class in cpg.
     *
     * @param attribute The attribute being analyzed
     * @param helper    The helper consisting of useful collections of elements within cpg
     */
    private static Map<CPGClass.Method, Integer> determineAttributeUsage(CPGClass.Attribute attribute, Helper helper) {
        ArrayList<CPGClass.Method> allMethods = helper.allMethods;
        Map<CPGClass.Method, Integer> methodsWhichCallAttr = new HashMap<>();
        for (CPGClass.Method method : allMethods) {
            int count = 0;
            if (method.getAttributeCalls().contains(attribute)) {
                count = Math.toIntExact(method.instructions.stream().
                        filter(instruction -> instruction.label.equals("FIELD_IDENTIFIER")
                                && instruction.code.contains(attribute.name)).count());
            }
            methodsWhichCallAttr.put(method, count);
        }
        return Collections.unmodifiableMap(methodsWhichCallAttr);
    }

    private static Map<CPGClass, Integer> determineClassAttributeUsage(Map<CPGClass.Method, Integer> methodsWhichCallAttr) {
        Map<CPGClass, Integer> classWhichCallAttr = new HashMap<>();
        methodsWhichCallAttr.forEach((key, value) -> classWhichCallAttr.
                put(key.getParent(), classWhichCallAttr.getOrDefault(key.getParent(), 0) + value));
        return Collections.unmodifiableMap(classWhichCallAttr);
    }

    /**
     * Determine the total attribute usage across all methods within cpg for this attribute.
     *
     * @param methodsWhichCallAttr The map indicating how many times each method has called this attribute
     * @return An integer representing the total number of times this attribute was used within cpg
     */
    private static int returnTotalUsage(Map<CPGClass.Method, Integer> methodsWhichCallAttr) {
        final int[] count = {0};
        methodsWhichCallAttr.forEach((key, value) -> count[0] += value);
        return count[0];
    }

    @Override
    public String toString() {
        return "AttributeStat for: " + attribute.toString() + " belonging to: " + attribute.getParent().name;
    }
}
