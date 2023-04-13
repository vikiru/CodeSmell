package com.CodeSmell.smell;

import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.parser.Parser;

import java.util.ArrayList;

public abstract class SmellTester {

    protected Parser p;
    protected CodePropertyGraph cpg;

    protected Smell smell;
    ArrayList<Smell.CodeFragment> detections;

    protected Smell.CodeFragment hasClass(String name){
        for(Smell.CodeFragment f : detections){
            if(f.classes[0].classFullName.equals(name)) {
                return f;
            }
        }
        return null;
    }

    protected Smell.CodeFragment hasMethod(String theClass, String theMethod){
        //boolean hasDetection = false;
        for(Smell.CodeFragment f : detections){
            if(f.classes[0].classFullName.equals(theClass) && f.methods[0].name.equals(theMethod)) {
                return f;
            }
        }
        return null;
    }
}
