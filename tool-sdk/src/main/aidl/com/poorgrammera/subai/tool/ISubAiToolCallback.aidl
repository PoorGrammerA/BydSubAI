package com.poorgrammera.subai.tool;

/** Asynchronous result callback for a SubAI tool invocation. */
oneway interface ISubAiToolCallback {
    void onResult(String requestId, String responseJson);
    void onError(String requestId, String errorCode, String message);
}
