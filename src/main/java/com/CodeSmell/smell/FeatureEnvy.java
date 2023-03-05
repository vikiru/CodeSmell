package com.CodeSmell.smell;

import com.CodeSmell.parser.*;
import com.CodeSmell.stat.Helper;
import com.CodeSmell.stat.MethodStat;

import java.util.*;

public class FeatureEnvy extends Smell {

    private static final int ENVY_THRESHOLD = 3;
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

                    if(!m.returnType.equals("") && !m.name.equals("main")) //void and main methods do not count
                        accessList.add(m);
                }
            }
        }

        /* For each CPGClass, tally up a list of how many times it accesses:
        - any attribute from a given class, or
        - any method that returns something from a given class
        Then compare the tally for an external class for the tally for the class itself.
         */
        for(Map.Entry<CPGClass, ArrayList<CPGClass.Method>> classEntry : methodAccesses.entrySet()) {

            if (!classEntry.getKey().classType.equals("interface")){
                Map<String, Integer> classCallCount = new HashMap<>();

            for (CPGClass.Method methodEntry : classEntry.getValue()) {

                int methodCallCount = 1;

                if (classCallCount.containsKey(methodEntry.getParent().classFullName)) {
                    classCallCount.put(methodEntry.getParent().classFullName, classCallCount.get(methodEntry.getParent().classFullName) + methodCallCount);
                } else {
                    classCallCount.put(methodEntry.getParent().classFullName, methodCallCount);
                }
            }
            for (CPGClass.Attribute attrEntry : attrAccesses.get(classEntry.getKey())) {
                int attrCallCount = 1;
                String targetClass;
                if(classCallCount.containsKey(attrEntry.attributeType)){
                    targetClass = attrEntry.attributeType;
                }else{
                    targetClass = attrEntry.getParent().classFullName;
                }

                if (classCallCount.containsKey(targetClass)){
                    classCallCount.put(targetClass, classCallCount.get(targetClass) + attrCallCount);
                } else {
                    classCallCount.put(targetClass, attrCallCount);
                }
            }
            //figure out how many calls of its own fields the class has
            int ownCallCount;
            if (classCallCount.containsKey(classEntry.getKey().classFullName))
                ownCallCount = classCallCount.get(classEntry.getKey().classFullName);
            else
                ownCallCount = 0;
            for (Map.Entry<String, Integer> countEntry : classCallCount.entrySet()) {
                if (countEntry.getValue() > ownCallCount && countEntry.getValue() > ENVY_THRESHOLD) {
                    CPGClass theClass = null;
                    for(CPGClass c : cpg.getClasses()){
                        if(c.classFullName == countEntry.getKey())
                            theClass = c;
                    }
                    this.addSmell(classEntry.getKey(), theClass, countEntry.getValue(), ownCallCount);
                }
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
