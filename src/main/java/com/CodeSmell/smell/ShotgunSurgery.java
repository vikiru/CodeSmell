package com.CodeSmell.smell;

import com.CodeSmell.parser.*;
import com.CodeSmell.stat.Helper;
import com.CodeSmell.stat.MethodStat;

import java.util.*;

public class ShotgunSurgery extends Smell {

    private static final int CALLING_METHOD_CAP = 10;
    private static final int CALLING_CLASS_CAP = 5;
    public LinkedList<CodeFragment> detections;

    protected ShotgunSurgery(CodePropertyGraph cpg) {
        super("Shotgun Surgery", cpg);
        this.detections = new LinkedList<>();
        detectAll();
    }

    @Override
    public CodeFragment detectNext() {
        return detections.poll();
    }

    private void detectAll() {
        ArrayList<CPGClass> classes = cpg.getClasses();

        //first, build the dictionaries
        for (CPGClass curClass : classes) {
            for (CPGClass.Method curMethod : curClass.getMethods()) {

                MethodStat mStat = new MethodStat(curMethod, cpg, new Helper(cpg));
                ArrayList<CPGClass> classCalls = new ArrayList<>();
                ArrayList<CPGClass.Method> methodCalls = new ArrayList<>();
                //build lists of classes this method calls, and number of methods this calls
                for (Map.Entry<CPGClass, List<CPGClass.Method>> entry : mStat.distinctMethodCalls.entrySet()) {

                    for (CPGClass.Method m : entry.getValue()) {
                        methodCalls.add(m);
                        if (!classCalls.contains(m.getParent())) {
                            classCalls.add(m.getParent());
                        }
                    }
                }
                //check to see if it hits the main thresholds, if so, add a smell
                if ((methodCalls.size() >= CALLING_METHOD_CAP)
                        && (classCalls.size() >= CALLING_CLASS_CAP)) {
                    this.addSmell(curMethod, methodCalls, classCalls, curClass);
                }
            }
        }
    }

    /**
     * @param method the Method in question that has the shotgun surgery smell
     */
    private void addSmell(CPGClass.Method method, ArrayList<CPGClass.Method> callMethods, ArrayList<CPGClass> callClasses, CPGClass parentClass) {
        String description = method.getParent().classFullName + "." + method.name + " may have shotgun surgery, making it difficult.\n"
                + method.name + " references " + callClasses.size() + " classes and " + callMethods.size() + " methods.\n"
                + "It may make use of too many different functions and classes to define its functionality.";
        CodeFragment fragment = new CodeFragment(description, new CPGClass[]{parentClass}, new CPGClass.Method[]{method}, null, null, null, null);
        this.detections.add(fragment);
    }

    @Override
    public String description() {
        return "A feature whose behavior is defined in many different classes and methods, making it difficult to modify.";
    }

    @Override
    public LinkedList<CodeFragment> getDetections() {
        return detections;
    }
}
