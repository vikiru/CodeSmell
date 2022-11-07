package com.CodeSmell;

import java.util.EventObject;

import com.CodeSmell.UMLClass;

public class RenderEvent extends EventObject {

	public enum Type {
		RENDER,
		REPOSITION,
		RENDER_CONNECTIONS,
	}

	public final Type type;
	private Object response;

	RenderEvent(Type type, UMLClass source) {
		super(source);
		this.type = type;
	}

	public void setResponse(Object response) {
		this.response = response;
	}

	public Object getResponse() {
		return this.response;
	}
}