package com.CodeSmell.smell;

import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.stat.ClassStat;
import com.CodeSmell.stat.MethodStat;
import com.CodeSmell.stat.StatTracker;
import javafx.util.Pair;

import java.util.*;

/**
 * A Lazy Class is a class that is under used. This simply makes the code base unnecessarily complicated.
 * It could be a result of a class that was refactored and had a lot of its functionality removed, or result
 * of a class that was supposed to be developed in the future but was not worked on.
 * <p>
 * Ways to detect Lazy Class via the Code Smell.
 * <p>
 * Algorithm:
 * 0. Abstract classes/interfaces could be considered if there are not ever implemented
 * 0.1 A class that's not connected to any other
 * 1. Gather a list of all the methods in the class
 * 2. See if those methods call other methods (If these methods call other methods it is likely this class is doing something.
 * Need to think about a threshold)
 * 3. More importantly, scan all other classes and see how many times the methods in those classes called the methods
 * in the suspected lazy class. If the class does not call, and is not called a lot it may be lazy.
 * 3.1 Look at similarilty in instrcutions and see if two methods are doing the same thing. (Method stat.unique instruction For every method
 * compare unique instructions and retain all, see if passes threshold.
 * 4. The user can only ever be offered a suggestion as to if the class is lazy, I.E check this out, may be lazy. Please decide if can be
 * refactored into another class.
 * <p>
 * <p>
 * Get forward connections, now have record of all connection and can use it to figure out which classes are used and which are not, also how many times
 */
public class LazyClass extends Smell {

    public static StatTracker stats;
    ArrayList<CPGClass> lazyClasses = new ArrayList<>();
    HashMap<CPGClass, CPGClass> lazySharedMethods = new HashMap<>();
    public LinkedList<CodeFragment> detections = new LinkedList<>();

    protected LazyClass(CodePropertyGraph cpg) {
        super("Lazy Class", cpg);
        stats = Common.stats;
        lazyClasses = returnLazyClasses();
        lazySharedMethods = checkSimilarInstructions();
        System.out.println();
    }

    private void tallyRelations() {
        for (CPGClass aClass : super.cpg.getClasses()) {
            int timesCalling = 0;
            int timeCalled = 0;

            for (CPGClass.Method method : aClass.getMethods()) {
                for (CPGClass.Method calledMethod : method.getMethodCalls()) {
                    timesCalling++;
                    int index = 0;
                    for (CPGClass calledClass : super.cpg.getClasses()) {
                        index++;
                        if (calledClass.name.equals(calledMethod.parentClassName)) {
                            break;
                        }
                    }
                    timeCalled = super.cpg.getClasses().get(index - 1).getTimesCalled() + 1;
                    super.cpg.getClasses().get(index - 1).setTimesCalled(timeCalled);
                }

            }
            aClass.setTimesCalling(timesCalling);
        }
    }

    public ArrayList<CPGClass> returnLazyClasses() {

        HashMap<CPGClass, Pair<Integer, Integer>> usesAndUsages = new HashMap<>();

        //Get the number of times it uses other classes
        for (ClassStat classStat : Common.stats.classStats.values()) {
            int uses = 0;
            for (Map.Entry<CPGClass, Integer> calledClasses : classStat.totalClassMethodCalls.entrySet()) {
                uses += calledClasses.getValue();
            }
            usesAndUsages.put(classStat.cpgClass, new Pair<>(uses, classStat.classUsage));
        }

        //If the class is not called and is not calling any other class it can be considered lazy as it
        //serves no use and should be considered for a refactor
        for (Map.Entry<CPGClass, Pair<Integer, Integer>> classesEntry : usesAndUsages.entrySet()) {
            if (classesEntry.getValue().getKey() == 0 && classesEntry.getValue().getValue() == 0) {
                lazyClasses.add(classesEntry.getKey());
            }
        }

        //Run for loop inside the other with the classes, check to make sure class names aren't same
        //Then get method intructions and compare agaisnt each other, if a set of instructions are the same add to a set
        //If the set is large enough then
        return lazyClasses;
    }

    @Override
    public CodeFragment detectNext() {
        detections.add(new CodeFragment(null, (CPGClass[]) lazyClasses.toArray(), null, null, null, null, null));
        return new CodeFragment(null, (CPGClass[]) lazyClasses.toArray(), null, null, null, null, null);
    }

    public CodeFragment detectNextSimilarities() {
        CPGClass[] classesWithSimilarMethods = {(CPGClass) lazySharedMethods.keySet().toArray()[0], (CPGClass) lazySharedMethods.values().toArray()[0]};
        lazySharedMethods.remove(lazySharedMethods.keySet().toArray()[0]);
        return new CodeFragment(null, classesWithSimilarMethods, null, null, null, null, null);
    }

    @Override
    public String description() {
        tallyRelations();
        return "";
    }

    @Override
    public LinkedList<CodeFragment> getDetections() {
        return detections;
    }


    private HashMap<CPGClass, CPGClass> checkSimilarInstructions() {
        HashMap<CPGClass, CPGClass> lazySharedMethods = new HashMap<>();
        for (MethodStat methodStat : Common.stats.methodStats.values()) {
            for (MethodStat otherMethodStat : Common.stats.methodStats.values()) {

                if (otherMethodStat.equals(methodStat)) {
                    continue;
                } else {
                    for (CPGClass.Method.Instruction instructions : methodStat.method.instructions) {
                        int sameLines = 0;
                        for (CPGClass.Method.Instruction otherInstructions : otherMethodStat.method.instructions) {
                            if (otherInstructions.equals(instructions)) {
                                sameLines++;
                            }
                        }
                        if (otherMethodStat.method.instructions.size() > 0) {
                            if (((float) (sameLines / otherMethodStat.method.instructions.size())) > 0.75) {
                                lazySharedMethods.put(otherMethodStat.method.getParent(), methodStat.method.getParent());
                            }
                        }
                    }
                }
            }
        }

        return lazySharedMethods;
    }

}
