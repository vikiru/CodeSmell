package com.CodeSmell;

import java.util.ArrayList;
import org.junit.Test;
import com.CodeSmell.packa.ClassTest;

//todo use JavaFx, TestFX for this.
public class MainAppTest
{
    @Test
    public void testMainApp() {
        ClassTest t = new ClassTest();
        System.out.println("result " + t.doTest());
        //ArrayList arr =new ArrayList<Integer>();
        //arr.add();
        //t.a.setter(arr); //should crash
        System.out.println(t.a.getter());
    }
}
