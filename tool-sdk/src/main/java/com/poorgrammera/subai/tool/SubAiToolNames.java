package com.poorgrammera.subai.tool;

import java.util.Locale;

/** Deterministic naming rules shared by providers, hosts, and compatibility tests. */
public final class SubAiToolNames {
    private SubAiToolNames() { }

    public static boolean isValidLocalName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,64}");
    }

    public static String providerAlias(String providerId) {
        String value = providerId == null ? "" : providerId.trim();
        String[] segments = value.split("\\.");
        String alias = segments.length == 0 ? value : segments[segments.length - 1];
        alias = alias.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return alias.isEmpty() ? "provider" : alias;
    }

    public static String externalFunctionBase(String providerId, String localName) {
        if (!isValidLocalName(localName)) {
            throw new IllegalArgumentException("Invalid local tool name: " + localName);
        }
        String value = "ext_" + providerAlias(providerId) + "_" + localName.toLowerCase(Locale.ROOT);
        value = value.replaceAll("[^a-z0-9_]", "_");
        return value.length() <= 128 ? value : value.substring(0, 128);
    }
}
