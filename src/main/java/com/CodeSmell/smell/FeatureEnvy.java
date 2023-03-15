package com.CodeSmell.smell;

import com.CodeSmell.parser.*;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class FeatureEnvy extends Smell {
    public LinkedList<CodeFragment> detections;
    protected FeatureEnvy( CodePropertyGraph cpg) {
        super("Feature Envy", cpg);
        this.detections = new LinkedList<>();
        this.detectAll();
    }

    @Override
    public CodeFragment detectNext() {
        return detections.poll();
    }

    @Override
    public LinkedList<CodeFragment> getDetections() {
        return this.detections;
    }

    /**
     * Fills out a list of classes that 'envy' (meaning: access other classes' methods more than their own) other classes
     */
    private void detectAll(){
        ArrayList<CPGClass> classes = cpg.getClasses();
        //Fill out a dictionary listing ALL of the method accesses each CPGClass does, as well as attribute accesses.
        Map<CPGClass, ArrayList<CPGClass.Method>> methodAccesses = new HashMap<CPGClass, ArrayList<CPGClass.Method>>();
        Map<CPGClass, ArrayList<CPGClass.Attribute>> attrAccesses = new HashMap<CPGClass, ArrayList<CPGClass.Attribute>>();

        //Fill out methodAccesses and attrAccesses
        for(CPGClass curClass : classes){
            ArrayList<CPGClass.Method> accessList = new ArrayList<CPGClass.Method>();
            ArrayList<CPGClass.Attribute> attrList = new ArrayList<CPGClass.Attribute>();

            methodAccesses.put(curClass,accessList);
            attrAccesses.put(curClass, attrList);
            for(CPGClass.Method method : curClass.getMethods()){
                for(CPGClass.Attribute a : method.getAttributeCalls()){
                    attrList.add(a);
                }
                for(CPGClass.Method m : method.getMethodCalls()){
                    if(!m.returnType.equals("")) //void methods do not count
                        accessList.add(m);
                }
            }
        }


        /* For each CPGClass, tally up a list of how many times it accesses:
        - any attribute from a given class, or
        - any method that returns something from a given class
        Then compare the tally for an external class for the tally for the class itself.
         */
        for(Map.Entry<CPGClass, ArrayList<CPGClass.Method>> classEntry : methodAccesses.entrySet()){
            Map<CPGClass, Integer> classCallCount = new HashMap<>();
            for(CPGClass.Method methodEntry : classEntry.getValue()){
                if(classCallCount.containsKey(methodEntry.getParent())){
                    classCallCount.put(methodEntry.getParent(), classCallCount.get(methodEntry.getParent()) + 1);
                }else{
                    classCallCount.put(methodEntry.getParent(), 1);
                }
            }
            for(CPGClass.Attribute attrEntry : attrAccesses.get(classEntry.getKey())){
                if(classCallCount.containsKey(attrEntry.getParent())){
                    classCallCount.put(attrEntry.getParent(), classCallCount.get(attrEntry.getParent()) + 1);
                }else{
                    classCallCount.put(attrEntry.getParent(), 1);
                }
            }
            //figure out how many calls of its own fields the class has
            int ownCallCount;
            if(classCallCount.containsKey(classEntry.getKey()))
                ownCallCount = classCallCount.get(classEntry.getKey());
            else
                ownCallCount = 0;

            for(Map.Entry<CPGClass, Integer> countEntry : classCallCount.entrySet()){
                if(countEntry.getValue() > ownCallCount){
                    this.addSmell(classEntry.getKey(), countEntry.getKey(), countEntry.getValue(), ownCallCount);
                }
            }

        }

    }

    private void addSmell(CPGClass envyingClass, CPGClass enviedClass, int envyCount, int ownCount){
        String description = String.format( "%s has feature envy for %s, calling %s %d times versus calling %s %d times.",
                envyingClass.name, enviedClass.name, enviedClass.name, envyCount, envyingClass.name, ownCount);
        ArrayList<CPGClass.Method> methodList = new ArrayList<CPGClass.Method>();
        for(CPGClass.Method m : envyingClass.getMethods()){
            for(CPGClass.Method calledM : m.getMethodCalls()){
                if(calledM.getParent() == enviedClass && !(methodList.contains(m))){
                    methodList.add(m);
                }
            }
        }

        CPGClass.Method[] methods = new CPGClass.Method[methodList.size()];
        for(int n = 0; n < methodList.size() ; n++){
            methods[n] = methodList.get(n);
        }
        CodeFragment fragment = new CodeFragment(description, new CPGClass[]{envyingClass, enviedClass}, methods,
                null, null, null, null);
        this.detections.add(fragment);
    }

    @Override
    public String description() {
        return "A class that accesses other class' data more often than its own.";
    }
}
