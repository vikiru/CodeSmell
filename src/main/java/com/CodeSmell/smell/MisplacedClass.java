package com.CodeSmell.smell;

import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.stat.ClassStat;
import com.CodeSmell.stat.StatTracker;
import javafx.util.Pair;
import static com.CodeSmell.smell.Common.*;

import java.util.*;

public class MisplacedClass extends Smell{

    public static StatTracker stats;
    Set<CPGClass> misplacedClasses = new HashSet<>();
    public LinkedList<CodeFragment> detections = new LinkedList<>();
    public MisplacedClass(String name, CodePropertyGraph cpg) {
        super(name, cpg);
        stats = new StatTracker(cpg);
        returnMisplacedClasses();
    }

    @Override
    public CodeFragment detectNext() {
        CPGClass[] classes = new CPGClass[1];
        if(!misplacedClasses.isEmpty())
        {
            classes[0] = misplacedClasses.iterator().next();
            misplacedClasses.remove(misplacedClasses.iterator().next());

            detections.add(new CodeFragment("Misplaced Classes",classes,null,null,null,null,null));
            return new CodeFragment("Misplaced Classes",classes,null,null,null,null,null);
        }
        else
            return null;
    }

    public void returnMisplacedClasses()
    {

        HashMap<CPGClass, Pair<Integer,Integer>> packageUsages= new HashMap<>();
        for (ClassStat classStat : Common.stats.classStats.values()) {
            int classInPackage = 0;
            int classOutPackage = 0;
            for (Map.Entry<CPGClass, Integer> calledClasses : classStat.totalClassMethodCalls.entrySet()) {

                    if (calledClasses.getKey().packageName.equals(classStat.cpgClass.packageName)) {
                        classInPackage += calledClasses.getValue();
                    } else {
                        classOutPackage += calledClasses.getValue();
                    }

                }
                if (classInPackage < classOutPackage) {
                    misplacedClasses.add(classStat.cpgClass);
                }
            }
        }

    public Set<CPGClass> getMisplacedClasses() {
        return misplacedClasses;
    }

    @Override
    public String description() {
        return null;
    }

    @Override
    public LinkedList<CodeFragment> getDetections() {
        return detections;
    }
}
