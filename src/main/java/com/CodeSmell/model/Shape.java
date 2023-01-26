package com.CodeSmell.model;

import com.CodeSmell.model.Position;
import com.CodeSmell.model.RenderObject;

public class Shape extends RenderObject {
	public final Position[] vertex;
	public final String colour;

	public Shape(Position[] vertex, String colour) {
		this.vertex = vertex;
		this.colour = colour;
	}
}