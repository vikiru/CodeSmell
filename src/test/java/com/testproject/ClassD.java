package com.testproject;

import com.testproject.ClassB;

public class ClassD implements ClassC {
	private ClassB b;

	ClassD(ClassB b) {
		this.b = b;
	}

	public void doThing() {
		b.i += 1;
	}

}