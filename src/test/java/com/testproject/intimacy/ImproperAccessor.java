package com.testproject.intimacy;

import com.CodeSmell.smell.InappropriateIntimacy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImproperAccessor {
    public static void main(String[] args) {
        PrivateFieldsOwner privateFieldsOwner = new PrivateFieldsOwner();
        int localCount = privateFieldsOwner.getCount();
        Map<Integer, Integer> counts = privateFieldsOwner.getCounts();
        List<String> names = privateFieldsOwner.getNames();
        boolean truth = privateFieldsOwner.getTruth();
    }
}
