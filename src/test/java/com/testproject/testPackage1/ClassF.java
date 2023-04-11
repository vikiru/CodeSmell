package com.testproject.testPackage1;

public class ClassF {
    int counter = 0;

    public ClassF(int counter) {
        this.counter = counter;
    }

    public int getF() {
        return counter;
    }

    public void setF(int f) {
        this.counter = f;
    }

    public void increment()
    {
        counter++;
    }

    public void decrement()
    {
        counter--;
    }
}
