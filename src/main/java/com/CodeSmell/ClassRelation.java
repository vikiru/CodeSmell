package com.CodeSmell;

import java.util.ArrayList;

import com.CodeSmell.UMLClass;
import com.CodeSmell.Position;

class ClassRelation {

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
	public ArrayList<Position> route;

	public ClassRelation(UMLClass source, 
			UMLClass target, Type type) {
		this.type = type;
		this.source = source;
		this.target = target;
		this.route = route;
	}
}