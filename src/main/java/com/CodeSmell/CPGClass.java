package com.CodeSmell;

import java.util.ArrayList;
import java.util.HashMap;

public class CPGClass {

    public final String name;
    public final String type;
    private ArrayList<Method> methods;
    private ArrayList<Attribute> attributes;

    CPGClass(String name, String type) {
        this.name = name;
        this.type = type;
        this.methods = new ArrayList<Method>();
        this.attributes = new ArrayList<Attribute>();
    }

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

    public static class Method {

        // the name of the method
        public final String name;
        // a print out of the method instructions
        public final Instruction[] instructions;
        // list of modifiers the method has (0 or more)
        public final Modifier[] modifiers;
        // a hashmap of all the method parameters, key is the name of the parameters
        // and value is the type
        public final HashMap<String, String> parameters;
        public final String returnType;
        // return a list of methods which this calls
        private HashMap<Method, String> calls;
        private ArrayList<Method> methodCalls;

        protected Method(String name, Instruction[] instructions, Modifier[] modifiers, HashMap<String, String> parameters, String returnType) {
            this.name = name;
            this.instructions = instructions;
            this.modifiers = modifiers;
            this.parameters = parameters;
            this.returnType = returnType;
            this.calls = new HashMap<>();
            this.methodCalls = new ArrayList<>();
        }

        protected void addCall(Method m, String s) {
            this.calls.put(m, s);
        }

        protected void addMethodCall(Method m) {
            this.methodCalls.add(m);
        }

        protected HashMap<Method, String> getCalls() {
            return calls;
        }

        public ArrayList<Method> getMethodCalls() {
            return methodCalls;
        }

        protected void addToParameters(String type, String paramName) {
            parameters.put(type, paramName);
        }

        public static class Instruction {
            // The label associated with each line of code (i.e. METHOD_RETURN, CALL, etc)
            public final String label;
            // The line of code
            public final String code;
            // The line number of where the line of code occurs within the method body.
            public final String lineNumber;

            public Instruction(String label, String code, String lineNumber) {
                this.label = label;
                this.code = code;
                this.lineNumber = lineNumber;
            }
        }
    }

    public static class Attribute {
        // the name of the attribute
        public final String name;

        // list of modifiers the attribute has (0 or more)
        public final Modifier[] modifiers;

        // the type of theattribute
        public final String type;

        protected Attribute(String name, String type, Modifier[] modifiers) {
            this.name = name;
            this.type = type;
            this.modifiers = modifiers;
        }
    }
}