package com.CodeSmell.smell;

import com.CodeSmell.ProjectManager;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class InappropriateIntimacyTest {
    private static InappropriateIntimacy inappropriateIntimacy;

    @BeforeClass
    public static void before() {
        CodePropertyGraph cpg = ProjectManager.getCPG("testproject");
        inappropriateIntimacy = new InappropriateIntimacy(cpg);
    }

    @Test
    public void testSmell() {
        List<Smell.CodeFragment> detections = inappropriateIntimacy.detections;
        for (Smell.CodeFragment cf : detections) {
            var allAffectedAttrs = Arrays.stream(cf.attributes)
                    .filter(attr -> attr.modifiers.contains(CPGClass.Modifier.PRIVATE) ||
                            attr.modifiers.contains(CPGClass.Modifier.PROTECTED) || attr.modifiers.contains(CPGClass.Modifier.PACKAGE_PRIVATE))
                    .collect(Collectors.toList());
            var allAffectedMethods = Arrays.stream(cf.methods)
                    .filter(method -> method.modifiers.contains(CPGClass.Modifier.PUBLIC) &&
                            !method.name.equals("toString"))
                    .collect(Collectors.toList());
            assertEquals("All attributes must be private", allAffectedAttrs.size(), cf.attributes.length);
            assertEquals("All methods must be public and not toString", allAffectedMethods.size(), cf.methods.length);
        }
    }
}
