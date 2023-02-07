package com.testproject;


public class GodClass {
    ClassA field1;
    ClassA[] field2 = new ClassA[2];
    ClassB field3;
    InterfaceC field4;
    ClassD[] field5;
    ClassE[] field6;
    ClassD field7;
    ClassD field8;
    ClassD field9;
    ClassD field10;
    ClassD field11;
    ClassD field12;

    public GodClass(ClassA a1, ClassA a2) {
        this.field2[0] = a1;
        this.field2[1] = a2;
    }

    public boolean compareAs() {
        for (ClassA a : field2) {
            if (a == this.field1) {
                return true;
            }
        }
        return false;
    }

    public boolean compareB() {
        for (ClassA a : field2) {
            if (a.getB() == this.field3 && this.field1 != a) {
                return true;
            }
        }
        this.field4.doThing();
        return false;
    }
}