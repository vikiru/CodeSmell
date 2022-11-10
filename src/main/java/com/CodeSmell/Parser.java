package com.CodeSmell;

import java.util.ArrayList;

import com.CodeSmell.CPGClass;
import com.CodeSmell.CPGClass.Method;
import com.CodeSmell.CPGClass.Attribute;
import com.CodeSmell.CPGClass.Modifier;
import com.CodeSmell.CodePropertyGraph;

public class Parser {	

	private class JavaAttribute extends Attribute {
		JavaAttribute(String name, Modifier[] m) {
			super(name, m);
		}
	}

	private class JavaMethod extends Method {
		
		JavaMethod(String name, String[] instructions, Modifier[] modifiers) {
			super(name, instructions, modifiers);
		}



	}

	public CodePropertyGraph buildCPG(String destination) {
		CodePropertyGraph cpg = new CodePropertyGraph();

		CPGClass jc1 = new CPGClass("FirstClass");
		CPGClass jc2 = new CPGClass("SecondClass");
		CPGClass jc3 = new CPGClass("ThirdClass");
		Attribute a1 = new JavaAttribute("attributeOne", new Modifier[] {Modifier.PUBLIC});
		Attribute a2 = new JavaAttribute("attributeTwo", new Modifier[] {Modifier.STATIC, Modifier.PRIVATE});
		Method m1 = new JavaMethod("methodOne(void);", new String[] {"instruction one;", "instruction two"},
				new Modifier[] {Modifier.PRIVATE});
		Method m2 = new JavaMethod("methodTwo(int x, int y);",
				new String[] {"return x + y;"}, new Modifier[] {Modifier.PRIVATE});
		Method m3 = new JavaMethod("methodThree(void);", new String[] {"return 0;"}, 
			new Modifier[] {Modifier.PRIVATE});
		Method m4 = new JavaMethod("methodFour(void);", new String[] {"return 0;"}, 
			new Modifier[] {Modifier.PRIVATE});
		Method m5 = new JavaMethod("methodFive(void);", new String[] {"return 0;"}, 
			new Modifier[] {Modifier.PRIVATE});
		Method m6 = new JavaMethod("methodSix(void);", new String[] {"return 0;"}, 
			new Modifier[] {Modifier.PRIVATE});
		Method m7 = new JavaMethod("methodSeven(void);", new String[] {"return 0;"}, 
			new Modifier[] {Modifier.PRIVATE});
		Method m8 = new JavaMethod("methodEight(void);", new String[] {"return 0;"}, 
			new Modifier[] {Modifier.PRIVATE});
		m1.addCall(m2);
		jc1.addMethod(m1);
		Modifier[] m = new Modifier[] {Modifier.PUBLIC};
		jc1.addAttribute(a1);
		jc1.addAttribute(a2);
		jc2.addMethod(m2);
		jc3.addMethod(m3);
		jc3.addMethod(m4);
		jc3.addMethod(m5);
		jc3.addMethod(m6);
		jc3.addMethod(m7);
		jc3.addMethod(m8);
		cpg.addClass(jc1);
		cpg.addClass(jc2);
		cpg.addClass(jc3);
		return cpg;
	}
}
