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
        while (smell.detect()) {
            arr.add(smell.lastDetection);
        }
        return arr;
    }

    @Test
    public void TestGodClass() {
        System.out.println("GodClass Test:");
        GodClass gc = new GodClass(this.cpg, -1, -1, -1, -1);
        ArrayList<CodeFragment> detections = getDetections(gc);
        assertNotEquals(0, detections.size());
    }

    @Test
    public void TestLazyClass()
    {
        LazyClass lc = new LazyClass("lazyClass", this.cpg);
        lc.description();
        lc.returnLazyClasses();
    }
}