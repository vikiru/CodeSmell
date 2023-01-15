package com.testproject;

import com.testproject.ClassB;

public class ClassA {

	private ClassB[] compositionField;

	ClassA() {
		this.compositionField = new ClassB[] {
			new ClassB(1), new ClassB(2)};
	}

	public ClassB getB() {
		return compositionField[0];
	}
}