package com.CodeSmell;

import java.util.EventObject;

import com.CodeSmell.RenderObject;

public class RenderEvent {

	public enum Type {
		RENDER,
		REPOSITION
	}

	public final Type type;
	public final RenderObject source;
	private Object response;

	RenderEvent(Type type, RenderObject source) {
		this.source = source;
		this.type = type;
	}

	public void setResponse(Object response) {
		this.response = response;
	}

	public Object getResponse() {
		return this.response;
	}

	public void dispatch() {
		this.source.dispatch(this);
	}
}