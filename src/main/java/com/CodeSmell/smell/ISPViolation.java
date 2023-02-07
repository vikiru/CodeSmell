package com.CodeSmell.smell;

import com.CodeSmell.parser.CodePropertyGraph;
import com.CodeSmell.parser.CPGClass;
import com.CodeSmell.parser.CPGClass.*;
import com.CodeSmell.smell.Smell;
import com.CodeSmell.smell.Smell.CodeFragment;
import static com.CodeSmell.smell.Common.*;
import com.CodeSmell.smell.Common.*;
import com.CodeSmell.model.Pair;

import java.util.HashMap;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.ArrayList;
import java.util.Map;

public class ISPViolation extends Smell {

	// a set of interfaces along with the classes that implement them
	Iterator<Map.Entry<CPGClass, ArrayList<CPGClass>>> interfaces;

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

	// maps methods such that isNotImplemented(m) == true for each
	// Method key object m 
	private HashMap<Method, ArrayList<CPGClass>> segregations;

	private Iterator<CodeFragment> lastBatch;

	private boolean isNotImplemented(Method m) {
		// returns true if a method
		return true;
	}

	private boolean containsViolation(
			CPGClass iface, 
			CPGClass[] implementors) {

		for (CPGClass  c : implementors) {
			Method[] ifaceMethods = Common.interfaceMethods(c);
			for (Method m : ifaceMethods) {
				if (isNotImplemented(m)) {
					ArrayList<CPGClass> arr = this.segregations
						.getOrDefault(m, new ArrayList<>());
					arr.add(c);
				}
			}
		}
		return false;
	}

	public CodeFragment detectNext() {
		if (this.lastBatch.hasNext()) {
			return this.lastBatch.next();
		}

		if (!this.interfaces.hasNext()) {
			return null;
		}
		this.segregations = new HashMap<>();
		Map.Entry<CPGClass, ArrayList<CPGClass>> iface;
		while ((iface = this.interfaces.next()) != null) {
			CPGClass[] implementors = iface.getValue().toArray(new CPGClass[0]);
			if (containsViolation(iface.getKey(), implementors)) {
				return processDetection(implementors.length);
			}
		}
		return null;
	}

	private static class Segregation {
		public Set<CPGClass> classSet;
		public Set<Method> methodSet;
	}

	private CodeFragment processDetection(int numImplementors) {
		// processes a batch of detections, using
		// this.segregations to build an iterator of CodeFragments,
		// returning the first element of the iterator and saving 
		// the iterator to the lastBatch field

		ArrayList<Segregation> refinedSegregations = new ArrayList<>();

		for (Method m : this.segregations.keySet()) {
			ArrayList<CPGClass> effectedClasses = this.segregations.get(m);
			if (effectedClasses.size() >= numImplementors) {
				// if the implementation is ignored for 
				// all implementors then ignore this ISP
				this.segregations.remove(m);
				continue;
			} 

			// group all methods for which this.segregations 
			refinedSegregations.stream()
				.filter( seg -> seg.classSet.containsAll(Set.of(effectedClasses)))
				.findFirst()
				.ifPresent(seg -> seg.methodSet.add(m));				
		}


		ArrayList<CodeFragment> lastBatch = new ArrayList<>();

		refinedSegregations.forEach((seg) -> {
			String description = String.format(
				"Move methods %s into new interface with classes", 
				seg.methodSet, seg.classSet); 
			lastBatch.add(CodeFragment.makeFragment(
				description,
				seg.methodSet.toArray(),
				seg.classSet.toArray()));
		});

		this.lastBatch = lastBatch.iterator();
		
		// may be null if item was removed from segregations
		return this.lastBatch.next();
	}
}