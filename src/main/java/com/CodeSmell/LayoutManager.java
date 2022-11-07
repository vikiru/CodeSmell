package com.CodeSmell;

import java.util.ArrayList;

import com.CodeSmell.UMLClass;


class LayoutManager  {

	public LayoutManager() {}

	public void positionClasses(ArrayList<UMLClass> classes) {
		/**
		 * given a list of classes with a defined size attribute,
		 * set their position with the classes setPosition() method
		 */

		// determine coordinates for starting c
		int x = 0;
		int y = 0;

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
		}
	}

	public void setRelationPaths(ArrayList<ClassRelation> relations) {
		/**
		 * given a list of classes which have been rendered and 
		 * positioned, set the path for their index of their connections
		 * attribute 
		 */
		//ArrayList<Position> path = new ArrayList<Position>();
		//relations.get(0).setPath(path);
	}
}