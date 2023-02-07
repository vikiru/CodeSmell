package com.CodeSmell.smell;

import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;
import javafx.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * A Lazy Class is a class that is under used. This simply makes the code base unnecessarily complicated.
 * It could be a result of a class that was refactored and had a lot of its functionality removed, or result
 * of a class that was supposed to be developed in the future but was not worked on.
 *
 * Ways to detect Lazy Class via the Code Smell.
 *
 * Algorithm:
 * 0. Abstract classes/interfaces could be considered if there are not ever implemented
 * 0.1 A class thats not connected to any other
 * 1. Gather a list of all the methods in the class
 * 2. See if those methods call other methods (If these methods call other methods it is likely this class is doing something.
 *    Need to think about a threshold)
 * 3. More importantly, scan all other classes and see how many times the methods in those classes called the methods
 * in the suspected lazy class. If the class does not call, and is not called a lot it may be lazy.
 * 4. The user can only ever be offered a suggestion as to if the class is lazy, I.E check this out, may be lazy. Please decide if can be
 * refactored into another class.
 *
 *
 * Get forward connections, now have record of all connection and can use it to figure out which classes are used and which are not, also how many times
 */
public class LazyClass extends Smell{

    ArrayList<Class> classes = new ArrayList<>();

    public static StatTracker stats;

    protected LazyClass(String name, CodePropertyGraph cpg) {
        super(name, cpg);
        stats = new StatTracker(cpg);
        System.out.println();
    }

    private void tallyRelations()
    {
        for(CPGClass aClass : super.cpg.getClasses())
        {
            int timesCalling = 0;
            int timeCalled  = 0;

            for(CPGClass.Method method : aClass.methods)
            {
                for(CPGClass.Method calledMethod : method.getMethodCalls())
                {
                    timesCalling++;
                    int index = 0;
                    for(CPGClass calledClass : super.cpg.getClasses())
                    {
                        index++;
                        if(calledClass.name.equals(calledMethod.parentClassName))
                        {
                            break;
                        }
                    }
                    timeCalled = super.cpg.getClasses().get(index).getTimesCalled()+1;
                    super.cpg.getClasses().get(index).setTimesCalled(timeCalled);
                }

            }
            aClass.setTimesCalling(timesCalling);
        }
    }

    public ArrayList<CPGClass> returnLazyClasses()
    {
        ArrayList<CPGClass> lazyClasses = new ArrayList<>();
        HashMap<CPGClass, Pair<Integer,Integer>> usesAndUsages = new HashMap<>();
        for(Map.Entry<CPGClass, Integer> classes : stats.classUsage.entrySet())
        {
            usesAndUsages.put(classes.getKey(),new Pair<>(classes.getValue(),0));
        }

        int index = 0;
        for(Map.Entry<CPGClass, HashMap<CPGClass, Integer>> classes : stats.totalClassMethodCalls.entrySet())
        {
            usesAndUsages.replace(classes.getKey(),new Pair<>(usesAndUsages.get(classes.getKey()).getKey(),(Integer)((classes.getValue().values().size()))));
            index++;
        }
        //If the class is not called and is not calling any other class it can be considered lazy as it
        //serves no use and should be considered for a refactor
        for(Map.Entry<CPGClass, Pair<Integer,Integer>> classesEntry : usesAndUsages.entrySet())
        {
            if(classesEntry.getValue().getKey() == 0 && classesEntry.getValue().getValue()==0)
            {
                lazyClasses.add(classesEntry.getKey());
            }
        }
        return lazyClasses;
    }


    @Override
    public CodeFragment detectNext() {
        return null;
    }

    @Override
    public String description() {
        tallyRelations();
       /* for(CPGClass aClass : super.cpg.getClasses())
        {
            System.out.println(aClass.classFullName+" Times calling:"+aClass.getTimesCalling()+" Times Called"+aClass.getTimesCalled());
        }*/
        return "";
    }

    //TODO
    /**
     * no usages and no using of other classes - Done
     * no methods, and not main or interface/abstract
     * less than 10 lines and is not a main (probably could be refactored)
     *
     * Common functionality? -> similar number of lines, calling the same methods and the same classes. Could be instace of lazy class
     *
     * Need to determine the amount of times used - easy usedClass
     * Need to determine amount of times it uses someone else- use totalClassMethodCalls to see how many times a class calls another class.
     * Also parse through methods via the instructions and see if one of the class names show, not its own shows up.
     */
}
