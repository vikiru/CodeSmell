package com.CodeSmell;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.Arrays;

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

	private boolean testCollision(Line line, UMLClass[] exclude) {
		/* returns true if the given line does not
		 hit the bounding box of any class not in exclude */

		for (UMLClass c : classes) {
			if (Arrays.asList(exclude).contains(c)) {
				continue;
			}
			if (intersection(line, classBoxLines(c)) != null) {
				return false;
			}
		}
		return true;
	}

	private Position intersection(Line l1, Line[] with) {
		/* gets the Position of where l1 interesects with a line in with */

		for (Line l2 : with) {
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

	private enum Direction {
		TOP,
		BOTTOM,
		LEFT, 
		RIGHT
	}

	private Position classMiddle(UMLClass c) {
		/* Gets the position of the middle of a class bounding box */

		Position p = c.getPosition(); // topleft corner of c
		return new Position(p.x + c.getWidth() / 2, p.y + c.getHeight() / 2);
	}

	private Position classMiddle(UMLClass c, Direction d) {
		/* Gets the position of the middle of the given edge (direction)
		 of a class bounding box */

		Position p = c.getPosition(); // topleft corner of c
		if (d == Direction.TOP) {
			return new Position(p.x + c.getWidth() / 2, p.y);
		} else if (d == Direction.BOTTOM) {
			return new Position(p.x + c.getWidth() / 2, p.y + c.getHeight());
		} else if (d == Direction.LEFT) {
			return new Position(p.x, p.y + c.getHeight() / 2);
		} else if (d == Direction.RIGHT) {
			return new Position(p.x + c.getWidth(), p.y + c.getHeight() / 2);
		} else {
			throw new RuntimeException("Bad direction");
		}
	}

	private Pair<Position, Position> closetNodes(UMLClass c1, UMLClass c2) {
		/* 
		returns the two closest points relations that sit on 
		each classes box boundry to connect the two clases 
		*/

		// get mid point of each class
		Position mid1 = classMiddle(c1);
		Position mid2 = classMiddle(c2);

		// return the position of where a line connecting the midpoints
		// intersects with the class box boundaries
		Line line = new Line(mid1, mid2);
		Position p1, p2;
		p1 = intersection(line, classBoxLines(c1));
		p2 = intersection(line, classBoxLines(c2));
		if (p1 == null || p2 == null) {
			throw new RuntimeException("No intersection");
		}
		return new Pair<Position, Position>(p1, p2);
	} 

	private Position indirectConnection(UMLClass c1, UMLClass c2) {
		/* gets the  position of a bend where the perpendicular lines
		coming from the midpoints of c1 and c2 intersect */

		// get top left corners of each class 
		Position mid1 = classMiddle(c1);
		Position mid2 = classMiddle(c2);

		// new Dot(mid1, "blue").render();
		// new Dot(mid2, "red").render();
		Line l1 = new Line(mid1.x, mid1.y, mid2.x, mid1.y);
		Line l2 = new Line(mid2.x, mid2.y, mid2.x, mid1.y);
		Position p = intersection(l1, new Line[] { l2 });
		//l2.render();
		if (p == null) {
			throw new RuntimeException("No intersection");
		}
		return p;
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
			boolean collision = !testCollision(
					new Line(test, terminal), new UMLClass[]{ r.source, r.target });

			if (collision) {
				System.out.printf("Indirect path from %s to %s\n", 
						r.source.name, r.target.name);
		
				Position bend, mid1, mid2;
				bend = indirectConnection(r.source, r.target);
				mid1 = classMiddle(r.source);
				mid2 = classMiddle(r.target);

				if (bend.y > mid1.y && bend.x < mid2.x) {
					mid1 = classMiddle(r.source, Direction.BOTTOM);
					mid2 = classMiddle(r.target, Direction.LEFT);
				} else if (bend.x < mid1.x && bend.y > mid2.y) {
					mid1 = classMiddle(r.source, Direction.LEFT);
					mid2 = classMiddle(r.target, Direction.BOTTOM);
				} else if (bend.x > mid2.x && bend.y < mid1.y) {
					mid1 = classMiddle(r.source, Direction.TOP);
					mid2 = classMiddle(r.target, Direction.RIGHT);
				} else if (bend.x > mid1.x && bend.y < mid2.y) {
					mid1 = classMiddle(r.source, Direction.RIGHT);
					mid2 = classMiddle(r.target, Direction.TOP);
				}

				path.add(mid1);
				path.add(bend);
				path.add(mid2);
				System.out.println(path);
				r.setPath(path);
			} else {
				path.add(test);
				System.out.printf("Direct path from %s to %s\n", 
						r.source.name, r.target.name);
				path.add(terminal);
				System.out.println(path);
				r.setPath(path);
			}
		}
	}
}