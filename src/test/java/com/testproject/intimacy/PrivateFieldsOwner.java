package com.testproject.intimacy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class PrivateFieldsOwner {
    private final List<String> names;
    private final int count;
    private final boolean truth;
    private final Map<Integer, Integer> counts;

    public List<String> getNames() {
        return names;
    }

    public int getCount() {
        return count;
    }

    public boolean getTruth() {
        return truth;
    }

    public Map<Integer, Integer> getCounts() {
        return counts;
    }

    public PrivateFieldsOwner() {
        this.names = new ArrayList<>();
        this.count = 0;
        this.truth = true;
        this.counts = new HashMap<>();
    }

}
