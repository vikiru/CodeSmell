package com.CodeSmell;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;

import com.CodeSmell.UMLClass;
import com.CodeSmell.Position;
import com.CodeSmell.RenderEvent;
import com.CodeSmell.Shape;

class LayoutManager  {

	public LayoutManager() {}

	private ArrayList<UMLClass> classes;

	public void positionClasses(ArrayList<UMLClass> classes) {
		/**
		 * given a list of classes with a defined size attribute,
		 * set their position with the classes setPosition() method
		 */

		this.classes = classes;
		sortClasses();

		double x = 0;
		double y = 0;

		for (int i=0; i < classes.size(); i++) {
			// set the classes's ith class' 
			// x position to be after i-1th's with
			// some padding. 
			if (i >= 1) {
				UMLClass lastClass = classes.get(i - 1);
				Double lastX = lastClass.getPosition().x;
				Double lastWidth = classes.get(i - 1).getWidth();
				Double padding = lastWidth / 2;
				x = lastX + lastWidth + padding;
				Double lastY = lastClass.getPosition().y;
				y = lastY + classes.get(i - 1).getHeight();

			}
			classes.get(i).setPosition(x, y);
		}
	}

	private class Line extends Shape {
		public final double x1, x2, y1, y2;
		
		Line(Position p1, Position p2) {
			super(new Position[]{p1, p2}, "#4C94A2");
			this.x1 = p1.x;
			this.x2 = p2.x;
			this.y1 = p1.y;
			this.y2 = p2.y;	
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
		Dot(Position p, String colour) {
			super(new Position[]{p}, colour);
		}
	}

	private ArrayList<Line> classBoxLines(UMLClass c) {
		ArrayList<Line> lines = new ArrayList<>();
		Position p1 = c.getPosition();
		Position corner2 = new Position(p1.x + c.getWidth(), p1.y);
		Position corner3 = new Position(p1.x + c.getWidth(), p1.y + c.getHeight());
		Position corner4 = new Position(p1.x, p1.y + c.getHeight());
		lines.add(new Line(p1, corner2));
		lines.add(new Line(corner2, corner3));
		lines.add(new Line(corner3, corner4));
		lines.add(new Line(corner4, p1));
		return lines;
	}

	private boolean testCollision(Line line) {
		/* returns true if the given line does not
		 hit the bounding box of any class */

		for (UMLClass c : classes) {
			if (intersection(line, classBoxLines(c)) != null) {
				return false;
			}
		}
		return true;
	}


	private Position intersection(Line l1, ArrayList<Line> with) {
		/* gets the Position of where l1 interesects with a line in with */

		for (Line l2 : with) {
			double m1 = l1.magnitude();
			double m2 = l2.magnitude();
			double x1 = l1.x1;
			double x2 = l1.x2;
			double x3 = l2.x1;
			double x4 = l2.x2;
			double y1 = l1.y1;
			double y2 = l1.y2;
			double y3 = l2.y1;
			double y4 = l2.y2;
			double a = ((x4-x3)*(y1-y3) - (y4-y3)*(x1-x3)) / 
					((y4-y3)*(x2-x1)  - (x4-x3)*(y2-y1));
			double b = ((x2-x1)*(y1-y3) - (y2-y1)*(x1-x3)) / 
					((y4-y3)*(x2-x1)  - (x4-x3)*(y2-y1));
			if (a >= 0 && a <= 1 &&  b >= 0 && b <= 1) {
				return new Position(x1 + a*(x2-x1), y1 + a*(y2-y1));
			}
		}
		return null;
	}

	private Pair<Position, Position> closetNodes(UMLClass c1, UMLClass c2) {
		/* 
		returns the two closest points relations that sit on 
		each classes box boundry to connect the two clases 
		*/
		
		// get top left corners of each class
		Position p1 = c1.getPosition();
		Position p2 = c2.getPosition();

		// get mid point of each class
		Position mid1 = new Position(p1.x + c1.getWidth() / 2, p1.y + c1.getHeight() / 2);
		Position mid2 = new Position(p2.x + c2.getWidth() / 2, p2.y + c2.getHeight() / 2);

		// return the position of where a line connecting the midpoints
		// intersects with the class box boundaries
		Line line = new Line(mid1, mid2);
		line.render();
		p1 = intersection(line, classBoxLines(c1));
		p2 = intersection(line, classBoxLines(c2));
		new Dot(p1, "#98BE75").render();
		new Dot(p2, "#98BE75").render();
		if (p1 == null || p2 == null) {
			throw new RuntimeException("No intersection");
		}
		return new Pair<Position, Position>(p1, p2);
	} 

	private Position indirectConnection(UMLClass c1, UMLClass c2) {
		// get top left corners of each class
		Position p1 = c1.getPosition();
		Position p2 = c2.getPosition();

		// get mid point of each class
		Position mid1 = new Position(p1.x + c1.getWidth() / 2, p1.y + c1.getHeight() / 2);
		Position mid2 = new Position(p2.x + c2.getWidth() / 2, p2.y + c2.getHeight() / 2);

		// need to calculate the right most and bottom most coordinates.
		// for now just use constants
		double viewRight = 10000;
		double viewBottom = 10000;
		Line line1 = new Line(new Position(0, mid2.y), new Position(viewRight, mid2.y));
		Line line2 = new Line(new Position(mid1.x, 0), new Position(mid1.x, viewBottom));
		ArrayList<Line> lines = new ArrayList<Line>();
		lines.add(line2);
		return intersection(line1, lines);
	}

	private void sortClasses() {
		// sorts the classes such that the ones which are furthest left are first

		Comparator<UMLClass> classHorzComp = new Comparator<UMLClass>() {
	        public int compare(UMLClass c1, UMLClass c2) {
	        	Double d = (c1.getPosition().x - c2.getPosition().x);
	            return d.intValue();
	        }
	    };
	    
	    Collections.sort(classes, classHorzComp);
	}

	public void setRelationPaths(ArrayList<ClassRelation> relations) {
		/**
		 * given a list of classes which have been rendered and 
		 * positioned, set the path for their index of their connections
		 * attribute.
		 *  
		 */

		for (ClassRelation r : relations) {
			ArrayList<Position> path = new ArrayList<Position>();
		
			Pair<Position, Position> p = closetNodes(r.source, r.target);
			Position terminal = p.second;
			Position test = p.first; 
			boolean collision = !testCollision(new Line(test, terminal));
			path.add(test);

			if (collision) {
				System.out.printf("Indirect path from %s to %s\n", 
						r.source.name, r.target.name);
				Position bend = indirectConnection(r.source, r.target);
				path.add(bend);
				path.add(terminal);
				System.out.println(path);
				r.setPath(path);
			} else {
				System.out.printf("Direct path from %s to %s\n", 
						r.source.name, r.target.name);
				path.add(terminal);
				System.out.println(path);
				r.setPath(path);
			}
		}
	}
}