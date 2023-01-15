package com.testproject;

import com.testproject.ClassA;
import com.testproject.ClassB;
import com.testproject.ClassC;
import com.testproject.ClassD;
import com.testproject.ClassE;

public class GodClass {
	ClassA a;
	ClassA[] moreA;
	ClassB b;
	ClassC c;
	ClassD[] ds;
	ClassE[] es;

	public boolean compareAs() {
		for (ClassA a : moreA) {
			if (a == this.a) {
				return true;
			}
		}
		return false;
	}

	public boolean compareB() {
		for (ClassA a : moreA) {
			if (a.getB() == this.b && this.a != a) {
				return true;
			}
		}
		this.c.doThing();
		return false;
	}
}