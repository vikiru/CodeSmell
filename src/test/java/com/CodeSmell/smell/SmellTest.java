package com.CodeSmell.smell;

import com.CodeSmell.parser.*;
import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;

public class SmellTest {

    private final String jsonPath = "src/main/python/joernFiles/sourceCode.json";
    private Parser p;
    private Gson gson;

    @Before
    public void before() {
        p = new Parser();
        gson = new Gson();
    }

    @Test
    public void TestGodClass() {
    }
}