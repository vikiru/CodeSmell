package com.CodeSmell;

import com.CodeSmell.CPGClass.Attribute;
import com.CodeSmell.CPGClass.Method;
import com.CodeSmell.CPGClass.Modifier;
import com.google.gson.Gson;
import javafx.util.Pair;

import java.io.File;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Parser {

    public static void main(String[] args) {
        Parser p = new Parser();
        CodePropertyGraph g = p.buildCPG("");
    }

    /**
     * @param destination A file location for source code
     * @return
     */
    public CodePropertyGraph buildCPG(String destination) {
        Gson gson = new Gson();
        File sourceCode = new File("src/main/python/joernFiles/sourceCode.json");
        CodePropertyGraph cpg = new CodePropertyGraph();
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
                String name = (String) ((Map<?, ?>) classMap).get("name");
                String type = (String) ((Map<?, ?>) classMap).get("type");
                cpg.addClass(new CPGClass(name, type));
                classCount++;
                ArrayList methods = parseSourceCodeMethods((ArrayList) ((Map<?, ?>) classMap).get("methods"));
                for (Object method : methods) {
                    Method thisMethod = (Method) method;
                    cpg.getClasses().get(classCount).addMethod(thisMethod);

                }
                ArrayList fields = parseSourceCodeFields((ArrayList) ((Map<?, ?>) classMap).get("fields"));
                for (Object field : fields) {
                    cpg.getClasses().get(classCount).addAttribute((Attribute) field);
                }
            }
            // close reader
            reader.close();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        //cpg.addRelation(new CodePropertyGraph.Relation(cpg.getClasses().get(0), cpg.getClasses().get(1), ClassRelation.Type.ASSOCIATION));
        HashMap<String, Method> allMethods = new HashMap();
        for (CPGClass cpgClass : cpg.getClasses())
        {
            for(Method method : cpgClass.getMethods())
            {
                allMethods.put(method.name,method);
            }
        }
        updateMethodCalls(allMethods, cpg);
        return cpg;
    }


    private void updateMethodCalls(HashMap<String, Method> allMethods, CodePropertyGraph cpg)
    {
        for (CPGClass cpgClass : cpg.getClasses())
        {
            for(Method method : cpgClass.getMethods())
            {
              for(String calledMethod : method.getCalls().values())
              {
                  if(allMethods.containsKey(calledMethod))
                  {
                      method.addMethodCall(allMethods.get(calledMethod));
                  }
              }
            }
        }
    }

    private ArrayList<Method> parseSourceCodeMethods(ArrayList methods) {
        ArrayList<Method> parsedMethods = new ArrayList<Method>();
        Pair<Method,String> methodToCalledMethod;
        String calledMethod = "";
        HashMap<String, String> parameters = new HashMap<>();
        for (Object method : methods) {
            method = (Map<?, ?>) method;
            String methodName = "";
            ArrayList<String> methodInstructions = new ArrayList<>();
            ArrayList<Modifier> methodModifiers = new ArrayList<>();
            for (Map.Entry<?, ?> methodCharacteristic : ((Map<?, ?>) method).entrySet()) {
                switch ((String) methodCharacteristic.getKey()) {
                    case "name":
                        methodName = (String) methodCharacteristic.getValue();
                        break;
                    case "instructions":
                        String label = "";
                        String code = "";
                        ArrayList instructions = (ArrayList) methodCharacteristic.getValue();
                        for (Object instructionsTree : instructions) {
                            for (Map.Entry<?, ?> instruction : ((Map<?, ?>) instructionsTree).entrySet()) {
                                if (instruction.getKey().equals("code")) {
                                    code = (String) instruction.getValue();
                                    methodInstructions.add(code);
                                }
                                else if(instruction.getKey().equals("_label") && instruction.getValue().equals("CALL")) {
                                    if (!((Map<?, ?>) instructionsTree).get("methodCall").equals("")) {
                                        calledMethod = (String) ((Map<?, ?>) instructionsTree).get("methodCall");
                                    }
                                }
                            }
                        }
                        break;
                    case "parameters":
                        ArrayList allParameters = (ArrayList) methodCharacteristic.getValue();
                        for(Object paramPair : allParameters)
                        {
                            String name = "";
                            String type = "";
                            for (Map.Entry<?, ?> param : ((Map<?, ?>) paramPair).entrySet())
                            {
                                if(param.getKey().equals("name"))
                                {
                                    name = (String)param.getValue();
                                }
                                else if(param.getKey().equals("type"))
                                {
                                    type = (String)param.getValue();
                                }
                                //Add to pair param and type, then add to methods
                            }
                            if(!(name.equals("") && type.equals("")))
                            {
                                parameters.put(type,name);
                            }
                        }
                    break;
                    case "modifiers":
                        ArrayList methodModifier = (ArrayList) methodCharacteristic.getValue();
                        if (!methodModifier.isEmpty()) {
                            for (int i = 0; i < methodModifier.size(); i++) {
                                switch ((String) methodModifier.get(i)) {
                                    case "private":
                                        methodModifiers.add(Modifier.PRIVATE);
                                        break;
                                    case "public":
                                        methodModifiers.add(Modifier.PUBLIC);
                                        break;
                                    case "protected":
                                        methodModifiers.add(Modifier.PROTECTED);
                                        break;
                                    case "static":
                                        methodModifiers.add(Modifier.STATIC);
                                        break;
                                    case "final":
                                        methodModifiers.add(Modifier.FINAL);
                                        break;
                                    case "synchronized":
                                        methodModifiers.add(Modifier.SYNCHRONIZED);
                                        break;
                                    case "abstract":
                                        methodModifiers.add(Modifier.ABSTRACT);
                                        break;
                                    case "native":
                                        methodModifiers.add(Modifier.NATIVE);
                                        break;

                                }
                            }
                        }
                }
            }
            String[] methodInstruct = new String[methodInstructions.size()];
            Modifier modifiers[] = new Modifier[methodModifiers.size()];
            modifiers = methodModifiers.toArray(modifiers);
            if (modifiers.length == 0) {
                modifiers = new Modifier[]{};
            }

            Method thisMethod =  new Method(methodName, methodInstruct = methodInstructions.toArray(methodInstruct), modifiers, parameters);
            // Getting a Set of Key-value pairs
            Set entrySet = parameters.entrySet();

            // Obtaining an iterator for the entry set
            Iterator paramIterator = entrySet.iterator();

            // Iterate through HashMap entries(Key-Value pairs)
            while(paramIterator.hasNext()){
                Map.Entry param = (Map.Entry)paramIterator.next();
                thisMethod.addToParameters((String)param.getKey(),(String)param.getValue());
            }
            thisMethod.addCall(thisMethod,calledMethod);
            parsedMethods.add(thisMethod);
        }

        return parsedMethods;
    }

    private ArrayList<Attribute> parseSourceCodeFields(ArrayList fields) {
        ArrayList<Attribute> parsedFields = new ArrayList<Attribute>();

        for (Object field : fields) {
            field = (Map<?, ?>) field;
            String fieldName = "";
            String fieldType = "";
            ArrayList<Modifier> fieldModifiers = new ArrayList<>();
            for (Map.Entry<?, ?> fieldCharacteristic : ((Map<?, ?>) field).entrySet()) {
                switch ((String) fieldCharacteristic.getKey()) {
                    case "name":
                        fieldName = (String) fieldCharacteristic.getValue();
                        break;
                    case "type":
                        fieldType = (String) fieldCharacteristic.getValue();
                        break;
                    case "modifiers":
                        ArrayList fieldModifier = (ArrayList) fieldCharacteristic.getValue();
                        if (!fieldModifier.isEmpty()) {
                            for (int i = 0; i < fieldModifier.size(); i++) {
                                switch ((String) fieldModifier.get(i)) {
                                    case "private":
                                        fieldModifiers.add(Modifier.PRIVATE);
                                        break;
                                    case "public":
                                        fieldModifiers.add(Modifier.PUBLIC);
                                        break;
                                    case "protected":
                                        fieldModifiers.add(Modifier.PROTECTED);
                                        break;
                                    case "static":
                                        fieldModifiers.add(Modifier.STATIC);
                                        break;
                                    case "final":
                                        fieldModifiers.add(Modifier.FINAL);
                                        break;
                                    case "synchronized":
                                        fieldModifiers.add(Modifier.SYNCHRONIZED);
                                        break;
                                    case "abstract":
                                        fieldModifiers.add(Modifier.ABSTRACT);
                                        break;
                                    case "native":
                                        fieldModifiers.add(Modifier.NATIVE);
                                        break;
                                }
                            }
                        }
                }
            }

            Modifier modifiers[] = new Modifier[fieldModifiers.size()];
            modifiers = fieldModifiers.toArray(modifiers);
            if (modifiers.length == 0) {
                modifiers = new Modifier[]{};
            }
            parsedFields.add(new Attribute(fieldName, fieldType, modifiers));
        }
        return parsedFields;
    }

    private class JavaAttribute extends Attribute {
        JavaAttribute(String name, String type, Modifier[] m) {
            super(name, type, m);
        }
    }

    private class JavaMethod extends Method {
        JavaMethod(String name, String[] instructions, Modifier[] modifiers, HashMap<String, String> parameters) {
            super(name, instructions, modifiers, parameters);
        }
    }
}