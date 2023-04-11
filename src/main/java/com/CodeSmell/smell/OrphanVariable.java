package com.CodeSmell.smell;

import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CPGClass.*;
import com.CodeSmell.parser.CPGClass.Method.*;
import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.stat.AttributeStat;
import com.CodeSmell.stat.ClassStat;
import com.CodeSmell.stat.StatTracker;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * An Orphan Variable/Constant Class can be defined as a collection of constants
 * that belong elsewhere than the class they are defined within.
 * </p>
 * <p>
 * A constant can be defined as an attribute that is a primitive type and possess
 * the following modifiers: "FINAL", and "STATIC". However, private static final
 * attributes can only be accessed by the class that owns the attribute, therefore
 * "PUBLIC" is an additional requirement for the Orphan Variable code smell.
 * </p>
 */
public class OrphanVariable extends Smell {
    public final LinkedList<CodeFragment> detections;

    public OrphanVariable(CodePropertyGraph cpg) {
        super("Orphan Variable", cpg);
        this.detections = new LinkedList<>();
        detectAll(detections);
    }

    @Override
    public CodeFragment detectNext() {
        return detections.poll();
    }

    @Override
    public String description() {
        return "A collection of constants that belong in a different class than the class they are defined within";
    }

    @Override
    public LinkedList<CodeFragment> getDetections() {
        return this.detections;
    }

    /**
     * <p>
     * In order for a class to be labeled as having an Orphan Variable code smell, the following conditions must be
     * satisfied:
     * <p>
     * 1) The class must posses attributes that can be considered a constant (primitive type and public, static, final modifiers)
     * </p>
     * <p>
     * 2) These attributes are not used at all within the class that they are defined, but they are used elsewhere in
     * other classes
     * </p>
     *
     * @param detections The list of detections for the OrphanVariable smell
     */
    protected static void detectAll(LinkedList<CodeFragment> detections) {
        List<ClassStat> filteredStats = returnFilteredClassStat();
        Modifier[] affectedModifiers = new Modifier[]{Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL};
        StringBuilder sb = new StringBuilder();
        for (ClassStat classStat : filteredStats) {
            CPGClass parentClass = classStat.cpgClass;
            List<AttributeStat> attributeStats = new ArrayList<>(classStat.attributeStats.values());
            List<AttributeStat> filteredAttrStats = returnFilteredAttributeStat(attributeStats, parentClass);

            CPGClass[] affectedClasses = returnAffectedClasses(filteredAttrStats, parentClass);
            Attribute[] affectedAttributes = returnAffectedAttributes(filteredAttrStats);
            Method[] affectedMethods = returnAffectedMethods(filteredAttrStats);
            Instruction[] affectedInstructions = returnAffectedInstructions(affectedAttributes, affectedMethods);
            sb.append(parentClass.name).append(" has a collection of unused constants that belong elsewhere.");
            String description = sb.toString();

            CodeFragment codeFragment = CodeFragment.makeFragment(description, affectedClasses, affectedMethods,
                    affectedModifiers, affectedAttributes, new Parameter[0], affectedInstructions);
            detections.add(codeFragment);
        }
    }

    /**
     * Determine if a given class has constants within its attributes. Constants are
     * attributes which possess static, final modifiers. However, it is additionally necessary
     * that these attributes are public as private attributes with these modifiers can only be
     * accessed within that class itself.
     *
     * @param cpgClass The class being analyzed
     * @return A boolean indicating if the class has constants or not
     */
    protected static boolean hasConstants(CPGClass cpgClass) {
        var constantCheck = cpgClass.getAttributes()
                .stream()
                .filter(attribute -> attribute.modifiers.contains(Modifier.PUBLIC) &&
                        attribute.modifiers.contains(Modifier.STATIC) &&
                        attribute.modifiers.contains(Modifier.FINAL))
                .collect(Collectors.toList());
        return !constantCheck.isEmpty();
    }

    /**
     * Return a list of filtered ClassStats, this list contains ClassStat objects where the CPGClass contains constants
     * as attributes.
     *
     * @return A list of filtered ClassStats with constants
     */
    private static List<ClassStat> returnFilteredClassStat() {
        List<ClassStat> classStats = new ArrayList<>(Common.stats.classStats.values());
        List<ClassStat> filteredClassStats = new ArrayList<>();
        classStats.stream().filter(classStat -> OrphanVariable.hasConstants(classStat.cpgClass)).forEach(filteredClassStats::add);
        return filteredClassStats;
    }

    /**
     * Return a list of filtered AttributeStats, this list contains AttributeStat objects which are of primitive type,
     * has the necessary modifiers to be a constant, the attribute is used within cpg and the parentClass of the attribute
     * does not use the attribute.
     *
     * @param attributeStats All the AttributeStats of a given ClassStat
     * @param parentClass    The class that the ClassStat is referencing
     * @return A list of filtered AttributeStats for all the class constants
     */
    private static List<AttributeStat> returnFilteredAttributeStat(List<AttributeStat> attributeStats, CPGClass parentClass) {
        List<String> primitiveTypeList = List.of(new String[]{"boolean", "byte", "char", "double",
                "float", "int", "long", "short"});
        List<AttributeStat> filteredAttrStats = new ArrayList<>();
        List<Modifier> requiredModifiers = List.of(new Modifier[]{Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL});

        attributeStats.stream().filter(attributeStat -> primitiveTypeList.contains(attributeStat.attribute.attributeType) &&
                        new HashSet<>(attributeStat.attribute.modifiers).containsAll(requiredModifiers) &&
                        attributeStat.attributeUsage > 0 && attributeStat.classesWhichCallAttr.get(parentClass) == 0).
                forEach(filteredAttrStats::add);
        return filteredAttrStats;
    }

    /**
     * Iterate through all the AttributeStats and add all classes which call each reference Attribute within
     * each AttributeStat along with the parent CPGClass and return an array of CPGClass.
     *
     * @param filteredStats A list of filtered AttributeStats for the constants of a given CPGClass
     * @param parentClass   The parentClass which owns these constants
     * @return An array consisting of all classes related to the constants
     */
    private static CPGClass[] returnAffectedClasses(List<AttributeStat> filteredStats, CPGClass parentClass) {
        Set<CPGClass> affectedClasses = new LinkedHashSet<>();
        affectedClasses.add(parentClass);
        filteredStats.forEach(attributeStat -> attributeStat.classesWhichCallAttr
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 0)
                .forEach(entry -> affectedClasses.add(entry.getKey())));
        return affectedClasses.toArray(CPGClass[]::new);
    }

    /**
     * Return an array consisting of all the constants, given a list of filtered AttributeStats containing references
     * to the constants of a given class.
     *
     * @param filteredStats A list of filtered AttributeStats for the constants of a given class
     * @return An array of all the attributes that are constants
     */
    private static Attribute[] returnAffectedAttributes(List<AttributeStat> filteredStats) {
        Set<Attribute> affectedAttributes = new HashSet<>();
        filteredStats.forEach(attributeStat -> affectedAttributes.add(attributeStat.attribute));
        return affectedAttributes.toArray(Attribute[]::new);
    }

    /**
     * Iterate through the filtered AttributeStats and return an array consisting of all the methods
     * which call each Attribute.
     *
     * @param filteredStats A list of filtered AttributeStats for the constants of a given class
     * @return An array of all the methods which call the constants of a given class
     */
    private static Method[] returnAffectedMethods(List<AttributeStat> filteredStats) {
        Set<Method> affectedMethods = new HashSet<>();
        filteredStats.forEach(attributeStat -> attributeStat.methodsWhichCallAttr
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 0)
                .forEach(entry -> affectedMethods.add(entry.getKey())));
        return affectedMethods.toArray(Method[]::new);
    }

    /**
     * Iterate through all the affected methods and find the affected instructions. Starting with the "METHOD" instruction,
     * followed by all "FIELD_IDENTIFIER" instructions that has a code field matching the name of one of the affected attributes.
     *
     * @param affectedAttributes All the constants of a given class
     * @param affectedMethods    All the methods which call the constants
     * @return An array of affected instructions
     */
    private static Instruction[] returnAffectedInstructions(Attribute[] affectedAttributes, Method[] affectedMethods) {
        List<Instruction> affectedInstructions = new ArrayList<>();
        List<String> attributeNames = new ArrayList<>();
        Arrays.stream(affectedAttributes).forEach(attribute -> attributeNames.add(attribute.name));
        Arrays.stream(affectedMethods).forEach(method -> method.instructions
                .stream()
                .filter(ins -> ins.label.equals("METHOD") || (ins.label.equals("FIELD_IDENTIFIER") && attributeNames.contains(ins.code)))
                .forEach(affectedInstructions::add));
        return affectedInstructions.toArray(Instruction[]::new);
    }


}