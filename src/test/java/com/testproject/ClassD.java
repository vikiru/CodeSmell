package com.CodeSmell.testproject;

import com.CodeSmell.testproject.ClassB;

public class ClassD implements InterfaceC {
	private ClassB b;
	private int counter;

	ClassD(ClassB b) {
		this.b = b;
	}

	public void doThing() {
		b.i += 1;
		counter = b.i * 3;
	}

	public int getCounter() {
		return this.counter;
	}

}