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

public class FeatureEnvyTest {

    private Parser p;
    private CodePropertyGraph cpg;

    @Before
    public void before() {
        this.cpg = ProjectManager.getCPG("testproject");
        initStatTracker(cpg);
    }

    public ArrayList<Smell.CodeFragment> getDetections(Smell smell) {
        ArrayList<Smell.CodeFragment> arr = new ArrayList<Smell.CodeFragment>();
        while (smell.detect()) {
            arr.add(smell.lastDetection);
        }
        return arr;
    }
    @Test
    public void TestFeatureEnvy(){
        System.out.println("Feature Envy Test:");
        FeatureEnvy fe = new FeatureEnvy(this.cpg);
        ArrayList<Smell.CodeFragment> detections = getDetections(fe);
        System.out.println("   Number of detections: " + detections.size());
        for(Smell.CodeFragment f : detections){
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
        assertNotEquals(0, detections.size());

    }
}