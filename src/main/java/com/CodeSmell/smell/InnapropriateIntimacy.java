package com.CodeSmell.smell;

import com.CodeSmell.model.ClassRelation;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class InnapropriateIntimacy extends Smell {
    public LinkedList<CodeFragment> detections;

    public InnapropriateIntimacy(CodePropertyGraph cpg) {
        super("Inappropriate Intimacy", cpg);
        this.detections = new LinkedList<>();
        detectAll();
    }

    public CodeFragment detectNext() {
        return detections.poll();
    }

    public void detectAll() {
        List<CodePropertyGraph.Relation> allBidirectionalRelations = cpg.getRelations().stream().
                filter(relation -> relation.type.equals(ClassRelation.RelationshipType.BIDIRECTIONAL_ASSOCIATION)).
                collect(Collectors.toList());

        List<CodePropertyGraph.Relation> allDependencies = cpg.getRelations().stream().
                filter(relation -> relation.type.equals(ClassRelation.RelationshipType.DEPENDENCY)).
                collect(Collectors.toList());

        List<CodePropertyGraph.Relation> allInheritance = cpg.getRelations().stream().
                filter(relation -> relation.type.equals(ClassRelation.RelationshipType.INHERITANCE)).
                collect(Collectors.toList());

        analyzeAllBidirectionalRelations(allBidirectionalRelations);
        analyzeAllDependencies(allDependencies);
        analyzeAllInheritances(allInheritance);
    }

    private void analyzeAllBidirectionalRelations(List<CodePropertyGraph.Relation> allBidirectionalRelations) {
        for (CodePropertyGraph.Relation relation : allBidirectionalRelations) {
            CPGClass sourceClass = relation.source;
            CPGClass destinationClass = relation.destination;
            CPGClass[] affectedClasses = new CPGClass[]{sourceClass, destinationClass};

            List<CPGClass.Attribute> sourceAttributesMatchingDest = Arrays.stream(sourceClass.attributes).
                    filter(attribute -> attribute.attributeType.equals(destinationClass.name)).collect(Collectors.toList());
            List<CPGClass.Attribute> destinationAttributesMatchingSrc = Arrays.stream(destinationClass.attributes).
                    filter(attribute -> attribute.attributeType.equals(sourceClass.name)).collect(Collectors.toList());
            List<CPGClass.Attribute> allAttributes = new ArrayList<CPGClass.Attribute>();
            allAttributes.addAll(destinationAttributesMatchingSrc);
            allAttributes.addAll(sourceAttributesMatchingDest);
            CPGClass.Attribute[] affectedAttributes = allAttributes.toArray(new CPGClass.Attribute[allAttributes.size()]);

            String description = "";
            description += sourceClass.name + " and " + destinationClass.name + " have a bi-directional relationship.";

            CodeFragment fragmentToAdd = new CodeFragment(description, affectedClasses,
                    null, null, affectedAttributes,
                    null, null);
            detections.add(fragmentToAdd);
        }
    }

    private void analyzeAllDependencies(List<CodePropertyGraph.Relation> allDependencies) {
        for (CodePropertyGraph.Relation relation : allDependencies) {
            CPGClass sourceClass = relation.source;
            CPGClass destinationClass = relation.destination;
            CPGClass[] affectedClasses = new CPGClass[]{sourceClass, destinationClass};
            List<CPGClass.Method> allDestinationClassMethods = new ArrayList<>();
            for (CPGClass.Method srcMethod : sourceClass.methods) {
                List<CPGClass.Method> methodCalls = srcMethod.getMethodCalls().stream().
                        filter(method -> method.parentClassName.equals(destinationClass.name)).collect(Collectors.toList());
                allDestinationClassMethods.addAll(methodCalls);
            }
            if (allDestinationClassMethods.size() >= 5) {
                String description = "";
                description += sourceClass.name + " makes " + allDestinationClassMethods.size() +
                        " method calls to " + destinationClass.name;
                CodeFragment fragmentToAdd = new
                        CodeFragment(description, affectedClasses,
                        allDestinationClassMethods.toArray(new CPGClass.Method[allDestinationClassMethods.size()]),
                        null, null, null, null);
                detections.add(fragmentToAdd);
            }
        }
    }

    private void analyzeAllInheritances(List<CodePropertyGraph.Relation> allInheritances) {
        for (CodePropertyGraph.Relation relation : allInheritances) {
            CPGClass sourceClass = relation.source;
            CPGClass destinationClass = relation.destination;
            CPGClass[] affectedClasses = new CPGClass[]{sourceClass, destinationClass};
            CPGClass.Method[] affectedMethods;
            CPGClass.Attribute[] affectedAttributes;
            String description = "";

            // Add all the public super class fields
            List<CPGClass.Attribute> publicDestClassAttr = new ArrayList<>();
            for (CPGClass.Attribute attribute : destinationClass.attributes) {
                List<CPGClass.Modifier> modList = Arrays.asList(attribute.modifiers);
                if (modList.contains(CPGClass.Modifier.PUBLIC)) {
                    publicDestClassAttr.add(attribute);
                }
            }

            List<CPGClass.Method> subClassMethods = Arrays.stream(sourceClass.methods).
                    filter(method -> method.parentClassName.equals(sourceClass.name)).collect(Collectors.toList());
            List<CPGClass.Method> allSuperMethodCalls = new ArrayList<>();

            // Iterate through the methods of the subclass and add all the class to the superclass
            for (CPGClass.Method m : subClassMethods) {
                for (CPGClass.Method methodCall : m.getMethodCalls()) {
                    if (methodCall.parentClassName.equals(destinationClass.name)) {
                        allSuperMethodCalls.add(methodCall);
                    }
                }
            }

        }
    }

    public String description() {
        return "One class uses the internal fields and methods of another class";
    }
}
