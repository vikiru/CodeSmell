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
						ArrayList methods = (ArrayList) entry.getValue();
						for(Object method: methods)
						{
							method = (Map<?, ?>)method;
							String methodName = "";
							ArrayList<String> methodInstructions = new ArrayList<>();
							Modifier[] methodModifiers = new Modifier[1];
							for(Map.Entry<?, ?> methodCharacteristic:((Map<?, ?>) method).entrySet()) {
								switch ((String) methodCharacteristic.getKey()) {
									case "name":
										methodName = (String) methodCharacteristic.getValue();
										break;
									case "instructions":
										String label = "";
										String code = "";
										ArrayList instructions = (ArrayList) methodCharacteristic.getValue();
										for(Object instructionsTree:instructions)
										{
											for(Map.Entry<?, ?> instruction:((Map<?, ?>) instructionsTree).entrySet())
											{
												if(instruction.getKey().equals("_label"))
												{
													label = (String)instruction.getValue();
												}
												if(instruction.getKey().equals("code"))
												{
													code = (String)instruction.getValue();
												}
											}
										}
										methodInstructions.add(label+":"+code);
										break;
									case "modifiers":
										ArrayList  methodModifier = (ArrayList) methodCharacteristic.getValue();
										switch ((String)methodModifier.get(0))
										{
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
							String[] methodInstruct = new String[methodInstructions.size()];
							cpg.getClasses().get(classCount).addMethod(new Method(methodName,methodInstruct = methodInstructions.toArray(methodInstruct), methodModifiers));
						}
					}
					if(entry.getKey().equals("fields"))
					{
						ArrayList fields = (ArrayList) entry.getValue();
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
										switch ((String)fieldModifier.get(0))
										{
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
							cpg.getClasses().get(classCount).addAttribute(new Attribute(fieldName,fieldModifiers));
						}

					}

				}
			}

			// close reader
			reader.close();

		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return cpg;
	}

}
