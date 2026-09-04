package com.poorgrammera.bydsubai.tool;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ExternalToolResultsTest {
    @Test
    public void firstItemReadsSuccessfulCollectionAndNumbers() {
        Map<String, Object> item = new HashMap<>();
        item.put("x", "127.1054");
        Map<String, Object> result = new HashMap<>();
        result.put("documents", Arrays.asList(item));
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("result", result);

        Map<String, Object> first = ExternalToolResults.firstItem(response, "documents");

        assertEquals(item, first);
        assertEquals(127.1054, ExternalToolResults.number(first, "x"), 0.000001);
    }

    @Test
    public void firstItemRejectsErrorsAndEmptyCollections() {
        assertNull(ExternalToolResults.firstItem(Collections.singletonMap("status", "error"),
                "documents"));

        Map<String, Object> result = new HashMap<>();
        result.put("documents", Collections.emptyList());
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("result", result);
        assertNull(ExternalToolResults.firstItem(response, "documents"));
    }
}
