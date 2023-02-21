package com.CodeSmell.smell;

import com.CodeSmell.ProjectManager;
import com.CodeSmell.parser.CodePropertyGraph;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class RefusedBequestTest {
    private static RefusedBequest refusedBequest;

    @Before
    public void before() {
        CodePropertyGraph cpg = ProjectManager.getCPG("testproject");
        refusedBequest = new RefusedBequest(cpg);
    }

    @Test
    public void testSmell() {
        assertNotNull(refusedBequest.detections.poll());
    }
}
