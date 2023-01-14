package com.CodeSmell;

import com.google.gson.Gson;
import org.junit.Before;
import org.junit.BeforeClass;
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

    private final static String jsonPath = "src/main/python/joernFiles/sourceCode.json";
    private static Parser p;
    private static Gson gson;
    private static CodePropertyGraph ourCPGWithRelations;
    private static CodePropertyGraph ourCPGWithoutRelations;

    // run the test only once
    @BeforeClass
    public static void before() {
        p = new Parser();
        gson = new Gson();
        String ourDirectory = Paths.get("").toAbsolutePath() + "/src/main/java/com/CodeSmell";
        JoernServer.start(false, ourDirectory);
        // Read the joern file using only gson (will only have classes, no relations)
        try (Reader reader = Files.newBufferedReader(Paths.get(jsonPath))) {
            ourCPGWithoutRelations = gson.fromJson(reader, CodePropertyGraph.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Obtain CPG after Parser has properly initialized it, using our project's
        // source code
        ourCPGWithRelations = p.initializeCPG(jsonPath);
    }

    @Test
    public void readFromJSON() {
        assertEquals("CPG has no relations within it.", true, ourCPGWithoutRelations.getRelations().size() == 0);
        assertEquals("CPG has classes within it", true, ourCPGWithoutRelations.getClasses().size() > 0);

        // Read from the JSON obtained after Parser
        assertNotNull(ourCPGWithRelations);
        assertEquals("CPG has relations within it", true, ourCPGWithRelations.getRelations().size() > 0);
        assertEquals("CPG has classes within it", true, ourCPGWithRelations.getClasses().size() > 0);

        // Compare the CPG obtained after joern_query and reading by gson vs. the CPG
        // after Parser has properly initialized it
        assertEquals("Both CPGs should have same number of classes", true,
                ourCPGWithRelations.getClasses().size() == ourCPGWithoutRelations.getClasses().size());
        assertEquals("Both CPGS should not have the same number of relations",
                ourCPGWithRelations.getRelations().size() != ourCPGWithoutRelations.getRelations().size());
    }

    @Test
    public void writeToFile() {
        String filePath = "src/main/python/joernFiles/sourceCodeEmpty.json";
        // Write an empty graph to a file
        CodePropertyGraph graph = new CodePropertyGraph();
        p.writeToJson(graph, filePath);

        // Read in the empty graph json file and ensure both graphs has 0 classes and 0
        // relations
        CodePropertyGraph readGraph = p.initializeCPG(filePath);
        assertEquals("Both CPGS have no classes", graph.getClasses().size(), readGraph.getClasses().size());
        assertEquals("Both CPGS have no relations", graph.getRelations().size(), readGraph.getRelations().size());

        // Delete the empty file
        File fileToDelete = new File(filePath);
        assertTrue(fileToDelete.delete());
    }

    @Test
    public void testDuplicateRelations() {
        for (CodePropertyGraph.Relation r : ourCPGWithRelations.getRelations()) {
            CPGClass cpgClass = r.source;
            CPGClass destClass = r.destination;

            var additionalCheck = ourCPGWithRelations.getRelations().stream()
                    .filter(relation -> relation.source.equals(cpgClass)
                            && relation.destination.equals(destClass))
                    .collect(Collectors.toList());

            assertEquals("There should be no duplicate relations between src and dest class",
                    true, additionalCheck.size() == 1);
        }
    }

    @Test
    public void testAssociationRelationship() {
        var allAssociationRelationships = ourCPGWithRelations.getRelations().stream()
                .filter(relation -> relation.type.equals(ClassRelation.RelationshipType.UNIDIRECTIONAL_ASSOCIATION) ||
                        relation.type.equals(ClassRelation.RelationshipType.BIDIRECTIONAL_ASSOCIATION) ||
                        relation.type.equals(ClassRelation.RelationshipType.REFLEXIVE_ASSOCIATION))
                .collect(Collectors.toList());

        for (CodePropertyGraph.Relation r : allAssociationRelationships) {
            CPGClass sourceClass = r.source;
            CPGClass destClass = r.destination;
            ClassRelation.RelationshipType type = r.type;

            // collect all attributes of destClass where the attributeType matches
            // sourceClass's name
            // list should be empty for unidirectional and should not be empty for
            // bidirectional association
            var destAttributes = Arrays.stream(destClass.attributes)
                    .filter(attribute -> attribute.attributeType.contains(sourceClass.name))
                    .collect(Collectors.toList());
            destAttributes.forEach(attribute -> System.out.println(attribute));
            boolean destClassHasNoSrcClassAttribute = destAttributes.isEmpty();

            // reflexive association check
            boolean sameClass = sourceClass == destClass;
            if (type.equals(ClassRelation.RelationshipType.REFLEXIVE_ASSOCIATION)) {
                assertEquals("Same class should have a reflexive association", sameClass,
                        type.equals(ClassRelation.RelationshipType.REFLEXIVE_ASSOCIATION));
            }

            if (type.equals(ClassRelation.RelationshipType.UNIDIRECTIONAL_ASSOCIATION)) {
                // unidirectional association check (1 way association, src -> dest)
                assertEquals("Unidirectional relation is valid", true,
                        destClassHasNoSrcClassAttribute);
            }

            if (type.equals(ClassRelation.RelationshipType.BIDIRECTIONAL_ASSOCIATION)) {
                // bidirectional association check (2 way association, src -> dest)
                assertEquals("Bidirectionl association is valid", true,
                        !destClassHasNoSrcClassAttribute);
            }

            assertEquals("Multiplicity should not be empty", false, r.multiplicity.equals(""));
        }
    }

    @Test
    public void testCompositionRelationship() {
        var allCompositonRelationships = ourCPGWithRelations.getRelations().stream()
                .filter(relation -> relation.type.equals(ClassRelation.RelationshipType.COMPOSITION))
                .collect(Collectors.toList());
        for (CodePropertyGraph.Relation r : allCompositonRelationships) {
            CPGClass sourceClass = r.source;
            CPGClass destClass = r.destination;

            var attributesMatchingDest = Arrays.stream(sourceClass.attributes)
                    .filter(attribute -> attribute.attributeType.equals(destClass.name)).collect(Collectors.toList());
            var constructorResult = Arrays.stream(sourceClass.methods)
                    .filter(method -> method.name.equals(sourceClass.name)).collect(Collectors.toList());

            if (!constructorResult.isEmpty()) {
                CPGClass.Method constructor = constructorResult.get(0);
                assertEquals("Dest class should not be within constructor params",
                        true, !constructor.methodBody.contains(destClass.name));
                var constructorInstructions = Arrays.stream(constructor.instructions)
                        .filter(instruction -> instruction.code.contains(destClass.name)).collect(Collectors.toList());
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
        var allDependencyRelationships = ourCPGWithRelations.getRelations().stream()
                .filter(relation -> relation.type.equals(ClassRelation.RelationshipType.INHERITANCE))
                .collect(Collectors.toList());

        for (CodePropertyGraph.Relation r : allDependencyRelationships) {
            CPGClass sourceClass = r.source;
            CPGClass destinationClass = r.destination;
            var sourceClassMethods = Arrays.stream(sourceClass.methods)
                    .filter(method -> !method.name.equals(sourceClass.name)).collect(Collectors.toList());
            var sourceClassAttributes = Arrays.stream(sourceClass.attributes)
                    .filter(attribute -> attribute.attributeType.equals(destinationClass.name))
                    .collect(Collectors.toList());
            assertEquals("Source class should not have attributes matching destination class",
                    true, sourceClassAttributes.isEmpty());

            for (CPGClass.Method m : sourceClassMethods) {
                var methodCalls = m.getMethodCalls().stream()
                        .filter(method -> method.parentClassName.equals(destinationClass)).collect(Collectors.toList());
                boolean destClassWithinMethodBody = m.methodBody.contains(destinationClass.name);
                boolean destClassWithinMethodCalls = methodCalls.isEmpty();
                assertEquals("Destination class should be within method params or method calls", true,
                        destClassWithinMethodBody || destClassWithinMethodCalls);
            }
        }

    }

    @Test
    public void testInheritanceRelationship() {
        var allInheritanceRelationships = ourCPGWithRelations.getRelations().stream()
                .filter(relation -> relation.type.equals(ClassRelation.RelationshipType.INHERITANCE))
                .collect(Collectors.toList());

        for (CodePropertyGraph.Relation r : allInheritanceRelationships) {
            CPGClass subClass = r.source;
            CPGClass superClass = r.destination;

            var subClassAllMethods = Arrays.stream(subClass.methods)
                    .filter(method -> method.parentClassName.contains(superClass.name)).collect(Collectors.toList());

            assertEquals("Subclass should have the methods of superclass", false, subClassAllMethods.isEmpty());
            assertEquals("Subclass extends superclass", true,
                    subClass.code.contains("extends") && subClass.code.contains(superClass.name));
            assertEquals("Superclass does not extend subclass", true, !superClass.code.contains(subClass.name));
            assertEquals("Multiplicity should be left blank", "", r.multiplicity);
        }
    }

    @Test
    public void testRealizationRelationship() {
        var allRealizationRelationships = ourCPGWithRelations.getRelations().stream()
                .filter(relation -> relation.type.equals(ClassRelation.RelationshipType.REALIZATION))
                .collect(Collectors.toList());

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
        // Read the joern file using only gson, check to make sure classes are present
        // and no relations are present
        try (Reader reader = Files.newBufferedReader(Paths.get(jsonPath))) {
            CodePropertyGraph withoutRelations = gson.fromJson(reader, CodePropertyGraph.class);
            for (CPGClass cpgClass : withoutRelations.getClasses()) {
                assertEquals("Class code should be empty", "", cpgClass.code);
                assertEquals("Class import statements should be empty", 0, cpgClass.importStatements.length);
                assertEquals("Class modifiers should be empty", 0, cpgClass.modifiers.length);
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
    public void testAssignProperFieldsAndMethods() {
        try (Reader reader = Files.newBufferedReader(Paths.get(jsonPath))) {
            CodePropertyGraph badCPG = gson.fromJson(reader, CodePropertyGraph.class);
            for (CPGClass c : badCPG.getClasses()) {
                for (CPGClass.Attribute a : c.attributes) {
                    assertNotNull(a);
                    if (a.packageName.equals("java.util") && !a.attributeType.contains("<")) {
                        assertEquals("Type should be just ArrayList", "ArrayList", a.attributeType);
                    }
                }
                for (CPGClass.Method m : c.methods) {
                    assertNotNull(m);
                }
            }
            CodePropertyGraph goodCPG = p.assignProperAttributesAndMethods(badCPG, 2);
            for (CPGClass c2 : goodCPG.getClasses()) {
                for (CPGClass.Attribute a2 : c2.attributes) {
                    assertNotNull(a2);
                    if (a2.packageName.equals("java.util")) {
                        assertEquals("Type should not be just ArrayList", true, a2.attributeType.contains("<"));
                    }
                }
                for (CPGClass.Method m2 : c2.methods) {
                    assertNotNull(m2);
                    assertEquals("Method calls should be greater than or equal to 0",
                            true, m2.getMethodCalls().size() >= 0);
                }
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
        assertEquals("Type should be a space separated string", "CPGClass Modifier", p.getProperTypeName(hashMap));
        assertEquals("Type should be a space separated string",
                "HashMap HashMap HashMap HashMap String String String String String String",
                p.getProperTypeName(complexType));
        assertEquals("Type should not have the $ or CPGClass", "Modifier", p.getProperTypeName(nestedClass));
    }
    // ----------------------------------------------------------------------------------------------------------

    @Test
    public void testInitializeCPG() {
        JoernServer.start(true, "");
        CodePropertyGraph g = p.initializeCPG(jsonPath);
        // assertEquals(g.getRelations().size(), g.getClasses().size(), 0);
        // int[] arr = new int[] {1, 2, 3};
        // ClassA a = new ClassA(arr);
        // ClassA a2 = new ClassA(arr);
        // System.out.println(a.compare(a2));

        for (CodePropertyGraph.Relation r : g.getRelations()) {
            System.out.println("----------");
            System.out.print("\t" + r);
            System.out.println("\n----------");
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

    public class ClassA {
        public final int[] arr;

        ClassA(int[] arr) {
            this.arr = arr;
        }

        public boolean compare(ClassA other) {
            return other.arr == this.arr;
        }
    }

}
