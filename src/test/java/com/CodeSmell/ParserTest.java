package com.CodeSmell;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import com.CodeSmell.CPGClass;
import com.CodeSmell.JoernServer;

//todo - I'll do this once I finish Parser - V
public class ParserTest {

    public class ClassA {
        public final int[] arr;
        ClassA(int[] arr) {
            this.arr = arr;
        }

        public boolean compare(ClassA other) {
            return other.arr == this.arr;
        }
    }

    @Test
    public void testInitializeCPG() {
        Parser p = new Parser();
        JoernServer.start(true);
        CodePropertyGraph g = p.initializeCPG("src/main/python/joernFiles/sourceCode.json");
        //assertEquals(g.getRelations().size(), g.getClasses().size(), 0);
        //int[] arr = new int[] {1, 2, 3};
        //ClassA a = new ClassA(arr);
        //ClassA a2 = new ClassA(arr);
       // System.out.println(a.compare(a2));

        for (CodePropertyGraph.Relation r : g.getRelations()) {
            System.out.println("----------");
            System.out.print("\t" + r);
            System.out.println("\n----------");
        }
    }

    @Test
    public void testBuildCPG() {
        //CodePropertyGraph g = Parser.buildCPG("src/main/python/joernFiles/sourceCode.json");
        //assertEquals(g.getRelations().size(), g.getClasses().size(), 1);
    }

    @Test
    public void readFromJSON() {
    }

    @Test
    public void testMultiplicities() {
    }

    @Test
    public void testRelationships() {
    }

    @Test
    public void verifyMethodCalls() {
    }
}
