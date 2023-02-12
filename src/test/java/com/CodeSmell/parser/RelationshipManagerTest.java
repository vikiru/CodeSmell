package com.CodeSmell.parser;

import com.CodeSmell.ProjectManager;
import com.CodeSmell.model.ClassRelation;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class RelationshipManagerTest {
    private static CodePropertyGraph cpgToAnalyze;

    @BeforeClass
    public static void before() {
        CodePropertyGraph cpg = ProjectManager.getCPG("testproject");
        cpgToAnalyze = new CodePropertyGraph();
        for (CPGClass cpgClass : cpg.getClasses()) {
            cpgToAnalyze.addClass(cpgClass);
        }
        RelationshipManager relationshipManager = new RelationshipManager(cpgToAnalyze);
    }

    @Test
    public void testBidirectionalRelationship() {
        var result = cpgToAnalyze.getRelations().stream().
                filter(relation -> relation.type.equals(ClassRelation.RelationshipType.BIDIRECTIONAL_ASSOCIATION)).
                collect(Collectors.toList());

        assertTrue("There should be an even number of bi-directional relations or 0 relations of this type",
                (result.size() == 0 || result.size() % 2 == 0));

        for (CodePropertyGraph.Relation relation : result) {
            CPGClass sourceClass = relation.source;
            CPGClass destinationClass = relation.destination;
            String multiplicity = relation.multiplicity;
            boolean sameClass = sourceClass == destinationClass;
            assertFalse("Should not be same class", sameClass);
            assertNotEquals("Multiplicity should not be blank", "", multiplicity);

            var destClassHasAttrMatchingSrc = destinationClass.getAttributes().stream().
                    filter(attribute -> attribute.getTypeList().contains(sourceClass)).collect(Collectors.toList());
            assertFalse("Destination class should have reference to source class",
                    destClassHasAttrMatchingSrc.isEmpty());

            var checkMatchingRelation = result.stream().filter(relToFind -> relToFind.source.equals(destinationClass) &&
                    relToFind.destination.equals(sourceClass)).collect(Collectors.toList());
            assertFalse("There should be a matching bi-directional relation in opposite direction",
                    checkMatchingRelation.isEmpty());
        }
    }

    @Test
    public void testUnidirectionalAssociation() {
        var result = cpgToAnalyze.getRelations().stream().
                filter(relation -> relation.type.equals(ClassRelation.RelationshipType.UNIDIRECTIONAL_ASSOCIATION)).
                collect(Collectors.toList());

        for (CodePropertyGraph.Relation relation : result) {
            CPGClass sourceClass = relation.source;
            CPGClass destinationClass = relation.destination;
            String multiplicity = relation.multiplicity;
            boolean sameClass = sourceClass == destinationClass;
            assertFalse("Should not be same class", sameClass);
            assertNotEquals("Multiplicity should not be blank", "", multiplicity);

            var destClassHasAttrMatchingSrc = destinationClass.getAttributes().stream().
                    filter(attribute -> attribute.getTypeList().contains(sourceClass)).collect(Collectors.toList());
            assertTrue("Destination class should not have reference to source class",
                    destClassHasAttrMatchingSrc.isEmpty());
            boolean checkComposition = RelationshipManager.determineCompositionRelationship(sourceClass, destinationClass);
            assertFalse("Composition should not happen in unidirectional relations", checkComposition);
        }
    }

    @Test
    public void testReflexiveAssociation() {
        var result = cpgToAnalyze.getRelations().stream().
                filter(relation -> relation.type.equals(ClassRelation.RelationshipType.REFLEXIVE_ASSOCIATION)).
                collect(Collectors.toList());

        for (CodePropertyGraph.Relation relation : result) {
            CPGClass sourceClass = relation.source;
            CPGClass destinationClass = relation.destination;
            String multiplicity = relation.multiplicity;
            boolean sameClass = sourceClass == destinationClass;
            assertFalse("Should be same class", sameClass);
            assertNotEquals("Multiplicity should not be blank", "", multiplicity);
        }
    }

    @Test
    public void testComposition() {
        var result = cpgToAnalyze.getRelations().stream().
                filter(relation -> relation.type.equals(ClassRelation.RelationshipType.COMPOSITION)).
                collect(Collectors.toList());

        for (CodePropertyGraph.Relation relation : result) {
            CPGClass sourceClass = relation.source;
            CPGClass destinationClass = relation.destination;
            String multiplicity = relation.multiplicity;
            boolean sameClass = sourceClass == destinationClass;
            assertFalse("Should not be same class", sameClass);
            assertNotEquals("Multiplicity should not be blank", "", multiplicity);

            var destClassHasAttrMatchingSrc = destinationClass.getAttributes().stream().
                    filter(attribute -> attribute.getTypeList().contains(sourceClass)).collect(Collectors.toList());
            assertTrue("Destination class should not have reference to source class",
                    destClassHasAttrMatchingSrc.isEmpty());

            boolean checkComposition = RelationshipManager.determineCompositionRelationship(sourceClass, destinationClass);
            assertTrue("There should be a composition between source and destination class", checkComposition);
        }
    }

    @Test
    public void testDependency() {
        var result = cpgToAnalyze.getRelations().stream().
                filter(relation -> relation.type.equals(ClassRelation.RelationshipType.DEPENDENCY)).
                collect(Collectors.toList());

        for (CodePropertyGraph.Relation relation : result) {
            CPGClass sourceClass = relation.source;
            CPGClass destinationClass = relation.destination;
            String multiplicity = relation.multiplicity;

            ArrayList<CPGClass.Method> allSourceMethodCalls = new ArrayList<>();
            sourceClass.getMethods().forEach(method -> allSourceMethodCalls.addAll(method.getMethodCalls()));
            var filteredMethodCalls = allSourceMethodCalls.stream().
                    filter(methodCall -> methodCall.getParent().equals(destinationClass)).collect(Collectors.toList());

            ArrayList<CPGClass> allSourceMethodParameters = new ArrayList<>();
            sourceClass.getMethods().
                    forEach(method -> method.parameters.
                            forEach(parameter -> allSourceMethodParameters.addAll(parameter.getTypeList())));

            assertTrue("Destination class should appear either in method calls or method parameters of source class",
                    (allSourceMethodParameters.contains(destinationClass) || !filteredMethodCalls.isEmpty()));
        }
    }

    @Test
    public void testInheritance() {
        var result = cpgToAnalyze.getRelations().stream().
                filter(relation -> relation.type.equals(ClassRelation.RelationshipType.INHERITANCE)).
                collect(Collectors.toList());

        for (CodePropertyGraph.Relation relation : result) {
            CPGClass sourceClass = relation.source;
            CPGClass destinationClass = relation.destination;
            String multiplicity = relation.multiplicity;

            Set<CPGClass.Attribute> superClassAttr = new HashSet<>(destinationClass.getAttributes());
            Set<CPGClass.Method> superClassMethod = new HashSet<>(destinationClass.getMethods());
            Set<CPGClass.Attribute> subClassAttr = new HashSet<>(sourceClass.getAttributes());
            Set<CPGClass.Method> subClassMethod = new HashSet<>(sourceClass.getMethods());

            superClassAttr.removeAll(subClassAttr);
            superClassMethod.removeAll(subClassMethod);

            assertEquals("Multiplicity should be blank", "", multiplicity);
            assertTrue("Subclass should have inherited all fields of superclass", superClassAttr.isEmpty());
            assertTrue("Subclass should have inherited all methods of superclass", superClassMethod.isEmpty());
        }
    }

    @Test
    public void testRealization() {
        var result = cpgToAnalyze.getRelations().stream().
                filter(relation -> relation.type.equals(ClassRelation.RelationshipType.REALIZATION)).
                collect(Collectors.toList());

        var interfaceCheck = cpgToAnalyze.getClasses().stream().
                filter(cpgToFind -> cpgToFind.classType.equals("interface")).collect(Collectors.toList());
        assertFalse("Interfaces should be present within cpg, if there is any realization relation",
                (interfaceCheck.isEmpty() && result.isEmpty()));

        for (CodePropertyGraph.Relation relation : result) {
            CPGClass sourceClass = relation.source;
            CPGClass destinationClass = relation.destination;
            String multiplicity = relation.multiplicity;

            Set<String> sourceClassMethods = new HashSet<>();
            sourceClass.getMethods().forEach(method -> sourceClassMethods.add(method.name));
            Set<String> destClassMethods = new HashSet<>();
            destinationClass.getMethods().forEach(method -> destClassMethods.add(method.name));
            destClassMethods.removeAll(sourceClassMethods);

            assertEquals("Multiplicity should be blank", "", multiplicity);
            assertEquals("Destination class should be interface", "interface", destinationClass.classType);
            assertEquals("Source class should be a class", "class", sourceClass.classType);
            assertTrue("Source class should implement all the methods of destination class", destClassMethods.isEmpty());
        }
    }
}
