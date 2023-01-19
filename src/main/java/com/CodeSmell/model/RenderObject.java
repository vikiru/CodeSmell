package com.CodeSmell.model;

import java.util.ArrayList;

import com.CodeSmell.view.RenderEventListener;
import com.CodeSmell.model.RenderEvent;
import com.CodeSmell.Shape;

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

	public void render() {
		// renders an object (ignores the UI response)

		RenderEvent re = new RenderEvent(RenderEvent.Type.RENDER, this);
		dispatchToRenderEventListeners(re);
	}
}