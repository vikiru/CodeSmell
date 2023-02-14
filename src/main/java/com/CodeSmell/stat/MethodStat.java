package com.CodeSmell.stat;

import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

/**
 * MethodStat contains stats relevant to a given method pertaining to how it is used within cpg.
 */
public final class MethodStat {
    /**
     * The reference to the method that this MethodStat is providing information about
     */
    public final CPGClass.Method method;
    /**
     * The total times this method was called within cpg
     */
    public final int methodUsage;
    /**
     * A map containing a count of how many times each method within cpg has called this method, >= 0.
     */
    public final Map<CPGClass.Method, Integer> methodsWhichCallMethod;
    /**
     * A map containing a count of how many times each class within cpg has called this method, >= 0.
     */
    public final Map<CPGClass, Integer> classesWhichCallMethod = new HashMap<>();
    /**
     * A map containing a count of how many times the methods of another class were used within this method
     */
    public final Map<CPGClass, ArrayList<CPGClass.Method>> totalClassMethodCalls;
    /**
     * A map containing a count of how many times the attributes of another class were used within this method
     */
    public final Map<CPGClass, ArrayList<CPGClass.Attribute>> totalClassAttributeCalls;

    /**
     * A map containing a count of how many times each parameter within this method was used, >= 0
     */
    public final Map<CPGClass.Method.Parameter, Integer> parameterUsage;
    /**
     * All the non-duplicated instructions that appear as-is in the .java file for a given method.
     */
    public final List<CPGClass.Method.Instruction> uniqueInstructions;

    public MethodStat(CPGClass.Method method, CodePropertyGraph cpg, Helper helper) {
        this.method = method;
        this.methodsWhichCallMethod = Collections.unmodifiableMap(determineMethodUsage(method, helper, classesWhichCallMethod));
        this.totalClassAttributeCalls = Collections.unmodifiableMap(determineTotalAttributeCalls(method, cpg));
        this.totalClassMethodCalls = Collections.unmodifiableMap(determineTotalMethodCalls(method, cpg));
        this.methodUsage = returnTotalUsage(methodsWhichCallMethod);
        this.parameterUsage = Collections.unmodifiableMap(determineParameterUsage(method));
        this.uniqueInstructions = Collections.unmodifiableList(obtainUniqueInstructions(method, helper));
    }

    /**
     * Determine how many times this method was used by each method within cpg and additionally,
     * how many times each class has called this method.
     *
     * @param method                 The method being analyzed
     * @param helper                 The helper consisting of useful collections of elements within cpg
     * @param classesWhichCallMethod The map indicating how many times each class has called this method
     */
    private static Map<CPGClass.Method, Integer> determineMethodUsage(CPGClass.Method method, Helper helper,
                                                                      Map<CPGClass, Integer> classesWhichCallMethod) {
        Map<CPGClass.Method, Integer> methodsWhichCallMethod = new HashMap<>();
        ArrayList<CPGClass.Method> allMethods = helper.allMethods;
        String toFind = method.getParent().name + "." + method.name;
        for (CPGClass.Method methodInCPG : allMethods) {
            int count = 0;
            if (methodInCPG.getMethodCalls().contains(method)) {
                count = Math.toIntExact(methodInCPG.instructions.stream().
                        filter(instruction -> instruction.methodCall.equals(toFind)).count());
            }
            methodsWhichCallMethod.put(methodInCPG, count);
        }
        methodsWhichCallMethod.forEach((key, value) -> classesWhichCallMethod.
                put(key.getParent(), classesWhichCallMethod.getOrDefault(key.getParent(), 0) + value));
        return methodsWhichCallMethod;
    }

    /**
     * Return an integer value representing the total number of times this method was called across all methods
     * within cpg.
     *
     * @param methodsWhichCallMethod The map indicating how many times each method has called this method
     * @return An integer value representing how many times this method has been called within cpg
     */
    private static int returnTotalUsage(Map<CPGClass.Method, Integer> methodsWhichCallMethod) {
        final int[] count = {0};
        methodsWhichCallMethod.forEach((key, value) -> count[0] += value);
        return count[0];
    }

    private static HashMap<CPGClass, ArrayList<CPGClass.Method>> determineTotalMethodCalls(CPGClass.Method method, CodePropertyGraph cpg) {
        HashMap<CPGClass, ArrayList<CPGClass.Method>> totalMethodClassCalls = new HashMap<>();
        for (CPGClass.Method methodCall : method.getMethodCalls()) {
            totalMethodClassCalls.putIfAbsent(methodCall.getParent(), new ArrayList<>());
            totalMethodClassCalls.get(methodCall.getParent()).add(methodCall);
        }
        cpg.getClasses().forEach(cpgClass -> totalMethodClassCalls.putIfAbsent(cpgClass, new ArrayList<>()));
        return totalMethodClassCalls;
    }

    private static HashMap<CPGClass, ArrayList<CPGClass.Attribute>> determineTotalAttributeCalls(CPGClass.Method method, CodePropertyGraph cpg) {
        HashMap<CPGClass, ArrayList<CPGClass.Attribute>> totalAttributeClassCalls = new HashMap<>();
        for (CPGClass.Attribute attributeCall : method.getAttributeCalls()) {
            totalAttributeClassCalls.putIfAbsent(attributeCall.getParent(), new ArrayList<>());
            totalAttributeClassCalls.get(attributeCall.getParent()).add(attributeCall);
        }
        cpg.getClasses().forEach(cpgClass -> totalAttributeClassCalls.putIfAbsent(cpgClass, new ArrayList<>()));
        return totalAttributeClassCalls;
    }

    /**
     * Determine how many times the parameters of this method were used within the method's instructions.
     *
     * @param method The method being analyzed
     */
    private static Map<CPGClass.Method.Parameter, Integer> determineParameterUsage(CPGClass.Method method) {
        Map<CPGClass.Method.Parameter, Integer> parameterUsage = new HashMap<>();
        for (CPGClass.Method.Parameter parameter : method.parameters) {
            var filteredInstructions = method.instructions.stream().filter(ins -> ins.label.equals("IDENTIFIER")
                    && ins.code.contains(parameter.name)).collect(Collectors.toList());
            parameterUsage.put(parameter, filteredInstructions.size());
        }
        return parameterUsage;
    }

    /**
     * Analyze the existing method and its instructions and return a new list filled with only unique constructions
     * that would appear the same as if read from the .java file.
     *
     * @param method The method being analyzed
     */
    private static List<CPGClass.Method.Instruction> obtainUniqueInstructions(CPGClass.Method method, Helper helper) {
        List<CPGClass.Method.Instruction> uniqueInstructions = new ArrayList<>();
        ArrayList<String> allAttributeNames = helper.allAttributeNames;
        method.getParent().getAttributes().forEach(attr -> allAttributeNames.add(attr.name));
        String[] ignoredLabels = new String[]{"FIELD_IDENTIFIER", "IDENTIFIER", "LITERAL",
                "METHOD", "METHOD_PARAMETER_IN", "METHOD_PARAMETER_OUT", "METHOD_RETURN"};
        String[] ignoredCode = new String[]{"<operator>", "<empty>"};
        ArrayList<String> ignoredLabelList = new ArrayList<>(Arrays.asList(ignoredLabels));
        ArrayList<String> ignoredCodeList = new ArrayList<>(Arrays.asList(ignoredCode));
        var filteredIns = method.instructions.stream().
                filter(ins -> !ignoredLabelList.contains(ins.label) && !ignoredCodeList.contains(ins.code) && !ins.code.contains("$id")
                        && ins.lineNumber >= method.lineNumberStart).
                collect(Collectors.toCollection(ArrayList::new));
        for (CPGClass.Method.Instruction ins : filteredIns) {
            var isSubString = filteredIns.stream().
                    filter(insToFind -> insToFind.code.contains(ins.code)
                            && insToFind.code.length() > ins.code.length()).collect(Collectors.toList());
            String testStr = ins.code.replace("this.", "").trim();
            if (isSubString.isEmpty() && !allAttributeNames.contains(testStr) && (ins.code.endsWith(")") || ins.code.endsWith(";"))) {
                uniqueInstructions.add(ins);
            }
        }
        uniqueInstructions.sort(comparing(ins -> ins.lineNumber));
        return uniqueInstructions;
    }

    @Override
    public String toString() {
        return "MethodStat for: " + method.toString() + " belonging to: " + method.getParent().name;
    }
}
