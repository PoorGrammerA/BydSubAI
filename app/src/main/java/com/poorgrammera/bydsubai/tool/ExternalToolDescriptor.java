package com.poorgrammera.bydsubai.tool;

import android.content.ComponentName;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.poorgrammera.subai.tool.SubAiToolNames;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable snapshot returned by an external SubAI Tool service. */
public final class ExternalToolDescriptor {
    public static final class Function {
        public final String name;
        public final String displayName;
        public final String description;
        public final JSONObject parameters;

        Function(String name, String displayName, String description, JSONObject parameters) {
            this.name = name;
            this.displayName = displayName;
            this.description = description;
            this.parameters = parameters;
        }
    }

    public final ComponentName component;
    public final String certificateSha256;
    public final String applicationLabel;
    public final JSONObject manifest;
    public final JSONObject status;

    private ExternalToolDescriptor(ComponentName component, String certificateSha256,
                                   String applicationLabel, JSONObject manifest, JSONObject status) {
        this.component = component;
        this.certificateSha256 = certificateSha256;
        this.applicationLabel = applicationLabel;
        this.manifest = manifest;
        this.status = status;
    }

    public static ExternalToolDescriptor create(ComponentName component, String certificateSha256,
                                                String applicationLabel, String manifestJson,
                                                String statusJson) throws JSONException {
        JSONObject manifest = new JSONObject(manifestJson);
        JSONObject status = statusJson == null || statusJson.trim().isEmpty()
                ? new JSONObject().put("status", "unavailable")
                : new JSONObject(statusJson);
        validateManifest(manifest);
        return new ExternalToolDescriptor(component, certificateSha256, applicationLabel,
                manifest, status);
    }

    public static ExternalToolDescriptor fromStoredJson(String storedJson) throws JSONException {
        JSONObject root = new JSONObject(storedJson);
        return create(
                new ComponentName(root.getString("packageName"), root.getString("serviceName")),
                root.getString("certificateSha256"),
                root.optString("applicationLabel", root.getString("packageName")),
                root.getJSONObject("manifest").toString(),
                root.getJSONObject("status").toString());
    }

    public String toStoredJson() throws JSONException {
        return new JSONObject()
                .put("packageName", component.getPackageName())
                .put("serviceName", component.getClassName())
                .put("certificateSha256", certificateSha256)
                .put("applicationLabel", applicationLabel)
                .put("manifest", manifest)
                .put("status", status)
                .toString();
    }

    public String identity() {
        return component.flattenToString() + "|" + certificateSha256;
    }

    public int protocolVersion() {
        return manifest.optInt("protocolVersion", -1);
    }

    public JSONObject provider() {
        return manifest.optJSONObject("provider");
    }

    public String providerId() {
        JSONObject provider = provider();
        return provider == null ? component.getPackageName() : provider.optString("id", component.getPackageName());
    }

    public String providerName() {
        JSONObject provider = provider();
        return provider == null ? applicationLabel : provider.optString("name", applicationLabel);
    }

    public String providerVersion() {
        JSONObject provider = provider();
        return provider == null ? "" : provider.optString("version", "");
    }

    public String sourceUrl() {
        JSONObject provider = provider();
        return provider == null ? "" : provider.optString("sourceUrl", "");
    }

    public List<String> disclosures() {
        JSONObject provider = provider();
        JSONArray array = provider == null ? null : provider.optJSONArray("disclosures");
        if (array == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    public boolean isReady() {
        return "ready".equals(status.optString("status", "unavailable"));
    }

    public String statusMessage() {
        return status.optString("message", "");
    }

    public List<Function> functions() {
        JSONArray tools = manifest.optJSONArray("tools");
        if (tools == null) return Collections.emptyList();
        List<Function> result = new ArrayList<>();
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.optJSONObject(i);
            if (tool == null) continue;
            String name = tool.optString("name", "");
            String description = tool.optString("description", "");
            JSONObject parameters = tool.optJSONObject("parameters");
            if (name.isEmpty() || description.isEmpty() || parameters == null) continue;
            result.add(new Function(name, tool.optString("displayName", name), description, parameters));
        }
        return result;
    }

    public String geminiPrefix() {
        return "ext_" + SubAiToolNames.providerAlias(providerId()) + "_";
    }

    private static void validateManifest(JSONObject manifest) throws JSONException {
        int version = manifest.getInt("protocolVersion");
        if (version != 1) throw new JSONException("Unsupported protocol version: " + version);
        JSONObject provider = manifest.getJSONObject("provider");
        requireText(provider, "id");
        requireText(provider, "name");
        JSONArray tools = manifest.getJSONArray("tools");
        if (tools.length() == 0) throw new JSONException("Tool manifest has no tools");
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.getJSONObject(i);
            String name = requireText(tool, "name");
            if (!SubAiToolNames.isValidLocalName(name)) {
                throw new JSONException("Invalid local tool name: " + name);
            }
            requireText(tool, "description");
            JSONObject parameters = tool.getJSONObject("parameters");
            if (!"object".equalsIgnoreCase(parameters.optString("type"))) {
                throw new JSONException("Tool parameters must have object type: " + name);
            }
        }
    }

    private static String requireText(JSONObject object, String key) throws JSONException {
        String value = object.getString(key).trim();
        if (value.isEmpty()) throw new JSONException("Missing value: " + key);
        return value;
    }
}
