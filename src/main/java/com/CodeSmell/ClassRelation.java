package com.CodeSmell;

import com.CodeSmell.UMLClass;
class ClassRelation {

	enum Type {
		DEPENDENCY,
		ASSOCIATION,
		AGGREGATION,
		COMPOSITION,
		INHERITANCE,
		INTERFACE
	}

	final Type type;
	final UMLClass source;
	final UMLClass target;

	public ClassRelation(UMLClass source, UMLClass target, Type type) {
		this.type = type;
		this.source = source;
		this.target = target;
	}
}