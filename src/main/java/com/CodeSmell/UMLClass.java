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
	private static WebControl webControl;
	private static LayoutManager layoutManager;

	UMLClass() {
		this.id = -1; // id is set on render
		this.methods = new ArrayList<String>();
		this.attributes = new ArrayList<String>();
		this.connections = new ArrayList<ClassRelation>();
		this.position = new int[2];
		this.size = new int[2];
	}

	public static void setWebControl(WebControl webControl) {
		UMLClass.webControl = webControl;
	}

	public static void setLayoutManager(LayoutManager webControl) {
		UMLClass.layoutManager = layoutManager;
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
		webControl.repositionClass(this.id, x, y);
	}

	public void render() {
		/**
		 * Draws the class rectangle to the webView 
		 * page, rendering all the other classes which moved as
		 * necessary
		*/

		// first render the object to get its dimensions
		this.id = webControl.renderClass(this);
		this.size = webControl.getClassDimensions(this.id);
		// add class to the layout
		layoutManager.addClass(this);
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