package com.CodeSmell;

public class Pair<F, S> {
	
	public final F first;
	public final S second;
	
	Pair(F first, S second) {
		this.first = first;
		this.second = second;
	}
}