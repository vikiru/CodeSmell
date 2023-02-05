package com.CodeSmell.parser;

import com.CodeSmell.model.ClassRelation.RelationshipType;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * The CodePropertyGraph which contains all the classes and relations
 */
public class CodePropertyGraph implements Serializable {

    /**
     * All the relationships between classes within the CodePropertyGraph
     */
    private ArrayList<Relation> relations;

    /**
     * All the classes within the CodePropertyGraph
     */
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

    /**
     * Add a {@link CPGClass} to the CodePropertyGraph
     *
     * @param c - The class to be added
     */
    protected void addClass(CPGClass c) {
        this.classes.add(c);
    }

    /**
     * Add a {@link Relation} to the CodePropertyGraph
     *
     * @param r - The relation to be added
     */
    protected void addRelation(Relation r) {
        this.relations.add(r);
    }

    /**
     * A relationship that exists between two classes, with a type and associated multiplicity (if any)
     */
    public static class Relation implements Serializable {
        /**
         * The source class of the relationship
         */
        public final CPGClass source;

        /**
         * The destination class of the relationship
         */
        public final CPGClass destination;

        /**
         * The {@link RelationshipType} that defines this relation (e.g. COMPOSITION)
         */
        public final RelationshipType type;

        /**
         * The multiplicity associated with the relation (empty for relations other than some form of ASSOCIATION relation)
         */
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