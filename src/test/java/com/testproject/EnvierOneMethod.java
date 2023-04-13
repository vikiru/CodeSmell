package com.testproject;

public class EnvierOneMethod {
    //Class to test if a class can have feature envy if all the envying only happens from calling one method.
    int attr1;

    public EnvierOneMethod(int a){
        attr1 = a;
    }

    public int getAttr1(){
        return this.attr1;
    }

    public void incrementAttr1(){
        attr1++;
    }

    public int theMethod(){
        FeatureEnvied envied = new FeatureEnvied(attr1);
        envied.complexMethod(attr1, attr1*2);
        for(int a = 0; a < 5; a++){
            envied.complexMethod(attr1, attr1*2);
        }
        this.incrementAttr1();
        envied.complexMethod(attr1, attr1*2);
        return envied.complexMethod(attr1, attr1*2);
    }
}
