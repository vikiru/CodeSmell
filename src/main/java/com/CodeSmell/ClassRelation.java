package com.CodeSmell;

import java.util.ArrayList;

public class ClassRelation extends RenderObject {

    public final Type type;
    public final String multiplicity;
    public final UMLClass source;
    public final UMLClass target;
    private int pathContainerId;
    private ArrayList<Position> path;

    public ClassRelation(UMLClass source, UMLClass target, Type type, String multiplicity) {
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
        ASSOCIATION,
        UNIDIRECTIONAL_ASSOCIATION,
        BIDIRECTIONAL_ASSOCIATION,
        REFLEXIVE_ASSOCIATION,
        AGGREGATION,
        COMPOSITION,
        DEPENDENCY,
        INHERITANCE,
        REALIZATION,
    }
}