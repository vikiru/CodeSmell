package com.CodeSmell;

import java.util.HashMap;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
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
    public void testOwnProject() {
        Parser p = new Parser();
        JoernServer.start(false);
        CodePropertyGraph g = p.initializeCPG("src/main/python/joernFiles/sourceCode.json");
        HashMap<CPGClass, Boolean> connectedClasses = new HashMap<CPGClass, Boolean>(); 
         for (CPGClass c : g.getClasses()) {
            connectedClasses.put(c, false);
        }


        for (CodePropertyGraph.Relation r : g.getRelations()) {
            boolean sourceCheck = connectedClasses.get(r.source); 
            boolean destCheck = connectedClasses.get(r.destination);
            // ensure the classes in the relation are the
            // ones in the classes list
            String s = "Class in relation not found in cpg class list\n" + r;
            assertEquals(s, sourceCheck, false);
            assertEquals(s, destCheck, false);

            connectedClasses.put(r.source, true);
            connectedClasses.put(r.destination, true);
        }

        connectedClasses.forEach( (c, isConnected) -> {
            assertEquals("Disconnected class\n" + c, isConnected, true);
        });
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
