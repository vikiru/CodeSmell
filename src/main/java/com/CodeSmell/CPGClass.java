package com.CodeSmell;

import java.util.ArrayList;
import java.util.HashMap;

public class CPGClass {

    public static class Method {
        // the name of the method
        public final String name;

        // a print out of the method instructions
        public final String[] instructions;

        // list of modifiers the method has (0 or more)
        public final Modifier[] modifiers;

        // return a list of methods which this calls
        private HashMap<Method, String> calls;

        private ArrayList<Method> methodCalls;

        // a hashmap of all of the method parameters, key is the name of the parameters
        // and value is the type
        public final HashMap<String, String> parameters;

        protected Method(String name, String[] instructions, Modifier[] modifiers, HashMap<String, String> parameters) {
            this.name = name;
            this.instructions = instructions;
            this.modifiers = modifiers;
            this.parameters = parameters;
            this.calls = new HashMap<>();
            this.methodCalls = new ArrayList<>();
        }

        protected void addCall(Method m, String s) {
            this.calls.put(m,s);
        }

        protected void addMethodCall(Method m)
        {
            this.methodCalls.add(m);
        }

        protected HashMap<Method, String> getCalls()
        {
            return calls;
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

        // the type of theattribute
        public final String type;

        protected Attribute(String name, String type, Modifier[] modifiers) {
            this.name = name;
            this.type = type;
            this.modifiers = modifiers;
        }
    }

    public final String name;
    public final String type;
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

    CPGClass(String name, String type) {
        this.name = name;
        this.type = type;
        this.methods = new ArrayList<Method>();
        this.attributes = new ArrayList<Attribute>();
    }
}