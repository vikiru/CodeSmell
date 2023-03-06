package com.testproject;

public class FeatureEnvied {

    private int anInt;
    public final String aString;

    public FeatureEnvied(int n){
        this.anInt = n;
        this.aString = "A string with a number: " + n;
    }

    public int getAnInt(){
        return anInt;
    }

    public int complexMethod(int x, int y){
        return anInt * (x + y);
    }


}