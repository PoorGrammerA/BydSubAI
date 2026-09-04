package com.poorgrammera.subai.tool;

/** Constants shared by SubAI hosts and independently distributed Tool applications. */
public final class SubAiToolContract {
    private SubAiToolContract() { }

    public static final int PROTOCOL_VERSION = 1;
    public static final String ACTION_TOOL_SERVICE =
            "com.poorgrammera.subai.action.TOOL_SERVICE";

    public static final String STATUS_READY = "ready";
    public static final String STATUS_NEEDS_CONFIGURATION = "needs_configuration";
    public static final String STATUS_UNAVAILABLE = "unavailable";

    public static final String ERROR_INVALID_ARGUMENTS = "INVALID_ARGUMENTS";
    public static final String ERROR_NOT_CONFIGURED = "NOT_CONFIGURED";
    public static final String ERROR_PERMISSION_REQUIRED = "PERMISSION_REQUIRED";
    public static final String ERROR_RATE_LIMITED = "RATE_LIMITED";
    public static final String ERROR_UNAVAILABLE = "UNAVAILABLE";
    public static final String ERROR_TIMEOUT = "TIMEOUT";
    public static final String ERROR_INTERNAL = "INTERNAL_ERROR";

    public static final int MAX_MANIFEST_BYTES = 256 * 1024;
    public static final int MAX_REQUEST_BYTES = 256 * 1024;
    public static final int MAX_RESPONSE_BYTES = 256 * 1024;
    public static final long DEFAULT_TIMEOUT_MS = 15_000L;
    public static final long MAX_TIMEOUT_MS = 60_000L;
}
