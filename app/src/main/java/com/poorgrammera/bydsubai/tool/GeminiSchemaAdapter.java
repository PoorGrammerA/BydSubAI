package com.poorgrammera.bydsubai.tool;

import com.google.genai.types.Schema;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Converts the protocol's constrained OpenAPI schema into the Google GenAI Java SDK model. */
final class GeminiSchemaAdapter {
    private GeminiSchemaAdapter() { }

    static Schema fromJson(JSONObject json) throws JSONException {
        return fromJson(json, 0);
    }

    private static Schema fromJson(JSONObject json, int depth) throws JSONException {
        if (depth > 8) throw new JSONException("Schema nesting exceeds 8 levels");
        String type = json.optString("type", "object").toUpperCase();
        Schema.Builder builder = Schema.builder().type(type);
        String description = json.optString("description", "").trim();
        if (!description.isEmpty()) builder.description(description);

        JSONObject propertiesObject = json.optJSONObject("properties");
        if (propertiesObject != null) {
            Map<String, Schema> properties = new HashMap<>();
            java.util.Iterator<String> keys = propertiesObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                properties.put(key, fromJson(propertiesObject.getJSONObject(key), depth + 1));
            }
            builder.properties(properties);
        }

        JSONArray requiredArray = json.optJSONArray("required");
        if (requiredArray != null && requiredArray.length() > 0) {
            List<String> required = new ArrayList<>();
            for (int i = 0; i < requiredArray.length(); i++) required.add(requiredArray.getString(i));
            builder.required(required);
        }

        JSONObject items = json.optJSONObject("items");
        if (items != null) builder.items(fromJson(items, depth + 1));

        JSONArray enumArray = json.optJSONArray("enum");
        if (enumArray != null && enumArray.length() > 0) {
            List<String> values = new ArrayList<>();
            for (int i = 0; i < enumArray.length(); i++) values.add(enumArray.getString(i));
            builder.enum_(values);
        }
        if (json.has("minimum")) builder.minimum(json.getDouble("minimum"));
        if (json.has("maximum")) builder.maximum(json.getDouble("maximum"));
        return builder.build();
    }
}
