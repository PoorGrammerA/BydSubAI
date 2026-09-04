package com.poorgrammera.subai.tool;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubAiToolNamesTest {
    @Test
    public void createsStableNamespacedGeminiName() {
        assertEquals("ext_kortools_search_places",
                SubAiToolNames.externalFunctionBase(
                        "com.poorgrammera.subai.kortools", "search_places"));
    }

    @Test
    public void validatesLocalNames() {
        assertTrue(SubAiToolNames.isValidLocalName("address_to_coordinates"));
        assertFalse(SubAiToolNames.isValidLocalName("address.to.coordinates"));
        assertFalse(SubAiToolNames.isValidLocalName(""));
    }

    @Test
    public void sanitizesProviderAlias() {
        assertEquals("my_tools", SubAiToolNames.providerAlias("com.example.My-Tools"));
        assertEquals("provider", SubAiToolNames.providerAlias(""));
    }
}
