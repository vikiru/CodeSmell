package com.CodeSmell;

import java.util.ArrayList;

import com.CodeSmell.RenderEventListener;
import com.CodeSmell.RenderEvent;

public abstract class RenderObject {

	private static ArrayList<RenderEventListener> renderEventListeners = new ArrayList<RenderEventListener>();

	public static void addRenderEventListener(RenderEventListener rel) {
		renderEventListeners.add(rel);
	}

	public void dispatchToRenderEventListeners(RenderEvent re) {
		for (RenderEventListener rel : renderEventListeners) {
			rel.renderEventPerformed(re);
		}
	}
}