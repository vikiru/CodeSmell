package com.testproject;

class EntryPoint {
    public static void main(String[] args) {
        ClassA a1 = new ClassA();
        ClassA a2 = new ClassA();
        GodClass gc = new GodClass(a1, a2);

    }
}