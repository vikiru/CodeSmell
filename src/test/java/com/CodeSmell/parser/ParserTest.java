package com.CodeSmell.parser;

import com.CodeSmell.ProjectManager;
import com.CodeSmell.stat.StatTracker;
import com.google.gson.Gson;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class ParserTest {

    private static CodePropertyGraph ourCPGWithRelations;
    private static CodePropertyGraph ourCPGWithoutRelations;

    // run the before method only once, for all tests
    @BeforeClass
    public static void before() {
        ourCPGWithRelations = ProjectManager.getCPG("testproject");
        ourCPGWithoutRelations = new CodePropertyGraph();

        // Create a CPG object without any relations
        for (CPGClass c : ourCPGWithRelations.getClasses()) {
            ourCPGWithoutRelations.addClass(c);
        }
    }

    @Test
    public void readFromJSON() {
        assertEquals("CPG has no relations within it.", 0, ourCPGWithoutRelations.getRelations().size());
        assertTrue("CPG has classes within it", ourCPGWithoutRelations.getClasses().size() > 0);

        assertNotNull(ourCPGWithRelations);
        assertTrue("CPG has relations within it", ourCPGWithRelations.getRelations().size() > 0);
        assertTrue("CPG has classes within it", ourCPGWithRelations.getClasses().size() > 0);

        // Compare the CPGs with and without relations
        assertTrue("Both CPGs should have same number of classes",
                ourCPGWithRelations.getClasses().size() == ourCPGWithoutRelations.getClasses().size());
        assertTrue("Both CPGs should not have the same number of relations",
                (ourCPGWithRelations.getRelations().size() != ourCPGWithoutRelations.getRelations().size()
                        || ourCPGWithRelations.getRelations().size() == 0));
    }

    @Test
    public void testDuplicateRelations() {
        for (CodePropertyGraph.Relation r : ourCPGWithRelations.getRelations()) {
            CPGClass cpgClass = r.source;
            CPGClass destClass = r.destination;

            var additionalCheck = ourCPGWithRelations.getRelations().stream()
                    .filter(relation -> relation.source.equals(cpgClass)
                            && relation.destination.equals(destClass)
                            && relation.type.equals(r.type)
                            && relation.multiplicity.equals(r.multiplicity))
                    .collect(Collectors.toList());

            assertEquals("There should be no duplicate relations between src and dest class",
                    true, additionalCheck.size() == 1);
        }
    }

    @Test
    public void testOwnProject() {
        HashMap<CPGClass, Boolean> connectedClasses = new HashMap<CPGClass, Boolean>();
        for (CPGClass c : ourCPGWithRelations.getClasses()) {
            connectedClasses.put(c, false);
        }

        for (CodePropertyGraph.Relation r : ourCPGWithRelations.getRelations()) {
            boolean sourceCheck = connectedClasses.get(r.source);
            boolean destCheck = connectedClasses.get(r.destination);
            // ensure the classes in the relation are the
            // ones in the classes list
            String s = "Class in relation not found in cpg class list\n";
            assertNotNull(s + r.source, sourceCheck);
            assertNotNull(s + r.destination, destCheck);

            connectedClasses.put(r.source, true);
            connectedClasses.put(r.destination, true);
        }

        connectedClasses.forEach((c, isConnected) -> {
            assertEquals("Disconnected class: " + c, isConnected, true);
        });
    }
}
