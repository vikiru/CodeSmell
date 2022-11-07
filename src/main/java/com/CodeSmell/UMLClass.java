package com.CodeSmell;

import javafx.util.Pair;
import java.util.ArrayList;
import com.CodeSmell.WebControl;
import com.CodeSmell.Position;

class UMLClass {
	
	private int id;
	private ArrayList<String> methods;
	private ArrayList<String> attributes;
	private ArrayList<ClassRelation> connections;
	// private ArrayList<Smell> smells;
	private Position position;
	private int width;
	private int height;
	private static WebControl webControl;

	UMLClass() {
		this.id = -1; // id is set on render
		this.methods = new ArrayList<String>();
		this.attributes = new ArrayList<String>();
		this.connections = new ArrayList<ClassRelation>();
		this.position = new Position(0, 0);
		this.width = 0;
		this.height = 0;
	}

	public static void setWebControl(WebControl webControl) {
		UMLClass.webControl = webControl;
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
		this.position = new  Position(x, y);
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
		int[] size = webControl.getClassDimensions(this.id);
		this.width = size[0];
		this.height = size[1];
	}

	public String[] getMethods() {
		String[] s = new String[this.methods.size()];
		for (int i=0; i < methods.size(); i++) {
			s[i] = (String) this.methods.get(i);
		}  
		return s;
	}

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}
}