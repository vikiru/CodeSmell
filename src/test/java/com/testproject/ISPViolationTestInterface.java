package com.testproject;

public interface ISPViolationTestInterface {

	//
	public void methodWithError();

    // if a blank method
    public void blankMethod();

	//
    // ISPClass will throw an error
    // in the next method condtionally.
    // 
    // The static analyzer is expected to think
    // this is counts as actually "implementing"
    // behaviour instead of bypassing the function
    // 
    public void conditionalError(); 
}