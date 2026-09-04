package com.poorgrammera.bydsubai.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.poorgrammera.bydsubai.data.ConfigData;
import com.poorgrammera.bydsubai.service.GeminiLiveService;
import com.poorgrammera.bydsubai.util.SoundPoolManager;

/** Transparent bridge used by SubAIListener to cold-start a Gemini Live session. */
public final class ListenerTriggerActivity extends AppCompatActivity {
    private static final String TAG = "ListenerTriggerActivity";
    private static final String EXTRA_CHARACTER = "character";
    private static final long STARTUP_TIMEOUT_MS = 20_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean receiverRegistered;
    private boolean sessionStarted;

    private final Runnable startupTimeout = () -> {
        if (!sessionStarted) {
            Log.w(TAG, "Gemini session did not start before timeout; finishing bridge Activity");
            finishWithoutAnimation();
        }
    };

    private final BroadcastReceiver sessionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? null : intent.getAction();
            if (GeminiLiveService.ACTION_GEMINI_SESSION_STARTED.equals(action)) {
                sessionStarted = true;
                mainHandler.removeCallbacks(startupTimeout);
                Log.i(TAG, "Gemini session started; bridge Activity will wait for completion");
            } else if (GeminiLiveService.ACTION_GEMINI_SESSION_ENDED.equals(action)) {
                Log.i(TAG, "Gemini session ended; returning to the previous screen");
                finishWithoutAnimation();
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        registerSessionReceiver();
        startGeminiForCharacter(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!sessionStarted) {
            startGeminiForCharacter(intent);
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (receiverRegistered) {
            unregisterReceiver(sessionReceiver);
            receiverRegistered = false;
        }
        super.onDestroy();
    }

    private void registerSessionReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(GeminiLiveService.ACTION_GEMINI_SESSION_STARTED);
        filter.addAction(GeminiLiveService.ACTION_GEMINI_SESSION_ENDED);
        ContextCompat.registerReceiver(
                this,
                sessionReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        receiverRegistered = true;
    }

    private void startGeminiForCharacter(Intent incoming) {
        String character = normalizeCharacter(
                incoming == null ? null : incoming.getStringExtra(EXTRA_CHARACTER)
        );
        getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE)
                .edit()
                .putString(ConfigData.KEY_VOICE_MODE, character)
                .commit();

        SoundPoolManager sounds = SoundPoolManager.getInstance(this);
        sounds.preload(this, ConfigData.VOICE_STREAM_START);
        sounds.preload(this, ConfigData.VOICE_STREAM_END);

        Intent serviceIntent = new Intent(this, GeminiLiveService.class)
                .setAction(GeminiLiveService.ACTION_TRIGGER_GEMINI)
                .putExtra(EXTRA_CHARACTER, character)
                .putExtra("use_button_sound", false)
                .putExtra("use_start_sound", true)
                .putExtra("use_end_sound", true);
        try {
            ContextCompat.startForegroundService(this, serviceIntent);
            mainHandler.removeCallbacks(startupTimeout);
            mainHandler.postDelayed(startupTimeout, STARTUP_TIMEOUT_MS);
            Log.i(TAG, "Gemini service requested for character=" + character);
        } catch (Exception error) {
            Log.e(TAG, "Unable to start Gemini service", error);
            finishWithoutAnimation();
        }
    }

    private static String normalizeCharacter(String character) {
        if ("kitt".equals(character) || "asurada".equals(character)) {
            return character;
        }
        return "rumi";
    }

    private void finishWithoutAnimation() {
        finish();
        overridePendingTransition(0, 0);
    }
}
