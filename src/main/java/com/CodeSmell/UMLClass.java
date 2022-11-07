package com.CodeSmell;

import java.util.ArrayList;
import com.CodeSmell.WebControl;
import com.CodeSmell.Position;
import com.CodeSmell.RenderEvent;
import com.CodeSmell.Pair;

class UMLClass {
	
	private int id;
	private ArrayList<String> methods;
	private ArrayList<String> attributes;
	private ArrayList<ClassRelation> connections;
	// private ArrayList<Smell> smells;
	private Position position;
	private int width;
	private int height;
	private static ArrayList<RenderEventListener> rel = new ArrayList<>();

	UMLClass() {
		this.id = -1; // id is set on render
		this.methods = new ArrayList<String>();
		this.attributes = new ArrayList<String>();
		this.connections = new ArrayList<ClassRelation>();
		this.position = new Position(0, 0);
		this.width = 0;
		this.height = 0;
	}

	public static void addRenderEventListener(RenderEventListener rel) {
		UMLClass.rel.add(rel);
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
		RenderEvent fe = new RenderEvent(RenderEvent.Type.REPOSITION, this);
		dispatchToRenderEventListeners(fe);
	}

	public void render() {
		/**
		 * Draws the class rectangle to the webView 
		 * page, rendering all the other classes which moved as
		 * necessary
		*/

		// first render the object to get its dimensions
		RenderEvent fe = new RenderEvent(RenderEvent.Type.RENDER, this);
		dispatchToRenderEventListeners(fe);
		Pair<Integer, Pair<Integer, Integer>> p;
		p = (Pair<Integer, Pair<Integer, Integer>>) fe.getResponse();
		this.id = p.first;
		this.width = p.second.first;
		this.height = p.second.second;
	}

	public void dispatchToRenderEventListeners(RenderEvent e) {
		for (RenderEventListener rel : this.rel) {
			rel.renderEventPerformed(e);
		}
	}

	public String[] getMethods() {
		String[] s = new String[this.methods.size()];
		for (int i=0; i < methods.size(); i++) {
			s[i] = (String) this.methods.get(i);
		}  
		return s;
	}

	public int getId() {
		return this.id;
	}

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}

	public Position getPosition() {
		return this.position;
	}
}