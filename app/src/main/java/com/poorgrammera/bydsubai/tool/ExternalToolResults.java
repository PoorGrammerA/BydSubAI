package com.poorgrammera.bydsubai.tool;

import java.util.List;
import java.util.Map;

/** Small helpers for host UI flows that consume structured external Tool results. */
public final class ExternalToolResults {
    private ExternalToolResults() { }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> firstItem(Map<String, Object> response, String collectionKey) {
        if (response == null || !"success".equals(response.get("status"))) return null;
        Object resultValue = response.get("result");
        if (!(resultValue instanceof Map)) return null;
        Object collection = ((Map<String, Object>) resultValue).get(collectionKey);
        if (!(collection instanceof List) || ((List<?>) collection).isEmpty()) return null;
        Object first = ((List<?>) collection).get(0);
        return first instanceof Map ? (Map<String, Object>) first : null;
    }

    public static Double number(Map<String, Object> item, String key) {
        if (item == null) return null;
        Object value = item.get(key);
        if (value instanceof Number) return ((Number) value).doubleValue();
        try { return value == null ? null : Double.parseDouble(value.toString()); }
        catch (Exception ignored) { return null; }
    }
}
