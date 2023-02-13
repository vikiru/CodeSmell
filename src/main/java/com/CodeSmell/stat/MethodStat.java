package com.CodeSmell.stat;

import com.CodeSmell.parser.CPGClass;

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
    public final HashMap<CPGClass.Method, Integer> methodsWhichCallMethod = new HashMap<>();
    /**
     * A map containing a count of how many times each class within cpg has called this method, >= 0.
     */
    public final HashMap<CPGClass, Integer> classesWhichCallMethod = new HashMap<>();
    /**
     * A map containing a count of how many times each parameter within this method was used, >= 0
     */
    public final HashMap<CPGClass.Method.Parameter, Integer> parameterUsage = new HashMap<>();
    /**
     * All the non-duplicated instructions that appear as-is in the .java file for a given method.
     */
    public final ArrayList<CPGClass.Method.Instruction> uniqueInstructions = new ArrayList<>();

    public MethodStat(CPGClass.Method method, Helper helper) {
        this.method = method;
        determineMethodUsage(method, helper, methodsWhichCallMethod, classesWhichCallMethod);
        this.methodUsage = returnTotalUsage(methodsWhichCallMethod);
        determineParameterUsage(method, parameterUsage);
        obtainUniqueInstructions(method, helper, uniqueInstructions);
    }

    /**
     * Determine how many times this method was used by each method within cpg and additionally,
     * how many times each class has called this method.
     *
     * @param method                 The method being analyzed
     * @param helper                 The helper consisting of useful collections of elements within cpg
     * @param methodsWhichCallMethod The map indicating how many times each method has called this method
     * @param classesWhichCallMethod The map indicating how many times each class has called this method
     */
    private static void determineMethodUsage(CPGClass.Method method,
                                             Helper helper,
                                             HashMap<CPGClass.Method, Integer> methodsWhichCallMethod,
                                             HashMap<CPGClass, Integer> classesWhichCallMethod) {
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
    }

    /**
     * Return an integer value representing the total number of times this method was called across all methods
     * within cpg.
     *
     * @param methodsWhichCallMethod The map indicating how many times each method has called this method
     * @return An integer value representing how many times this method has been called within cpg
     */
    private static int returnTotalUsage(HashMap<CPGClass.Method, Integer> methodsWhichCallMethod) {
        final int[] count = {0};
        methodsWhichCallMethod.forEach((key, value) -> count[0] += value);
        return count[0];
    }

    /**
     * Determine how many times the parameters of this method were used within the method's instructions.
     *
     * @param method         The method being analyzed
     * @param parameterUsage The map indicating how many times each method parameter was used
     */
    private static void determineParameterUsage(CPGClass.Method method,
                                                HashMap<CPGClass.Method.Parameter, Integer> parameterUsage) {
        for (CPGClass.Method.Parameter parameter : method.parameters) {
            var filteredInstructions = method.instructions.stream().filter(ins -> ins.label.equals("IDENTIFIER")
                    && ins.code.contains(parameter.name)).collect(Collectors.toList());
            parameterUsage.put(parameter, filteredInstructions.size());
        }
    }

    /**
     * Analyze the existing method and its instructions and return a new list filled with only unique constructions
     * that would appear the same as if read from the .java file.
     *
     * @param method             The method being analyzed
     * @param uniqueInstructions The list of unique instructions
     */
    private static void obtainUniqueInstructions(CPGClass.Method method, Helper helper,
                                                 ArrayList<CPGClass.Method.Instruction> uniqueInstructions) {
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
    }

    @Override
    public String toString() {
        return "MethodStat for: " + method.toString() + " belonging to: " + method.getParent().name;
    }
}
