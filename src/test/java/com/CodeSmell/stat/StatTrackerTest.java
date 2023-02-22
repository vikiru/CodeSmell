package com.CodeSmell.stat;

import com.CodeSmell.ProjectManager;
import com.CodeSmell.parser.CodePropertyGraph;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class StatTrackerTest {
    private static CodePropertyGraph cpg;
    private static StatTracker statTracker;
    private static List<ClassStat> classStats;

    @BeforeClass
    public static void before() {
        cpg = ProjectManager.getCPG("testproject");
        statTracker = new StatTracker(cpg);
        classStats = new ArrayList<>(statTracker.classStats.values());
    }

    @Test
    public void testDistinctClassTypes() {
        int expected = cpg.getClasses().size();
        int[] classCheck = {0};
        statTracker.distinctClassTypes.forEach((key, value) -> classCheck[0] += value.size());
        assertEquals("The size of each arraylist within distinctClassTypes added together should equal" +
                "the total number of classes present within cpg", expected, classCheck[0]);
    }

    @Test
    public void testDistinctionRelations() {
        int expected = cpg.getRelations().size();
        int[] relationCheck = {0};
        statTracker.distinctRelations.forEach((key, value) -> relationCheck[0] += value.size());
        assertEquals("The size of each arraylist within distinctRelations added together should equal" +
                "the total number of relations present within cpg", expected, relationCheck[0]);
    }

    @Test
    public void testClassLines() {
        for (ClassStat classStat : classStats) {
            int expected = classStat.totalClassLines;
            int[] check = {0};
            classStat.classLineMap.forEach((key, value) -> check[0] += value);
            assertEquals("Total class lines should be equal to the values of each key within classLineMap combined",
                    expected, check[0]);
        }
    }

    @Test
    public void testClassUsage() {
        for (ClassStat classStat : classStats) {
            int expected = classStat.classUsage;
            int[] check = {0};
            classStat.usageMap.forEach((key, value) -> check[0] += value);
            assertEquals("Class usage should be equal to the values of each key within usageMap combined",
                    expected, check[0]);
        }
    }

    @Test
    public void testAttributeUsage() {
        for (ClassStat classStat : classStats) {
            for (AttributeStat attributeStat : classStat.attributeStats.values()) {
                int expected = attributeStat.attributeUsage;
                int[] methodCheck = {0};
                int[] classCheck = {0};
                attributeStat.methodsWhichCallAttr.forEach((key, value) -> methodCheck[0] += value);
                assertEquals("Attribute usage should be equal to the total counts of " +
                                "the methods that call this attribute combined",
                        expected, methodCheck[0]);
                attributeStat.classesWhichCallAttr.forEach((key, value) -> classCheck[0] += value);
                assertEquals("Attribute usage should be equal to the total counts of " +
                                "the classes that call this attribute combined",
                        expected, classCheck[0]);
            }
        }
    }

    @Test
    public void testMethodUsage() {
        for (ClassStat classStat : classStats) {
            for (MethodStat methodStat : classStat.methodStats.values()) {
                int expected = methodStat.methodUsage;
                int[] methodCheck = {0};
                int[] classCheck = {0};
                methodStat.methodsWhichCallMethod.forEach((key, value) -> methodCheck[0] += value);
                assertEquals("Method usage should be equal to the total counts of " +
                                "the methods that call this method combined",
                        expected, methodCheck[0]);
                methodStat.classesWhichCallMethod.forEach((key, value) -> classCheck[0] += value);
                assertEquals("Attribute usage should be equal to the total counts of " +
                                "the classes that call this method combined",
                        expected, classCheck[0]);
            }
        }
    }


}
