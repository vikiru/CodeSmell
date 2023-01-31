package com.CodeSmell.parser;

import java.util.ArrayList;
import java.io.Serializable;

/**
 * A class within the source code.
 */
public class CPGClass implements Serializable {

    // The name of the class
    public final String name;

    public final String code;

    // The line number which the class was declared within the file
    public final int lineNumber;

    public final String[] importStatements;

    public final Modifier[] modifiers;

    // The full name of the class (either the same as name or if the class is a nested class, will be "CPGClass$Attribute" for example)
    public final String classFullName;

    public final String[] inheritsFrom;

    // the type of the object (class, enum, abstract class, interface)
    public final String classType;

    // the filePath of the class (full path)
    public final String filePath;

    // the package name of the class
    public final String packageName;

    // the list of fields within the class
    public final Attribute[] attributes;

    // the list of methods within the class
    public final Method[] methods;

    // a list of outward relations of the class
    private ArrayList<CodePropertyGraph.Relation> outwardRelations;

    CPGClass(String name, String code, int lineNumber, String[] importStatements, Modifier[] modifiers, String classFullName, String[] inheritsFrom, String classType, String filePath, String packageName, Attribute[] attributes, Method[] methods) {
        this.name = name;
        this.code = code;
        this.lineNumber = lineNumber;
        this.importStatements = importStatements;
        this.modifiers = modifiers;
        this.classFullName = classFullName;
        this.inheritsFrom = inheritsFrom;
        this.classType = classType;
        this.filePath = filePath;
        this.packageName = packageName;
        this.attributes = attributes;
        this.methods = methods;
        this.outwardRelations = new ArrayList<CodePropertyGraph.Relation>();
    }

    public void addOutwardRelation(CodePropertyGraph.Relation r) {
        this.outwardRelations.add(r);
    }

    public ArrayList<CodePropertyGraph.Relation> getOutwardRelations() {
        return new ArrayList<>(this.outwardRelations);
    }


    @Override
    public String toString() {
        return this.code;
    }

    public enum Modifier {
        PUBLIC("public"),
        PRIVATE("private"),
        PROTECTED("protected"),
        STATIC("static"),
        SYNCHRONIZED("synchronized"),
        VIRTUAL("virtual"),
        VOLATILE("volatile"),
        ABSTRACT("abstract"),
        NATIVE("native"),
        FINAL("final");

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
    public static class Attribute implements Serializable {
        public final String parentClassName;

        // the name of the attribute
        public final String name;

        // line number where the field was declared within the file
        public final int lineNumber;

        public final String code;

        // the package name of the field
        public final String packageName;

        // list of modifiers the attribute has (0 or more)
        public final Modifier[] modifiers;

        // the type of the attribute
        public final String attributeType;

        // the full type decl obtained from Joern (Without modification)
        public final String typeFullName;

        protected Attribute(String parentClassName, String name, int lineNumber, String code, String packageName, String attributeType, Modifier[] modifiers, String typeFullName) {
            this.parentClassName = parentClassName;
            this.name = name;
            this.lineNumber = lineNumber;
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
    public static class Method implements Serializable {

        // the parent class of the method (used to differentiate between methods within
        // methodCalls)
        public final String parentClassName;

        public final String code;

        // line numbers where the method starts and ends
        public final int lineNumberStart;

        public final int lineNumberEnd;

        // the name of the method
        public final String name;

        // list of modifiers the method has (0 or more)
        public final Modifier[] modifiers;

        // the return type of the method
        public final String returnType;

        // the method body of the method with parameters excluding the modifiers and
        // return type i.e. "CPGClass(String name, String filePath, String type)"
        public final String methodBody;

        // an array containing all the method parameters
        public final Parameter[] parameters;

        // a print out of the method instructions
        public final Instruction[] instructions;

        // a list of methods which this calls
        // gson IGNORE THIS
        private ArrayList<Method> methodCalls;

        protected Method(String parentClassName, String code, int lineNumberStart,
                         int lineNumberEnd, String name, Modifier[] modifiers,
                         String returnType, String methodBody, Parameter[] parameters, Instruction[] instructions) {

            this.parentClassName = parentClassName;
            this.code = code;
            this.lineNumberStart = lineNumberStart;
            this.lineNumberEnd = lineNumberEnd;
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

        public static class Parameter implements Serializable {

            public final String evaluationStrategy;

            public final String code;

            // the name of the method parameter
            public final String name;

            // the type of the method parameter
            public final String type;

            public Parameter(String evaluationStrategy, String code, String name, String type) {
                this.evaluationStrategy = evaluationStrategy;
                this.code = code;
                this.name = name;
                this.type = type;
            }

            @Override
            public String toString() {
                return this.type + " : " + this.name;
            }
        }

        // The instructions (lines of code) within each method body
        public static class Instruction implements Serializable {

            // The label associated with each line of code (i.e. METHOD_RETURN, CALL, etc)
            public final String label;

            // The line of code
            public final String code;

            // The line number of where the line of code occurs within the method body.
            public final int lineNumber;

            // The name of the method that the instruction is calling, if any
            public final String methodCall;

            public Instruction(String label, String code, int lineNumber, String methodCall) {
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