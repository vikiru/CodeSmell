package com.testproject;

import com.testproject.ClassA;
import com.testproject.ClassB;
import com.testproject.InterfaceC;
import com.testproject.ClassD;
import com.testproject.ClassE;

public class GodClass {
	ClassA field1;
	ClassA[] field2;
	ClassB field3;
	InterfaceC field4;
	ClassD[] field5;
	ClassE[] field6;
	ClassD field7;
	ClassD field8;
	ClassD field9;
	ClassD field10;
	ClassD field11;
	ClassD field12;

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