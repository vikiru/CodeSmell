package com.CodeSmell.smell;

import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.stat.ClassStat;
import com.CodeSmell.stat.StatTracker;
import javafx.util.Pair;

import static com.CodeSmell.smell.Common.*;

import java.util.*;

public class MisplacedClass extends Smell {

    public static StatTracker stats;
    public Set<CPGClass> misplacedClasses = new HashSet<>();
    public LinkedList<CodeFragment> detections = new LinkedList<>();

    public MisplacedClass(CodePropertyGraph cpg) {
        super("Misplaced Class", cpg);
        stats = Common.stats;
        returnMisplacedClasses();
        detectAll();
    }

    @Override
    public CodeFragment detectNext() {
        return detections.poll();
    }

    private void detectAll() {
        for (CPGClass cpgClass : misplacedClasses) {
            String description = cpgClass.name + " is a misplaced class.";
            CodeFragment cf = CodeFragment.makeFragment(description, cpgClass);
            detections.add(cf);
        }
    }

    public void returnMisplacedClasses() {

        HashMap<CPGClass, Pair<Integer, Integer>> packageUsages = new HashMap<>();
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
