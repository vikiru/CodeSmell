package com.CodeSmell.smell;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Set;

import com.CodeSmell.parser.*;
import com.CodeSmell.smell.GodClass;


import com.CodeSmell.ProjectManager;

import com.CodeSmell.smell.Smell.CodeFragment;
import com.CodeSmell.parser.CPGClass.*;
import static org.junit.Assert.*;
import static com.CodeSmell.smell.Common.initStatTracker;
import static com.CodeSmell.smell.Common.interfaceMethods;
import static com.CodeSmell.smell.Common.stats;
import static com.CodeSmell.smell.Common.findClassByName;
import com.CodeSmell.stat.StatTracker;
import org.junit.Before;
import org.junit.Test;

public class SmellTest {

    private Parser p;
    private CodePropertyGraph cpg;
    private StatTracker stats;

    @Before
    public void before() {
        this.cpg = ProjectManager.getCPG("testproject");
        initStatTracker(cpg);
    }

    public ArrayList<CodeFragment> getDetections(Smell smell) {
        ArrayList<CodeFragment> arr = new ArrayList<CodeFragment>();
        System.out.println("\nsmell " + smell.name);
        while (smell.detect()) {
            CodeFragment f = smell.lastDetection;
            arr.add(f);
            System.out.println(f);
        }
        return arr;
    }

    @Test
    public void TestGodClass() {
        System.out.println("GodClass Test:");
        GodClass gc = new GodClass(this.cpg, -1, -1, -1, -1);
        ArrayList<CodeFragment> detections = getDetections(gc);
        //assertNotEquals(0, detections.size());
    }

    @Test
    public void TestISPViolation() {
        System.out.println("ISP Violation Test:");
        ISPViolation  smell = new ISPViolation(this.cpg);
        ArrayList<CodeFragment> detections = getDetections(smell);
        assertEquals(2, detections.size());

        // one description should suggest to move 
        // move methodWithError() into new interface 
        // with the classes that implement it [NoneISPClass]
        // 

        // another should suggest
        // move blankMethod() into a new interface
        // with [NoneISPClass, ISPClassThree, ISPClassTwo]

        CPGClass c2 = findClassByName(this.cpg, "ISPClass.ISPClassTwo");
        CPGClass c3 = findClassByName(this.cpg, "ISPClass.ISPClassThree");
        assertNotNull(c2);
        assertNotNull(c3);
        ArrayList<Method> ifaceMethods = new ArrayList<Method>(
            Arrays.asList(interfaceMethods(c2)));
        Method blankMethod = ifaceMethods
            .stream()
            .filter(m -> 
                m.toString().equals("blankMethod() : void")).findFirst().get();

        assertNotNull(blankMethod);

        CPGClass[] classes = new CPGClass[] {c3, c2};
        Method[] methods = new Method[] {blankMethod};
        Collection<CodeFragment> detection1 = detections
                .stream()
                .filter(codeFrag -> 
                    Set.of(codeFrag.classes).equals(Set.of(classes)))
                    //    && 
                   // Arrays.equals(codeFrag.methods, methods))
                .collect(Collectors.toList());
        assertEquals(1, detection1.size());
    }
}