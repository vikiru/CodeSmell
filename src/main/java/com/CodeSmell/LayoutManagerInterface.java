package com.CodeSmell;

import java.util.ArrayList;
import com.CodeSmell.Position;

public interface LayoutManagerInterface {

    // calls setPosition() for each class  
    void positionClasses(ArrayList<UMLClass> classes);

    // sets the 'route' attribute
    // on each index of each classes connections attribute
    void setConnectionRoutes(ArrayList<UMLClass> classes); 
}
