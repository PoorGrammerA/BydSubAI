package com.poorgrammera.bydsubai.service;
import com.poorgrammera.bydsubai.BuildConfig;
import com.poorgrammera.bydsubai.R;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        Log.d(TAG, "onReceive: Received broadcast action = " + action);

        // Handle boot, quick boot, and BYD vehicle accessory/ignition ON events
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.byd.action.ACC_ON".equals(action)
                || "com.byd.action.IGN_ON".equals(action)
                || "com.byd.accmode.ACC_MODE_CHANGED".equals(action)) {

            Log.i(TAG, "Triggering service startup due to action: " + action);

            try {
                Intent bydIntent = new Intent(context, BydAutoService.class);
                bydIntent.putExtra("play_system_on", true);
                ContextCompat.startForegroundService(context, bydIntent);
                Log.i(TAG, "BydAutoService startForegroundService triggered from BootReceiver.");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start BydAutoService: " + e.getMessage(), e);
            }

            try {
                Intent geminiIntent = new Intent(context, GeminiLiveService.class);
                ContextCompat.startForegroundService(context, geminiIntent);
                Log.i(TAG, "GeminiLiveService startForegroundService triggered from BootReceiver.");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start GeminiLiveService: " + e.getMessage(), e);
            }
        }
    }
}
