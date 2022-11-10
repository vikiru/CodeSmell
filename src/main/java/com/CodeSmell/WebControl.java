package com.CodeSmell;

import java.util.ArrayList;
import javafx.scene.web.WebEngine;

import com.CodeSmell.UMLClass;
import com.CodeSmell.RenderEvent;
import com.CodeSmell.ClassRelation;
import com.CodeSmell.Pair;

import com.CodeSmell.CPGClass.Method;
import com.CodeSmell.CPGClass.Modifier;
import com.CodeSmell.CPGClass.Attribute;

// todo: support this in maven run configuration
//import com.sun.webkit.dom.JSObject;

public class WebControl implements RenderEventListener {

    private WebEngine engine;

    WebControl(WebEngine engine) {
    	this.engine = engine;
    }


    private Integer renderClass(UMLClass c) {
        // renders a class box at the origin and return its id

        // draw the box
        String js = String.format("renderClassBox(\"%s\");", c.name);
        Integer id = (Integer) this.engine.executeScript(js);
        ArrayList<String> modStrings = new ArrayList<String>();

        // add the methods
        for (Method m: c.getMethods()) {
            for (Modifier m2 : m.modifiers) {
                modStrings.add(m2.name());
            }
            String modifiers = String.join(" ", modStrings).toLowerCase();
            js = String.format("addField(false, %d, \"%s\", \"%s\");",
                    id, m.name, modifiers);
            this.engine.executeScript(js);
            modStrings.clear();
        }

        // add the attributes
        for (Attribute a: c.getAttributes()) {
            for (Modifier m2 : a.modifiers) {
                modStrings.add(m2.name());
            }
            String modifiers = String.join(" ", modStrings).toLowerCase();
            js = String.format("addField(false, %d, \"%s\", \"%s\");", 
                    id, a.name, modifiers);
            this.engine.executeScript(js);
            modStrings.clear();
        }

        return id;
    }

    private Integer renderPath(ClassRelation cr) {
        // render a path and return the number after the 
        // 'P' in the path's element's id
        return 0;
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