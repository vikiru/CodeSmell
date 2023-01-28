package com.CodeSmell.control;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Arrays;
import java.util.Iterator;

import com.CodeSmell.model.UMLClass;
import com.CodeSmell.model.Position;
import com.CodeSmell.model.RenderEvent;
import com.CodeSmell.model.Shape;
import com.CodeSmell.model.Pair;
import com.CodeSmell.model.ClassRelation;
import com.CodeSmell.parser.JoernServer.ReaderThread;

public class LayoutManager  {

	// amount to scale coordinates returned by graphViz
	private static final double SCALING_FACTOR = 1;

	// node (class) seperation parameter propegated to graphViz
	private static final String NODE_SEP = "100";

	public static void setLayout(
			ArrayList<UMLClass> classes,
			ArrayList<ClassRelation> relations) 
		throws IOException {
		
		BufferedReader graphVizReader = callGraphViz(classes, relations);

		String line;
		while ((line = graphVizReader.readLine()) != null) {
			parseDotLine(classes, relations, line);
		}
	}

	private static final String NUMBERS_RE = "((((\\d+[.]\\d+)|(\\d+)) ){2,})";
	private static final Pattern RELATION_DOT_RE = Pattern.compile(
			"edge ([a-zA-Z]+) ([a-zA-Z]+) (\\d+) " + NUMBERS_RE);
	private static final Pattern CLASS_DOT_RE = Pattern.compile(
			"node ([a-zA-Z]+) ((((\\d+[.]\\d+)|(\\d+)) ){2})");

	private static void parseDotLine(
			ArrayList<UMLClass> classes, 
			ArrayList<ClassRelation> relations,
			String line) throws IOException {

		String numbers = "((((\\d+[.]\\d+)|(\\d+)) ){2,})";

		if (line.startsWith("graph") || line.startsWith("stop")) return;
		Matcher edgeMatch = RELATION_DOT_RE.matcher(line);
		Matcher classMatch = CLASS_DOT_RE.matcher(line);

		if (edgeMatch.find()) {
			String sourceClassName = edgeMatch.group(1);
			ArrayList<Position> path = parsePaths(edgeMatch);
			for (ClassRelation cr : relations) {
				if (cr.source.name.equals(sourceClassName)) {
					System.out.println(cr.type);
					System.out.printf("edge name: %s\n%s\n", 
						sourceClassName, line);
					for (Position p : path) {
						System.out.println(p);
					}
					cr.setPath(path);
					return;
				}
			}
			//System.out.println("EDGE MATCH " + path);
		} else if (classMatch.find()) {
			//System.out.println("CLASS MATCH " + line);
			String className = classMatch.group(1);
			String[] rest = classMatch.group(2).split(" ");
			double x = Double.parseDouble(rest[0]);
			double y = Double.parseDouble(rest[1]);
			System.out.printf("name: %s, x: %f, y: %f\n",
				className, x, y);
			System.out.println(line);
			for (UMLClass c : classes) {
				if (c.name.equals(className)) {
					// coordinates graphViz uses
					// are relative to the center, convert
					c.setPosition(x * SCALING_FACTOR - c.getWidth() / 2, 
						y * SCALING_FACTOR - c.getHeight() / 2);
					return;
				}
			}
		} else {
			throw new IOException(
				"unexpected line in graphViz output:\n" + line);
		}
	}

	private static ArrayList<Position> parsePaths(
			Matcher edgeMatch) throws IOException {
		int numPaths = Integer.parseInt(edgeMatch.group(3));
		ArrayList<Position> pathNodes = new ArrayList<Position>();

		String pathString = edgeMatch.group(4);
		Iterator<String> pathIter = Arrays.asList(
			pathString.split(" ")).iterator();
		String xCord, yCord;
		while (pathIter.hasNext()) {
			numPaths--;
			xCord = pathIter.next();
			//System.out.println(numPaths);
			//System.out.println(xCord);
			if (numPaths < 0) {
				throw new IOException(
					"dot (graphViz) gave a path " +
					"with an incorrect size:\n" + pathString);
			}
			yCord = pathIter.next();
			pathNodes.add(new Position(Double.parseDouble(xCord),
				Double.parseDouble(yCord)));
		}
		return pathNodes;
	}

	private static BufferedReader callGraphViz(ArrayList<UMLClass> classes,
			ArrayList<ClassRelation> relations) throws IOException {
		String graphVizIn = compileGraphVizInvokeCommand(classes, relations);
		//File inFile = createTempFile("graphViz-gen-codesmell", "-tmp");
		System.out.println("Calling graphViz with command\n" + graphVizIn);

		Process graphVizProcess = new ProcessBuilder(
			// can also do 
			//"dot", "-y" instead of "fdp"
			//fdp, dot, sfdp, neato are all provided by
			// graphViz. Some use flipped y coordinates, 
			// some don't. -y controls flipped y.
			"fdp", "-Tplain").start();
		
		// std in buffer
		OutputStream graphVizOut = graphVizProcess.getOutputStream();

		// std error buffer 
		BufferedReader graphVizErrorReader = new BufferedReader(
				new InputStreamReader(graphVizProcess.getErrorStream()));
		
		// std out buffer 
		BufferedReader graphVizReader = new BufferedReader(
				new InputStreamReader(graphVizProcess.getInputStream()));

		// start the error reader
		new ReaderThread(graphVizErrorReader).start();

		//System.out.println(graphVizIn);
		byte[] inFileBuffer = graphVizIn.getBytes("utf-8");
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos.write(inFileBuffer, 0, inFileBuffer.length);
		baos.writeTo(graphVizOut);
		graphVizOut.flush();
		graphVizOut.close();
		try {
			graphVizProcess.waitFor();
		} catch (InterruptedException e) {}
		return graphVizReader;
	}

	private static String compileGraphVizInvokeCommand(ArrayList<UMLClass> classes,
			ArrayList<ClassRelation> relations) {
		StringBuilder graphVizIn = new StringBuilder(
			"digraph G {\nsplines=polyline\n" +
			"nodesep=" + NODE_SEP + "\n");
		appendClassParameters(graphVizIn, classes);
		appendPathParameters(graphVizIn, relations);
		graphVizIn.append("}");
		return graphVizIn.toString();
	}

	private static void appendClassParameters(StringBuilder graphVizIn,
			ArrayList<UMLClass> classes) {
		for (UMLClass c : classes) {
			graphVizIn.append(String.format(
				"\"%s\" [width=%f, height=%f, " +
				"shape=\"rectangle\", fixedsize=true]\n", 
				c.name, c.getWidth() * SCALING_FACTOR, 
				c.getHeight() * SCALING_FACTOR));
		}
	}

	private static void appendPathParameters(StringBuilder graphVizIn,
			ArrayList<ClassRelation> relations) {
		for (ClassRelation cr : relations) {
			graphVizIn.append(
				String.format("\"%s\" -> \"%s\"\n", 
				cr.source.name, cr.target.name));
		}
	}
}
