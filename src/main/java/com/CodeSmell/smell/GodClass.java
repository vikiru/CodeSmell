package com.CodeSmell;

import com.CodeSmell.CodePropertyGraph;
import com.CodeSmell.CPGClass;
import com.CodeSmell.CPGClass.*;
import com.CodeSmell.smell.Smell;
import com.CodeSmell.smell.Smell.CodeFragment;
import static com.CodeSmell.smell.Common.*;
import com.CodeSmell.smell.Common.*;
import com.CodeSmell.Pair;

import java.util.HashMap;
import java.util.LinkedList;

public class GodClass extends Smell {

	public CodeFragment lastDetection;
	private RelationSorter relationSorter;
	private ContentSorter contentSorter;
	
	// Smell triggers for class when class has a larger proportion of instruction
	// characters with respect ot the hold set of classes than the given threshhold
	private final double INSTRUCTION_PROPORTION_THRESHHOLD = 0.4;
	
	// Smell triggers for class when class has a larger proportion of relations
	// formed between it and other classes than the given threshhold
	private final double RELATION_PROPORTION_THRESHHOLD = 0.6;
		
	// classes must be at least this many lines of code for the
	// GodClass smell to be triggered 
	private final int MIN_LINE_COUNT = 350;

	// classes exceeding this many lines will always be labeled as a godclass,
	// regardless of overall project size
	private final int MAX_LINE_COUNT = 1500; 

	public LinkedList<CodeFragment> detections; 

	GodClass(CodePropertyGraph cpg) {
		super("God Class", cpg);
		HashMap<CPGClass, CPGClass[]>  classes = collapseNestedClasses(cpg.getClasses());
		this.relationSorter = new RelationSorter();
		this.relationSorter.sortNested(classes);
		this.contentSorter = new ContentSorter();
		this.contentSorter.sortNested(classes);
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
		if (this.lastDetection == null) {
			LinkedList<CodeFragment> godClasses = new LinkedList();
			
			for (int i=contentSorter.itemCount()-1; i >= 0; i--) {
				CPGClass c =  contentSorter.getKey(i);
				int lineCount = contentSorter.getVal(i);

				if (lineCount < MIN_LINE_COUNT) {
					continue;
				}

				String description = "";

				if (lineCount > MAX_LINE_COUNT) {
					description += String.format(
						"%s exceeds max line count (%d lines)",
						c.name, lineCount);
				}

				double proportion = lineCount / contentSorter.getTotal();
				if (proportion > INSTRUCTION_PROPORTION_THRESHHOLD) {
					description += String.format(
						"%s contains %f%% of the instructions (%d lines)",
						c.name, proportion * 100, lineCount);
				}

				int relationIndex =  relationSorter.getIndex(c);
				int relationSize = relationSorter.getVal(relationIndex);
				proportion = relationSize / relationSorter.getTotal();
				if (proportion > RELATION_PROPORTION_THRESHHOLD) {
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
		return null;
	}

}
