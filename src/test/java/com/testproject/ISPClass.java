package com.testproject;

import java.lang.Math;

/**
 * This class defines the test scheme for ISPViolation.
 *  
 * 
 * It contains the following classes:
 * ISPClass, ISPClassTwo, ISPClassThree, NonISPClass
 * 
 * NonISPClass does not violate any ISP.
 * The other three violate ISP.
 * 
 * Each individual detection in ISPViolation 
 * should point to a set of classes which all
 * implement the same subset of methods of an interface.
 * 
 */

public class ISPClass implements ISPViolationTestInterface {

	public void methodWithError() {
		// should triger ISP Violation. This isn't actually
		// implementing a method, but bypassing one.
		throw new RuntimeException("Is ISP Violation");
	}

	public void blankMethod() {}  // should trigger ISP violation

	public void conditionalError() {
		// this is a conditional error
		// it counts as implementing the method  
		if (blackBox()) { throw new RuntimeException("Test"); };
	}

	public void method3() {}


	public static boolean blackBox() {

		// the point of this method is to 
		// make it explicitely impossible
		// for static analysis to determine 
		// which branch of some control structure
		// was taken.

		return Math.random() > 0.5;
	}

	// To ensure ISPViolation.java groups
	// detections correctly, 
	// there needs to be a duplicate class
	// that fails to implement (almost)
	// all methods in the same way ISPClass 
	// does.

	public static class ISPClassTwo implements ISPViolationTestInterface {

		private double f;

		public void methodWithError() {
			throw new RuntimeException("Is ISP Violation 2");
		}

		public void blankMethod() {
			// for this class, blankMethod()
			// is actually not blank.

			// 
			this.f = Math.random();
		}

		public static boolean blackBox() {
			return ISPClass.blackBox();
		}

		public  void method3() {
		}

		public void conditionalError() {
			// this is a conditional error
			// it counts as implementing the method  
			if (blackBox()) { throw new RuntimeException("Test"); };
		}
	}

	// a class that should be grouped
	// along with ISPClassTwo 
	// in terms of suggested segregations

	public static class ISPClassThree implements ISPViolationTestInterface {

		private double f;

		public void methodWithError() {
			throw new RuntimeException("Is ISP Violation 3");
		}

		public void blankMethod() {
			// for this class, blankMethod()
			// is actually not blank.

			this.f = 0.2 + Math.random();
		}

		public static boolean blackBox() {
			return !ISPClass.blackBox();
		}

		public  void method3() {
			System.out.println("implements");
		}

		public void conditionalError() {
			// this is a conditional error
			// it counts as implementing the method  
			if (blackBox()) { throw new RuntimeException("Test"); };
		}
	}

	

	// finally, there is one more class
	// for which ALL methods are properly
	// implemented

	public static class NoneISPClass implements ISPViolationTestInterface {

		private static double f;

		public void methodWithError() {
			NoneISPClass.f += 0.2;
		}

		public void blankMethod() {
			// for this class, blankMethod()
			// is actually not blank.

			this.f = 0.3 + Math.random();
		}

		public static boolean blackBox() {
			return NoneISPClass.f < 0.25 && !ISPClass.blackBox();
		}

		public  void method3() {
			System.out.println("implements");
		}

		public void conditionalError() {
			// this is a conditional error
			// it counts as implementing the method  
			String tryToThrowDetectionOff = "throw";
			if (blackBox()) { throw new RuntimeException("Test"); };
		}
	}
}



// todo: do these later. Edge case is not worth the effort

	
//  public void trickyMethodWithError() {
		// should triger ISP Violation. Tries to 
		// throw detection off by hiding the runtime
		// exception after a conditional
	//	if (true) { throw new RuntimeException("Test"); };
//	}

//	public void otherConditionalError() {

		// another conditional error
		// that uses an attribute to 

//		if (this.stateCheck) { throw new RuntimeException("Test"); };
//	}
