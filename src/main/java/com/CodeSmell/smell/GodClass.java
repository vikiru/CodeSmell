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
import java.util.LinkedList;

public class GodClass extends Smell {

	public CodeFragment lastDetection;
	private RelationSorter relationSorter;
	private ContentSorter contentSorter;
	
	// Smell triggers for class when class has a larger proportion of instruction
	// characters with respect ot the hold set of classes than the given Threshold
	private final double contentThreshold;
	
	// Smell triggers for class when class has a larger proportion of relations
	// formed between it and other classes than the given Threshold
	private final double relationThreshold;
		
	// classes must be at least this many lines of code for the
	// GodClass smell to be triggered 
	private final int minLineCount;

	// classes exceeding this many lines will always be labeled as a godclass,
	// regardless of overall project size
	private final int maxLineCount; 

	public LinkedList<CodeFragment> detections;


	@Override
	public LinkedList<CodeFragment> getDetections() {
		return this.detections;
	}
	/**
	 * Create a new GodClass object with the given parameters.
	 * For all parameters, negative number will result in using
	 * default fallback values.
	 */
	public GodClass(CodePropertyGraph cpg, double contentThreshold,
			double relationThreshold, int minLineCount, int maxLineCount) {
		super("God Class", cpg);
		HashMap<CPGClass, CPGClass[]>  classes = collapseNestedClasses(cpg.getClasses());
		this.relationSorter = new RelationSorter();
		this.relationSorter.sortNested(classes);
		this.contentSorter = new ContentSorter();
		this.contentSorter.sortNested(classes);
		this.contentThreshold = (contentThreshold < 0) ? 0.4 : contentThreshold;
		this.relationThreshold = (relationThreshold < 0) ? 0.6 : relationThreshold;
		this.minLineCount = (minLineCount < 0) ? 40 : minLineCount;
		this.maxLineCount =  (maxLineCount < 0) ? 600 : maxLineCount;
	}


	/**
	 * Create a new GodClass object with the given parameters.
	 * For all parameters, negative number will result in using
	 * default fallback values.
	 */
	public GodClass(CodePropertyGraph cpg) {
		this(cpg, -1.0, -1.0, -1, -1);
	}


	public String description() {
		return "A class that has too many responsibilities, or is just too large.";
	}

	public CodeFragment detectNext() {
		if (detections == null) {
			this.detections = detectAll();
		}
		return detections.poll();
	}

	public LinkedList<CodeFragment> detectAll() {
		if (this.lastDetection != null) {
			return null;
		}
		LinkedList<CodeFragment> godClasses = new LinkedList();
		
		for (int i=contentSorter.itemCount()-1; i >= 0; i--) {
			CPGClass c =  contentSorter.getKey(i);
			int lineCount = contentSorter.getVal(i);
			System.out.print(c + ":  line count: " + lineCount);

			if (lineCount < minLineCount) {
				System.out.println("");
				continue;
			}

			String description = "";

			if (lineCount > maxLineCount) {
				description += String.format(
					"%s exceeds max line count (%d lines)",
					c.name, lineCount);
			}
			
			double proportion = ((float) lineCount / contentSorter.getTotal());
			if (proportion > contentThreshold) {
				description += String.format(
					"%s contains %f%% of the instructions (%d lines)",
					c.name, proportion * 100, lineCount);
			}

			int relationIndex =  relationSorter.getIndex(c);
			int relationSize = relationSorter.getVal(relationIndex);
			proportion = ((float) relationSize / relationSorter.getTotal());
			System.out.println(" :  relation proportion: " + proportion);
			if (proportion > relationThreshold) {
				description += String.format(
					"%s contains %f%% of all relations (%d)",
					c.name, proportion * 100, relationSize);
			}

			if (description.equals("")) {
				continue;
			}
			godClasses.add(CodeFragment.makeFragment(description, c));
		}
		
		return godClasses;
	}

}
