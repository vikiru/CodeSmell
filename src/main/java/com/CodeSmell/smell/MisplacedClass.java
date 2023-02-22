package com.CodeSmell.smell;

import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CodePropertyGraph;
import javafx.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MisplacedClass extends Smell{

    public static StatTracker stats;
    ArrayList<CPGClass> misplacedClasses = new ArrayList<>();
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
            classes[0] = misplacedClasses.remove(0);
            return new CodeFragment("Misplaced Classes",classes,null,null,null,null,null);
        }
        else
            return null;
    }

    public void returnMisplacedClasses()
    {

        HashMap<CPGClass, Pair<Integer,Integer>> packageUsages= new HashMap<>();
        for(Map.Entry<CPGClass, HashMap<CPGClass, Integer>> classes : stats.totalClassMethodCalls.entrySet())
        {
            int classInPackage = 0;
            int classOutPackage = 0;

          for(CPGClass calledClass : classes.getValue().keySet())
          {
              if(calledClass.packageName.equals(classes.getKey().packageName))
              {
                  classInPackage+=classes.getValue().get(calledClass);
              }
              else
              {
                  classOutPackage+=classes.getValue().get(calledClass);
              }
          }
            packageUsages.put(classes.getKey(),new Pair<>(classInPackage,classOutPackage));
        }
        for(Map.Entry<CPGClass, Pair<Integer,Integer>> classesEntry : packageUsages.entrySet())
        {
            if(classesEntry.getValue().getKey()<classesEntry.getValue().getValue())
            {
                misplacedClasses.add(classesEntry.getKey());
            }
        }
    }

    public ArrayList<CPGClass> getMisplacedClasses() {
        return misplacedClasses;
    }

    @Override
    public String description() {
        return null;
    }
}
