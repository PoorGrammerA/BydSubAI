package com.poorgrammera.bydsubai.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.poorgrammera.bydsubai.data.ConfigData;

/**
 * Manifest entry point for wake-word broadcasts sent while the SubAI process is not running.
 */
public final class GeminiTriggerReceiver extends BroadcastReceiver {
    private static final String TAG = "GeminiTriggerReceiver";

    public static final String ACTION_TRIGGER_RUMI =
            "com.poorgrammera.bydsubai.TRIGGER_GEMINI_RUMI";
    public static final String ACTION_TRIGGER_KITT =
            "com.poorgrammera.bydsubai.TRIGGER_GEMINI_KITT";
    public static final String ACTION_TRIGGER_ASURADA =
            "com.poorgrammera.bydsubai.TRIGGER_GEMINI_ASURADA";

    @Override
    public void onReceive(Context context, Intent incoming) {
        if (incoming == null || incoming.getAction() == null) {
            return;
        }

        String characterKey = characterForAction(incoming.getAction());
        if (characterKey == null) {
            Log.w(TAG, "Ignoring unsupported action: " + incoming.getAction());
            return;
        }

        context.getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(ConfigData.KEY_VOICE_MODE, characterKey)
                .apply();

        Intent serviceIntent = new Intent(context, GeminiLiveService.class)
                .setAction(GeminiLiveService.ACTION_TRIGGER_GEMINI)
                .putExtra("character", characterKey);

        try {
            ContextCompat.startForegroundService(context, serviceIntent);
            Log.i(
                    TAG,
                    "Triggered GeminiLiveService: action=" + incoming.getAction()
                            + ", character=" + characterKey
            );
        } catch (Exception error) {
            Log.e(TAG, "Failed to trigger GeminiLiveService for " + characterKey, error);
        }
    }

    private static String characterForAction(String action) {
        if (ACTION_TRIGGER_RUMI.equals(action)) {
            return "rumi";
        }
        if (ACTION_TRIGGER_KITT.equals(action)) {
            return "kitt";
        }
        if (ACTION_TRIGGER_ASURADA.equals(action)) {
            return "asurada";
        }
        return null;
    }
}
