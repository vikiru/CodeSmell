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
    ISPClass ispClass;
    ISPClass.ISPClassTwo ispClass2;
    ISPClass.ISPClassThree ispClass3;
    ISPClass.NoneISPClass ispClass4;

    public GodClass(ClassA a1, ClassA a2) {
        this.field2[0] = a1;
        this.field2[1] = a2;
        ISPClass isp =  new ISPClass();
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

    public void ispMethod0() {
        ispClass.blankMethod();
    }

    public void ispMethod01() {
        ispClass.method3();
    }

    public void ispMethod2() {
        ispClass2.blankMethod();
    }

    public void ispMethod22() {
        ispClass2.method3();
    }

    public void ispMethod3() {
        ispClass3.blankMethod();
    }

    public void ispMethod32() {
        ispClass3.method3();
    }


    public void ispMethod4() {
        ispClass4.blankMethod();
    }

    public void ispMethod42() {
        ispClass4.method3();
    }

    public void isp1cond() {
        ispClass.conditionalError();
    }

    public void isp2cond() {
        ispClass2.conditionalError();
    }

    public void isp3cond() {
        ispClass3.conditionalError();
    }

    public void isp4cond() {
        ispClass4.conditionalError();
        isp3cond();
        isp2cond(); 
        isp1cond();
        ispMethod42();
        ispMethod32();
        ispMethod3();
        ispMethod0();
        ispMethod01();
        ispMethod2();
        ispMethod22();
    }
}