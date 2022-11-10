package com.CodeSmell;

import java.util.ArrayList;
import java.lang.reflect.Array;

import com.CodeSmell.UMLClass;



class LayoutManager  {

	private static int yPadding = 30;

	public LayoutManager() {}

	public void positionClasses(ArrayList<UMLClass> classes) {
		/**
		 * given a list of classes with a defined size attribute,
		 * set their position with the classes setPosition() method
		 */

		// determine coordinates for starting c
		//int x = 0;
		//int y = 0;
		
		// Find the number of columns that this list of classes will turn into. -M
		int nColumns = (int) Math.round(Math.sqrt(classes.size()));

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
		setSpacing(grid);
		/*
		for (int i=0; i < classes.size(); i++) {
			// all this layout manager does is
			// set the classes's ith class' 
			// x position to be after i-1th's with
			// some padding. 
			if (i >= 1) {
				int lastX = classes.get(i - 1).getPosition().x;
				int lastWidth = classes.get(i - 1).getWidth();
				int padding = lastWidth / 2;
				x = lastX + lastWidth + padding;
			}
			classes.get(i).setPosition(x, y);
		}*/
	}


	/**
	* Given a grid (array of arraylists) of UML classes, arrange them, spaced out accordingly
	*
	*/
	private void setSpacing(ArrayList<UMLClass>[] grid){
		//First, iterate over each column and set x
		int x = 0;
		int y;
		for(int col = 0; col < grid.length; col++){
			y = 0;
			if (col > 0){
				int lastX = grid[col-1].get(0).getPosition().x;
				int lastWidth = maxWidth(grid[col-1]);
				int padding = lastWidth / 2;
				x = lastX + lastWidth + padding;
			}
			//Then iterate through each column and set y
			for(int i = 0; i < grid[col].size(); i++){
				if(i > 0){
					y = y + grid[col].get(i-1).getHeight() + yPadding;
				}
				grid[col].get(i).setPosition(x, y);
			}
		}
		
	}

	/**
	* Self-explanatory, gets the ceiling of the width of a list of classes.
	*
	*/
	private int maxWidth(ArrayList<UMLClass> classes){
		int max = 0;
		int width;
		for(int n = 0; n < classes.size(); n++){
			width = classes.get(n).getWidth();
			if(width > max){
				max = width;
			}
		}
		return max;
	}

	/**
	 * Given a list of classes which have been rendered and 
	 * positioned, set the path for their index of their connections
	 * attribute 
	 */
	public void setRelationPaths(ArrayList<ClassRelation> relations) {
		
		//ArrayList<Position> path = new ArrayList<Position>();
		//relations.get(0).setPath(path);
		
		

	}
}