package com.CodeSmell;

import java.io.File;
import java.io.Reader;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;

import com.CodeSmell.CPGClass;
import com.CodeSmell.CPGClass.Method;
import com.CodeSmell.CPGClass.Attribute;
import com.CodeSmell.CPGClass.Modifier;
import com.CodeSmell.CodePropertyGraph;
import com.google.gson.Gson;

public class Parser {

	private class JavaAttribute extends Attribute {
		JavaAttribute(String name, Modifier[] m) {
			super(name, m);
		}
	}

	private class JavaMethod extends Method {
		JavaMethod(String name, String[] instructions, Modifier[] modifiers) {
			super(name, instructions, modifiers);
		}
	}

	public static void main(String[] args) {
		Parser p = new Parser();
		p.buildCPG("");
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
				for (Map.Entry<?, ?> entry : completeClassMap.entrySet()) {

					if(entry.getKey().equals("name"))
					{
						cpg.addClass(new CPGClass((String)entry.getValue()));
						classCount++;
					}
					if(entry.getKey().equals("methods"))
					{
						ArrayList methods = parseSourceCodeMethods((ArrayList) entry.getValue());
						for(Object method: methods)
						{
							cpg.getClasses().get(classCount).addMethod((Method)method);
						}
					}
					if(entry.getKey().equals("fields"))
					{
						ArrayList fields = parseSourceCodeFields((ArrayList) entry.getValue());
						for(Object field: fields)
						{
							cpg.getClasses().get(classCount).addAttribute((Attribute) field);
						}
					}

				}
			}
			// close reader
			reader.close();

		} catch (Exception ex) {
			ex.printStackTrace();
		}
		cpg.addRelation(new CodePropertyGraph.Relation(cpg.getClasses().get(0),cpg.getClasses().get(1), ClassRelation.Type.ASSOCIATION));
		return cpg;
	}

	private ArrayList<Method> parseSourceCodeMethods(ArrayList methods)
	{
		ArrayList<Method> parsedMethods = new ArrayList<Method>();

		for(Object method: methods) {
			method = (Map<?, ?>) method;
			String methodName = "";
			ArrayList<String> methodInstructions = new ArrayList<>();
			Modifier[] methodModifiers = new Modifier[1];
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
								}
							}
						}
						methodInstructions.add(code);
						break;
					case "modifiers":
						ArrayList methodModifier = (ArrayList) methodCharacteristic.getValue();
						if (!methodModifier.isEmpty()) {
								switch ((String) methodModifier.get(0)) {
									case "private":
										methodModifiers[0] = Modifier.PRIVATE;
										break;
									case "public":
										methodModifiers[0] = Modifier.PUBLIC;
										break;
									case "protected":
										methodModifiers[0] = Modifier.PROTECTED;
										break;
								}
						}
				}
			}
			String[] methodInstruct = new String[methodInstructions.size()];
			parsedMethods.add(new Method(methodName, methodInstruct = methodInstructions.toArray(methodInstruct), methodModifiers));
		}

		return parsedMethods;
	}

	private ArrayList<Attribute> parseSourceCodeFields(ArrayList fields)
	{
		ArrayList<Attribute> parsedFields = new ArrayList<Attribute>();

		for(Object field: fields)
		{
			field = (Map<?, ?>)field;
			String fieldName = "";
			Modifier[] fieldModifiers = new Modifier[1];
			for(Map.Entry<?, ?> fieldCharacteristic:((Map<?, ?>) field).entrySet()) {
				switch ((String) fieldCharacteristic.getKey()) {
					case "name":
						fieldName = (String) fieldCharacteristic.getValue();
						break;
					case "modifiers":
						ArrayList fieldModifier = (ArrayList) fieldCharacteristic.getValue();
						if (!fieldModifier.isEmpty()){
							switch ((String) fieldModifier.get(0)) {
								case "private":
									fieldModifiers[0] = Modifier.PRIVATE;
									break;
								case "public":
									fieldModifiers[0] = Modifier.PUBLIC;
									break;
								case "protected":
									fieldModifiers[0] = Modifier.PROTECTED;
									break;
							}
					}
				}
			}
			parsedFields.add(new Attribute(fieldName,fieldModifiers));
		}
		return parsedFields;
	}

}
