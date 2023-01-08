package com.CodeSmell.packa;


import java.util.ArrayList;

public class ClassA {

	private ArrayList<Integer> arr;

	protected void setter(ArrayList arr)	{
		//if (this.arr != null) { Not needed becausethis is protected
		//            // only classTest ( the parser) can access it!
		//	throw new RuntimeException("cannot be reset");
		//}
		this.arr = arr;
	}

	public ArrayList<Integer> getter() {
		return new ArrayList(arr);
	}
}