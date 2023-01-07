package com.CodeSmell;

import com.CodeSmell.ClassRelation.Type;
import com.CodeSmell.ClassRelation.Multiplicity;
import com.google.gson.annotations.Expose;

import java.util.ArrayList;

public class CodePropertyGraph {

    @Expose(deserialize = false)
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
        return this.classes;
    }

    protected ArrayList<Relation> getRelations() {
        return this.relations;
    }

    public void addClass(CPGClass c) {
        this.classes.add(c);
    }

    public void addRelation(Relation r) {
        this.relations.add(r);
    }

    public static class Relation {
        public final CPGClass source;
        public final CPGClass destination;
        public final Type type;
        public final Multiplicity multiplicity;

        Relation(CPGClass source, CPGClass destination, Type type, Multiplicity multiplicity) {
            this.source = source;
            this.destination = destination;
            this.type = type;
            this.multiplicity = multiplicity;
        }
    }
}