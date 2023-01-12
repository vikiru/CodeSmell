package com.CodeSmell;

import com.google.gson.annotations.Expose;

import java.util.ArrayList;

/**
 * A class within the source code.
 */
public class CPGClass {

    // The name of the class
    @Expose(serialize = true, deserialize = true)
    public final String name;

    @Expose(serialize = true, deserialize = false)
    public final String code;

    @Expose(serialize = true, deserialize = false)
    public final String[] importStatements;

    @Expose(serialize = true, deserialize = false)
    public final Modifier[] modifiers;

    // The full name of the class (either the same as name or if the class is a nested class, will be "CPGClass$Attribute" for example)
    @Expose(serialize = true, deserialize = true)
    public final String classFullName;

    // the type of the object (class, enum, abstract class, interface)
    @Expose(serialize = true, deserialize = true)
    public final String classType;

    // the filePath of the class (full path)
    @Expose(serialize = true, deserialize = true)
    public final String filePath;

    // the package name of the class
    @Expose(serialize = true, deserialize = true)
    public final String packageName;

    // the list of fields within the class
    @Expose(serialize = true, deserialize = true)
    public final Attribute[] attributes;

    // the list of methods within the class
    @Expose(serialize = true, deserialize = true)
    public final Method[] methods;

    CPGClass(String name, String code, String[] importStatements, Modifier[] modifiers, String classFullName, String classType, String filePath, String packageName, Attribute[] attributes, Method[] methods) {
        this.name = name;
        this.code = code;
        this.importStatements = importStatements;
        this.modifiers = modifiers;
        this.classFullName = classFullName;
        this.classType = classType;
        this.filePath = filePath;
        this.packageName = packageName;
        this.attributes = attributes;
        this.methods = methods;
    }

    @Override
    public String toString() {
        return this.code;
    }

    public enum Modifier {
        @Expose(serialize = true, deserialize = true)
        PUBLIC("public"),
        @Expose(serialize = true, deserialize = true)
        PRIVATE("private"),
        @Expose(serialize = true, deserialize = true)
        PROTECTED("protected"),
        @Expose(serialize = true, deserialize = true)
        STATIC("static"),
        @Expose(serialize = true, deserialize = true)
        SYNCHRONIZED("synchronized"),
        @Expose(serialize = true, deserialize = true)
        VOLATILE("volatile"),
        @Expose(serialize = true, deserialize = true)
        ABSTRACT("abstract"),
        @Expose(serialize = true, deserialize = true)
        NATIVE("native"),
        @Expose(serialize = true, deserialize = true)
        FINAL("final");

        @Expose(serialize = true, deserialize = true)
        public final String modString;

        Modifier(String modString) {
            this.modString = modString;
        }

        @Override
        public String toString() {
            return this.modString;
        }
    }

    /**
     * An attribute belonging to a class
     */
    public static class Attribute {
        // the name of the attribute
        @Expose(serialize = true, deserialize = true)
        public final String name;

        @Expose(serialize = true, deserialize = false)
        public final String code;

        // the package name of the field
        @Expose(serialize = true, deserialize = true)
        public final String packageName;

        // list of modifiers the attribute has (0 or more)
        @Expose(serialize = true, deserialize = true)
        public final Modifier[] modifiers;

        // the type of the attribute
        @Expose(serialize = true, deserialize = true)
        public final String attributeType;

        // the full type decl obtained from Joern (Without modification)
        @Expose(serialize = true, deserialize = true)
        public final String typeFullName;

        protected Attribute(String name, String code, String packageName, String attributeType, Modifier[] modifiers, String typeFullName) {
            this.name = name;
            this.code = code;
            this.packageName = packageName;
            this.attributeType = attributeType;
            this.modifiers = modifiers;
            this.typeFullName = typeFullName;
        }

        @Override
        public String toString() {
            return this.name + " : " + this.attributeType;
        }
    }

    /**
     * A method belonging to a class
     */
    public static class Method {

        // the parent class of the method (used to differentiate between methods within
        // methodCalls)
        @Expose(serialize = true, deserialize = true)
        public final String parentClassName;

        @Expose(serialize = true, deserialize = true)
        public final String code;

        // the name of the method
        @Expose(serialize = true, deserialize = true)
        public final String name;

        // list of modifiers the method has (0 or more)
        @Expose(serialize = true, deserialize = true)
        public final Modifier[] modifiers;

        // the return type of the method
        @Expose(serialize = true, deserialize = true)
        public final String returnType;

        // the method body of the method with parameters excluding the modifiers and
        // return type i.e. "CPGClass(String name, String filePath, String type)"
        @Expose(serialize = true, deserialize = true)
        public final String methodBody;

        // an array containing all the method parameters
        @Expose(serialize = true, deserialize = true)
        public final Parameter[] parameters;

        // a print out of the method instructions
        @Expose(serialize = true, deserialize = true)
        public final Instruction[] instructions;

        // return a list of methods which this calls
        @Expose(serialize = true, deserialize = true)
        private ArrayList<Method> methodCalls;

        protected Method(String parentClassName, String code, String name, Modifier[] modifiers,
                         String returnType, String methodBody, Parameter[] parameters, Instruction[] instructions) {

            this.parentClassName = parentClassName;
            this.code = code;
            this.name = name;
            this.modifiers = modifiers;
            this.returnType = returnType;
            this.methodBody = methodBody;
            this.parameters = parameters;
            this.instructions = instructions;
            this.methodCalls = new ArrayList<>();
        }

        public ArrayList<Method> getMethodCalls() {
            return new ArrayList<>(methodCalls);
        }

        protected void setMethodCalls(ArrayList<Method> methodCalls) {
            this.methodCalls = methodCalls;
        }

        @Override
        public String toString() {
            if (!returnType.equals("")) {
                return this.methodBody + " : " + this.returnType;
            } else return this.methodBody;
        }

        public static class Parameter {
            // the name of the method parameter
            @Expose(serialize = true, deserialize = true)
            public final String name;

            // the type of the method parameter
            @Expose(serialize = true, deserialize = true)
            public final String type;

            public Parameter(String name, String type) {
                this.name = name;
                this.type = type;
            }

            @Override
            public String toString() {
                return this.type + " : " + this.name;
            }
        }

        // The instructions (lines of code) within each method body
        public static class Instruction {
            // The label associated with each line of code (i.e. METHOD_RETURN, CALL, etc)
            @Expose(serialize = true, deserialize = true)
            public final String label;
            // The line of code
            @Expose(serialize = true, deserialize = true)
            public final String code;
            // The line number of where the line of code occurs within the method body.
            @Expose(serialize = true, deserialize = true)
            public final String lineNumber;
            // The name of the method that the instruction is calling, if any
            @Expose(serialize = true, deserialize = true)
            public final String methodCall;

            public Instruction(String label, String code, String lineNumber, String methodCall) {
                this.label = label;
                this.code = code;
                this.lineNumber = lineNumber;
                this.methodCall = methodCall;
            }

            @Override
            public String toString() {
                return this.code;
            }
        }
    }
}