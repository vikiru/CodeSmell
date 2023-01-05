package com.CodeSmell;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A class within the source code.
 */
public class CPGClass {

    // The name of the class
    public final String name;

    // the filePath of the class (full path)
    public final String filePath;

    // the package name of the class
    public final String packageName;

    // the type of the object (class, enum, abstract class, interface)
    public final String type;

    // the list of methods within the class
    public ArrayList<Method> methods;

    // the list of fields within the class
    public ArrayList<Attribute> attributes;

    CPGClass(String name, String filePath, String packageName, String type) {
        this.name = name;
        this.filePath = filePath;
        this.packageName = packageName;
        this.type = type;
        this.methods = new ArrayList<Method>();
        this.attributes = new ArrayList<Attribute>();
    }

    @Override
    public String toString() {
        return String.format("CPGClass{name='%s', filePath='%s', packageName='%s', type='%s', methods=%s, attributes=%s}", name, filePath, packageName, type, methods, attributes);
    }

    public enum Modifier {
        PUBLIC("public"),
        PRIVATE("private"),
        PROTECTED("protected"),
        STATIC("protected"),
        SYNCHRONIZED("synchronized"),
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
    public static class Attribute {
        // the name of the attribute
        public final String name;

        // the package name of the field
        public final String packageName;

        // list of modifiers the attribute has (0 or more)
        public final Modifier[] modifiers;

        // the type of the attribute
        public String type;

        protected Attribute(String name, String packageName, String type, Modifier[] modifiers) {
            this.name = name;
            this.packageName = packageName;
            this.type = type;
            this.modifiers = modifiers;
        }

        @Override
        public String toString() {
            return "Attribute{" +
                    "name='" + name + '\'' +
                    ", packageName='" + packageName + '\'' +
                    ", modifiers=" + Arrays.toString(modifiers) +
                    ", type='" + type + '\'' +
                    '}';
        }
    }

    // A method belonging to a class
    public static class Method {

        // the parent class of the method (used to differentiate between methods within
        // methodCalls)
        public final CPGClass parentClass;

        // the name of the method
        public final String name;

        // the method body of the method with parameters excluding the modifiers and
        // return type i.e. "CPGClass(String name, String filePath, String type)"
        public final String methodBody;

        // a print out of the method instructions
        public final Instruction[] instructions;

        // list of modifiers the method has (0 or more)
        public final Modifier[] modifiers;

        // a arraylist containing all the method parameters
        public final ArrayList<Parameter> parameters;

        // the return type of the method
        public final String returnType;

        // return a list of methods which this calls
        public ArrayList<Method> methodCalls;

        protected Method(CPGClass parentClass, String name, String methodBody, Instruction[] instructions,
                         Modifier[] modifiers,
                         ArrayList<Parameter> parameters, String returnType) {

            this.parentClass = parentClass;
            this.name = name;
            this.methodBody = methodBody;
            this.instructions = instructions;
            this.modifiers = modifiers;
            this.parameters = parameters;
            this.returnType = returnType;
            this.methodCalls = new ArrayList<>();
        }

        @Override
        public String toString() {
            if (!returnType.equals("")) {
                return this.methodBody + ":" + this.returnType;
            } else return this.methodBody;
        }

        public static class Parameter {
            // the name of the method parameter
            public final String name;

            // the type of the method parameter
            public final String type;

            public Parameter(String name, String type) {
                this.name = name;
                this.type = type;
            }

            @Override
            public String toString() {
                return "Parameter{" +
                        "name='" + name + '\'' +
                        ", type='" + type + '\'' +
                        '}';
            }
        }

        // The instructions (lines of code) within each method body
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

            @Override
            public String toString() {
                return "Instruction{" +
                        "label='" + label + '\'' +
                        ", code='" + code + '\'' +
                        ", lineNumber='" + lineNumber + '\'' +
                        '}';
            }

        }
    }
}