package com.CodeSmell;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

//todo - I'll do this once I finish Parser - V
public class ParserTest {

    @Test
    public void testInitializeCPG() {
        Parser p = new Parser();
        CodePropertyGraph g = p.initializeCPG("src/main/python/joernFiles/sourceCode.json");
        //assertEquals(g.getRelations().size(), g.getClasses().size(), 0);
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
