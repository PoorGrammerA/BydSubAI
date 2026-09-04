package com.poorgrammera.subai.tool;

import android.app.PendingIntent;
import com.poorgrammera.subai.tool.ISubAiToolCallback;

/** Stable Binder surface for SubAI Tool Protocol v1 providers. */
interface ISubAiToolService {
    String getManifestJson();
    String getStatusJson();
    PendingIntent getSettingsIntent();
    void execute(String requestJson, ISubAiToolCallback callback);
    void cancel(String requestId);
}
