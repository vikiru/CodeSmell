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
import static com.CodeSmell.smell.Common.originalInterfaceMethods;
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
        GodClass gc = new GodClass(this.cpg, -1, -1, 0, -1);
        ArrayList<CodeFragment> detections = getDetections(gc);
        assertEquals(0, detections.size());
        gc = new GodClass(this.cpg, -1, 0.3, 0, -1);
        detections = getDetections(gc);
        assertEquals(1, detections.size());
    }

    @Test
    public void TestISPViolation() {
        System.out.println("ISP Violation Test:");
        ISPViolation  smell = new ISPViolation(this.cpg);
        ArrayList<CodeFragment> detections = getDetections(smell);
        assertEquals(2, detections.size());

        CPGClass c2 = findClassByName(this.cpg, "ISPClass.ISPClassTwo");
        CPGClass c3 = findClassByName(this.cpg, "ISPClass.ISPClassThree");
        assertNotNull(c2);
        assertNotNull(c3);

        ArrayList<Method> ifaceMethods = new ArrayList<Method>(
            Arrays.asList(interfaceMethods(c2)));
        Method blankMethod = Arrays.stream(originalInterfaceMethods(c2))
            .filter(m -> m.toString().equals("blankMethod() : void")).findFirst().get();
        assertNotNull(blankMethod);

        CPGClass[] classes = new CPGClass[] {c3, c2};

        Collection<CodeFragment> detection1 = detections
            .stream()
            .filter(codeFrag -> 
                Set.of(codeFrag.classes).equals(Set.of(classes))
                    && 
                Set.of(codeFrag.methods).equals(Set.of(blankMethod)))
            .collect(Collectors.toList());
        assertEquals(1, detection1.size());
    }
}