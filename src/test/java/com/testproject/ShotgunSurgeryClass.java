package com.testproject;

public class ShotgunSurgeryClass {
    private void internal1(){
        System.out.println("foo");
    }

    private int internal2(int n){
        return n+2;
    }

    private String internal3(){
        return "bar";
    }

    /** Calls ten different functions and six different classes, including itself.
     *
     */
    public void basicShotgunMethod(){

        ClassA a = new ClassA();
        a.addB(new ClassB(4));
        ClassD d = new ClassD(a.getB());
        d.doThing();
        ClassE e = new ClassE();
        GodClass g = new GodClass(new ClassA(), new ClassA());
        if(g.compareAs() && g.compareB()){
            System.out.println("foobar");
        }

    }
}
