package com.CodeSmell.smell;

import java.nio.file.Paths;

import com.CodeSmell.parser.*;
import com.CodeSmell.smell.GodClass;


import com.CodeSmell.ProjectManager;

import org.junit.Before;
import org.junit.Test;

public class SmellTest {

    private Parser p;
    private CodePropertyGraph cpg;

    @Before
    public void before() {
        this.cpg = ProjectManager.getCPG("testproject");
    }

    @Test
    public void TestGodClass() {
        System.out.println("GodClass Test:");
        GodClass gc = new GodClass(this.cpg, -1, -1, -1, -1);
        while (gc.detect()) {
            System.out.println(gc.lastDetection);
        }
    }
}