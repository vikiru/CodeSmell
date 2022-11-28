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

    public enum Type {
        DEPENDENCY,
        ASSOCIATION,
        AGGREGATION,
        COMPOSITION,
        INHERITANCE,
        INTERFACE
    }

    // todo - add any missing multiplicities, here (0..*, *..0, 0..1, 1..0?)
    public enum Multiplicity {
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_ONE,
        MANY_TO_MANY
    }
}