package com.CodeSmell.parser;

import com.CodeSmell.parser.CPGClass.Attribute;
import com.CodeSmell.parser.CPGClass.Method;
import com.CodeSmell.smell.StatTracker;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The Parser class that reads in the JSON source code of the project that is being analysed and then
 * converts the JSON code to a code property object that can be
 */
public class Parser {

    public static final File CPG_BACKUP_JSON = new File("bak.cpg");
    public static final File JOERN_QUERY_LOGFILE = new File(
            "src/main/python/joern_query.log");

    static {
        JOERN_QUERY_LOGFILE.delete();
        try {
            JOERN_QUERY_LOGFILE.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static class ArrayListExclusion implements ExclusionStrategy {

        private Type listParameterizedType = new TypeToken<List<String>>() {
        }.getType();

        public boolean shouldSkipClass(Class<?> c) {
            return false;
        }

        public boolean shouldSkipField(FieldAttributes f) {
            return listParameterizedType.equals(f.getDeclaredType());
        }
    }

    private static int nextInputSize(InputStream cpgStream) throws IOException {
        byte[] contentBytes = new byte[4];
        int bytesRead = cpgStream.read(contentBytes, 0, 4);
        if (bytesRead != 4) {
            throw new RuntimeException("invalid byte size input");
        }
        ByteBuffer buffer = ByteBuffer.wrap(contentBytes);
        buffer.order(ByteOrder.nativeOrder());
        return buffer.getInt();
    }

    private static String nextJson(InputStream cpgStream,
                                   int size) throws IOException {
        byte[] contentBytes = new byte[size];
        ByteBuffer buffer = ByteBuffer.wrap(contentBytes);
        int bytesRead = cpgStream.read(contentBytes, 0, size);
        if (bytesRead != size) {
            throw new RuntimeException("Bad class size");
        }
        return StandardCharsets.UTF_8.decode(buffer).toString();
    }

    private static void readFromJoernQuery(CodePropertyGraph cpg,
                                           BufferedInputStream bis, Gson gson) throws IOException {

        int classSize = nextInputSize(bis);
        do {
            System.out.println("Reading in new class of size: " + classSize);
            String classJson = nextJson(bis, classSize);
            System.out.println(classJson);
            CPGClass cpgClass = gson.fromJson(classJson, CPGClass.class);
            if (cpgClass != null) {
                cpg.addClass(cpgClass);
                System.out.println(cpgClass.name);
            } else {
                throw new IllegalArgumentException("Bad JSON read by Parser.");
            }
            classSize = nextInputSize(bis);
        } while (classSize > 0);

        if (classSize != -1) {
            // after joern_query prints the last class, it must
            // print 0 (16 byte signed default edian)
            throw new IllegalArgumentException(
                    "Parser given illegal class size " + classSize);
        }
    }

    /**
     * Reads in a .json file to create an initial CodePropertyGraph and then calls methods to obtain missing information
     * and update necessary fields of every element within cpg. Finally, adds relationships to the cpg object and then
     * serializes it into a .json file.
     *
     * @param cpgStream        - The input stream from JoernServer
     * @param serializedObject - if true read serialized backup, if false read as
     *                         joern_query.py  standard output
     * @return A CodePropertyGraph object containing the source code classes and all relations
     */
    public static CodePropertyGraph initializeCPG(InputStream cpgStream,
                                                  boolean serializedObject) throws InvalidClassException {

        CodePropertyGraph cpg = new CodePropertyGraph();

        if (!serializedObject) {
            System.out.println("Reading in CPG from joern_query.");
            try {
                Gson gson = new GsonBuilder()
                        .setExclusionStrategies(new ArrayListExclusion())
                        .create();
                readFromJoernQuery(cpg, new BufferedInputStream(cpgStream), gson);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
            cpg = assignRelationships(cpg);
            writeBackup(cpg);

        } else {
            System.out.println("Reading backup file");
            try {
                ObjectInputStream ois = new ObjectInputStream(cpgStream);
                cpg = (CodePropertyGraph) ois.readObject();
            } catch (InvalidClassException e) {
                e.printStackTrace();
                throw e;
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        System.out.printf("Project read: %d classes, %d relations\n",
                cpg.getClasses().size(), cpg.getRelations().size());
        return cpg;
    }

    protected static void writeBackup(CodePropertyGraph cpg) {
        // write the resulting CPG
        // to a backup file for recovery in the event of a crash
        try {
            FileOutputStream fos = new FileOutputStream(
                    CPG_BACKUP_JSON.getPath());
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(cpg);
            oos.flush();
            oos.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    protected static CodePropertyGraph assignRelationships(CodePropertyGraph cpg) {
        cpg = assignProperAttributesAndMethods(cpg, 2);
        RelationshipManager relationshipManager = new RelationshipManager(cpg);
        cpg = relationshipManager.cpg;
        return cpg;
    }

    /**
     * Iterates through the provided CodePropertyGraph object, calling helper
     * methods to update the attributes and methods. Once each attribute and method is updated,
     * a new CPGClass is created containing these new Attribute and Method objects. This class is then added to a
     * new CodePropertyGraph which will finally be returned at the end.
     *
     * @param cpg        - The CodePropertyGraph object containing the updated Attribute and Methods
     * @param iterations - The number of iterations that the method must be called in order to properly update fields and methods.
     * @return
     */
    protected static CodePropertyGraph assignProperAttributesAndMethods(CodePropertyGraph cpg, int iterations) {
        CodePropertyGraph graph = new CodePropertyGraph();
        for (CPGClass cpgClass : cpg.getClasses()) {
            ArrayList<Attribute> properAttributes = new ArrayList<>(Arrays.asList(cpgClass.attributes));
            ArrayList<Method> properMethods = new ArrayList<>();
            for (Method m : cpgClass.methods) {
                int iterationNumber = (iterations - 1) == 0 ? 2 : 1;
                // ignore lambdas for now
                if (!m.name.contains("lambda")) {
                    Method properMethod = updateMethodWithMethodCalls(cpg, m, iterationNumber);
                    properMethods.add(properMethod);
                }
            }

            CPGClass properClass = new CPGClass(cpgClass.name, cpgClass.code, cpgClass.lineNumber, cpgClass.importStatements, cpgClass.modifiers,
                    cpgClass.classFullName, cpgClass.inheritsFrom, cpgClass.classType, cpgClass.filePath, cpgClass.fileLength, cpgClass.emptyLines,
                    cpgClass.nonEmptyLines, cpgClass.packageName,
                    properAttributes.toArray(new Attribute[properAttributes.size()]),
                    properMethods.toArray(new Method[properMethods.size()]));
            graph.addClass(properClass);
        }
        if (iterations - 1 == 0) {
            return graph;
        } else return assignProperAttributesAndMethods(graph, iterations - 1);
    }

    /**
     * Updates a given method with its proper methodCalls, requires two iterations to get all the proper method
     * calls of each method called within this list.
     *
     * @param cpg            - The CodePropertyGraph containing the source code information
     * @param methodToUpdate - The method that is having its methodCalls field updated
     * @return The updated method with its methodCalls
     */
    protected static Method updateMethodWithMethodCalls(CodePropertyGraph cpg, Method methodToUpdate, int iterationNumber) {
        StatTracker.Helper helper = new StatTracker.Helper(cpg);
        ArrayList<Method> allMethodsInCPG = helper.allMethods;
        ArrayList<String> allMethodNames = new ArrayList<>();
        ArrayList<String> allClassNames = helper.allClassNames;
        ArrayList<Method.Instruction> allMethodInstructions = new ArrayList<>(Arrays.asList(methodToUpdate.instructions));
        cpg.getClasses().forEach(cpgClass -> Arrays.stream(cpgClass.methods).forEach(method -> allMethodNames.add(method.name)));

        // Get the indexes of the names of each Method called by methodToUpdate
        ArrayList<Integer> indexes = new ArrayList<>();
        for (Method.Instruction instruction : methodToUpdate.instructions) {
            String label = instruction.label;
            String code = instruction.code;
            int lineNumber = instruction.lineNumber;
            String methodCall = instruction.methodCall;
            if (label.equals("CALL") && (!methodCall.equals("") || !methodCall.equals("toString"))) {
                int indexOfMethodCalled = allMethodNames.indexOf(methodCall);
                int lastIndexOfMethodCalled = allMethodNames.lastIndexOf(methodCall);
                if (indexOfMethodCalled != lastIndexOfMethodCalled) {
                    Method firstMethod = allMethodsInCPG.get(indexOfMethodCalled);
                    Method secondMethod = allMethodsInCPG.get(lastIndexOfMethodCalled);
                    List<CPGClass> filteredClasses = cpg.getClasses().stream().
                            filter(cpgClass -> cpgClass.name.equals(firstMethod.parentClassName) ||
                                    cpgClass.name.equals(secondMethod.parentClassName)).collect(Collectors.toList());
                    var result = filteredClasses.stream().
                            filter(cpgClass -> cpgClass.classType.equals("interface")).collect(Collectors.toList());
                    if (!result.isEmpty()) {
                        if (firstMethod.parentClassName.equals(result.get(0).name)) {
                            indexes.add(lastIndexOfMethodCalled);
                        } else if (secondMethod.parentClassName.equals(result.get(0).name)) {
                            indexes.add(indexOfMethodCalled);
                        }
                    }
                } else {
                    if (indexOfMethodCalled != -1) {
                        indexes.add(indexOfMethodCalled);
                    }
                }
            }
        }
        var identifierResult = allMethodInstructions.stream().
                filter(ins -> ins.label.equals("IDENTIFIER")
                        && allClassNames.contains(ins.code)).collect(Collectors.toList());
        var methodCallResult = allMethodInstructions.stream().
                filter(ins -> ins.label.equals("CALL") && !ins.methodCall.equals("")).collect(Collectors.toList());
        var localResult = allMethodInstructions.stream().
                filter(ins -> ins.label.equals("LOCAL")).collect(Collectors.toList());
        // Check for identifier and calls
        for (Method.Instruction identifier : identifierResult) {
            String parentName = identifier.code;
            for (Method.Instruction mc : methodCallResult) {
                String methodCalled = mc.methodCall;
                // Determine the number of method parameters provided by the instruction code
                String code = mc.code;
                int startIndex = code.indexOf("(");
                code = code.substring(startIndex).replace("(", "").replace(")", "").trim();
                int paramCount = 0;
                if (code.contains(",")) {
                    String[] params = code.split(",");
                    paramCount = params.length;
                } else paramCount = 1;
                int finalParamCount = paramCount;
                // Find the method matching parentClassName, methodName, and parameter count
                var methodResult = allMethodsInCPG.stream().
                        filter(method -> method.name.equals(methodCalled)
                                && method.parentClassName.equals(parentName)
                                && method.parameters.length == finalParamCount).collect(Collectors.toList());
                if (!methodResult.isEmpty()) {
                    indexes.add(allMethodsInCPG.indexOf(methodResult.get(0)));
                }
            }
        }
        // Check for local variable creation
        for (Method.Instruction localIns : localResult) {
            String[] localInstanceVar = localIns.code.split(" ");
            if (localInstanceVar.length == 2) {
                String type = localInstanceVar[0];
                String name = localInstanceVar[1];
                if (allClassNames.contains(type)) {
                    var checkCreation = methodCallResult.stream().
                            filter(i -> i.methodCall.contains(type) && i.code.contains("=")).collect(Collectors.toList());
                    for (Method.Instruction creation : checkCreation) {
                        String methodCalled = creation.methodCall;
                        int index = allMethodNames.indexOf(methodCalled);
                        if (index != -1) {
                            indexes.add(index);
                        }
                    }
                }
            }
        }
        ArrayList<Method> methodCalls = new ArrayList<>();
        Set<Integer> uniqueIndexes = new LinkedHashSet<>(indexes);
        uniqueIndexes.forEach(index -> methodCalls.add(allMethodsInCPG.get(index)));

        if (iterationNumber == 1) {
            Method properMethod = new Method(methodToUpdate.parentClassName,
                    methodToUpdate.lineNumberStart, methodToUpdate.lineNumberEnd,
                    methodToUpdate.totalMethodLength, methodToUpdate.name, methodToUpdate.modifiers, methodToUpdate.returnType,
                    methodToUpdate.methodBody, methodToUpdate.parameters, methodToUpdate.instructions);
            properMethod.setMethodCalls(methodCalls);
            return properMethod;
        } else {
            ArrayList<Method> fixMethodReferences = new ArrayList<>();
            for (Method m : methodToUpdate.getMethodCalls()) {
                var result = allMethodsInCPG.stream().
                        filter(method -> method.name.equals(m.name) && method.parentClassName.equals(m.parentClassName))
                        .collect(Collectors.toList());
                fixMethodReferences.add(result.get(0));
            }
            methodToUpdate.setMethodCalls(fixMethodReferences);
            return methodToUpdate;
        }
    }
}
