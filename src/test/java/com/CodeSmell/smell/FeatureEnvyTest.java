package com.CodeSmell.smell;

import com.CodeSmell.ProjectManager;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.parser.Parser;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static com.CodeSmell.smell.Common.initStatTracker;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class FeatureEnvyTest extends SmellTester{

    //private Parser p;
    //private CodePropertyGraph cpg;

    //private FeatureEnvy fe;
    //ArrayList<Smell.CodeFragment> detections;

    @Before
    public void before() {
        this.cpg = ProjectManager.getCPG("testproject");
        initStatTracker(cpg);
        smell = new FeatureEnvy(this.cpg);
        detections = getDetections(smell);
    }

    public ArrayList<Smell.CodeFragment> getDetections(Smell smell) {
        ArrayList<Smell.CodeFragment> arr = new ArrayList<Smell.CodeFragment>();
        while (smell.detect()) {
            arr.add(smell.lastDetection);
        }
        return arr;
    }
    @Test
    public void testWorksAtAll(){
        System.out.println("Feature Envy Test:");
        System.out.println("   Number of detections: " + detections.size());
        assertNotEquals(0, detections.size());
    }

    @Test
    public void detectBasicEnvy(){
        Smell.CodeFragment fragment = this.hasClass("FeatureEnvyer");
        assertTrue(fragment != null);
        if(fragment != null){
            System.out.println(fragment.description);
        }
    }

    @Test
    public void detectOneMethodEnvy(){
        Smell.CodeFragment fragment = this.hasClass("EnvierOneMethod");
        assertTrue(fragment != null);
        if(fragment != null){
            System.out.println(fragment.description);
        }
    }

    /*private boolean hasClass(String name){
        boolean hasDetection = false;
        for(Smell.CodeFragment f : detections){
            if(f.classes[0].classFullName.equals(name)){
                hasDetection = true;
            }
            if(f.classes.length > 0) {
                String outLine = f.description + "\n";
                outLine += "     Methods: {";
                for(int n = 0; n < f.methods.length; n++){
                    outLine += f.methods[n].name;
                    if((n+1) < f.methods.length)
                        outLine += ", ";
                }
                outLine += " }";
                System.out.println(outLine);
            }
        }
        return hasDetection;
    }*/
}