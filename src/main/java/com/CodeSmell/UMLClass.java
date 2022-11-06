package com.CodeSmell;

import javafx.util.Pair;
import java.util.ArrayList;
import com.CodeSmell.WebControl;

class UMLClass {
	
	private int id;
	private ArrayList<String> methods;
	private ArrayList<String> attributes;
	private ArrayList<ClassRelation> connections;
	// private ArrayList<Smell> smells;
	private int[] position;
	private int[] size;
	private WebControl controller;

	UMLClass(WebControl controller) {
		this.id = -1; // id is set on render
		this.methods = new ArrayList<String>();
		this.attributes = new ArrayList<String>();
		this.connections = new ArrayList<ClassRelation>();
		this.position = new int[2];
		this.size = new int[2];
		this.controller = controller;
	}

	public void addField(boolean isMethod, String s) {
		if (isMethod) {
			this.methods.add(s);
		} else {
			this.attributes.add(s);
		}
	}

	public void addRelationship(ClassRelation r) {
		this.connections.add(r);
	}

	public void setPosition(int x, int y) {
		position[0] = x;
		position[1] = y;
		this.controller.repositionClass(this.id, x, y);
	}

	public void render(LayoutManager lm) {
		/**
		 * Draws the class rectangle to the webView 
		 * page, rendering all the other classes which moved as
		 * necessary
		 * 
		 @param lm the layout manager to use to determine
		 class position 
		*/

		// first render the object to get its dimensions
		this.id = this.controller.renderClass(this);
		this.size = this.controller.getClassDimensions(this.id);
		// add class to the layout
		lm.addClass(this);
	}

	public String[] getMethods() {
		String[] s = new String[this.methods.size()];
		for (int i=0; i < methods.size(); i++) {
			s[i] = (String) this.methods.get(i);
		}  
		return s;
	}

	public int[] getSize() {
		return this.size;
	}
}