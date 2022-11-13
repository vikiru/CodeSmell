package com.CodeSmell;

public class Position {
	
	public final double x;
	public final double y;

	public Position(double x, double y) {
		this.x = x;
		this.y = y;
	}

	public String toString() {
		return String.format("(%f, %f)", x, y);
	}
}