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

    private void detectAll(){
        ArrayList<CPGClass> classes = cpg.getClasses();

        Map<CPGClass.Method, ArrayList<CPGClass.Method>> callMethods = new HashMap<CPGClass.Method, ArrayList<CPGClass.Method>>();
        Map<CPGClass.Method, ArrayList<CPGClass>> callClasses = new HashMap<CPGClass.Method, ArrayList<CPGClass>>();

        //Map<CPGClass.Method, CPGClass> methodClasses = new HashMap<CPGClass.Method, CPGClass>();

        //first, build the dictionaries
        for(CPGClass curClass : classes){
            for(CPGClass.Method curMethod : curClass.getMethods()){

                MethodStat mStat = new MethodStat(curMethod, cpg, new Helper(cpg));

                int methodCallsSize = 0;
                ArrayList<CPGClass> classCalls = new ArrayList<>();
                ArrayList<CPGClass.Method> methodCalls = new ArrayList<>();
                //build lists of classes this method calls, and number of methods this calls

                for(Map.Entry<CPGClass, List<CPGClass.Method>> entry : mStat.distinctMethodCalls.entrySet()){
                    methodCallsSize += entry.getValue().size();
                    for(CPGClass.Method m : entry.getValue()){
                        methodCalls.add(m);
                        if(!classCalls.contains(m.getParent())){
                            classCalls.add(m.getParent());
                        }
                    }
                }
                //System.out.println("   distinctMethodCalls size: " + methodCallsSize);
                //System.out.println("   classCalls size: " + classCalls.size());
                if((methodCallsSize >= CALLING_METHOD_CAP)
                    && (classCalls.size() >= CALLING_CLASS_CAP)){
                    this.addSmell(curMethod, methodCalls, classCalls, curClass);
                }

            }
        }
    }

    /**
     *
     * @param method the Method in question that has the shotgun surgery smell
     */
    private void addSmell(CPGClass.Method method, ArrayList<CPGClass.Method> callMethods, ArrayList<CPGClass> callClasses, CPGClass parentClass){
        String description = method.getParent().classFullName + "." + method.name + " may have shotgun surgery.\n"
                + method.name + " references " + callClasses.size() + " classes and " + callMethods.size() + " methods.\n"
                + "It may make use of too many different functions and classes to define its functionality.";
        CodeFragment fragment = new CodeFragment(description, new CPGClass[]{parentClass}, new CPGClass.Method[]{method}, null, null, null, null);
        this.detections.add(fragment);
    }

    @Override
    public String description() {
        return "A feature that is implemented or used in many different locations simultaneously.";
    }
}
