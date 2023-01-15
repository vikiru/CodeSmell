package com.CodeSmell.smell;

import com.CodeSmell.CodePropertyGraph;
import com.CodeSmell.CPGClass;
import com.CodeSmell.CPGClass.*;

public abstract class Smell {
	
	public final String name;
	public final CodePropertyGraph cpg;
	public CodeFragment lastDetection; // the last detected instance of this smell in CPG
	
	protected Smell(String name, CodePropertyGraph cpg) {
		this.name = name;
		this.cpg = cpg;
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
