package com.testproject;

import com.testproject.ClassB;

public class ClassA {

	private ClassB[] compositionField;

	public ClassB getB() {
		return compositionField[0];
	}

	public ClassB addB(B b) {
		return compositionField[0];
	}

	public ClassB bDotProduct() {
		return compositionField[0];
	}
}