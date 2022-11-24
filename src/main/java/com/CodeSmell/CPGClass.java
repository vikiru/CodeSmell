package com.CodeSmell;

import java.util.ArrayList;

public class CPGClass {

	public static class Method {
		// the name of the method
		public final String name;

		// a print out of the method instructions
		public final String[] instructions;

		// list of modifiers the method has (0 or more)
		public final Modifier[] modifiers;

		// return a list of methods which this calls
		private ArrayList<Method> calls;

		protected Method(String name, String[] instructions, Modifier[] modifiers) {
			this.name = name;
			this.instructions = instructions;
			this.modifiers = modifiers;
			this.calls = new ArrayList<Method>();
		}

		protected void addCall(Method m) {
			this.calls.add(m);
		}
	}

	public enum Modifier {
		PUBLIC, 
		PRIVATE,
		PROTECTED,
		STATIC,
		SYNCHRONIZED,
		VOLATILE,
		ABSTRACT,
		NATIVE,
		FINAL
	}

	public static class Attribute {
		// the name of the attribute
		public final String name;

		// list of modifiers the attribute has (0 or more)
		public final Modifier[] modifiers;

		protected Attribute(String name, Modifier[] modifiers) {
			this.name = name;
			this.modifiers = modifiers;
		}
	}

	public final String name;
	private ArrayList<Method> methods;
	private ArrayList<Attribute> attributes;

	protected ArrayList<Method> getMethods() {
		return this.methods;
	}

	protected ArrayList<Attribute> getAttributes() {
		return this.attributes;
	}

	protected void addMethod(Method m) {
		this.methods.add(m);
	}

	protected void addAttribute(Attribute a) {
		this.attributes.add(a);
	}

	CPGClass(String name) {
		this.name = name;
		this.methods = new ArrayList<Method>();
		this.attributes = new ArrayList<Attribute>();
	} 
}