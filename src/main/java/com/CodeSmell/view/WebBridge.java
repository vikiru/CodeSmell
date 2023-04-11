package com.CodeSmell.view;

import com.CodeSmell.model.Pair;
import com.CodeSmell.model.Position;
import com.CodeSmell.model.Shape;
import com.CodeSmell.model.ClassRelation;
import com.CodeSmell.model.RenderEvent;
import com.CodeSmell.model.RenderObject;
import com.CodeSmell.model.UMLClass;
import com.CodeSmell.parser.CPGClass.Attribute;
import com.CodeSmell.parser.CPGClass.Method;
import com.CodeSmell.parser.CPGClass.Modifier;
import com.CodeSmell.smell.Smell;
import javafx.scene.web.WebEngine;

import java.util.ArrayList;

// todo: support this in maven run configuration
//import com.sun.webkit.dom.JSObject;

public class WebBridge implements RenderEventListener {

    private WebEngine engine;

    public WebBridge(WebEngine engine) {
        this.engine = engine;
    }


    private Integer renderClass(UMLClass c) {
        // renders a class box at the origin and return its id

        // draw the box
        //JS formats for a js method call, this calls engine on line 35
        //engine return the result of the html form, as a result of the js function in the markup file
        String js = String.format("renderClassBox('%s');", c.name);
        Integer id = (Integer) this.engine.executeScript(js);
        Integer fieldId = 0;
        Integer smellId = 0;
        ArrayList<String> modStrings = new ArrayList<String>();

        // add the methods
        for (Method m : c.getMethods()) {
            for (Modifier methodModifier : m.modifiers) {
                modStrings.add(methodModifier.modString);
            }
            String modifiers = String.join(" ", modStrings).toLowerCase();
            js = String.format("addField(false, %d, '%s', '%s', '%s');",
                    id, m.name, modifiers, c.name+":"+id+","+m.name+":"+fieldId);
            this.engine.executeScript(js);
            modStrings.clear();
            fieldId++;
        }

        // add the attributes
        for (Attribute a : c.getAttributes()) {
            for (Modifier attributeModifier : a.modifiers) {
                modStrings.add(attributeModifier.modString);
            }
            //Check for equality between a and the class smells list, if they are
            //the same and have a hashmap that maps id(field) to smell, similar for method
            //Add child to the field (smells container -> need to be id
            //field.id = class + classIdNumber + field + fieldIdNumber +
            String modifiers = String.join(" ", modStrings).toLowerCase();
            js = String.format("addField(true, %d, '%s', '%s', '%s');",
                    id, a.name, modifiers, c.name+":"+id+","+a.name+":"+fieldId);
            this.engine.executeScript(js);
            modStrings.clear();
            fieldId++;
        }

        for(Smell smell : c.getSmells())
        {
            for(int i = 0 ; i <smell.getDetections().size(); i++)
            {
                if(smell.getDetections().get(i).classes!=null)
                {
                    js = String.format("addClassSmell(%d, '%s', '%s', '%s');",
                            id, smell.name, smell.description(),  c.name+":"+id+","+smell.name+":"+smellId);
                    this.engine.executeScript(js);
                    smellId++;
                }
            }
        }

        return id;
    }

    private Integer renderPath(ClassRelation cr) {
        // renders the path of a class relation to the
        // browser view and returns the index position
        // of its DOM data

        int classId = cr.source.getId();
        ArrayList<Position> path = cr.getPath();
        // Create the path DOM object and ensure the first 
        // node of the path borders a class object
        String js = String.format("createRelationPath(%d, %f, %f)",
                classId, path.get(0).x, path.get(0).y);
        Integer pathNumber = (Integer) this.engine.executeScript(js);
        if (pathNumber < 0) {
            // the given coordinates cr.position.x, cr.position.y were
            // not inside (or on the edge) of the drawing box
            throw new RuntimeException("Bad starting location for path draw");
        }

        for (Position p : path) {
            this.engine.executeScript(String.format(
                "appendPathNode(%d, %d, %f, %f)", classId, pathNumber, p.x, p.y));
        }
        this.engine.executeScript(String.format(
                "renderPath(%d, %d, \"%s\")", classId, pathNumber, cr.type));
        return pathNumber;
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
        h = (String) this.engine.executeScript(getHeight);
        return new Pair(Double.parseDouble(w.substring(0, w.length() - 2)),
                Double.parseDouble(h.substring(0, h.length() - 2)));
    }

    private void drawShape(Shape s) {
        String js;
        for (Position p : s.vertex) {
            js = String.format("drawDot(%f, %f, \"%s\");",
                    p.x, p.y, s.colour);
            this.engine.executeScript(js);
        }
    }
//THINK ABOUT HOW SMELLS DEFINE IN HERE, POTENTIALLY SEPERATE METHOD TO ADD SMELL TO CLASS
    public void renderEventPerformed(RenderEvent e) {
        Object source = e.source;
        if ((source instanceof RenderObject) == false) {
            throw new RuntimeException("Bad RenderEvent dispatcher.");
        }
        if (e.type == RenderEvent.Type.RENDER) {
            if (source instanceof UMLClass) {
                //FOR LOOP THAT INTERATES THROUH ALL SMELLS IN UMLCLASS
                //LOCAL METHOD ON EACH SMELL
                //LOOK at the ren
                //ID represents the front end obejct
                Integer id = renderClass((UMLClass) source);
                /*if(((UMLClass) source).name.equals("ConstantClass"))
                {
                    ((UMLClass) source).setPosition(100,100);
                }*/
                Pair<Double, Double> size = getClassDimensions(id);
                Pair<Integer, Pair<Double, Double>> p = new Pair(id, size);
                // add id and size to response
                e.setResponse((Object) p);
            } else if (source instanceof ClassRelation) {
                Integer id = renderPath((ClassRelation) source);
                e.setResponse((Object) id);
            } else if (source instanceof Shape) {
                drawShape((Shape) source);
            }
        } else if (e.type == RenderEvent.Type.REPOSITION) {
            UMLClass c = (UMLClass) source;
            repositionClass(c.getId(), c.getPosition().x, c.getPosition().y);
        }
    }

}