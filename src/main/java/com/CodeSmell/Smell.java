package com.CodeSmell;

import com.CodeSmell.CodePropertyGraph;
import com.CodeSmell.CPGClass;
import com.CodeSmell.CPGClass.*;

public abstract class Smell {
	
	public final String name;
	public final CodePropertyGraph cpg;
	public CodeFragment lastDetection; // the last detected instance of this smell in CPG
	
	Smell(String name, CodePropertyGraph cpg) {
		this.name = name;
		this.cpg = cpg;
	}
	
	protected abstract CodeFragment detectNext();
  
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
	public class CodeFragment {
		public final CPGClass[] classes;
		public final Method[] methods;
		public final Modifier[] modifiers;
		public final Attribute[] attributes;
		public final Method.Parameter[] parameters;
		public final Method.Instruction[] instructions;
		public final String description;

		CodeFragment(CPGClass[] classes, Method[] methods, 
				Modifier[] modifiers, Attribute[] attributes,
				Method.Parameter[] parameters, Method.Instruction[] instructions,
				String description) {
			this.classes = classes;
			this.methods = methods;
			this.modifiers = modifiers;
			this.attributes = attributes;
			this.parameters = parameters;
			this.instructions = instructions;
			this.description = description;
		}
	}
}
