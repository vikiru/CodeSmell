package com.CodeSmell.control;

import java.util.ArrayList;
import java.lang.reflect.Array;

import com.CodeSmell.Position;
import com.CodeSmell.model.ClassRelation;
import com.CodeSmell.model.UMLClass;

public class LayoutManager  {

	private enum Direction {
		RIGHT,
		LEFT;
		public Direction opposite(){
			switch(this){
				case RIGHT: return Direction.LEFT;
				case LEFT: return Direction.RIGHT;
				default: return Direction.RIGHT;
			}
		}
	}

	private static double Y_PADDING = 30; //size of gap between classes vertically
	private static double RELATION_GRID_SIZE = 0.4; 
	private static double MIDDLE_SPACING_SIZE = 0.3; //how much of the gap in width can be filled up by relations
	private static double PADDING_DIVISOR = 2; //how much padding (gap in width between two columns), as a divisor

	private ArrayList<UMLClass>[] grid;
	private ArrayList<ClassRelation> relations;

	public LayoutManager(ArrayList<UMLClass> classes, ArrayList<ClassRelation> relations) {

		this.positionClasses(classes);
		this.setSpacing();
		
		this.relations = relations;
		this.setRelationPaths();
	}

	public void positionClasses(ArrayList<UMLClass> classes) {
		/**
		 * given a list of classes with a defined size attribute,
		 * set their position with the classes setPosition() method
		 */
		
		// Find the number of columns that this list of classes will turn into.
		int nColumns = (int) Math.ceil(Math.sqrt(classes.size()));

		//Generate the grid
		ArrayList<UMLClass>[] grid = new ArrayList[nColumns];
		for(int i = 0; i < nColumns; i++){
			grid[i] = new ArrayList<UMLClass>();
		}

		//keep track of which column we're on
		int curColumn = 0;
		///Populate the grid
		for(int i = 0; i < classes.size(); i++){
			if(curColumn == nColumns){
				curColumn = 0;
			}
			grid[curColumn].add(classes.get(i));
			curColumn++;
		}
		this.grid = grid;
	}


	/**
	* Spaces out the positions of the classes in this.grid evenly.
	*
	*/
	private void setSpacing(){
		//First, iterate over each column and set x
		double x = 0;
		double y;
		for(int col = 0; col < this.grid.length; col++){
			y = 0;
			if (col > 0){
				double lastX = this.grid[col-1].get(0).getPosition().x;
				double lastWidth = maxWidth(col-1);
				double padding = lastWidth / 2;
				x = lastX + lastWidth + padding;
			}
			//Then iterate through each column and set y
			for(int i = 0; i < this.grid[col].size(); i++){
				if(i > 0){
					y = y + this.grid[col].get(i-1).getHeight() + Y_PADDING;
				}
				this.grid[col].get(i).setPosition(x, y);
			}
		}
		
	}

	/**
	* Self-explanatory, gets the ceiling of the width of a list of classes.
	*
	*/
	private double maxWidth(int col){
		double max = 0;
		double width;
		for(int n = 0; n < this.grid[col].size(); n++){
			width = this.grid[col].get(n).getWidth();
			if(width > max){
				max = width;
			}
		}
		return max;
	}

	/**
	* 
	*/
	private void setRelationPaths(){
		for(ClassRelation r : this.relations){
			
			ArrayList<Position> path = new ArrayList<Position>();
			int curColumn = getColumn(r.source); 
			int destColumn = getColumn(r.target);
			Direction dir = getRelationDir(r);
			double padding = maxWidth(curColumn)/(PADDING_DIVISOR*2);
			

			Position start = getStartPoint(r, r.source, dir);
			path.add(start); //Path start
			
			if(getRelationDir(r) == Direction.RIGHT)
				path.add(new Position(getPaddedPoint(r, curColumn), start.y));
			else
				path.add(new Position(getPaddedPoint(r, curColumn-1), start.y));

			Position end;
			
			if(r.source.getPosition().x == r.target.getPosition().x){
				//special case: starts and ends in the same column
				end = getStartPoint(r, r.target, Direction.RIGHT);
				path.add(new Position(getPaddedPoint(r, curColumn), end.y));
				
			}else{
			//Add intermediate path waypoints..
			while(Math.abs(curColumn - destColumn) > 1){
				addPassthrough(r, path, curColumn, destColumn);
				if(dir == Direction.RIGHT){ 
					curColumn++;
				}else{ 
					curColumn--;
				}
			}
			
			end = getStartPoint(r, r.target, this.getRelationDir(r).opposite());
			if(getRelationDir(r) == Direction.RIGHT)
				path.add(new Position(getPaddedPoint(r, curColumn), end.y));
			else
				path.add(new Position(getPaddedPoint(r, curColumn-1), end.y));
			
			}
			path.add(end);
			r.setPath(path);
		}
	}

	private void addPassthrough(ClassRelation r, ArrayList<Position> p, int startCol, int targetCol){
		
		double[] gapPositions = new double[grid[targetCol].size()];
		for(int n = 0; n < grid[targetCol].size(); n++){
			gapPositions[n] = grid[targetCol].get(n).getPosition().y 
				+ grid[targetCol].get(n).getHeight() 
				+ Y_PADDING/2;
			
		}
		double startX = p.get(p.size()-1).x;
		double startY = p.get(p.size()-1).y;
		
		double targetY = gapPositions[0];
		double targetX;
		if(targetCol < startCol)
			targetX = getPaddedPoint(r, startCol - 2);
		else
			targetX = getPaddedPoint(r, startCol + 1);
		
		for(double n : gapPositions){
			if(Math.abs(n - startY) < Math.abs(targetY - startY))
				targetY = n;
		}
		p.add(new Position(startX, targetY));
		p.add(new Position(targetX, targetY));
		
	}
	
	/**
	* Get which direction the relation is going horizontally - to the right or no difference (true) or to the left (false).
	*/
	private Direction getRelationDir(ClassRelation r){
		if(r.source.getPosition().x <= r.target.getPosition().x){
			return Direction.RIGHT;
		}else{
			return Direction.LEFT;
		}
	}

	/**
	* Get which column the class is in.
	*/
	private int getColumn(UMLClass theClass){
		for(int n = 0; n < grid.length; n++){
			if(grid[n].get(0).getPosition().x == theClass.getPosition().x)
				return n;
		}
		return 0;
	}
	
	/**
	* Gets a point in the middle of the 'padding' gap area directly in front of
	* the start point.
	*/
	private double getPaddedPoint(ClassRelation relation, int theCol){
		
		//get number of ClassRelations that pass through the area
		int nPassthroughs = 0;
		int index = 0;
		
		double startX = grid[theCol].get(0).getPosition().x;
		
		for(ClassRelation r: relations){
			if((startX <= relation.source.getPosition().x) 
				&& (r.target.getPosition().x >= relation.target.getPosition().x)){
				nPassthroughs++;
			} else if ((startX >= relation.source.getPosition().x) 
				&& (r.target.getPosition().x <= relation.target.getPosition().x)){
				nPassthroughs++;
			}
			if(r == relation)
				index = relations.indexOf(r);
		}
		double colWidth;
		double distroStart;
		double distroEnd;
		

		colWidth = maxWidth(theCol);
		distroStart = startX + colWidth + (colWidth/(PADDING_DIVISOR*2)) 
			- ((colWidth/PADDING_DIVISOR)*MIDDLE_SPACING_SIZE);
		distroEnd = startX + colWidth + (colWidth/(PADDING_DIVISOR*2)) 
			+ ((colWidth/PADDING_DIVISOR)*MIDDLE_SPACING_SIZE);
		
		double d = (distroEnd - distroStart)/(nPassthroughs + 1);
		double x = distroStart + index*d;
		return x;
	}

	/**
	* Get the start position for a given relation that starts from the class
	*
	*/
	private Position getStartPoint(ClassRelation relation, UMLClass theClass, Direction side){
		ArrayList<ClassRelation> relationList = new ArrayList<ClassRelation>();
		for(ClassRelation r : relations){
			if((r.source == theClass && getRelationDir(r) == side)
				|| (r.target == theClass && getRelationDir(r) == side.opposite())){
				relationList.add(r);
			}
		} 
		
		int index = 0;
		if(relation.source == theClass)
			index = relationList.indexOf(relation);
		
		double distroStart = theClass.getPosition().y + (theClass.getHeight()*0.5 
				- (theClass.getHeight()*RELATION_GRID_SIZE));
		double distroEnd = theClass.getPosition().y + (theClass.getHeight()*0.5
				+ (theClass.getHeight()*RELATION_GRID_SIZE));
				
	
		double d = (distroEnd - distroStart)/(relationList.size() + 1);
		double y = distroStart + index*d;
		double x;
		if(side == Direction.RIGHT){
			x = theClass.getPosition().x + theClass.getWidth();
		}else{
			x = theClass.getPosition().x;
		}
		return new Position(x, y);
	}

}