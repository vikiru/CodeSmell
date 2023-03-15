package com.CodeSmell.smell;

import com.CodeSmell.ProjectManager;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CPGClass.*;
import com.CodeSmell.parser.CPGClass.Method.*;
import com.CodeSmell.smell.Smell.*;
import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.stat.ClassStat;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class OrphanVariableTest {
    private static OrphanVariable orphanVariable;
    private static Map<CPGClass, List<CPGClass>> constantWithUserClass;
    private static Map<CPGClass, ClassStat> classStats;

    @BeforeClass
    public static void before() {
        CodePropertyGraph cpg = ProjectManager.getCPG("testproject");
        Common.initStatTracker(cpg);
        classStats = Common.stats.classStats;
        orphanVariable = new OrphanVariable(cpg);
        constantWithUserClass = returnConstantsWithAffectedClasses(orphanVariable.detections);
    }

    @Test
    public void testConstantClasses() {
        for (CPGClass constantClass : constantWithUserClass.keySet()) {
            assertTrue("Constant class should have constants", OrphanVariable.hasConstants(constantClass));
        }
    }

    @Test
    public void testAffectedClasses() {
        for (Map.Entry<CPGClass, List<CPGClass>> entry : constantWithUserClass.entrySet()) {
            CPGClass constantClass = entry.getKey();
            List<CPGClass> userClasses = entry.getValue();
            for (CPGClass userClass : userClasses) {
                ClassStat userClassStat = classStats.get(userClass);
                int totalAttributeCalls = userClassStat.totalClassAttributeCalls.get(constantClass);
                assertTrue("Constants from constant class should be used by class.", totalAttributeCalls > 0);
            }
        }
    }

    @Test
    public void testAffectedAttributes() {
        for (CodeFragment codeFragment : orphanVariable.detections) {
            Attribute[] affectedAttributes = codeFragment.attributes;
            List<Modifier> affectedModifiers = List.of(codeFragment.modifiers);
            for (Attribute attribute : affectedAttributes) {
                assertTrue("Attribute should contain public, static, and final modifiers.", attribute.modifiers.containsAll(affectedModifiers));
            }
        }

    }

    @Test
    public void testAffectedMethods() {
        for (CodeFragment codeFragment : orphanVariable.detections) {
            Set<Attribute> affectedAttributes = new HashSet<>(List.of(codeFragment.attributes));
            Method[] affectedMethods = codeFragment.methods;
            for (Method method : affectedMethods) {
                Set<Attribute> attributeCalls = new HashSet<>(method.getAttributeCalls());
                assertTrue("Affected method should have called at least one of the affected attributes",
                        affectedAttributes.removeAll(attributeCalls));
            }
        }
    }

    @Test
    public void testAffectedInstructions() {
        for (CodeFragment codeFragment : orphanVariable.detections) {
            List<Attribute> affectedAttributes = List.of(codeFragment.attributes);
            List<Method> affectedMethods = List.of(codeFragment.methods);
            List<String> attributeNames = new ArrayList<>();
            List<String> methodNames = new ArrayList<>();
            affectedMethods.forEach(method -> methodNames.add(method.name));
            affectedAttributes.forEach(attribute -> attributeNames.add(attribute.name));

            List<Instruction> fieldIdentifierIns = Arrays.stream(codeFragment.instructions)
                    .filter(ins -> ins.label.equals("FIELD_IDENTIFIER"))
                    .collect(Collectors.toList());
            fieldIdentifierIns.forEach(ins -> assertTrue("Method instruction should contain an affected attribute", attributeNames.contains(ins.code)));
            List<Instruction> methodBodyIns = Arrays.stream(codeFragment.instructions)
                    .filter(ins -> ins.label.equals("METHOD"))
                    .collect(Collectors.toList());
            assertEquals("The number of METHOD instructions should equal the number of affected methods", affectedMethods.size(), methodBodyIns.size());
            for (Instruction ins : methodBodyIns) {
                List<String> methodCheck = methodNames.stream().filter(ins.code::contains).collect(Collectors.toList());
                assertFalse("METHOD instruction should contain the name of an affected method", methodCheck.isEmpty());
            }
        }
    }

    private static Map<CPGClass, List<CPGClass>> returnConstantsWithAffectedClasses(List<CodeFragment> codeFragments) {
        Map<CPGClass, List<CPGClass>> constantWithUserClass = new HashMap<>();
        List<CPGClass> constantClasses = new ArrayList<>();
        codeFragments.forEach(codeFragment -> Arrays.stream(codeFragment.classes)
                .filter(OrphanVariable::hasConstants)
                .forEach(constantClasses::add));
        for (CodeFragment codeFragment : codeFragments) {
            List<CPGClass> constantSearch = Arrays.stream(codeFragment.classes)
                    .filter(OrphanVariable::hasConstants)
                    .limit(1)
                    .collect(Collectors.toList());
            CPGClass constantClass = codeFragment.classes[0];
            List<CPGClass> allUserClasses = Arrays.stream(codeFragment.classes)
                    .filter(cpgClass -> !constantClasses.contains(cpgClass)).collect(Collectors.toList());
            constantWithUserClass.put(constantClass, allUserClasses);
        }
        return constantWithUserClass;
    }
}
