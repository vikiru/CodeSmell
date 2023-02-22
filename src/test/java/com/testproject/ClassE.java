package com.testproject;

public class ClassE {
    public static void main(String[] args) {
        ClassA a = new ClassA();
        ClassD d = new ClassD(a.getB());
    }

    public static class NestedClassE {
        ClassB b;
        ClassD d;

        public void modifyB() {
            b.i = d.getCounter();
        }

        public static class DoubleNestedClassE {
            public int i;
            public int j;
            public int k;
        }
    }
}