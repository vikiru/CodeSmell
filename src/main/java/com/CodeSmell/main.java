package com.CodeSmell;

import java.util.ArrayList;

class IntPair {
    public int p1;
    public int p2;

    public IntPair(int p1, int p2) {
        this.p1 = p1;
        this.p2 = p2;
    }
}

class Rectangle {
    public IntPair size;
    public IntPair pos;

    public Rectangle(int x, int y, int width, int height) {
        IntPair size = new IntPair(width, height);
        IntPair pos = new IntPair(x, y);
        this.size = size;
        this.pos = pos;
    }
}

class Layout {
    private ArrayList<Rectangle> classes;
    private ArrayList<ArrayList<IntPair>> connectionArrowRoutes;

    public Layout() {
        ArrayList classes = new ArrayList<Rectangle>();
        ArrayList connectionArrowRoutes = new ArrayList<ArrayList<IntPair>>();
    }

    public void addClass(Rectangle classRectangleBounds) {
        classes.add(classRectangleBounds);
    }

    /** 
     * adds an element to the list of arrows connecting showing relationships
     * (multiplicity/inheritence/dependence) between classes 
     * 
     * @param connectionRoute a list of points, (x, y) coordinate pairs,
     * which a given multiplicity connection line will run through
     */
    public void addConnection(IntPair connecting, ArrayList<IntPair> connectionRoute) {
        connectionArrowRoutes.add(connectionRoute);
    }
}

class LayoutEngine {

    /** 
     * Generates a layout for the main gui. Determines the position for
     * a bunch of rectangles that represent classes of known sizes.
     * @param classes A list of rectangles represented by (width, height) pairs
     * @param connections A list of which connections (i.e, multiplicity relationships)
     * that exist between each class.
     * 
     * @return a layout class whose iterable elements represent the same
     * items that were passed to this function in order
     * */
    public static Layout determineLayout(Iterable<IntPair> classes, 
            Iterable<IntPair> connections) {
        for (IntPair c : classes) {
            System.out.println(c);
        }
        Layout layout = new Layout();
        return layout;
    }
}

class Main {
    public static void main(String[] args) {
        System.out.println("Calling com.CodeSmell.Layout Engine");
        IntPair class1_size = new IntPair(10, 10);
        IntPair class2_size = new IntPair(10, 10);
        ArrayList classes = new ArrayList<IntPair>();
        classes.add(class1_size);
        classes.add(class2_size);
        System.out.println(LayoutEngine.determineLayout(classes, classes));
        System.out.println("Done");
    }

}