package com.CodeSmell.stat;

import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CPGClass.Attribute;
import com.CodeSmell.parser.CPGClass.Method;
import com.CodeSmell.parser.CPGClass.Method.Instruction;
import com.CodeSmell.parser.CPGClass.Method.Parameter;
import com.CodeSmell.parser.CodePropertyGraph;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

/**
 * MethodStat contains stats relevant to a given method pertaining to how it is used within cpg.
 */
public class MethodStat {
    /**
     * The reference to the method that this MethodStat is providing information about
     */
    public final Method method;
    /**
     * The total times this method was called within cpg
     */
    public final int methodUsage;
    /**
     * A map containing a count of how many times each method within cpg has called this method, >= 0.
     */
    public final Map<Method, Integer> methodsWhichCallMethod;
    /**
     * A map containing a count of how many times each class within cpg has called this method, >= 0.
     */
    public final Map<CPGClass, Integer> classesWhichCallMethod;
    /**
     * A map containing a count of how many times the attributes of another class were used within this method
     */
    public final Map<CPGClass, List<Attribute>> distinctAttributeCalls;
    /**
     * A map containing a count of how many times the methods of another class were used within this method
     */
    public final Map<CPGClass, List<Method>> distinctMethodCalls;
    /**
     * A map containing a count of how many times each parameter within this method was used, >= 0
     */
    public final Map<Parameter, Integer> parameterUsage;
    /**
     * All the non-duplicated instructions that appear as-is in the .java file for a given method.
     */
    public final List<Instruction> uniqueInstructions;

    public MethodStat(Method method, CodePropertyGraph cpg, Helper helper) {
        this.method = method;
        this.methodsWhichCallMethod = determineMethodUsage(method, helper);
        this.classesWhichCallMethod = determineClassMethodUsage(methodsWhichCallMethod);
        this.distinctAttributeCalls = determineDistinctAttributeCalls(method, cpg);
        this.distinctMethodCalls = determineDistinctMethodCalls(method, cpg);
        this.methodUsage = returnTotalUsage(methodsWhichCallMethod);
        this.parameterUsage = determineParameterUsage(method);
        this.uniqueInstructions = obtainUniqueInstructions(method, helper);
    }

    /**
     * Determine how many times this method was used by each method within cpg.
     *
     * @param method The method being analyzed
     * @param helper The helper consisting of useful collections of elements within cpg
     * @return A map indicating how many times each method in cpg uses this method
     */
    private static Map<Method, Integer> determineMethodUsage(Method method, Helper helper) {
        Map<Method, Integer> methodsWhichCallMethod = new HashMap<>();
        List<Method> allMethods = helper.allMethods;
        String toFind = method.getParent().name + "." + method.name;
        for (Method methodInCPG : allMethods) {
            int count = 0;
            if (methodInCPG.getMethodCalls().contains(method)) {
                count = Math.toIntExact(methodInCPG.instructions
                        .stream()
                        .filter(instruction -> instruction.methodCall.equals(toFind))
                        .count());
            }
            methodsWhichCallMethod.put(methodInCPG, count);
        }
        return Collections.unmodifiableMap(methodsWhichCallMethod);
    }

    /**
     * Determine how many times this method was used by each class within cpg.
     *
     * @param methodsWhichCallMethod A map indicating how many times each method in cpg uses this method
     * @return A map indicating how many times each class in cpg uses this method
     */
    private static Map<CPGClass, Integer> determineClassMethodUsage(Map<Method, Integer> methodsWhichCallMethod) {
        Map<CPGClass, Integer> classesWhichCallMethod = new HashMap<>();
        methodsWhichCallMethod
                .forEach((key, value) -> classesWhichCallMethod.put(key.getParent(),
                        classesWhichCallMethod.getOrDefault(key.getParent(), 0) + value));
        return Collections.unmodifiableMap(classesWhichCallMethod);
    }

    /**
     * Return an integer value representing the total number of times this method was called across all methods
     * within cpg.
     *
     * @param methodsWhichCallMethod The map indicating how many times each method has called this method
     * @return An integer value representing how many times this method has been called within cpg
     */
    private static int returnTotalUsage(Map<Method, Integer> methodsWhichCallMethod) {
        final int[] count = {0};
        methodsWhichCallMethod.forEach((key, value) -> count[0] += value);
        return count[0];
    }

    /**
     * Group the method calls of this method to its parentClass.
     *
     * @param method The method being analyzed
     * @param cpg    The CodePropertyGraph containing existing classes and relations
     * @return A map indicating which methods of each class were used within this method's instructions
     */
    private static Map<CPGClass, List<Method>> determineDistinctMethodCalls(Method method,
                                                                            CodePropertyGraph cpg) {
        Map<CPGClass, List<Method>> totalMethodClassCalls = new HashMap<>();
        for (Method methodCall : method.getMethodCalls()) {
            totalMethodClassCalls.putIfAbsent(methodCall.getParent(), new ArrayList<>());
            totalMethodClassCalls.get(methodCall.getParent()).add(methodCall);
        }
        cpg.getClasses().forEach(cpgClass -> totalMethodClassCalls.putIfAbsent(cpgClass, new ArrayList<>()));
        return Collections.unmodifiableMap(totalMethodClassCalls);
    }

    /**
     * Group the attribute calls of this method to its parentClass.
     *
     * @param method The method being analyzed
     * @param cpg    The CodePropertyGraph containing existing classes and relations
     * @return A map indicating which attributes of each class were used within this method's instructions
     */
    private static Map<CPGClass, List<Attribute>> determineDistinctAttributeCalls(Method method,
                                                                                  CodePropertyGraph cpg) {
        Map<CPGClass, List<Attribute>> totalAttributeClassCalls = new HashMap<>();
        for (Attribute attributeCall : method.getAttributeCalls()) {
            totalAttributeClassCalls.putIfAbsent(attributeCall.getParent(), new ArrayList<>());
            totalAttributeClassCalls.get(attributeCall.getParent()).add(attributeCall);
        }
        cpg.getClasses().forEach(cpgClass -> totalAttributeClassCalls.putIfAbsent(cpgClass, new ArrayList<>()));
        return Collections.unmodifiableMap(totalAttributeClassCalls);
    }

    /**
     * Determine how many times the parameters of this method were used within the method's instructions.
     *
     * @param method The method being analyzed
     * @return A map indicating how many times each parameter of a method was used
     */
    private static Map<Parameter, Integer> determineParameterUsage(Method method) {
        Map<Parameter, Integer> parameterUsage = new HashMap<>();
        for (Parameter parameter : method.parameters) {
            var filteredInstructions = method.instructions
                    .stream()
                    .filter(ins -> ins.label.equals("IDENTIFIER") && ins.code.contains(parameter.name))
                    .collect(Collectors.toList());
            parameterUsage.put(parameter, filteredInstructions.size());
        }
        return Collections.unmodifiableMap(parameterUsage);
    }

    /**
     * Analyze the existing method and its instructions and return a new list filled with only unique constructions
     * that would appear the same as if read from the .java file.
     *
     * @param method The method being analyzed
     * @return A list containing all the unique instructions that would appear as-is within the .java file
     */
    private static List<Instruction> obtainUniqueInstructions(Method method, Helper helper) {
        List<Instruction> uniqueInstructions = new ArrayList<>();
        List<String> allAttributeNames = helper.allAttributeNames;
        String[] ignoredLabels = new String[]{"FIELD_IDENTIFIER", "IDENTIFIER", "LITERAL",
                "METHOD", "Parameter_IN", "Parameter_OUT", "METHOD_RETURN"};
        String[] ignoredCode = new String[]{"<operator>", "<empty>"};
        List<String> ignoredLabelList = new ArrayList<>(Arrays.asList(ignoredLabels));
        List<String> ignoredCodeList = new ArrayList<>(Arrays.asList(ignoredCode));
        var filteredIns = method.instructions
                .stream()
                .filter(ins -> !ignoredLabelList.contains(ins.label)
                        && !ignoredCodeList.contains(ins.code) && !ins.code.contains("$id")
                        && ins.lineNumber >= method.lineNumberStart)
                .collect(Collectors.toCollection(ArrayList::new));
        for (Instruction ins : filteredIns) {
            var isSubString = filteredIns
                    .stream()
                    .filter(insToFind -> insToFind.code.contains(ins.code)
                            && insToFind.code.length() > ins.code.length())
                    .collect(Collectors.toList());
            String testStr = ins.code.replace("this.", "").trim();
            if (isSubString.isEmpty() && !allAttributeNames.contains(testStr)
                    && (ins.code.endsWith(")") || ins.code.endsWith(";") || ins.code.contains("="))) {
                uniqueInstructions.add(ins);
            }
        }
        uniqueInstructions.sort(comparing(ins -> ins.lineNumber));
        return Collections.unmodifiableList(uniqueInstructions);
    }

    @Override
    public String toString() {
        return "MethodStat for: " + method.toString() + " belonging to: " + method.getParent().name;
    }
}
