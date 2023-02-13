package com.CodeSmell.stat;

import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * A class that contains potentially helpful lists containing info such as all attributes, all methods,
 * all method calls, all method parameters, all class names within cpg. Additionally, can retrieve class, method and
 * attribute by name.
 */
public final class Helper {
    /**
     * All the {@link com.CodeSmell.parser.CPGClass.Attribute} that exist within the cpg
     */
    public final ArrayList<CPGClass.Attribute> allAttributes = new ArrayList<>();

    /**
     * All the {@link com.CodeSmell.parser.CPGClass.Method} that exist within the cpg
     */
    public final ArrayList<CPGClass.Method> allMethods = new ArrayList<>();

    /**
     * All the {@link com.CodeSmell.parser.CPGClass.Method} calls that exist within the cpg
     */
    public final ArrayList<CPGClass.Method> allMethodCalls = new ArrayList<>();

    /**
     * All the {@link com.CodeSmell.parser.CPGClass.Attribute} calls that exist within the cpg
     */
    public final ArrayList<CPGClass.Attribute> allAttributeCalls = new ArrayList<>();

    /**
     * All the {@link com.CodeSmell.parser.CPGClass.Method.Parameter} that exist within the cpg
     */
    public final ArrayList<CPGClass.Method.Parameter> allParameters = new ArrayList<>();

    /**
     * All the class names (both name and classFullName) that exist within the cpg
     */
    public final ArrayList<String> allClassNames = new ArrayList<>();

    /**
     * All the method names that exist within the cpg
     */
    public final ArrayList<String> allMethodNames = new ArrayList<>();

    /**
     * All the attribute names that exist within the cpg
     */
    public final ArrayList<String> allAttributeNames = new ArrayList<>();

    public Helper(CodePropertyGraph cpg) {
        collectAllAttributes(cpg, allAttributes);
        collectAllAttributeNames(cpg, allAttributeNames);
        collectAllMethods(cpg, allMethods);
        collectAllMethodNames(cpg, allMethodNames);
        collectAllParameters(allMethods, allParameters);
        collectAllMethodCalls(allMethods, allMethodCalls);
        collectAllAttributeCalls(allMethods, allAttributeCalls);
        collectAllClassNames(cpg, allClassNames);
    }

    /**
     * Collect all the attributes that exist within cpg
     *
     * @param cpg           The CodePropertyGraph containing all existing classes and relations
     * @param allAttributes A list containing all the attributes within cpg
     */
    private static void collectAllAttributes(CodePropertyGraph cpg,
                                             ArrayList<CPGClass.Attribute> allAttributes) {
        cpg.getClasses().forEach(cpgClass -> allAttributes.addAll(cpgClass.getAttributes()));
    }

    /**
     * Collect all the attribute names that exist within cpg
     *
     * @param cpg               The CodePropertyGraph containing all existing classes and relations
     * @param allAttributeNames A list containing all the attribute names within cpg
     */
    private static void collectAllAttributeNames(CodePropertyGraph cpg, ArrayList<String> allAttributeNames) {
        cpg.getClasses().forEach(cpgClass -> cpgClass.getAttributes().forEach(attribute -> allAttributeNames.add(attribute.name)));
    }

    /**
     * Collect all the methods that exist within cpg.
     *
     * @param cpg        The CodePropertyGraph containing all existing classes and relations
     * @param allMethods A list containing all the methods that exist within cpg
     */
    private static void collectAllMethods(CodePropertyGraph cpg,
                                          ArrayList<CPGClass.Method> allMethods) {
        cpg.getClasses().forEach(cpgClass -> allMethods.addAll(cpgClass.getMethods()));
    }

    /**
     * Collect all the method names that exist within the cpg into one single list.
     *
     * @param cpg            The CodePropertyGraph containing all existing classes and relations
     * @param allMethodNames A list containing every method name in cpg
     */
    private static void collectAllMethodNames(CodePropertyGraph cpg,
                                              ArrayList<String> allMethodNames) {
        cpg.getClasses().forEach(cpgClass -> cpgClass.getMethods().forEach(method -> allMethodNames.add(method.name)));
    }


    /**
     * Collect all the method calls of each method into one single list.
     *
     * @param allMethods     All the methods within cpg
     * @param allMethodCalls A list containing all the method calls across all methods
     */
    private static void collectAllMethodCalls(ArrayList<CPGClass.Method> allMethods,
                                              ArrayList<CPGClass.Method> allMethodCalls) {
        allMethods.forEach(method -> allMethodCalls.addAll(method.getMethodCalls()));
    }

    /**
     * Collect all the attribute calls of each method into one single list.
     *
     * @param allMethods        All the methods within cpg
     * @param allAttributeCalls A list containing all the attribute calls across all methods
     */
    private static void collectAllAttributeCalls(ArrayList<CPGClass.Method> allMethods,
                                                 ArrayList<CPGClass.Attribute> allAttributeCalls) {
        allMethods.forEach(method -> allAttributeCalls.addAll(method.getAttributeCalls()));
    }

    /**
     * Collect all the method parameters of each method into one single list.
     *
     * @param allMethods    All the methods within cpg
     * @param allParameters A list containing all the method parameters across all methods
     */
    private static void collectAllParameters(ArrayList<CPGClass.Method> allMethods,
                                             ArrayList<CPGClass.Method.Parameter> allParameters) {
        allMethods.forEach(method -> allParameters.addAll((method.parameters)));
    }

    /**
     * Collect all the class names that exist within the cpg (both name and classFullName) into one single list.
     *
     * @param cpg           The CodePropertyGraph containing all existing classes and relations
     * @param allClassNames A list containing the names of every class within cpg
     */
    private static void collectAllClassNames(CodePropertyGraph cpg,
                                             ArrayList<String> allClassNames) {
        HashSet<String> uniqueNames = new HashSet<>();
        cpg.getClasses().forEach(cpgClass -> uniqueNames.add(cpgClass.name));
        cpg.getClasses().forEach(cpgClass -> uniqueNames.add(cpgClass.classFullName));
        allClassNames.addAll(uniqueNames);
    }


}
