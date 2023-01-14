package com.CodeSmell;

import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class ParserTest {

    private final String jsonPath = "src/main/python/joernFiles/sourceCode.json";
    private Parser p;
    private Gson gson;

    @Before
    public void before() {
        p = new Parser();
        gson = new Gson();
    }

    @Test
    public void readFromJSON() {
        // Read the joern file using only gson, check to make sure classes are present and no relations are present
        try (Reader reader = Files.newBufferedReader(Paths.get(jsonPath))) {
            CodePropertyGraph withoutRelations = gson.fromJson(reader, CodePropertyGraph.class);
            assertEquals("CPG has no relations within it.", false, withoutRelations.getRelations().size() > 0);
            assertEquals("CPG has classes within it", true, withoutRelations.getClasses().size() > 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Read from the JSON obtained after Joern integration
        CodePropertyGraph g = p.initializeCPG(jsonPath);
        assertNotNull(g);
        assertEquals("CPG has relations within it", true, g.getRelations().size() > 0);
        assertEquals("CPG has classes within it", true, g.getClasses().size() > 0);

        // Read from the JSON obtained after Parser writes to JSON file
        CodePropertyGraph g2 = p.initializeCPG("src/main/python/joernFiles/sourceCodeWithRelations.json");
        assertNotNull(g2);
        assertEquals("CPG has relations within it", true, g2.getRelations().size() > 0);
        assertEquals("CPG has classes within it", true, g2.getClasses().size() > 0);

        assertEquals("Both CPGS have the same number of classes", g.getClasses().size(), g2.getClasses().size());
        assertEquals("Both CPGS have the same number of relations", g.getRelations().size(), g2.getRelations().size());
    }

    @Test
    public void writeToFile() {
        String filePath = "src/main/python/joernFiles/sourceCodeEmpty.json";
        // Write an empty graph to a file
        CodePropertyGraph graph = new CodePropertyGraph();
        p.writeToJson(graph, filePath);

        // Read in the empty graph json file and ensure both graphs has 0 classes and 0 relations
        CodePropertyGraph readGraph = p.initializeCPG(filePath);
        assertEquals("Both CPGS have no classes", graph.getClasses().size(), readGraph.getClasses().size());
        assertEquals("Both CPGS have no relations", graph.getRelations().size(), readGraph.getRelations().size());

        // Delete the empty file
        File fileToDelete = new File(filePath);
        assertTrue(fileToDelete.delete());
    }

    @Test
    public void testAssociationRelationship() {
        String filePath = "src/main/python/joernFiles/sourceCodeWithRelations.json";
        CodePropertyGraph cpg = p.initializeCPG(filePath);

        var allAssociationRelationships = cpg.getRelations().stream().
                filter(relation -> relation.type.equals(ClassRelation.Type.UNIDIRECTIONAL_ASSOCIATION) ||
                        relation.type.equals(ClassRelation.Type.BIDIRECTIONAL_ASSOCIATION) ||
                        relation.type.equals(ClassRelation.Type.REFLEXIVE_ASSOCIATION)).collect(Collectors.toList());

        for (CodePropertyGraph.Relation r : allAssociationRelationships) {
            CPGClass sourceClass = r.source;
            CPGClass destClass = r.destination;
            ClassRelation.Type type = r.type;

            // collect all attributes of destClass where the attributeType matches sourceClass's name
            // list should be empty for unidirectional and should not be empty for bidirectional association
            var destAttributes = Arrays.stream(destClass.attributes).
                    filter(attribute -> attribute.attributeType.equals(sourceClass.name)).collect(Collectors.toList());
            boolean destClassHasNoSrcClassAttribute = destAttributes.isEmpty();

            // reflexive association check
            boolean sameClass = sourceClass == destClass;
            assertEquals("Same class should have a reflexive association", sameClass,
                    type.equals(ClassRelation.Type.REFLEXIVE_ASSOCIATION));

            // unidirectional association check (1 way association, src -> dest)
            assertEquals("Unidirectional relation is valid", true,
                    destClassHasNoSrcClassAttribute &&
                            type.equals(ClassRelation.Type.UNIDIRECTIONAL_ASSOCIATION));

            assertEquals("Unidirectional relation should not be valid", false,
                    !destClassHasNoSrcClassAttribute &&
                            type.equals(ClassRelation.Type.UNIDIRECTIONAL_ASSOCIATION));

            // bidirectional association check (2 way association, src -> dest)
            assertEquals("Bidirectionl association is valid", true,
                    !destClassHasNoSrcClassAttribute && type.equals(ClassRelation.Type.BIDIRECTIONAL_ASSOCIATION));

            assertEquals("Bidirectional association should not be valid", false,
                    destClassHasNoSrcClassAttribute && type.equals(ClassRelation.Type.BIDIRECTIONAL_ASSOCIATION));

            assertEquals("Multiplicity should not be empty", false, r.multiplicity.equals(""));
        }
    }

    @Test
    public void testCompositionRelationship() {
        String filePath = "src/main/python/joernFiles/sourceCodeWithRelations.json";
        CodePropertyGraph cpg = p.initializeCPG(filePath);
        var allCompositonRelationships = cpg.getRelations().stream().
                filter(relation -> relation.type.equals(ClassRelation.Type.COMPOSITION)).collect(Collectors.toList());
        for (CodePropertyGraph.Relation r : allCompositonRelationships) {
            CPGClass sourceClass = r.source;
            CPGClass destClass = r.destination;
            var attributesMatchingDest = Arrays.stream(sourceClass.attributes).
                    filter(attribute -> attribute.attributeType.equals(destClass.name)).collect(Collectors.toList());
            var constructorResult = Arrays.stream(sourceClass.methods).
                    filter(method -> method.name.equals(sourceClass.name)).collect(Collectors.toList());

            if (!constructorResult.isEmpty()) {
                CPGClass.Method constructor = constructorResult.get(0);
                assertEquals("Dest class should not be within constructor params",
                        true, !constructor.methodBody.contains(destClass.name));
                var constructorInstructions = Arrays.stream(constructor.instructions).filter(instruction ->
                        instruction.code.contains(destClass.name)).collect(Collectors.toList());
                assertEquals("Constructor instructions should contain dest class", true,
                        !constructorInstructions.isEmpty());
            } else {
                for (CPGClass.Attribute a : attributesMatchingDest) {
                    assertEquals("Attribute should not be static", true, !a.code.contains("static"));
                }
            }
        }
    }

    @Test
    public void testDependencyRelationship() {
        String filePath = "src/main/python/joernFiles/sourceCodeWithRelations.json";
        CodePropertyGraph cpg = p.initializeCPG(filePath);
        var allDependencyRelationships = cpg.getRelations().stream().
                filter(relation -> relation.type.equals(ClassRelation.Type.INHERITANCE)).collect(Collectors.toList());

        for (CodePropertyGraph.Relation r : allDependencyRelationships) {
            CPGClass sourceClass = r.source;
            CPGClass destinationClass = r.destination;
            var sourceClassMethods = Arrays.stream(sourceClass.methods).
                    filter(method -> !method.name.equals(sourceClass.name)).collect(Collectors.toList());
            var sourceClassAttributes = Arrays.stream(sourceClass.attributes).
                    filter(attribute -> attribute.attributeType.equals(destinationClass.name)).collect(Collectors.toList());
            assertEquals("Source class should not have attributes matching destination class",
                    true, sourceClassAttributes.isEmpty());

            for (CPGClass.Method m : sourceClassMethods) {
                var methodCalls = m.getMethodCalls().stream().
                        filter(method -> method.parentClassName.equals(destinationClass)).collect(Collectors.toList());
                boolean destClassWithinMethodBody = m.methodBody.contains(destinationClass.name);
                boolean destClassWithinMethodCalls = methodCalls.isEmpty();
                assertEquals("Destination class should be within method params or method calls", true,
                        destClassWithinMethodBody || destClassWithinMethodCalls);
            }
        }

    }

    @Test
    public void testInheritanceRelationship() {
        String filePath = "src/main/python/joernFiles/sourceCodeWithRelations.json";
        CodePropertyGraph cpg = p.initializeCPG(filePath);
        var allInheritanceRelationships = cpg.getRelations().stream().
                filter(relation -> relation.type.equals(ClassRelation.Type.INHERITANCE)).collect(Collectors.toList());

        for (CodePropertyGraph.Relation r : allInheritanceRelationships) {
            CPGClass subClass = r.source;
            CPGClass superClass = r.destination;
            assertEquals("Subclass extends superclass", true,
                    subClass.code.contains("extends") && subClass.code.contains(superClass.name));
            assertEquals("Superclass does not extend subclass", true, !superClass.code.contains(subClass.name));
            assertEquals("Multiplicity should be left blank", "", r.multiplicity);
        }
    }

    @Test
    public void testRealizationRelationship() {
        String filePath = "src/main/python/joernFiles/sourceCodeWithRelations.json";
        CodePropertyGraph cpg = p.initializeCPG(filePath);
        var allRealizationRelationships = cpg.getRelations().stream().
                filter(relation -> relation.type.equals(ClassRelation.Type.REALIZATION)).collect(Collectors.toList());

        for (CodePropertyGraph.Relation r : allRealizationRelationships) {
            CPGClass source = r.source;
            CPGClass destination = r.destination;
            assertEquals("Source class should be a class or abstract class",
                    true, source.classType.contains("class"));
            assertEquals("Destination class should be an interface", true,
                    destination.classType.contains("interface"));

            // can implement multiple interfaces, but extend only one
            assertEquals("Source class should contain implements and destination class within declaration", true,
                    source.code.contains("implements") && source.code.contains(destination.name));
            assertEquals("Destination class should not implement", true, !destination.code.contains("implements"));
        }
    }

    @Test
    public void testMissingClassInfo() {
        // Read the joern file using only gson, check to make sure classes are present and no relations are present
        try (Reader reader = Files.newBufferedReader(Paths.get(jsonPath))) {
            CodePropertyGraph withoutRelations = gson.fromJson(reader, CodePropertyGraph.class);
            for (CPGClass cpgClass : withoutRelations.getClasses()) {
                assertNull("Class code should be null", cpgClass.code);
                assertNull("Class import statements should be null", cpgClass.importStatements);
                assertNull("Class modifiers should be null", cpgClass.modifiers);
                assertEquals("Package name should contain src", true, cpgClass.packageName.contains("src"));

                CPGClass updatedClass = p.assignMissingClassInfo(cpgClass);
                assertNotNull("Class code should not be null", updatedClass.code);
                assertNotNull("Class import statements should not be null", updatedClass.importStatements);
                assertNotNull("Class modifiers should not be null", updatedClass.modifiers);
                assertEquals("Package name should not contain src", false,
                        updatedClass.packageName.contains("src"));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testMultiplicity() {
        String singularInstance = "UMLClass source";
        String hashMap = "HashMap<CPGClass, Modifier>";
        String arr = "Position[]";
        String complexType = "HashMap<HashMap<HashMap<HashMap<HashMap<String, String>, String>, String>, String>, String>";
        assertEquals("Multiplicity should be 1..*", "1..*", p.obtainMultiplicity(hashMap, 1L));
        assertEquals("Multiplicity should be 1..*", "1..*", p.obtainMultiplicity(arr, 1L));
        assertEquals("Multiplicity should be 1..*", "1..*", p.obtainMultiplicity(complexType, 1L));
        assertEquals("Multiplicity should not be 1..*", "1..1", p.obtainMultiplicity(singularInstance, 1L));
        assertEquals("Multiplicity should be 1..N", "1..2", p.obtainMultiplicity(singularInstance, 2L));
        assertEquals("Multiplicity should be 1..N", "1..999", p.obtainMultiplicity(singularInstance, 999L));
    }

    @Test
    public void testGetProperName() {
        String singularInstance = "UMLClass";
        String hashMap = "HashMap<CPGClass, Modifier>";
        String arr = "Position[]";
        String nestedClass = "CPGClass$Modifier";
        String complexType = "HashMap<HashMap<HashMap<HashMap<HashMap<String, String>, String>, String>, String>, String>";

        assertEquals("Type should not have changed", singularInstance, p.getProperTypeName(singularInstance));
        assertEquals("Type should remove the []", "Position", p.getProperTypeName(arr));
        assertEquals("Type should be a comma separated string", " CPGClass Modifier", p.getProperTypeName(hashMap));
        assertEquals("Type should be a comma separated string",
                " HashMap HashMap HashMap HashMap String String String String String String",
                p.getProperTypeName(complexType));

        assertEquals("Type should not have the $ or CPGClass", "Modifier", p.getProperTypeName(nestedClass));
    }
    // ----------------------------------------------------------------------------------------------------------

    @Test
    public void testOwnProject() {
        JoernServer js = new JoernServer();
        js.start(false);
        CodePropertyGraph g = p.initializeCPG(jsonPath);
        HashMap<CPGClass, Boolean> connectedClasses = new HashMap<CPGClass, Boolean>();
        for (CPGClass c : g.getClasses()) {
            connectedClasses.put(c, false);
        }

        for (CodePropertyGraph.Relation r : g.getRelations()) {
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
