package com.CodeSmell.smell;

import com.CodeSmell.ProjectManager;
import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.parser.Parser;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.CodeSmell.smell.Common.initStatTracker;

public class MisplacedClassTest {

    private Parser p;
    private static CodePropertyGraph cpg;

    @BeforeClass
    public static void before() {
        cpg = ProjectManager.getCPG("testproject");
        initStatTracker(cpg);
    }

    @Test
    public void testMisplacedClass() {
        MisplacedClass mc = new MisplacedClass(cpg);
        for (Smell.CodeFragment cf : mc.detections) {
            System.out.println(cf.classes[0] + " is Misplaced");
        }
    }
}