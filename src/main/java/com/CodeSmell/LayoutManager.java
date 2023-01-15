package com.CodeSmell;

import java.util.ArrayList;
import java.lang.reflect.Array;

import com.CodeSmell.UMLClass;



class LayoutManager  {

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

	private static final double Y_PADDING = 30; //size of gap between classes vertically
	private static final double RELATION_GRID_SIZE = 0.4; 
	private static final double MIDDLE_SPACING_SIZE = 0.4; //how much of the gap in width can be filled up by relations
	private static final double PADDING_DIVISOR = 2; //how much padding (gap in width between two columns), as a divisor
	private static final double PASSTHROUGH_SIZE = 10; //how much to add to a given passthrough gap for each new line
	private static final int MAX_COLUMNS = 3;

	private ArrayList<UMLClass>[] grid;
	private ArrayList<ClassRelation> relations;
	private ArrayList<ClassRelation>[][] passthroughs;
	private boolean passthroughsPopulated;

	public LayoutManager(ArrayList<UMLClass> classes, ArrayList<ClassRelation> relations) {

		this.positionClasses(classes);
		this.setSpacing();
		this.passthroughsPopulated = false;
		this.relations = relations;
		this.setRelationPaths();
		this.offsetGrid();
		this.passthroughsPopulated = true;
		this.setRelationPaths();
	}

	public void positionClasses(ArrayList<UMLClass> classes) {
		/**
		 * given a list of classes with a defined size attribute,
		 * set their position with the classes setPosition() method
		 */
		
		// Find the number of columns that this list of classes will turn into.
		int nColumns = (int) Math.min(MAX_COLUMNS, Math.ceil(Math.sqrt(classes.size())));

		//Generate the grid
		ArrayList<UMLClass>[] grid = new ArrayList[nColumns];
		for(int i = 0; i < nColumns; i++){
			grid[i] = new ArrayList<UMLClass>();
		}
		
		//Algorithm for clustering parents and children together.
		ArrayList<UMLClass> workingList = (ArrayList<UMLClass>) classes.clone();
		ArrayList<UMLClass> sortedList = new ArrayList<UMLClass>();
		/**
		* Essentially, this finds 'parents' (classes with at least one outgoing relation)
		* And orders them in the list so that 'children' 
		* (classes their relations go to, that have only one relation) 
		* come right before them.
		*/
		for(int n = 0; n < workingList.size(); n++){
			if(workingList.get(n).getRelations().size() > 0){;
				boolean childAdded = false;
				for(ClassRelation r : workingList.get(n).getRelations()){;
					if(r.target.getRelations().size() == 1){
						childAdded = true;
						sortedList.add(r.target);
						workingList.remove(r.target);
					}
				}
				if(childAdded){ //if at least one child of this class was
					if(n >= workingList.size())
						n = workingList.size()-1;
					if(workingList.size() > 0){
						sortedList.add(workingList.get(n));
						workingList.remove(workingList.get(n));
					}
				}
			}
		}//Once this is done, everything that wasn't already added gets added to the end.
		for(int n = 0; n < workingList.size(); n++){
			sortedList.add(workingList.get(n));
		}
		
		
		
		int curRow = 0;
		int curCol = 0;
		int maxRows = (int)Math.ceil(((double)classes.size())/((double)nColumns));
		while(sortedList.size() > 0){
			if(curRow == maxRows){
				curRow = 0;
				curCol++;
			}
			
			grid[curCol].add(sortedList.get(0));
			sortedList.remove(0);
			curRow++;
		}
		this.grid = grid;
		this.passthroughs = new ArrayList[nColumns][maxRows];
		for(int x = 0; x < nColumns; x++){
			for(int y = 0; y < maxRows; y++){
				this.passthroughs[x][y] = new ArrayList<ClassRelation>();
			}
		}
		
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
				if(!this.passthroughsPopulated) //Do the first pass on finding passthroughs
					findPassthrough(r, path, curColumn, destColumn);
				else //Then second pass on actually adding passthroughs
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
			if(this.passthroughsPopulated)
				r.setPath(path);
		}
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
		int index = 1;
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
		double colWidth = maxWidth(theCol);
		double distroStart = startX + colWidth + (colWidth/(PADDING_DIVISOR*2)) 
			- ((colWidth/PADDING_DIVISOR)*MIDDLE_SPACING_SIZE);
		double distroEnd = startX + colWidth + (colWidth/(PADDING_DIVISOR*2)) 
			+ ((colWidth/PADDING_DIVISOR)*MIDDLE_SPACING_SIZE);
		
		return distro(distroStart, distroEnd, index, nPassthroughs);
	}

	/**
	* Get the start position for a given relation that starts from the class
	*
	*/
	private Position getStartPoint(ClassRelation relation, UMLClass theClass, Direction side){
		ArrayList<ClassRelation> relationList = new ArrayList<ClassRelation>();
		for(ClassRelation r : this.relations){
			if((r.source == theClass && getRelationDir(r) == side)
				|| (r.target == theClass && getRelationDir(r) == side.opposite())){
				relationList.add(r);
			}else if(r.source.getPosition().x == r.target.getPosition().x //case for vertical relations
				&& side == Direction.RIGHT)
				relationList.add(r);
		}
		
		int index = 0;
		if(relation.source == theClass || relation.target == theClass)
			index = relationList.indexOf(relation);
		
		double distroStart = theClass.getPosition().y + (theClass.getHeight()*0.5 
				- (theClass.getHeight()*RELATION_GRID_SIZE));
		double distroEnd = theClass.getPosition().y + (theClass.getHeight()*0.5
				+ (theClass.getHeight()*RELATION_GRID_SIZE));
				
		double y = distro(distroStart, distroEnd, index, relationList.size());//distroStart + index*d;
		double x;
		if(side == Direction.RIGHT){
			x = theClass.getPosition().x + theClass.getWidth();
		}else{
			x = theClass.getPosition().x;
		}
		return new Position(x, y);
	}
	
	/**
	* Used during initial pass to find the locations on the grid of passthrough points. 
	* addPassthrough() is used once that's done
	*/
	private void findPassthrough(ClassRelation r, ArrayList<Position> p, int startCol, int targetCol){
		
		int gridX;
		if(targetCol < startCol){
			gridX = targetCol + 1;
		}
		else{
			gridX = targetCol - 1;
		}
		
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
		
		int classRow = 0;
		for(int n = 0; n < gapPositions.length; n++){
			if(Math.abs(gapPositions[n] - startY) < Math.abs(targetY - startY)){
				targetY = gapPositions[n];
				classRow = n;
			}
		}
		
		int gridY = classRow + 1;
		
		this.passthroughs[gridX][gridY].add(r);
		
		p.add(new Position(startX, targetY));
		p.add(new Position(targetX, targetY));
		
	}
	/**
	* Used in place of findPassthrough() once all the passthrough locations are known and filled in.
	*/
	private void addPassthrough(ClassRelation r, ArrayList<Position> p, int startCol, int targetCol){
		
		int gridX;
		if(targetCol < startCol){
			gridX = targetCol + 1;
		} else {
			gridX = targetCol - 1;
		}
		double gapPosition = 0;
		for(int n = 0; n < grid[targetCol].size(); n++){
				if(passthroughs[gridX][n].indexOf(r) > -1){
					gapPosition = getPassthroughY(gridX, n, r);
				}
		}
		
		double startX = p.get(p.size()-1).x;
		double startY = p.get(p.size()-1).y;
		double targetY = gapPosition;

		double targetX;
		if(targetCol < startCol)
			targetX = getPaddedPoint(r, startCol - 2);
		else
			targetX = getPaddedPoint(r, startCol + 1);
		
		p.add(new Position(startX, targetY));
		p.add(new Position(targetX, targetY));
		
	}
	
	/**
	* Gets the Y value the passthrough line goes to.
	*/
	private double getPassthroughY(int col, int row, ClassRelation r){
		
		int index = this.passthroughs[col][row].indexOf(r);
		double start = this.grid[col].get(row).getPosition().y + this.grid[col].get(row).getHeight();
		
		double end;
		if(row + 1 >= this.grid[col].size()) //is it at the end, ie passing underneath everything? if so special case
			end = this.grid[col].get(row).getPosition().y + this.passthroughs[col][row].size() * PASSTHROUGH_SIZE;
		else
			end = this.grid[col].get(row + 1).getPosition().y; //- PASSTHROUGH_SIZE;
			
		if(this.passthroughsPopulated){
			double y = distro(start, end, index+1, this.passthroughs[col][row].size());
			return y;
		}else{
			return start;
		}
	}
	/**
	* Finds where the (index)th value of an even distribution from (start) to (end) of (size) values
	*/
	private static double distro(double start, double end, int index, int size){
		double d = (end - start)/(size + 1);
		return (start + index*d);
	}
	
	/**
	* Go through entire grid and add offsets based on number of passthroughs.
	*/
	private void offsetGrid(){
		for(int x = 0; x < this.passthroughs.length; x++){
			for(int y = 0; y < this.passthroughs[0].length; y++){
				if(this.passthroughs[x][y].size() > 1){
					for(int n = 1; n < this.passthroughs[x][y].size(); n++){
						this.offsetClass(x, y, PASSTHROUGH_SIZE);
					}
				}
			}
		
		}
		
	}
	
	/**
	* Increase the gap underneath the class in the row/column.
	*/
	private void offsetClass(int col, int row, double offset){

		if(row < this.grid[col].size() - 1){//check if there are classes past this one
			UMLClass curClass;
			for(int n = row+1; n < this.grid[col].size(); n++){
				
				curClass = this.grid[col].get(n);
				curClass.setPosition(curClass.getPosition().x, curClass.getPosition().y + offset);
			}
		}
	}
	


}