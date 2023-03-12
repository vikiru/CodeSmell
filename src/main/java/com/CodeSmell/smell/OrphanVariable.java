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
        detectAll(Common.stats, detections);
    }

    @Override
    public CodeFragment detectNext() {
        return detections.poll();
    }

    @Override
    public String description() {
        return "A collection of constants that belong in a different class than the class they are defined within";
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
     * @param statTracker The StatTracker containing useful stats about a given codebase
     * @param detections  The list of detections for the OrphanVariable smell
     */
    protected static void detectAll(StatTracker statTracker, LinkedList<CodeFragment> detections) {
        List<ClassStat> filteredStats = returnFilteredClassStat(new ArrayList<>(statTracker.classStats.values()));
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

    private static boolean hasConstants(ClassStat classStat) {
        var constantCheck = classStat.cpgClass.getAttributes()
                .stream()
                .filter(attribute -> attribute.modifiers.contains(Modifier.PUBLIC) &&
                        attribute.modifiers.contains(Modifier.STATIC) &&
                        attribute.modifiers.contains(Modifier.FINAL))
                .collect(Collectors.toList());
        return !constantCheck.isEmpty();
    }

    private static List<ClassStat> returnFilteredClassStat(List<ClassStat> classStats) {
        List<ClassStat> filteredClassStats = new ArrayList<>();
        classStats.stream().filter(OrphanVariable::hasConstants).forEach(filteredClassStats::add);
        return filteredClassStats;
    }

    private static List<AttributeStat> returnFilteredAttributeStat(List<AttributeStat> attributeStats, CPGClass parentClass) {
        List<String> primitiveTypeList = List.of(new String[]{"boolean", "byte", "char", "double",
                "float", "int", "long", "short"});
        List<AttributeStat> filteredAttrStats = new ArrayList<>();
        attributeStats
                .stream()
                .filter(attributeStat -> primitiveTypeList.contains(attributeStat.attribute.attributeType)
                        && attributeStat.attributeUsage > 0 && attributeStat.classesWhichCallAttr.get(parentClass) == 0)
                .forEach(filteredAttrStats::add);
        return filteredAttrStats;
    }

    private static CPGClass[] returnAffectedClasses(List<AttributeStat> filteredStats, CPGClass parentClass) {
        Set<CPGClass> affectedClasses = new HashSet<>();
        affectedClasses.add(parentClass);
        filteredStats.forEach(attributeStat -> attributeStat.classesWhichCallAttr
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 0)
                .forEach(entry -> affectedClasses.add(entry.getKey())));
        return affectedClasses.toArray(CPGClass[]::new);
    }

    private static Attribute[] returnAffectedAttributes(List<AttributeStat> filteredStats) {
        Set<Attribute> affectedAttributes = new HashSet<Attribute>();
        filteredStats.forEach(attributeStat -> affectedAttributes.add(attributeStat.attribute));
        return affectedAttributes.toArray(Attribute[]::new);
    }

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