package com.CodeSmell;

import com.CodeSmell.ClassRelation.Type;
import com.google.gson.annotations.Expose;

import java.util.ArrayList;

public class CodePropertyGraph {

    @Expose(serialize = true, deserialize = false)
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

    protected ArrayList<CPGClass> getClasses() {
        return new ArrayList(this.classes);
    }

    protected ArrayList<Relation> getRelations() {
        return new ArrayList(this.relations);
    }

    public void addClass(CPGClass c) {
        this.classes.add(c);
    }

    public void addRelation(Relation r) {
        this.relations.add(r);
    }

    public static class Relation {
        @Expose(serialize = true, deserialize = true)
        public final CPGClass source;
        @Expose(serialize = true, deserialize = true)
        public final CPGClass destination;
        @Expose(serialize = true, deserialize = true)
        public final Type type;
        @Expose(serialize = true, deserialize = true)
        public final String multiplicity;

        Relation(CPGClass source, CPGClass destination, Type type, String multiplicity) {
            this.source = source;
            this.destination = destination;
            this.type = type;
            this.multiplicity = multiplicity;
        }

        @Override
        public String toString() {
            return "Relation{" +
                    "source=" + source +
                    ", destination=" + destination +
                    ", type=" + type +
                    ", multiplicity=" + multiplicity +
                    '}';
        }
    }
}