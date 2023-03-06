package com.testproject.TestPackage2;

import com.testproject.testPackage1.ClassF;

import java.util.ArrayList;

public class ClassH {
    ArrayList<Integer> h;
    ClassF f;

    public ClassH() {
        h = new ArrayList<>();
       f =  new ClassF(0);
    }

     public void F()
    {
        f.increment();
    }

    public void F2()
    {
        f.increment();
    }

    public void F3()
    {
        f.decrement();
    }

    public void addToArray(int i)
    {
        h.add(i);
    }

    public void removeFromArray()
    {

        h.remove(0);
    }
}
