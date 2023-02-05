package com.CodeSmell.testproject;

import com.CodeSmell.testproject.ClassE;
import com.CodeSmell.testproject.ClassB;
import com.CodeSmell.testproject.ClassD;


public class ClassE {
	public static void main(String[] args) {
		ClassA a = new ClassA();
		ClassD d = new ClassD(a.getB());
	}

	public static class NestedClassE {
		ClassB b;
		ClassD d;

		public void modifyB() {
			b.i = d.getCounter();
		}

		public static class DoubleNestedClassE {
			public int i;
			public int j;
			public int k;
		}
	}
}