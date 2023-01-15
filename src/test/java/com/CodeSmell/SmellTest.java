package com.CodeSmell;

import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

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