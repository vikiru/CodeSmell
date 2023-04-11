package com.testproject.TestPackage2;

import com.testproject.testPackage1.ClassF;
import com.testproject.testPackage1.ClassG;

public class ClassI {
   ClassF f;
   ClassG g;
   ClassH h;

    public ClassI() {
        this.f = new ClassF(0);
        this.g = new ClassG("Test");
        this.h = new ClassH();
    }

    public void F()
    {
        f.increment();
    }

    public void F2()
    {
        f.increment();
    }

    public void gGet()
    {
        String test = g.getG();
    }

    public void gSet()
    {
        g.setG("New String");
    }

    public void H()
    {
        h.addToArray(0);
    }

}
