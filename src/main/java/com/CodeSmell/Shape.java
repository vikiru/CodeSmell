package com.CodeSmell;
import com.CodeSmell.Position;

public class Shape extends RenderObject {
	public final Position[] vertex;
	public final String colour;

	public Shape(Position[] vertex, String colour) {
		this.vertex = vertex;
		this.colour = colour;
	}
}