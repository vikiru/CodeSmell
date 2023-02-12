package com.CodeSmell.stat;

import com.CodeSmell.parser.CPGClass;

import java.util.ArrayList;
import java.util.HashMap;

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
    public final HashMap<CPGClass.Method, Integer> methodsWhichCallAttr = new HashMap<>();
    /**
     * A total count of how many times each class within cpg calls this attribute through all of their methods.
     */
    public final HashMap<CPGClass, Integer> classesWhichCallAttr = new HashMap<>();

    public AttributeStat(CPGClass.Attribute attribute, Helper helper) {
        this.attribute = attribute;
        determineAttributeUsage(attribute, helper, methodsWhichCallAttr, classesWhichCallAttr);
        this.attributeUsage = returnTotalUsage(methodsWhichCallAttr);
    }

    /**
     * Determine how many times this attribute was used in each method within cpg and additionally,
     * how many times it was used per class in cpg.
     *
     * @param attribute            The attribute being analyzed
     * @param helper               The helper consisting of useful collections of elements within cpg
     * @param methodsWhichCallAttr The map indicating how many times each method has called this attribute
     * @param classWhichCallAttr   The map indicating how many times each class has called this attribute
     */
    private static void determineAttributeUsage(CPGClass.Attribute attribute,
                                                Helper helper,
                                                HashMap<CPGClass.Method, Integer> methodsWhichCallAttr,
                                                HashMap<CPGClass, Integer> classWhichCallAttr) {
        ArrayList<CPGClass.Method> allMethods = helper.allMethods;
        for (CPGClass.Method method : allMethods) {
            int count = 0;
            if (method.getAttributeCalls().contains(attribute)) {
                count = Math.toIntExact(method.instructions.stream().
                        filter(instruction -> instruction.label.equals("FIELD_IDENTIFIER")
                                && instruction.code.contains(attribute.name)).count());
            }
            methodsWhichCallAttr.put(method, count);
        }
        methodsWhichCallAttr.forEach((key, value) -> classWhichCallAttr.
                put(key.getParent(), classWhichCallAttr.getOrDefault(key.getParent(), 0) + value));
    }

    /**
     * Determine the total attribute usage across all methods within cpg for this attribute.
     *
     * @param methodsWhichCallAttr The map indicating how many times each method has called this attribute
     * @return An integer representing the total number of times this attribute was used within cpg
     */
    private static int returnTotalUsage(HashMap<CPGClass.Method, Integer> methodsWhichCallAttr) {
        final int[] count = {0};
        methodsWhichCallAttr.forEach((key, value) -> count[0] += value);
        return count[0];
    }

    @Override
    public String toString() {
        return "AttributeStat for: " + attribute.toString() + " belonging to: " + attribute.getParent().name;
    }
}
