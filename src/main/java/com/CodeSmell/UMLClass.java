package com.CodeSmell;

import java.util.ArrayList;
import com.CodeSmell.WebControl;
import com.CodeSmell.Position;
import com.CodeSmell.RenderEvent;
import com.CodeSmell.Pair;
import com.CodeSmell.CPGClass.Method;
import com.CodeSmell.CPGClass.Attribute;

class UMLClass extends RenderObject {
	
	public final String name;
	private int id;
	private ArrayList<Method> methods;
	private ArrayList<Attribute> attributes;
	private ArrayList<ClassRelation> relations;
	// private ArrayList<Smell> smells;
	private Position position;
	private double width;
	private double height;
  
	UMLClass(String name) {
		this.name = name;
		this.id = -1; // id is set on render
		this.methods = new ArrayList<Method>();
		this.attributes = new ArrayList<Attribute>();
		this.relations = new ArrayList<ClassRelation>();
		this.position = new Position(0, 0);
		this.width = 0.0;
		this.height = 0.0;
	}

	public void addMethod(Method m) {
		this.methods.add(m);
	}

	public void addAttribute(Attribute a) {
		this.attributes.add(a);
	}

	public void addRelationship(ClassRelation r) {
		this.relations.add(r);
	}

	public void setPosition(double x, double y) {
		this.position = new Position(x, y);
		RenderEvent re = new RenderEvent(RenderEvent.Type.REPOSITION, this);
		dispatchToRenderEventListeners(re);
	}

	public void render() {
		/**
		 * Draws the class rectangle to the webView 
		 * page, rendering all the other classes which moved as
		 * necessary
		*/

		// first render the object to get its dimensions
		RenderEvent re = new RenderEvent(RenderEvent.Type.RENDER, this);
		dispatchToRenderEventListeners(re);
    
		Pair<Integer, Pair<Double, Double>> p;
		p = (Pair<Integer, Pair<Double, Double>>) re.getResponse();
		this.id = p.first;
		this.width = p.second.first;
		this.height = p.second.second;
	}

	public ArrayList<Attribute> getAttributes() {
		return this.attributes;
	}

	public ArrayList<Method> getMethods() {
		return this.methods;
	}

	public int getId() {
		return this.id;
	}

	public Double getWidth() {
		return this.width;
	}

	public Double getHeight() {
		return this.height;
	}

	public Position getPosition() {
		return this.position;
	}

	public ArrayList<ClassRelation> getRelations() {
		return this.relations;
	}
}