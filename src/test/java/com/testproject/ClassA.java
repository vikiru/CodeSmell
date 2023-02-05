package com.CodeSmell.testproject;

import com.CodeSmell.testproject.ClassB;

public class ClassA {

	private ClassB[] compositionField;

	public ClassA() {
		this.compositionField = new ClassB[4];

	}

	public ClassB getB() {
		return compositionField[0];
	}

	public ClassB addB(ClassB b) {
		return compositionField[0];
	}

	public ClassB bDotProduct() {
		return compositionField[0];
	}
}