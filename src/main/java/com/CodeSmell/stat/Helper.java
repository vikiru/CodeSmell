package com.CodeSmell.stat;

import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;

import java.util.*;

/**
 * A class that contains potentially helpful lists containing info such as all attributes, all methods,
 * all method calls, all method parameters, all class names within cpg.
 */
public class Helper {
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
        this.allAttributeNames = collectAllAttributeNames(allAttributes);
        this.allClassNames = collectAllClassNames(cpg);
        this.allMethods = collectAllMethods(cpg);
        this.allMethodNames = collectAllMethodNames(allMethods);
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
        Set<CPGClass.Attribute> allAttributes = new HashSet<>();
        cpg.getClasses().
                forEach(cpgClass -> allAttributes.addAll(cpgClass.getAttributes()));
        List<CPGClass.Attribute> toReturn = new ArrayList<>(allAttributes);
        return Collections.unmodifiableList(toReturn);
    }

    /**
     * Collect all the attribute names that exist within cpg
     *
     * @param allAttributes
     */
    private static List<String> collectAllAttributeNames(List<CPGClass.Attribute> allAttributes) {
        Set<String> allAttributeNames = new HashSet<>();
        allAttributes.forEach(attribute -> allAttributeNames.add(attribute.name));
        List<String> toReturn = new ArrayList<>(allAttributeNames);
        return Collections.unmodifiableList(toReturn);
    }

    /**
     * Collect all the class names that exist within the cpg (both name and classFullName) into one single list.
     *
     * @param cpg The CodePropertyGraph containing all existing classes and relations
     */
    private static List<String> collectAllClassNames(CodePropertyGraph cpg) {
        Set<String> uniqueNames = new HashSet<>();
        cpg.getClasses().forEach(cpgClass -> uniqueNames.add(cpgClass.name));
        cpg.getClasses().forEach(cpgClass -> uniqueNames.add(cpgClass.classFullName));
        List<String> toReturn = new ArrayList<>(uniqueNames);
        return Collections.unmodifiableList(toReturn);
    }

    /**
     * Collect all the methods that exist within cpg.
     *
     * @param cpg The CodePropertyGraph containing all existing classes and relations
     */
    private static List<CPGClass.Method> collectAllMethods(CodePropertyGraph cpg) {
        Set<CPGClass.Method> allMethods = new HashSet<>();
        cpg.getClasses().forEach(cpgClass -> allMethods.addAll(cpgClass.getMethods()));
        List<CPGClass.Method> toReturn = new ArrayList<>(allMethods);
        return Collections.unmodifiableList(toReturn);
    }

    /**
     * Collect all the method names that exist within the cpg into one single list.
     *
     * @param allMethods
     * @return A list of all the method names within cpg
     */
    private static List<String> collectAllMethodNames(List<CPGClass.Method> allMethods) {
        Set<String> allMethodNames = new HashSet<>();
        allMethods.forEach(method -> allMethodNames.add(method.name));
        List<String> toReturn = new ArrayList<>(allMethodNames);
        return Collections.unmodifiableList(toReturn);
    }

    /**
     * Collect all the attribute calls of each method into one single list.
     *
     * @param allMethods All the methods within cpg
     * @return A list of all the attributes within cpg
     */
    private static List<CPGClass.Attribute> collectAllAttributeCalls(List<CPGClass.Method> allMethods) {
        Set<CPGClass.Attribute> allAttributeCalls = new HashSet<>();
        allMethods.forEach(method -> allAttributeCalls.addAll(method.getAttributeCalls()));
        List<CPGClass.Attribute> toReturn = new ArrayList<>(allAttributeCalls);
        return Collections.unmodifiableList(toReturn);
    }

    /**
     * Collect all the method calls of each method into one single list.
     *
     * @param allMethods All the methods within cpg
     * @return A list of all the method calls within cpg
     */
    private static List<CPGClass.Method> collectAllMethodCalls(List<CPGClass.Method> allMethods) {
        Set<CPGClass.Method> allMethodCalls = new HashSet<>();
        allMethods.forEach(method -> allMethodCalls.addAll(method.getMethodCalls()));
        List<CPGClass.Method> toReturn = new ArrayList<>(allMethodCalls);
        return Collections.unmodifiableList(toReturn);
    }

    /**
     * Collect all the method parameters of each method into one single list.
     *
     * @param allMethods All the methods within cpg
     * @return A list of all the method parameters within cpg
     */
    private static List<CPGClass.Method.Parameter> collectAllParameters(List<CPGClass.Method> allMethods) {
        List<CPGClass.Method.Parameter> allParameters = new ArrayList<>();
        allMethods.forEach(method -> allParameters.addAll((method.parameters)));
        return Collections.unmodifiableList(allParameters);
    }

}
