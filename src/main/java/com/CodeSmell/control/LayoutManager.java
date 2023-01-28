package com.CodeSmell.control;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.Arrays;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;


import com.CodeSmell.model.UMLClass;
import com.CodeSmell.model.Position;
import com.CodeSmell.model.RenderEvent;
import com.CodeSmell.model.Shape;
import com.CodeSmell.model.Pair;
import com.CodeSmell.model.ClassRelation;
import com.CodeSmell.parser.JoernServer.ReaderThread;

public class LayoutManager  {


	public static void determineLayout(ArrayList<UMLClass> classes,
			ArrayList<ClassRelation> relations) throws IOException {
		
		String graphVizIn = compileGraphVizInvokeCommand(classes, relations);
		//File inFile = createTempFile("graphViz-gen-codesmell", "-tmp");


		Process graphVizProcess = new ProcessBuilder(
			"dot").start();
		
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
		} catch (InterruptedException e) {
		}
		String line;
		while ((line = graphVizReader.readLine()) != null) {
			System.out.println(line);
		}
		System.out.println("===========");
		
	}

	private static String compileGraphVizInvokeCommand(ArrayList<UMLClass> classes,
			ArrayList<ClassRelation> relations) {
		StringBuilder graphVizIn = new StringBuilder("digraph G {\n");
		appendClassParameters(graphVizIn, classes);
		appendPathParameters(graphVizIn, relations);
		graphVizIn.append("}");
		return graphVizIn.toString();
	}

	private static void appendClassParameters(StringBuilder graphVizIn,
			ArrayList<UMLClass> classes) {
		for (UMLClass c : classes) {
			graphVizIn.append(String.format(
				"\"%s\" [width=%f, height=%f, shape=\"rectangle\"]\n", 
				c.name, c.getWidth() / 50, c.getHeight() / 50));
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

	private class Line extends Shape {
		/* Represents a line (i.e, one connecting
		two classes) */

		public final double x1, x2, y1, y2;
		
		Line(Position p1, Position p2) {
			super(new Position[]{p1, p2}, "#4C94A2");
			this.x1 = p1.x;
			this.x2 = p2.x;
			this.y1 = p1.y;
			this.y2 = p2.y;	
		}

		Line(double x1, double y1, double x2, double y2) {
			super(new Position[] {
					new Position(x1, y1), new Position(x2, y2)
				}, "orange");
			this.x1 = x1;
			this.x2 = x2;;
			this.y1 = y1;
			this.y2 = y2;	
		}

		public String toString() {
			return String.format("(%f, %f) -> (%f, %f)", 
					x1, y1, x2, y2);
		}

		public double magnitude() {
			return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
		}
	}

	private class Dot extends Shape {
		/* A dot to be rendered for debugging purposes */

		Dot(Position p, String colour) {
			super(new Position[] { p }, colour);
		}
	}

	private Line[] classBoxLines(UMLClass c) {
		Line[] lines = new Line[4];
		Position p1 = c.getPosition();
		Position corner2 = new Position(p1.x + c.getWidth(), p1.y);
		Position corner3 = new Position(p1.x + c.getWidth(), p1.y + c.getHeight());
		Position corner4 = new Position(p1.x, p1.y + c.getHeight());
		lines[0] = new Line(p1, corner2);
		lines[1] = new Line(corner2, corner3);
		lines[2] = new Line(corner3, corner4);
		lines[3] = new Line(corner4, p1);
		return lines;
	}
}
