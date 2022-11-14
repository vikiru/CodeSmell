package com.CodeSmell;

import java.util.ArrayList;
import java.lang.reflect.Array;

import com.CodeSmell.UMLClass;



class LayoutManager  {

	private static int Y_PADDING = 30;
	private static double RELATION_GRID_SIZE = 0.4; //how much of 
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

		// determine coordinates for starting c
		//int x = 0;
		//int y = 0;
		
		// Find the number of columns that this list of classes will turn into.
		int nColumns = (int) Math.min(2, Math.ceil(Math.sqrt(classes.size())));

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
		//setSpacing(grid);
		/*
		for (int i=0; i < classes.size(); i++) {
			// all this layout manager does is
			// set the classes's ith class' 
			// x position to be after i-1th's with
			// some padding. 
			if (i >= 1) {
				int lastX = classes.get(i - 1).getPosition().x;
				int lastWidth = classes.get(i - 1).getWidth();
				int padding = lastWidth / PADDING_DIVISOR;
				x = lastX + lastWidth + padding;
			}
			classes.get(i).setPosition(x, y);
		}*/
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
			double padding = maxWidth(getColumn(r.source))/(PADDING_DIVISOR*2); 

			Position start = getPoint(r, r.source, this.getRelationDir(r));
			path.add(start); //Path start
			path.add(new Position(getPaddedPoint(r), start.y));
			//path.add(new Position((r.source.getPosition().x + (padding * 5)), start.y));

			Position end;
			if(r.source.getPosition().x == r.target.getPosition().x){//special case: starts and ends in the same column
				/*double padding = maxWidth(getColumn(r.source))/(PADDING_DIVISOR*2); 
				double x = r.source.getPosition().x + r.source.getWidth() + padding;
				double y = r.source.getPosition().y + (r.source.getHeight()/2);
				path.add(new Position(x, y));*/
				end = getPoint(r, r.target, this.getRelationDir(r));
				path.add(new Position(getPaddedPoint(r), end.y));
				//path.add(new Position((r.source.getPosition().x + (padding * 5)), end.y));
				
			}else{
			//Add intermediate path waypoints..
			
			end = getPoint(r, r.target, !this.getRelationDir(r));
			path.add(new Position(getPaddedPoint(r), end.y));
			//path.add(new Position((r.source.getPosition().x + (padding * 5)), end.y));
			
			}
			path.add(end);
			r.setPath(path);
		}
	}
	
	/**
	* Get which direction the relation is going horizontally - to the right or no difference (true) or to the left (false).
	*/
	private boolean getRelationDir(ClassRelation r){
		if(r.source.getPosition().x <= r.target.getPosition().x){ //going to right
			return true;
		}else{
			return false;
		}
	}

	private int getColumn(UMLClass theClass){
		for(int n = 0; n < grid.length; n++){
			if(grid[n].get(0).getPosition().x == theClass.getPosition().x)
				return n;
		}
		return 0;
	}
	/**
	* Gets a point in the middle of the 'padding' area directly in front of the start point.
	*/
	private double getPaddedPoint(ClassRelation relation){
		
		
		//get number of ClassRelations that pass through the area
		int nPassthroughs = 0;
		int index = 0;

		for(ClassRelation r: relations){
			if((r.source.getPosition().x <= relation.source.getPosition().x) && (r.target.getPosition().x >= relation.target.getPosition().x)){
				nPassthroughs++;
			} else if ((r.source.getPosition().x >= relation.source.getPosition().x) && (r.target.getPosition().x <= relation.target.getPosition().x)){
				nPassthroughs++;
			}
			if(r == relation)
				index = relations.indexOf(r);
		}
		double colWidth;
		double distroStart;
		double distroEnd;
		if(getRelationDir(relation)){ //right
		colWidth = maxWidth(getColumn(relation.source));
		distroStart = relation.source.getPosition().x + colWidth + (colWidth/(PADDING_DIVISOR*2)) - ((colWidth/PADDING_DIVISOR)*MIDDLE_SPACING_SIZE);
		distroEnd = relation.source.getPosition().x + colWidth + (colWidth/(PADDING_DIVISOR*2)) + ((colWidth/PADDING_DIVISOR)*MIDDLE_SPACING_SIZE);
		} else{ //left
		colWidth = maxWidth(getColumn(relation.source)-1);
		distroStart = relation.source.getPosition().x - (colWidth/(PADDING_DIVISOR*2)) - ((colWidth/PADDING_DIVISOR)*MIDDLE_SPACING_SIZE);
		distroEnd = relation.source.getPosition().x - (colWidth/(PADDING_DIVISOR*2)) + ((colWidth/PADDING_DIVISOR)*MIDDLE_SPACING_SIZE);
		}
		
		double d = (distroEnd - distroStart)/(nPassthroughs + 1);
		double x = distroStart + index*d;
		return x;
	}

	/**
	* Get the start position for a given relation that starts from the class
	*
	*/
	private static Position getPoint(ClassRelation relation, UMLClass theClass, boolean side){
		int index = 1;
		if(relation.source == theClass)
			index = theClass.getRelations().indexOf(relation);
		
		double distroStart = theClass.getPosition().y + (theClass.getHeight()*0.5 - 
				(theClass.getHeight()*(RELATION_GRID_SIZE)));
		double distroEnd = theClass.getPosition().y + (theClass.getHeight()*0.5 + 
				(theClass.getHeight()*RELATION_GRID_SIZE));

		double d = (distroEnd - distroStart)/(theClass.getRelations().size() + 1);
		double y = distroStart + index*d;
		double x;
		if(side){//right side if true, left side if false
			x = theClass.getPosition().x + theClass.getWidth();
		}else{
			x = theClass.getPosition().x;
		}
		return new Position(x, y);
	}

}