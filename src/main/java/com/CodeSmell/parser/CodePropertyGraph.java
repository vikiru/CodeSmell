package com.CodeSmell.parser;

import com.CodeSmell.model.ClassRelation.RelationshipType;

import java.io.Serializable;
import java.util.ArrayList;

public class CodePropertyGraph implements Serializable {

    private ArrayList<Relation> relations;
    private ArrayList<CPGClass> classes;

    protected CodePropertyGraph() {
        this.classes = new ArrayList<>();
        this.relations = new ArrayList<>();
    }

    @Override
    public String toString() {
        return "CodePropertyGraph{" +
                "relations=" + relations +
                ", classes=" + classes +
                '}';
    }

    public ArrayList<CPGClass> getClasses() {
        return new ArrayList<>(this.classes);
    }

    public ArrayList<Relation> getRelations() {
        return new ArrayList<>(this.relations);
    }

    protected void addClass(CPGClass c) {
        this.classes.add(c);
    }

    protected void addRelation(Relation r) {
        this.relations.add(r);
    }

    public static class Relation implements Serializable {
        public final CPGClass source;
        public final CPGClass destination;
        public final RelationshipType type;
        public final String multiplicity;

        Relation(CPGClass source, CPGClass destination, RelationshipType type, String multiplicity) {
            this.source = source;
            this.destination = destination;
            this.type = type;
            this.multiplicity = multiplicity;
        }

        @Override
        public String toString() {
            return source.name + " -> " + destination.name + " : " + multiplicity + " " + type;
        }
    }
}