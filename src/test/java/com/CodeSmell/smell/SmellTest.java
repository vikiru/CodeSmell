package com.CodeSmell.smell;

import java.util.ArrayList;
import java.nio.file.Paths;

import com.CodeSmell.parser.*;
import com.CodeSmell.smell.GodClass;


import com.CodeSmell.ProjectManager;

import com.CodeSmell.smell.Smell.CodeFragment;
import static org.junit.Assert.*;
import static com.CodeSmell.smell.Common.initStatTracker;

import org.junit.Before;
import org.junit.Test;

public class SmellTest {

    private Parser p;
    private CodePropertyGraph cpg;

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

        // one description should suggest to move 
        // move methodWithError() into new interface 
        // with the classes that implement it [NoneISPClass]
        // 

        // another should suggest
        // move blankMethod() into a new interface
        // with [NoneISPClass, ISPClassThree, ISPClassTwo]
        assertNotEquals(0, detections.size());
    }
}