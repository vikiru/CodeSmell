package com.CodeSmell;

import javafx.scene.web.WebEngine;
import com.CodeSmell.UMLClass;
// todo: support this in maven run configuration
//import com.sun.webkit.dom.JSObject;

public class WebControl {

    private WebEngine engine;

    WebControl(WebEngine engine) {
    	this.engine = engine;
    }

    private Integer drawBox(int x, int y, int width, int height) {
        // draws a box and returns its id
        String js = String.format("drawBox(%d, %d, %d, %d);", 
                x, y, width, height);
        return (Integer) this.engine.executeScript(js);
    }

    public void repositionClass(int id, int x, int y) {
        // reposition a box with given id 
        // todo: make considerations for dpi-scaling solution
        String js = String.format("repositionBox(%d, %d, %d);", 
                id, x, y);
        this.engine.executeScript(js);
    }

    public int renderClass(UMLClass c) {
        // renders a class box at the origin and return its id
        String[] methods = c.getMethods();

        // start with a default width
        // todo: replace this with a configurable constant
        // once we have a class for UI parameters
        int width = 200;

        // for now assume each method will take
        // up 20 pixels worth of line height
        int height = 25 * methods.length;
        // later this value would not be calculated,
        // it would be read from the DOM
        // after everything which needs to be drawn
        // to the class box is drawn.

        return drawBox(0, 0, width, height);
    }

    public int[] getClassDimensions(int id) {
        /*
        Gets the dimensions (width, height) in units
        (the unit scale may have to be converted to be proportional to
        how the classes were sized in the layout manager)
        */

        // commented out and replaced with work around
        // gets an array containing a classes width and height
        // String js = String.format("boxDimensions(%d);", id);
        // String this.engine.executeScript(js);
        // JSObject box = (JSObject) this.engine.executeScript(js);
        // return new int[] {box.getMember("width"), 
        //       box.getMember("height")};
        String getWidth = String.format("boxDimW(%d);", id);
        String getHeight = String.format("boxDimH(%d);", id);
        String w, h;
        w = (String) this.engine.executeScript(getWidth);
        h =  (String) this.engine.executeScript(getHeight);
        return new int[] { Integer.parseInt(w.substring(0, w.length() - 2)), 
                 Integer.parseInt(h.substring(0, h.length() - 2)) };
    }
}