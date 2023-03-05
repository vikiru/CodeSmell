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
public class ShotgunSurgeryTest extends SmellTester{



    @Before
    public void before() {
        this.cpg = ProjectManager.getCPG("testproject");
        initStatTracker(cpg);
        smell = new ShotgunSurgery(this.cpg);
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
        System.out.println("Shotgun Surgery Test: ");
        System.out.println("   Number of detections: " + detections.size());
        assertNotEquals(0, detections.size());
    }

    /**
     * Detect if a basic shotgun surgery case is present.
     *
     * The basic case is a method that refers to the minimum number of other classes,
     * and the minimum number of other methods, required to count as shotgun surgery.
     */
    @Test
    public void detectBasicShotgunSurgery(){
        Smell.CodeFragment fragment = this.hasMethod("ShotgunSurgeryClass", "basicShotgunMethod");
        assertTrue(fragment != null);
        if(fragment != null){
            System.out.println(fragment.description);
        }
    }

}
