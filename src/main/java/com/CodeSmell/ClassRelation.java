package com.CodeSmell;

import java.util.ArrayList;

public class ClassRelation extends RenderObject {

    public final Type type;
    public final Multiplicity multiplicity;
    public final UMLClass source;
    public final UMLClass target;
    private int pathContainerId;
    private ArrayList<Position> path;

    public ClassRelation(UMLClass source, UMLClass target, Type type, Multiplicity multiplicity) {
        this.type = type;
        this.multiplicity = multiplicity;
        this.source = source;
        this.target = target;
    }

    public ArrayList<Position> getPath() {
        return path;
    }

    public void setPath(ArrayList<Position> path) {
        this.path = path;
        RenderEvent re = new RenderEvent(RenderEvent.Type.RENDER, this);
        dispatchToRenderEventListeners(re);
        pathContainerId = (Integer) re.getResponse();
    }

    public Multiplicity reverseMultiplicity() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.multiplicity.cardinality).reverse();
        Multiplicity reverseMultiplicity = null;
        for (Multiplicity m : Multiplicity.values()) {
            if (m.cardinality.equals(String.valueOf(sb))) {
                reverseMultiplicity = m;
            }
        }
        return reverseMultiplicity;
    }

    public enum Type {
        DEPENDENCY,
        ASSOCIATION,
        AGGREGATION,
        COMPOSITION,
        INHERITANCE,
        INTERFACE
    }

    public enum Multiplicity {
        ZERO_TO_ONE("0..1"),
        ZERO_TO_MANY("0..*"),
        ONE_TO_ZERO("1..0"),
        ONE_TO_ONE("1..1"),
        ONE_TO_MANY("1..*"),
        MANY_TO_ZERO("*..0"),
        MANY_TO_ONE("*..1"),
        MANY_TO_MANY("*..*");

        // Represents the cardinality of the Multiplicity enum as a String.
        public final String cardinality;

        Multiplicity(String cardinality) {
            this.cardinality = cardinality;
        }

        @Override
        public String toString() {
            return this.cardinality;
        }
    }
}