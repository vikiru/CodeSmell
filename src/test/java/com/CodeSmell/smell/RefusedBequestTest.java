package com.CodeSmell.smell;

import com.CodeSmell.ProjectManager;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CPGClass.Attribute;
import com.CodeSmell.parser.CPGClass.Method;
import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.smell.Smell.CodeFragment;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RefusedBequestTest {
    private static RefusedBequest refusedBequest;


    @BeforeClass
    public static void before() {
        CodePropertyGraph cpg = ProjectManager.getCPG("testproject");
        Common.initStatTracker(cpg);
        refusedBequest = new RefusedBequest(cpg);
    }

    @Test
    public void testAffectedClasses() {
        for (CodeFragment codeFragment : refusedBequest.detections) {
            CPGClass subClass = codeFragment.classes[0];
            CPGClass superClass = codeFragment.classes[1];
            assertTrue("Subclass should have unused properties from superclass", RefusedBequest.hasUnusedProperties(subClass, superClass));
        }
    }

    @Test
    public void testAffectedAttributes() {
        for (CodeFragment codeFragment : refusedBequest.detections) {
            CPGClass subClass = codeFragment.classes[0];
            CPGClass superClass = codeFragment.classes[1];
            Attribute[] affectedAttributes = codeFragment.attributes;
            List<Method> subClassMethods = subClass.getMethods()
                    .stream()
                    .filter(method -> method.getParent().equals(subClass))
                    .collect(Collectors.toList());

            List<Attribute> allAttributes = new ArrayList<>();
            subClassMethods.forEach(method -> method.getAttributeCalls()
                    .stream()
                    .filter(attributeCall -> attributeCall.getParent().equals(superClass))
                    .distinct()
                    .forEach(allAttributes::add));
            assertFalse("Subclass should not have affected attributes within its attribute calls",
                    allAttributes.containsAll(List.of(affectedAttributes)));
        }
    }

    @Test
    public void testAffectedMethods() {
        for (CodeFragment codeFragment : refusedBequest.detections) {
            CPGClass subClass = codeFragment.classes[0];
            CPGClass superClass = codeFragment.classes[1];
            Method[] affectedMethods = codeFragment.methods;
            List<Method> subClassMethods = subClass.getMethods()
                    .stream()
                    .filter(method -> method.getParent().equals(subClass))
                    .collect(Collectors.toList());

            List<Method> allMethods = new ArrayList<>();
            subClassMethods.forEach(method -> method.getMethodCalls()
                    .stream()
                    .filter(methodCall -> methodCall.getParent().equals(superClass))
                    .forEach(allMethods::add));
            assertFalse("Subclass should not have affected methods within its method calls",
                    allMethods.containsAll(List.of(affectedMethods)));
        }
    }
}
