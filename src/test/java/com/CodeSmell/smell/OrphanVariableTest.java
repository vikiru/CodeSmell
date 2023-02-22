package com.CodeSmell.smell;

import com.CodeSmell.ProjectManager;
import com.CodeSmell.parser.CodePropertyGraph;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class OrphanVariableTest {
    private static OrphanVariable orphanVariable;

    @Before
    public void before() {
        CodePropertyGraph cpg = ProjectManager.getCPG("testproject");
        orphanVariable = new OrphanVariable(cpg);
    }

    @Test
    public void testSmell() {
        assertNotNull(orphanVariable.detections.poll());
    }
}
