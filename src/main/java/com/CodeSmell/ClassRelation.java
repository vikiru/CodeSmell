package com.CodeSmell;

import java.util.ArrayList;

import com.CodeSmell.UMLClass;
import com.CodeSmell.Position;

class ClassRelation extends RenderObject {

	public enum Type {
		DEPENDENCY,
		ASSOCIATION,
		AGGREGATION,
		COMPOSITION,
		INHERITANCE,
		INTERFACE
	}

	public final Type type;
	public final UMLClass source;
	public final UMLClass target;
	private ArrayList<Position> path;

	public ClassRelation(UMLClass source, 
			UMLClass target, Type type) {
		this.type = type;
		this.source = source;
		this.target = target;
	}

	public void setPath(ArrayList<Position> path) {
		this.path = path;
		RenderEvent re = new RenderEvent(RenderEvent.Type.RENDER, this);
		dispatchToRenderEventListeners(re);
	}

	public ArrayList<Position> copyPath() {
		return new ArrayList<Position>(this.path); 
	}
}