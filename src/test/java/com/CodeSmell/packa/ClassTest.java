package com.CodeSmell.packa;

import java.util.ArrayList;

import com.CodeSmell.packa.ClassA;

// represents the parser
public class ClassTest {

	public ClassA a; // reresents CodePropertyGraph 

	public ClassTest() {}

	public boolean doTest() {
		this.a = new ClassA();
		ArrayList arr = new ArrayList<Integer>();
		arr.add(1);
		a.setter(arr);
		arr.add(4); // this still works

		// getter is a shallow copy
		if (!(a.getter() == arr)) {
			return false;
		}
		System.out.println("getter test passed");

		// setter test
		try {
			a.setter(arr);
			// the line of code below is commented out
			// when using the protected approach
			// as opposed to the 1 time check approach.
			// return false; // setter test failed
		} catch (Exception ex) {
			// good, it errored
			System.out.println("setter test passed");
		}
		return true;
	}
}