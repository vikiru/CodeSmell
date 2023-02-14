package com.CodeSmell.stat;

import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * A class that contains potentially helpful lists containing info such as all attributes, all methods,
 * all method calls, all method parameters, all class names within cpg. Additionally, can retrieve class, method and
 * attribute by name.
 */
public final class Helper {
    /**
     * All the {@link com.CodeSmell.parser.CPGClass.Attribute} that exist within the cpg
     */
    public final List<CPGClass.Attribute> allAttributes;
    /**
     * All the attribute names that exist within the cpg
     */
    public final List<String> allAttributeNames;
    /**
     * All the class names (both name and classFullName) that exist within the cpg
     */
    public final List<String> allClassNames;
    /**
     * All the {@link com.CodeSmell.parser.CPGClass.Method} that exist within the cpg
     */
    public final List<CPGClass.Method> allMethods;
    /**
     * All the method names that exist within the cpg
     */
    public final List<String> allMethodNames;
    /**
     * All the {@link com.CodeSmell.parser.CPGClass.Method.Parameter} that exist within the cpg
     */
    public final List<CPGClass.Method.Parameter> allParameters;
    /**
     * All the {@link com.CodeSmell.parser.CPGClass.Attribute} calls that exist within the cpg
     */
    public final List<CPGClass.Attribute> allAttributeCalls;
    /**
     * All the {@link com.CodeSmell.parser.CPGClass.Method} calls that exist within the cpg
     */
    public final List<CPGClass.Method> allMethodCalls;


    public Helper(CodePropertyGraph cpg) {
        this.allAttributes = collectAllAttributes(cpg);
        this.allAttributeNames = collectAllAttributeNames(cpg);
        this.allClassNames = collectAllClassNames(cpg);
        this.allMethods = collectAllMethods(cpg);
        this.allMethodNames = collectAllMethodNames(cpg);
        this.allParameters = collectAllParameters(allMethods);
        this.allAttributeCalls = collectAllAttributeCalls(allMethods);
        this.allMethodCalls = collectAllMethodCalls(allMethods);
    }

    /**
     * Collect all the attributes that exist within cpg
     *
     * @param cpg The CodePropertyGraph containing all existing classes and relations
     */
    private static List<CPGClass.Attribute> collectAllAttributes(CodePropertyGraph cpg) {
        List<CPGClass.Attribute> allAttributes = new ArrayList<>();
        cpg.getClasses().
                forEach(cpgClass -> allAttributes.addAll(cpgClass.getAttributes()));
        return Collections.unmodifiableList(allAttributes);
    }

    /**
     * Collect all the attribute names that exist within cpg
     *
     * @param cpg The CodePropertyGraph containing all existing classes and relations
     */
    private static List<String> collectAllAttributeNames(CodePropertyGraph cpg) {
        List<String> allAttributeNames = new ArrayList<>();
        cpg.getClasses().
                forEach(cpgClass -> cpgClass.getAttributes().
                        forEach(attribute -> allAttributeNames.add(attribute.name)));
        return Collections.unmodifiableList(allAttributeNames);
    }

    /**
     * Collect all the class names that exist within the cpg (both name and classFullName) into one single list.
     *
     * @param cpg The CodePropertyGraph containing all existing classes and relations
     */
    private static List<String> collectAllClassNames(CodePropertyGraph cpg) {
        HashSet<String> uniqueNames = new HashSet<>();
        cpg.getClasses().forEach(cpgClass -> uniqueNames.add(cpgClass.name));
        cpg.getClasses().forEach(cpgClass -> uniqueNames.add(cpgClass.classFullName));
        List<String> allClassNames = new ArrayList<>(uniqueNames);
        return Collections.unmodifiableList(allClassNames);
    }


    /**
     * Collect all the methods that exist within cpg.
     *
     * @param cpg The CodePropertyGraph containing all existing classes and relations
     */
    private static List<CPGClass.Method> collectAllMethods(CodePropertyGraph cpg) {
        List<CPGClass.Method> allMethods = new ArrayList<>();
        cpg.getClasses().forEach(cpgClass -> allMethods.addAll(cpgClass.getMethods()));
        return Collections.unmodifiableList(allMethods);
    }

    /**
     * Collect all the method names that exist within the cpg into one single list.
     *
     * @param cpg The CodePropertyGraph containing all existing classes and relations
     * @return
     */
    private static List<String> collectAllMethodNames(CodePropertyGraph cpg) {
        List<String> allMethodNames = new ArrayList<>();
        cpg.getClasses().forEach(cpgClass -> cpgClass.getMethods().forEach(method -> allMethodNames.add(method.name)));
        return Collections.unmodifiableList(allMethodNames);
    }

    /**
     * Collect all the attribute calls of each method into one single list.
     *
     * @param allMethods All the methods within cpg
     * @return
     */
    private static List<CPGClass.Attribute> collectAllAttributeCalls(List<CPGClass.Method> allMethods) {
        List<CPGClass.Attribute> allAttributeCalls = new ArrayList<>();
        allMethods.forEach(method -> allAttributeCalls.addAll(method.getAttributeCalls()));
        return Collections.unmodifiableList(allAttributeCalls);
    }

    /**
     * Collect all the method calls of each method into one single list.
     *
     * @param allMethods All the methods within cpg
     * @return
     */
    private static List<CPGClass.Method> collectAllMethodCalls(List<CPGClass.Method> allMethods) {
        List<CPGClass.Method> allMethodCalls = new ArrayList<>();
        allMethods.forEach(method -> allMethodCalls.addAll(method.getMethodCalls()));
        return Collections.unmodifiableList(allMethodCalls);
    }


    /**
     * Collect all the method parameters of each method into one single list.
     *
     * @param allMethods All the methods within cpg
     */
    private static List<CPGClass.Method.Parameter> collectAllParameters(List<CPGClass.Method> allMethods) {
        List<CPGClass.Method.Parameter> allParameters = new ArrayList<>();
        allMethods.forEach(method -> allParameters.addAll((method.parameters)));
        return Collections.unmodifiableList(allParameters);
    }

}
