package com.CodeSmell.parser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A class within the {@link CodePropertyGraph}.
 */
public class CPGClass implements Serializable {
    /**
     * The name of the class
     */
    public final String name;

    /**
     * The full name of the class (either the same as name or
     * if the class is a nested class, will be "CPGClass.Attribute" for example)
     */
    public final String classFullName;

    /**
     * The package that the class belongs to, if any
     */
    public final String packageName;

    /**
     * The import statements imported from the file where the class is defined
     */
    public final String[] importStatements;

    /**
     * The class declaration (i.e. "public abstract class Smell")
     */
    public final String code;

    /**
     * The line number in which the class is declared
     */
    public final int lineNumber;

    /**
     * The list containing all the {@link Modifier} of the class
     */
    public final List<Modifier> modifiers;

    /**
     * The type of the class ("abstract class", "class", "enum", "interface")
     */
    public final String classType;

    /**
     * The full filepath pointing to where the class is stored
     */
    public final String filePath;

    /**
     * The total length of the file where the class exists
     */
    public final int fileLength;

    /**
     * The total number of empty lines within a class
     */
    public final int emptyLines;

    /**
     * The total number of non-empty lines within a class (includes comments)
     */
    public final int nonEmptyLines;

    /**
     * The list of classes that the class inherits from in some way (can include interfaces as well)
     */
    private List<CPGClass> inheritsFrom;

    /**
     * An array of all of the {@link Attribute} that a class has
     */
    private List<Attribute> attributes;

    /**
     * All the {@link Method} that a class has
     */
    private List<Method> methods;

    /**
     * All the outward {@link CodePropertyGraph.Relation} that a class has.
     */
    private List<CodePropertyGraph.Relation> outwardRelations;

    public CPGClass(String name,
                    String classFullName,
                    String packageName,
                    String[] importStatements,
                    String code,
                    int lineNumber,
                    ArrayList<Modifier> modifiers,
                    String classType,
                    String filePath,
                    int fileLength,
                    int emptyLines,
                    int nonEmptyLines,
                    ArrayList<Attribute> attributes,
                    ArrayList<Method> methods) {
        this.name = name;
        this.classFullName = classFullName;
        this.packageName = packageName;
        this.importStatements = importStatements;
        this.code = code;
        this.lineNumber = lineNumber;
        this.modifiers = Collections.unmodifiableList(modifiers);
        this.classType = classType;
        this.filePath = filePath;
        this.fileLength = fileLength;
        this.emptyLines = emptyLines;
        this.nonEmptyLines = nonEmptyLines;
        this.attributes = attributes;
        this.methods = methods;
        this.inheritsFrom = new ArrayList<>();
        this.outwardRelations = new ArrayList<>();
    }

    public List<Attribute> getAttributes() {
        return new ArrayList<>(attributes);
    }

    protected void setAttributes(List<Attribute> attributes) {
        this.attributes = Collections.unmodifiableList(attributes);
    }

    public List<Method> getMethods() {
        return new ArrayList<>(methods);
    }

    protected void setMethods(List<Method> methods) {
        this.methods = Collections.unmodifiableList(methods);
    }

    public List<CPGClass> getInheritsFrom() {
        return new ArrayList<>(inheritsFrom);
    }

    protected void setInheritsFrom(List<CPGClass> inheritsFrom) {
        this.inheritsFrom = Collections.unmodifiableList(inheritsFrom);
    }

    /**
     * Add an outward relation to the existing outwardRelations of a given class
     *
     * @param r - The relation to add
     */
    public void addOutwardRelation(CodePropertyGraph.Relation r) {
        this.outwardRelations.add(r);
    }

    /**
     * Returns all the outward relations of a given class
     *
     * @return - The outward relations of a given class
     */
    public List<CodePropertyGraph.Relation> getOutwardRelations() {
        return new ArrayList<>(this.outwardRelations);
    }

    @Override
    public String toString() {
        return this.code;
    }

    /**
     * All the potential modifiers that can exist on a class, attribute or method
     */
    public enum Modifier {
        PACKAGE_PRIVATE("package private"),
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

        /**
         * The string representation of a given enum of type, Modifier
         */
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
        /**
         * The name of the attribute
         */
        public final String name;

        /**
         * The name of the package in which the type of the Attribute originates from
         * (i.e. "java.util.List")
         */
        public final String packageName;

        /**
         * The parent class which owns this attribute
         */
        private final CPGClass[] parentClass;

        /**
         * The full line of code in which the attribute is declared
         */
        public final String code;

        /**
         * The line number in which the attribute was declared
         */
        public final int lineNumber;

        /**
         * All the modifiers that the attribute has
         * {@link Modifier}
         */
        public final List<Modifier> modifiers;

        /**
         * The full type of the attribute
         */
        public final String attributeType;

        /**
         * All the types that can be extracted from the attributeType
         * (i.e. "HashMap < CPGClass, List< CPGClass.Method > >" will give ["CPGClass", "CPGClass.Method"]
         */
        private List<CPGClass> typeList;

        public Attribute(String name,
                         String packageName,
                         String code,
                         int lineNumber,
                         ArrayList<Modifier> modifiers,
                         String attributeType) {
            this.name = name;
            this.parentClass = new CPGClass[1];
            this.packageName = packageName;
            this.code = code;
            this.lineNumber = lineNumber;
            this.modifiers = Collections.unmodifiableList(modifiers);
            this.attributeType = attributeType;
            this.typeList = new ArrayList<>();
        }

        public CPGClass getParent() {
            return parentClass[0];
        }

        protected void setParent(CPGClass parent) {
            parentClass[0] = parent;
        }

        public List<CPGClass> getTypeList() {
            return new ArrayList<>(typeList);
        }

        protected void setTypeList(List<CPGClass> typeList) {
            this.typeList = Collections.unmodifiableList(typeList);
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

        /**
         * The name of the method
         */
        public final String name;

        /**
         * The class which owns the method
         */
        private final CPGClass[] parentClass;

        /**
         * The method body containing the name of the method along with all of its parameters, if any
         * (i.e. "CPGClass(String name, String filePath, String type)" )
         */
        public final String methodBody;

        /**
         * All the {@link Modifier} that a method has
         */
        public final List<Modifier> modifiers;

        /**
         * All the method {@link Parameter} belonging to a method
         */
        public final List<Parameter> parameters;

        /**
         * The return type of the method, if any
         */
        public final String returnType;

        /**
         * The line number where the method starts
         */
        public final int lineNumberStart;

        /**
         * The line number where the method ends
         */
        public final int lineNumberEnd;

        /**
         * The total length of the method (lineNumberEnd - lineNumberStart)
         */
        public final int totalMethodLength;


        /**
         * All the method {@link Instruction} belonging to a method
         */
        public final List<Instruction> instructions;

        /**
         * All the methods that this method calls, if any
         */
        private List<Method> methodCalls;

        /**
         * All the attributes that this method uses, if any
         */
        private List<Attribute> attributeCalls;

        public Method(String name,
                      String methodBody,
                      ArrayList<Modifier> modifiers,
                      ArrayList<Parameter> parameters,
                      String returnType,
                      int lineNumberStart,
                      int lineNumberEnd,
                      int totalMethodLength,
                      ArrayList<Instruction> instructions) {
            this.name = name;
            this.parentClass = new CPGClass[1];
            this.methodBody = methodBody;
            this.modifiers = Collections.unmodifiableList(modifiers);
            this.parameters = Collections.unmodifiableList(parameters);
            this.returnType = returnType;
            this.lineNumberStart = lineNumberStart;
            this.lineNumberEnd = lineNumberEnd;
            this.totalMethodLength = totalMethodLength;
            this.instructions = Collections.unmodifiableList(instructions);
            this.attributeCalls = new ArrayList<>();
            this.methodCalls = new ArrayList<>();
        }


        /**
         * Return all the method calls of a method, if any
         *
         * @return - All the method calls of the method
         */
        public List<Method> getMethodCalls() {
            return new ArrayList<>(methodCalls);
        }

        /**
         * Set the methodCalls field of a method to be equal to the provided methodCalls list
         *
         * @param methodCalls - The methodCalls belonging to the method
         */
        protected void setMethodCalls(List<Method> methodCalls) {
            this.methodCalls = Collections.unmodifiableList(methodCalls);
        }

        /**
         * Return all the attribute calls of a method, if any
         *
         * @return - All the attribute calls of the method
         */
        public List<Attribute> getAttributeCalls() {
            return new ArrayList<>(attributeCalls);
        }

        /**
         * Set the attributeCalls field of a method to be equal to the provided attributeCalls list
         *
         * @param attributeCalls - The attributeCalls belonging to the method
         */
        protected void setAttributeCalls(List<Attribute> attributeCalls) {
            this.attributeCalls = Collections.unmodifiableList(attributeCalls);
        }

        public CPGClass getParent() {
            return parentClass[0];
        }

        protected void setParent(CPGClass parent) {
            parentClass[0] = parent;
        }

        @Override
        public String toString() {
            if (!returnType.equals("")) {
                return this.methodBody + " : " + this.returnType;
            } else return this.methodBody;
        }

        /**
         * A parameter which belongs to a method
         */
        public static class Parameter implements Serializable {
            /**
             * The full line of code belonging to a method parameter
             */
            public final String code;

            /**
             * The name of the method parameter
             */
            public final String name;

            /**
             * The full type of the method parameter
             */
            public final String type;

            /**
             * All the types that can be extracted from the parameter type
             * (i.e. "HashMap < CPGClass, List< CPGClass.Method > >" will give ["CPGClass", "CPGClass.Method"]
             */
            private List<CPGClass> typeList;

            public Parameter(String code, String name, String type) {
                this.code = code;
                this.name = name;
                this.type = type;
                this.typeList = new ArrayList<>();
            }

            public List<CPGClass> getTypeList() {
                return new ArrayList<>(typeList);
            }

            protected void setTypeList(List<CPGClass> typeList) {
                this.typeList = Collections.unmodifiableList(typeList);
            }

            @Override
            public String toString() {
                return this.type + " : " + this.name;
            }
        }

        /**
         * Each individual line of code that exists within a method body
         */
        public static class Instruction implements Serializable {

            /**
             * The label associated with each line of code (i.e. METHOD_RETURN, CALL, FIELD_IDENTIFIER, LOCAL, etc)
             */
            public final String label;

            /**
             * The line of code
             */
            public final String code;

            /**
             * The line number of where the line of code occurs within the method body
             */
            public final int lineNumber;

            /**
             * The name of the method that the instruction is calling, if any
             */
            public final String methodCall;

            public Instruction(String label, String code, int lineNumber, String methodCall) {
                this.label = label;
                this.code = code;
                this.lineNumber = lineNumber;
                this.methodCall = methodCall;
            }

            @Override
            public String toString() {
                return String.format(
                        "label: %s\n" +
                                "code: %s\n" +
                                "methodCall: %s",
                        label, code, methodCall);
            }
        }
    }
}