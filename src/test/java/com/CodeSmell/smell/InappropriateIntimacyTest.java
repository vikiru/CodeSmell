package com.CodeSmell.smell;

import com.CodeSmell.ProjectManager;
import com.CodeSmell.parser.CodePropertyGraph;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class InappropriateIntimacyTest {
    private static InappropriateIntimacy inappropriateIntimacy;

    @Before
    public void before() {
        CodePropertyGraph cpg = ProjectManager.getCPG("testproject");
        inappropriateIntimacy = new InappropriateIntimacy(cpg);
    }

    @Test
    public void testSmell() {
        //assertNotNull(inappropriateIntimacy.detections.poll());
    }
}
