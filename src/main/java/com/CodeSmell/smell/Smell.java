package com.CodeSmell.smell;

import java.util.Arrays;
import java.util.LinkedList;

import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CPGClass.*;

public abstract class Smell {
	
	public final String name;
	public final CodePropertyGraph cpg;
	public CodeFragment lastDetection; // the last detected instance of this smell in CPG
	 final LinkedList<CodeFragment> detections;
	protected Smell(String name, CodePropertyGraph cpg) {
		this.name = name;
		this.cpg = cpg;
		this.detections = new LinkedList<>();
	}
	
	public abstract CodeFragment detectNext();
  
	public final boolean detect() {
	  CodeFragment cf = detectNext();
	  if (cf != null) {
	    this.lastDetection = cf;
	    return true;
	  }
	  return false;
	}

	// a description of the smell
	public abstract String description();


	public abstract LinkedList<CodeFragment> getDetections();

	/**
	 * Each detection is represented by a code
	 * fragment object containing the appropriate	
	 * information needed to identify what 
	 * the problem is in the CPG. Some (or 
	 * for Project-level smells, all) of the fields
	 * may be left null
	 */
	public static class CodeFragment {
		public final CPGClass[] classes;
		public final Method[] methods;
		public final Modifier[] modifiers;
		public final Attribute[] attributes;
		public final Method.Parameter[] parameters;
		public final Method.Instruction[] instructions;
		public final String description;

		CodeFragment(String description, CPGClass[] classes, 
				Method[] methods, Modifier[] modifiers,
				Attribute[] attributes,
				Method.Parameter[] parameters, 
				Method.Instruction[] instructions) {
			this.classes = classes;
			this.methods = methods;
			this.modifiers = modifiers;
			this.attributes = attributes;
			this.parameters = parameters;
			this.instructions = instructions;
			this.description = description;
			if (description == null || description.equals("")) {
				throw new IllegalArgumentException(
					"CodeFragment description cannot be empty");
			}
		}



		@Override
		public String toString() {
			String s = description;

			if (this.classes != null) {
				s += "classes: " + Arrays.toString(this.classes) + "\n";
			}
			if (this.methods != null) {
				s += "methods: " + Arrays.toString(this.methods) + "\n";
			}
			if (this.modifiers != null) {
				s += "modifiers: " + Arrays.toString(this.modifiers) + "\n";
			}
			if (this.attributes != null) {
				s += "attributes: " + Arrays.toString(this.attributes) + "\n";
			}
			if (this.parameters != null) {
				s += "parameters: " + Arrays.toString(this.parameters) + "\n";
			}
			if (this.instructions != null) {
				s += "instructions: " + Arrays.toString(this.instructions) + "\n";
			}
			return s;
		}

		public static CodeFragment makeFragment(String description, Object... args) {
			CPGClass[] classes = null;
			Method[] methods = null;
			Modifier[] modifiers = null;
			Attribute[] attributes = null;
			Method.Parameter[] parameters = null;
			Method.Instruction[] instructions = null;

			for (Object o : args) {

				if (o instanceof CPGClass[]) {
					classes = (CPGClass[]) o;
				} else if  (o instanceof CPGClass) {
					classes = new CPGClass[] {(CPGClass) o};

				} else if  (o instanceof Method[]) {
					methods = (Method[]) o;
				} else if  (o instanceof Method) {
					methods = new Method[] {(Method) o};

				} else if  (o instanceof Modifier[]) {
					modifiers = (Modifier[]) o;
				} else if  (o instanceof Modifier) {
					modifiers = new Modifier[] {(Modifier) o};
					
				} else if  (o instanceof Attribute[]) {
					attributes = (Attribute[]) o;
				} else if  (o instanceof Attribute) {
					attributes  = new Attribute[] {(Attribute) o};
					
				} else if  (o instanceof Method.Parameter[]) {
					parameters = (Method.Parameter[]) o;
				} else if  (o instanceof Method.Parameter) {
					parameters = new Method.Parameter[] {(Method.Parameter) o};
					
				} else if  (o instanceof Method.Instruction[]) {
					instructions = (Method.Instruction[]) o;
				} else if  (o instanceof Method.Instruction) {
					instructions = new  Method.Instruction[] {
						(Method.Instruction) o};

				} else {
					throw new IllegalArgumentException("Cannot create CodeFragment");
				}
			}

			return new CodeFragment(description, classes, methods, modifiers, 
				attributes, parameters, instructions);
		}
	}
}
