package com.x.cloudnext.server.drive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class NaturalOrderTest {

    @Test
    void sortsNumericFileNamesByNumericSegments() {
        List<String> names = new ArrayList<>(Arrays.asList("12", "2", "10", "1", "3"));
        names.sort(NaturalOrder.comparator());
        assertEquals(Arrays.asList("1", "2", "3", "10", "12"), names);
    }

    @Test
    void sortsChineseNamesWithEmbeddedNumbersNaturally() {
        List<String> names = new ArrayList<>(Arrays.asList("档案10", "档案2", "档案1"));
        names.sort(NaturalOrder.comparator());
        assertEquals(Arrays.asList("档案1", "档案2", "档案10"), names);
    }
}
