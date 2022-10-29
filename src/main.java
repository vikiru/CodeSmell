
import java.util.ArrayList;
import java.awt.List;

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

class LayoutEngine {
    public static ArrayList<Rectangle> determineLayout(Iterable<IntPair> classes, 
            Iterable<IntPair> connections) {
        for (IntPair c : classes) {
            System.out.println(c);
        }
        Rectangle r = new Rectangle(0, 0, 0, 0);
        ArrayList al = new ArrayList<Rectangle>();
        al.add(r);
        return al;
    }
}

class Main {
    public static void main(String[] args) {
        System.out.println("Calling Layout Engine"); 
        IntPair class1_size = new IntPair(10, 10);
        IntPair class2_size = new IntPair(10, 10);
        ArrayList classes = new ArrayList<IntPair>();
        classes.add(class1_size);
        classes.add(class2_size);
        System.out.println(LayoutEngine.determineLayout(classes, classes));
        System.out.println("Done");
    }

}