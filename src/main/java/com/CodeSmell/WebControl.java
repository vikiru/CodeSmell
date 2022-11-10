package com.CodeSmell;

import javafx.scene.web.WebEngine;
import com.CodeSmell.UMLClass;
import com.CodeSmell.RenderEvent;
import com.CodeSmell.ClassRelation;
import com.CodeSmell.Pair;
// todo: support this in maven run configuration
//import com.sun.webkit.dom.JSObject;

public class WebControl implements RenderEventListener {

    private WebEngine engine;

    WebControl(WebEngine engine) {
    	this.engine = engine;
    }


    private Integer renderClass(UMLClass c) {
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

        return drawClassBox(0, 0, width, height, c.name);
    }

    private Integer renderPath(ClassRelation cr) {
        // render a path and return the number after the 
        // 'P' in the path's element's id
        return 0;
    }

    private Integer drawClassBox(int x, int y, int width, int height, String name) {
        // draws a box and returns its id
        String js = String.format("renderClassBox(%d, %d, %d, %d, \"%s\");", 
                x, y, width, height, name);
        return (Integer) this.engine.executeScript(js);
    }

    private void repositionClass(int id, double x, double y) {
        // reposition a box with given id 
        // todo: make considerations for dpi-scaling solution
        String js = String.format("repositionClass(%d, %f, %f);", 
                id, x, y);
        this.engine.executeScript(js);
    }

    private Pair<Double, Double> getClassDimensions(int id) {
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
        String getWidth = String.format("getClassWidth(%d);", id);
        String getHeight = String.format("getClassHeight(%d);", id);
        String w, h;
        w = (String) this.engine.executeScript(getWidth);
        h =  (String) this.engine.executeScript(getHeight);
        return new Pair(Double.parseDouble(w.substring(0, w.length() - 2)), 
                 Double.parseDouble(h.substring(0, h.length() - 2)));
    }

    public void renderEventPerformed(RenderEvent e) {
        Object source = e.source; 
        if (( source instanceof RenderObject ) == false) {
            throw new RuntimeException("Bad RenderEvent dispatcher.");
        }
        if (e.type == RenderEvent.Type.RENDER) {
            if ( source instanceof UMLClass) {
                Integer id = renderClass((UMLClass) source);
                Pair<Double, Double> size = getClassDimensions(id);
                Pair<Integer, Pair<Double, Double>> p = new Pair(id, size);
                // add id and size to response
                e.setResponse((Object) p);
            } else {
                Integer id = renderPath((ClassRelation) source);
                e.setResponse((Object) id);
            }
        } else if (e.type == RenderEvent.Type.REPOSITION) {
            UMLClass c =  (UMLClass) source;
            repositionClass(c.getId(), c.getPosition().x, c.getPosition().y);
        }
    } 
}