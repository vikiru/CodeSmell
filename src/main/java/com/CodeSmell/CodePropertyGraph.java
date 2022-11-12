package com.CodeSmell;

import java.util.ArrayList;

import com.CodeSmell.CPGClass;
import com.CodeSmell.ClassRelation.Type;

public class CodePropertyGraph {

	public static class Relation {
		public final CPGClass source;
		public final CPGClass destination;
		public final Type type;
		
		Relation(CPGClass source, CPGClass destination, Type type) {
			this.source = source;
			this.destination = destination;
			this.type = type;
		}
	}

	private ArrayList<Relation> relations;
	private ArrayList<CPGClass> classes;

	protected ArrayList<CPGClass> getClasses() {
		return this.classes;
	} 

	protected ArrayList<Relation> getRelations() {
		return this.relations;
	} 

	protected CodePropertyGraph() {
		this.classes = new ArrayList<CPGClass>();
		this.relations = new ArrayList<Relation>();
	} 

	public void addClass(CPGClass c) {
		this.classes.add(c);
	}

	public void addRelation(Relation r) {
		this.relations.add(r);
	}
}