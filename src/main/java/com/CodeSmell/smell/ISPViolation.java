package com.CodeSmell.smell;

import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CPGClass.Method.*;
import com.CodeSmell.parser.CPGClass.*;
import com.CodeSmell.smell.Smell;
import com.CodeSmell.smell.Smell.CodeFragment;

import static com.CodeSmell.smell.Common.*;

import com.CodeSmell.smell.Common.*;
import com.CodeSmell.model.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.ArrayList;
import java.util.Map;

/*
	An ISP (Interface Segregation Principle) Violation
	occures when a class implementing an interface fails
	to provide proper definitions for all methods.

	Typically, when this happens, subsets of methods
	which are not properly implemented are common to 
	many implementors. 

	This implies implementors should be grouped together,
	either under a seperate interface or through some other
	layer of indirection/abstraction. 

	Each of these groupings (a set of implementors, plus the
	subset of methods of the interface they implement) 
	constitue one seperate detection

*/

public class ISPViolation extends Smell {

    // a set of interfaces along with the classes that implement them
    Iterator<Map.Entry<CPGClass, ArrayList<CPGClass>>> interfaces;

    // maps methods such that isNotImplemented(m) == true for each
    // Method key object m
    private HashMap<Method, ArrayList<CPGClass>> segregations;

    private Iterator<CodeFragment> lastBatch;


    public String description() {
        return "Implementors of an interface selectively fail to realize" +
                " all defined methods";
    }

    public ISPViolation(CodePropertyGraph cpg) {
        super("Interface Segregation Principle (ISP) Violation", cpg);
        this.interfaces = Common
                .interfaces
                .entrySet()
                .iterator();
        this.lastBatch = new ArrayList<CodeFragment>().iterator();
    }


    private boolean isNotImplemented(Method m) {
        // returns true if a method is not implemented

        //   one of two conditions must be met in order
        //   for a method to be considered unimplemented
        //
        //   1.) The method is either blank
        //   2.) The method throws an error
        //   declaration unconditionally

        if (getMethodStats(m).uniqueInstructions.size() == 0) {
            return true;
        }
        boolean foundControlStructure = false;
        for (Instruction i : m.instructions) {
            if (!foundControlStructure && 
                    i.label.equals("CONTROL_STRUCTURE")) {
                return false;
            } 

            if (!foundControlStructure) {
                if (i.label.equals("CALL") && 
                        i.code.startsWith("throw")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsViolation(
            CPGClass iface,
            CPGClass[] implementors) {

        for (CPGClass c : implementors) {
            Method[] ifaceMethods = Common.interfaceMethods(c);
            for (Method m : ifaceMethods) {
                if (isNotImplemented(m)) {
                    Method m2 = iface.getMethods()
                            .stream()
                            .filter(m3 -> m3.name.equals(m.name))
                            .findFirst()
                            .orElseThrow(RuntimeException::new);
                    ArrayList<CPGClass> arr = this.segregations
                            .getOrDefault(m2, new ArrayList<>());
                    System.out.print(m2 + " is not implemented");
                    System.out.println(" within " + m.getParent());
                    arr.add(c);
                    this.segregations.put(m2, arr);
                }
            }
        }
        return this.segregations.size() != 0;
    }

    public CodeFragment detectNext() {

        // detections for one interface
        // are computed one at a time but
        // must be returned seperately to do
        // interface design

        if (this.lastBatch.hasNext()) {
            return this.lastBatch.next();
        }

        this.segregations = new HashMap<>();
        while (this.interfaces.hasNext()) {
            Map.Entry<CPGClass, ArrayList<CPGClass>> iface = this.interfaces.next();
            CPGClass[] implementors = iface.getValue().toArray(new CPGClass[0]);
            if (containsViolation(iface.getKey(), implementors)) {
                System.out.println(iface.getKey() + " contains violation");
                return processDetection(iface.getKey(), implementors.length);
            }
        }
        return null;
    }

    private static class Segregation {
        public Set<CPGClass> classSet;
        public Set<Method> methodSet;

        @Override
        public String toString() {
            return "Methods " + methodSet.toString() + "\n" +
                "Classes " + classSet.toString() + "\n"; 
        }
    }

    private CodeFragment processDetection(CPGClass iface, int numImplementors) {
        // processes a batch of detections, using
        // this.segregations to build an iterator of CodeFragments,
        // returning the first element of the iterator and saving
        // the iterator to the lastBatch field

        // each element comprises one detection.
        ArrayList<Segregation> refinedSegregations = new ArrayList<>();

        Set<CPGClass> allNonImplementingClasses = new HashSet<>();
        for (Method m : this.segregations.keySet()) {
            allNonImplementingClasses.addAll(this.segregations.get(m));
        }

        // group sets of classes such that
        // all classes have usable implementations
        // for the same subset of methods of the interface
        // which they implement

        // pass 1
        // map methods to the list of classes which implement them

        for (Method m : this.segregations.keySet()) {
            ArrayList<CPGClass> nonImplementingClasses = this.segregations.get(m);
            Set<CPGClass> implementingClasses = new HashSet<>();
            implementingClasses.addAll(allNonImplementingClasses);
            implementingClasses.removeAll(nonImplementingClasses);
            Segregation s = new Segregation();
            s.classSet = implementingClasses;
            s.methodSet = new HashSet<>();
            s.methodSet.add(m);
            refinedSegregations.add(s);

        }

        // pass 2
        // if any sets of classes are the same, group the two Segregations together

        for (Segregation s : refinedSegregations) {
            refinedSegregations.stream()
                    .filter(seg -> seg
                        .classSet
                        .containsAll(
                            Set.of(s.classSet)) && seg != s)
                    .findFirst()
                    .ifPresent(seg -> seg.methodSet.addAll(s.methodSet));
        }


        ArrayList<CodeFragment> lastBatch = new ArrayList<>();

        refinedSegregations.forEach((seg) -> {

            // if the method is not implemented by anything
            // then this should be another smell (i.e, dead code)
            if (seg.classSet.size() != 0) {


                String description = String.format(
                        "Move methods %s into new interface with classes %s",
                        seg.methodSet, seg.classSet.toArray(new CPGClass[0]));
                lastBatch.add(CodeFragment.makeFragment(
                        description,
                        seg.methodSet.toArray(new Method[0]),
                        seg.classSet.toArray(new CPGClass[0])));
            }
        });

        this.lastBatch = lastBatch.iterator();

        // may be null if item was removed from segregations
        if (this.lastBatch.hasNext()) {
            return this.lastBatch.next();
        }
        return null;    
    }
}