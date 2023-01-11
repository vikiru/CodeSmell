package com.testproject;

import com.testproject.ClassE;

public class ClassE {
	public static void main(String[] args) {
		ClassA a = new ClassA();
		ClassD d = new ClassD(a.getB());
	}
}