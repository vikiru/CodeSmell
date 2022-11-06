package com.CodeSmell;

import java.util.ArrayList;

import com.CodeSmell.UMLClass;

class LayoutManager {


	private ArrayList<UMLClass> classes;

	LayoutManager() {
		this.classes = new ArrayList<UMLClass>();
	}

	public void addClass(UMLClass c) {
		/**
		 * given a class with a defined size attribute,
		 * call the controller to 
		 */
		this.classes.add(c);

		// determine coordinates for c
		int x = 0;
		int y = 0;
		if (this.classes.size() > 1) {
			int s = classes.get(classes.size() - 1).getSize()[0];
			x = s + (int) s/2;
			System.out.println("X: ");
			System.out.println(x);
		}
		c.setPosition(x, y);

		// compute a list of all classes which were effected
		// by adding c and need to be repositioned themselves
		// (this may have to be done recursively)
		ArrayList<UMLClass> effectedClasses = new ArrayList<>();
		for (UMLClass e : effectedClasses) {
			e.setPosition(x, y);
		}
	}
}