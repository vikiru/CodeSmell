package com.CodeSmell.smell.MisplacedClassTest;

import com.CodeSmell.ProjectManager;
import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.parser.Parser;
import com.CodeSmell.smell.MisplacedClass;
import org.junit.Before;
import org.junit.Test;

import static com.CodeSmell.smell.Common.initStatTracker;
import static org.junit.Assert.*;

public class MisplacedClassTest {

    private Parser p;
    private CodePropertyGraph cpg;

    @Before
    public void before() {
        this.cpg = ProjectManager.getCPG("testproject");
        initStatTracker(cpg);
    }

    @Test
    public void testMisplacedClass()
    {
        MisplacedClass mc =  new MisplacedClass("Misplaced Class", this.cpg);
        while(!mc.getMisplacedClasses().isEmpty())
        {
            System.out.println(mc.detectNext().classes[0] + " is Misplaced");
        }

    }
}