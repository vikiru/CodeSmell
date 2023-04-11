package com.testproject;

public class FeatureEnvyer {
    private int field1, field2;

    public FeatureEnvyer(int a, int b){
        field1 = a;
        field2 = b;
    }
    public int getField1() {
        return field1;
    }

    public int getField2() {
        return field2;
    }

    public void setField2(int field2) {
        this.field2 = field2;
    }

    public int useEnvied1(){
        FeatureEnvied n = new FeatureEnvied(this.field1);

        return n.complexMethod(field1, field2);
    }

    public String envyString(){
        int envyNumber = this.useEnvied1();
        envyNumber = new FeatureEnvied(envyNumber).getAnInt() + 2;
        return new FeatureEnvied(envyNumber).aString;
    }
}