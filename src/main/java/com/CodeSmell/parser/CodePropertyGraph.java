package com.CodeSmell.parser;

import com.CodeSmell.model.ClassRelation.RelationshipType;
import com.google.gson.annotations.Expose;

import java.util.ArrayList;
import java.io.Serializable;

public class CodePropertyGraph implements Serializable {

    @Expose(serialize = true, deserialize = true)
    private ArrayList<Relation> relations;

    @Expose(serialize = true, deserialize = true)
    private ArrayList<CPGClass> classes;

    protected CodePropertyGraph() {
        this.classes = new ArrayList<CPGClass>();
        this.relations = new ArrayList<Relation>();
    }

    @Override
    public String toString() {
        return "CodePropertyGraph{" +
                "relations=" + relations +
                ", classes=" + classes +
                '}';
    }

    public ArrayList<CPGClass> getClasses() {
        return new ArrayList(this.classes);
    }

    public ArrayList<Relation> getRelations() {
        return new ArrayList(this.relations);
    }

    public void addClass(CPGClass c) {
        this.classes.add(c);
    }

    public void addRelation(Relation r) {
        this.relations.add(r);
    }

    public static class Relation implements Serializable  {
        @Expose(serialize = true, deserialize = true)
        public final CPGClass source;
        @Expose(serialize = true, deserialize = true)
        public final CPGClass destination;
        @Expose(serialize = true, deserialize = true)
        public final RelationshipType type;
        @Expose(serialize = true, deserialize = true)
        public final String multiplicity;

        Relation(CPGClass source, CPGClass destination, RelationshipType type, String multiplicity) {
            this.source = source;
            this.destination = destination;
            this.type = type;
            this.multiplicity = multiplicity;
        }

        @Override
        public String toString() {
            return this.source.name + " -> " + destination.name + " : " + this.multiplicity + " " + this.type;
        }
    }
}