package com.CodeSmell.parser;

import com.CodeSmell.parser.CPGClass.Attribute;
import com.CodeSmell.parser.CPGClass.Method;
import com.CodeSmell.smell.StatTracker;
import com.google.gson.*;
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
 * converts the JSON code to a code property object
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
            cpg = updateCPG(cpg);
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

    /**
     * Update the properties of each CPGClass and additionally add relations and packages to the cpg.
     *
     * @param cpg
     * @return
     */
    protected static CodePropertyGraph updateCPG(CodePropertyGraph cpg) {
        updateCPGClassProperties(cpg);
        RelationshipManager relationshipManager = new RelationshipManager(cpg);
        PackageManager packageManager = new PackageManager(cpg);
        return cpg;
    }

    /**
     * Updates the properties of each CPGClass within the CPG. This includes its inheritFrom list, updating
     * the parent of all of its attributes and methods, updating the type lists of each attribute and parameter,
     * and finally, updating the attribute and method calls of each method.
     *
     * @param cpg - The CodePropertyGraph containing the source code information
     * @return The updated method with its methodCalls
     */
    protected static void updateCPGClassProperties(CodePropertyGraph cpg) {
        LinkedHashMap<Method, ArrayList<Method>> methodCallMap = new LinkedHashMap<>();
        LinkedHashMap<Method, ArrayList<Attribute>> attributeCallMap = new LinkedHashMap<>();
        // Set inheritsFrom lists for all classes within cpg
        // Additionally, if a class inheritsFrom a superclass, add all of its attributes and methods here.
        cpg.getClasses().
                forEach(cpgClass -> cpgClass.setInheritsFrom(returnInheritsFrom(cpgClass, cpg)));
        // Set parent classes for all attributes and methods
        cpg.getClasses().
                forEach(cpgClass -> cpgClass.getAttributes().forEach(attribute -> attribute.setParent(cpgClass)));
        cpg.getClasses().
                forEach(cpgClass -> cpgClass.getMethods().forEach(method -> method.setParent(cpgClass)));
        // Set typeLists for all attributes
        cpg.getClasses().
                forEach(cpgClass -> cpgClass.getAttributes().
                        forEach(attribute -> attribute.setTypeList(returnTypeLists(attribute.attributeType, cpg))));
        for (CPGClass cpgClass : cpg.getClasses()) {
            for (CPGClass.Method method : cpgClass.getMethods()) {
                // Set typeLists for all parameters of the method
                Arrays.stream(method.parameters).
                        forEach(parameter -> parameter.setTypeList(returnTypeLists(parameter.type, cpg)));
                // Get the attribute and method calls of each method
                methodCallMap.put(method, returnMethodCalls(cpg, method));
                attributeCallMap.put(method, returnAttributeCalls(cpg, method));
            }
        }
        // Finally, set attribute calls and method calls of all methods.
        methodCallMap.forEach(Method::setMethodCalls);
        attributeCallMap.forEach(Method::setAttributeCalls);
    }


    /**
     * Return all the method calls that a method calls.
     *
     * @param cpg
     * @param methodToUpdate
     * @return
     */
    protected static ArrayList<Method> returnMethodCalls(CodePropertyGraph cpg, Method methodToUpdate) {
        // Helper variables
        StatTracker.Helper helper = new StatTracker.Helper(cpg);
        ArrayList<Method> allMethodsInCPG = helper.allMethods;
        ArrayList<Method> methodCalls = new ArrayList<>();
        // Get all possible calls where the instruction's methodCall is not empty
        Set<String> allDistinctCalls = new HashSet<>();
        Arrays.stream(methodToUpdate.instructions).
                filter(instruction -> instruction.label.equals("CALL")
                        && (!instruction.methodCall.equals(""))).
                forEach(ins -> allDistinctCalls.add(ins.methodCall));
        // Add all the method calls.
        for (String call : allDistinctCalls) {
            String[] splitted = call.split("\\.");
            if (splitted.length == 2) {
                String className = splitted[0].trim();
                String methodName = splitted[1].trim();
                var methodToAdd = allMethodsInCPG.stream().
                        filter(method -> (method.getParent().name.equals(className) && method.name.equals(methodName))).
                        collect(Collectors.toList());
                if (!methodToAdd.isEmpty()) {
                    methodCalls.add(methodToAdd.get(0));
                }
            }
        }
        //methodToUpdate.setAttributeCalls(returnAttributeCalls(helper, methodToUpdate, allLocalTypes));
        return methodCalls;
    }

    /**
     * Return all the attributes that a method calls
     *
     * @param cpg
     * @param methodToUpdate
     * @return
     */
    protected static ArrayList<Attribute> returnAttributeCalls(CodePropertyGraph cpg, Method methodToUpdate) {
        Set<CPGClass> allPossibleClasses = new HashSet<>();
        // Get all local classes created
        Set<CPGClass> allLocalTypes = new HashSet<>();
        methodToUpdate.getMethodCalls().stream().filter(method -> method.name.equals(method.getParent().name)).
                forEach(method -> allLocalTypes.add(method.getParent()));
        Set<Attribute> possibleAttributes = new HashSet<>();
        HashMap<String, Attribute> attributes = new HashMap<>();
        HashMap<String, Integer> fieldLine = new HashMap<>();
        Arrays.stream(methodToUpdate.instructions).filter(instruction -> instruction.label.equals("FIELD_IDENTIFIER")).
                forEach(ins -> fieldLine.putIfAbsent(ins.code, ins.lineNumber));
        CPGClass methodParent = methodToUpdate.getParent();
        allPossibleClasses.add(methodParent);
        boolean classInherits = methodParent.code.contains("extends");
        if (classInherits) {
            var result = methodParent.getInheritsFrom().stream().
                    filter(cpgToFind -> !cpgToFind.classType.equals("interface")).collect(Collectors.toList());
            if (!result.isEmpty()) {
                allPossibleClasses.add(result.get(0));
            }
        }
        methodParent.getAttributes().stream().filter(attr -> fieldLine.containsKey(attr.name)).forEach(possibleAttributes::add);
        possibleAttributes.forEach(attr -> attributes.put(attr.name, attr));
        attributes.keySet().forEach(fieldLine::remove);
        methodParent.getAttributes().stream().filter(attr -> attr.getTypeList().size() == 1).
                forEach(attr -> allPossibleClasses.addAll(attr.getTypeList()));
        Arrays.stream(methodToUpdate.parameters).filter(parameter -> parameter.getTypeList().size() == 1).
                forEach(parameter -> allPossibleClasses.addAll(parameter.getTypeList()));
        allPossibleClasses.addAll(allLocalTypes);
        for (CPGClass cpgClass : allPossibleClasses) {
            cpgClass.getAttributes().stream().filter(attr -> fieldLine.containsKey(attr.name) && attr.code.contains("public")).
                    forEach(possibleAttributes::add);
        }
        possibleAttributes.forEach(attr -> attributes.put(attr.name, attr));
        return new ArrayList<>(attributes.values());
    }


    /**
     * Return the list of CPGClasses that a given CPGClass inherits from (either interfaces or class / abstract class)
     */
    protected static ArrayList<CPGClass> returnInheritsFrom(CPGClass cpgClass, CodePropertyGraph cpg) {
        String[] code = cpgClass.code.replaceAll(",", " ").split(" ");
        ArrayList<CPGClass> inheritsFrom = new ArrayList<>();
        for (String splitStr : code) {
            var classFindResult = cpg.getClasses().stream().filter(cpgToFind -> cpgToFind.name.equals(splitStr)
                            && !cpgToFind.name.equals(cpgClass.name)).
                    limit(2).
                    collect(Collectors.toList());
            if (!classFindResult.isEmpty()) {
                inheritsFrom.add(classFindResult.get(0));
            }
        }
        // Add all super class attributes and methods
        var inheritanceCheck = inheritsFrom.stream().
                filter(cpgToFind -> !cpgToFind.classType.equals("interface")).collect(Collectors.toList());
        if (!inheritanceCheck.isEmpty()) {
            CPGClass superClass = inheritanceCheck.get(0);
            Set<Attribute> allAttributes = new LinkedHashSet<>(cpgClass.getAttributes());
            Set<Method> allMethods = new LinkedHashSet<>(cpgClass.getMethods());
            allAttributes.addAll(superClass.getAttributes());
            allMethods.addAll(superClass.getMethods());
            cpgClass.setAttributes(new ArrayList<>(allAttributes));
            cpgClass.setMethods(new ArrayList<>(allMethods));
        }
        return inheritsFrom;
    }

    /**
     * Return all the types (CPGClass) that are associated within a given string belonging to a parameter or
     * attribute
     *
     * @param type
     * @param cpg
     * @return
     */
    protected static ArrayList<CPGClass> returnTypeLists(String type, CodePropertyGraph cpg) {
        type = type.replace("<", " ").replace(">", " ").replace("[]", "");
        String[] splitStr = type.split(" ");
        Set<CPGClass> distinctTypes = new HashSet<>();
        for (String str : splitStr) {
            var typeCheck = cpg.getClasses().stream().
                    filter(cpgClass -> cpgClass.name.equals(str) ||
                            cpgClass.classFullName.equals(str)).collect(Collectors.toList());
            if (!typeCheck.isEmpty()) {
                distinctTypes.add(typeCheck.get(0));
            }
        }
        return new ArrayList<>(distinctTypes);
    }
}