package com.CodeSmell;

import com.CodeSmell.CPGClass.*;
import com.google.gson.Gson;
import javafx.util.Pair;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Parser {

    public static void main(String[] args) {
        Parser p = new Parser();
        CodePropertyGraph g = p.buildCPG("");
    }

    public void getMissingAttributeType(CPGClass cpgClass, Attribute attribute) {
        String className = cpgClass.name;
        // Accounting for nested classes (i.e. CPGClass$Method)
        int separatorIndex = cpgClass.name.indexOf("$");
        if (separatorIndex != -1) {
            className = className.substring(separatorIndex).replace("$", "");
        }
        ArrayList<Method> classMethods = cpgClass.getMethods();
        String originalType = attribute.type;
        for (Method currentMethod : classMethods) {
            int index = currentMethod.methodBody.indexOf("(");
            if (index != -1) {
                String methodBodyWithoutParams = currentMethod.methodBody.substring(0, index);
                // First, check the constructor of the class, if it is present to see if the type of the field can be
                // identified
                if (methodBodyWithoutParams.equals(className)) {
                    for (Method.Instruction instruction : currentMethod.instructions) {
                        // Check the constructor, if the desired field is a parameter this will obtain the type
                        if (instruction.label.equals("METHOD")) {
                            HashMap<String, String> currentMethodsParameters = currentMethod.parameters;
                            if (currentMethodsParameters.containsKey(attribute.name)) {
                                attribute.type = currentMethodsParameters.get(attribute.name);
                            }
                        }
                        // Check the constructor's instructions, if the desired field is defined within the constructor, this will obtain the
                        // type
                        if (instruction.label.equals("CALL") && instruction.code.contains("<") && !instruction.code.contains("<>")) {
                            String fullInstruction = instruction.code;
                            int startIndex = fullInstruction.indexOf("<");
                            int endIndex = fullInstruction.indexOf(">");

                            String type = fullInstruction.substring(startIndex, endIndex + 1);
                            attribute.type += type;
                        }
                        if (!originalType.equals(attribute.type)) break;
                    }
                } else {
                    // Check all other methods apart from the constructor to determine if the desired field is used somewhere.
                    if (originalType.equals(attribute.type)) {
                        for (Method.Instruction instruction : currentMethod.instructions) {
                            String stringToFind = "this." + attribute.name;
                            if (instruction.code.contains(stringToFind) && instruction.label.equals("CALL")) {
                                if (instruction.code.contains("put") || instruction.code.contains("add")) {
                                    int indexToFind = instruction.code.indexOf("(");
                                    String parameter = instruction.code.substring(indexToFind).replace("(", "").replace(")", "");
                                    HashMap<String, String> currentMethodsParameters = currentMethod.parameters;
                                    if (currentMethodsParameters.containsKey(parameter)) {
                                        if (!parameter.contains("<")) {
                                            attribute.type += "<" + currentMethodsParameters.get(parameter) + ">";
                                        } else attribute.type += currentMethodsParameters.get(parameter);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void getMissingInfo(CodePropertyGraph cpg) {
        // Get all the fields that are a part of Collection (i.e. contain "< >" in their declaration)
        HashMap<CPGClass, Attribute> missingFieldMap = new HashMap<>();
        for (CPGClass cpgClass : cpg.getClasses()) {
            for (Attribute attribute : cpgClass.getAttributes()) {
                if (attribute.packageName.equals("java.util") && !attribute.type.contains("<")) {
                    this.getMissingAttributeType(cpgClass, attribute);
                }
            }
        }
    }

    /**
     * @param destination A file location for source code
     * @return cpg A code property graph of the source code containing all classes and their fields and methods
     */
    public CodePropertyGraph buildCPG(String destination) {
        Gson gson = new Gson();
        CodePropertyGraph cpg = new CodePropertyGraph();
        HashMap<Method, String> calls = new HashMap<>();
        try {
            Reader reader = Files.newBufferedReader(Paths.get("src/main/python/joernFiles/sourceCode.json"));
            // convert JSON file to map
            Map<?, ?> map = gson.fromJson(reader, Map.class);
            Object[] entryValues;
            entryValues = map.values().toArray();
            ArrayList classes = (ArrayList) entryValues[0];
            int classCount = -1;
            for (Object classMap : classes) {
                Map<?, ?> completeClassMap = (Map<?, ?>) classMap;
                if (completeClassMap != null) {
                    String name = (String) completeClassMap.get("name");
                    String type = (String) completeClassMap.get("type");
                    String filePath = (String) completeClassMap.get("filePath");
                    String packageName = (String) completeClassMap.get("packageName");
                    cpg.addClass(new CPGClass(name, filePath, packageName, type));
                    classCount++;
                    ArrayList methods = parseSourceCodeMethods(cpg.getClasses().get(classCount), (ArrayList) completeClassMap.get("methods"), calls);
                    for (Object method : methods) {
                        Method thisMethod = (Method) method;
                        cpg.getClasses().get(classCount).addMethod(thisMethod);
                    }
                    ArrayList fields = parseSourceCodeFields((ArrayList) completeClassMap.get("fields"));
                    for (Object field : fields) {
                        cpg.getClasses().get(classCount).addAttribute((Attribute) field);
                    }
                }
            }
            // close reader
            reader.close();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        //cpg.addRelation(new CodePropertyGraph.Relation(cpg.getClasses().get(0), cpg.getClasses().get(1), ClassRelation.Type.ASSOCIATION));
        HashMap<String, Method> allMethods = new HashMap();
        for (CPGClass cpgClass : cpg.getClasses()) {
            for (Method method : cpgClass.getMethods()) {
                allMethods.put(method.name, method);
            }
        }
        updateMethodCalls(allMethods, cpg, calls);
        getMissingInfo(cpg);

        return cpg;
    }

    private void updateMethodCalls(HashMap<String, Method> allMethods, CodePropertyGraph cpg, HashMap<Method, String> calls) {
        for (CPGClass cpgClass : cpg.getClasses()) {
            for (Method method : cpgClass.getMethods()) {
                for (String calledMethod : calls.values()) {
                    if (allMethods.containsKey(calledMethod)) {
                        method.addMethodCall(allMethods.get(calledMethod));
                    }
                }
            }
        }
    }

    private ArrayList<Method> parseSourceCodeMethods(CPGClass parentClass, ArrayList methods, HashMap<Method, String> calls) {
        ArrayList<Method> parsedMethods = new ArrayList<>();
        Pair<Method, String> methodToCalledMethod;
        String calledMethod = "";
        HashMap<String, String> parameters = new HashMap<>();
        for (Object method : methods) {
            Map<?, ?> completeMethodMap = (Map<?, ?>) method;
            String methodName = (String) completeMethodMap.get("name");
            ArrayList methodModifiers = (ArrayList) completeMethodMap.get("modifiers");
            String methodBody = (String) completeMethodMap.get("methodBody");
            String returnType = (String) completeMethodMap.get("returnType");
            ArrayList methodParameters = (ArrayList) completeMethodMap.get("parameters");
            ArrayList methodInstructions = (ArrayList) completeMethodMap.get("instructions");

            // Add all the instructions of a method
            Method.Instruction[] methodInstruct = new Method.Instruction[methodInstructions.size()];
            for (int i = 0; i < methodInstructions.size(); i++) {
                Map<?, ?> completeInstructionMap = (Map<?, ?>) methodInstructions.get(i);
                String label = (String) completeInstructionMap.get("_label");
                String code = (String) completeInstructionMap.get("code");
                String lineNumber = String.valueOf(completeInstructionMap.get("lineNumber"));
                calledMethod = (String) completeInstructionMap.get("methodCall");
                methodInstruct[i] = new Method.Instruction(label, code, lineNumber);
            }

            // Add all the parameters of a method
            for (Object parametersTree : methodParameters) {
                Map<?, ?> completeParametersMap = (Map<?, ?>) parametersTree;
                String name = (String) completeParametersMap.get("name");
                String type = (String) completeParametersMap.get("type");
                if (!(name.equals("") && type.equals(""))) {
                    parameters.put(name, type);
                }
            }

            // Add all the modifiers of a method
            Modifier[] modifiers = new Modifier[methodModifiers.size()];
            if (!methodModifiers.isEmpty()) {
                for (int i = 0; i < methodModifiers.size(); i++) {
                    for (Modifier m : Modifier.values()) {
                        if (methodModifiers.get(i).equals(m.modString)) {
                            modifiers[i] = m;
                        }
                    }
                }
            } else {
                modifiers = new Modifier[]{};
            }

            Method thisMethod = new Method(parentClass, methodName, methodBody, methodInstruct, modifiers, parameters, returnType);

            // Getting a Set of Key-value pairs
            Set<Map.Entry<String, String>> entrySet = parameters.entrySet();

            // Iterate through HashMap entries(Key-Value pairs)
            for (Object o : entrySet) {
                Map.Entry param = (Map.Entry) o;
                thisMethod.addToParameters((String) param.getKey(), (String) param.getValue());
            }
            if (!(calledMethod.equals(""))) {
                calls.put(thisMethod, calledMethod);
            }
            parsedMethods.add(thisMethod);
        }
        return parsedMethods;
    }

    private ArrayList<Attribute> parseSourceCodeFields(ArrayList fields) {
        ArrayList<Attribute> parsedFields = new ArrayList<>();
        for (Object field : fields) {
            Map<?, ?> completeFieldMap = (Map<?, ?>) field;
            String fieldName = (String) completeFieldMap.get("name");
            String fieldType = (String) completeFieldMap.get("type");
            String packageName = (String) completeFieldMap.get("packageName");
            ArrayList fieldModifiers = (ArrayList) completeFieldMap.get("modifiers");

            // Add all modifiers of a field
            Modifier[] modifiers = new Modifier[fieldModifiers.size()];
            if (!fieldModifiers.isEmpty()) {
                for (int i = 0; i < fieldModifiers.size(); i++) {
                    for (Modifier m : Modifier.values()) {
                        if (fieldModifiers.get(i).equals(m.modString)) {
                            modifiers[i] = m;
                        }
                    }
                }
            } else {
                modifiers = new Modifier[]{};
            }
            parsedFields.add(new Attribute(fieldName, packageName, fieldType, modifiers));
        }
        return parsedFields;
    }

    private class JavaAttribute extends Attribute {
        JavaAttribute(String name, String packageName, String type, Modifier[] m) {
            super(name, packageName, type, m);
        }
    }

    private class JavaMethod extends Method {
        JavaMethod(CPGClass parentClass, String name, String methodBody, Instruction[] instructions, Modifier[] modifiers, HashMap<String, String> parameters, String returnType) {
            super(parentClass, name, methodBody, instructions, modifiers, parameters, returnType);
        }
    }
}