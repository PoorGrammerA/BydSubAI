package com.poorgrammera.bydsubai.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.List;

public class SoundPoolManager {
    private static final String TAG = "SoundPoolManager";
    private static volatile SoundPoolManager instance;

    private SoundPool soundPool;
    private final SparseIntArray soundMap = new SparseIntArray();
    private final SparseBooleanArray loadedSoundIds = new SparseBooleanArray();
    private final SparseArray<List<PendingPlay>> pendingPlays = new SparseArray<>();
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean hasAudioFocus = false;
    private final Handler audioFocusHandler = new Handler(Looper.getMainLooper());
    private Runnable abandonFocusRunnable;

    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = focusChange -> 
        Log.d(TAG, "onAudioFocusChange: " + focusChange);

    private SoundPoolManager(Context context) {
        Context appContext = context.getApplicationContext();
        audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(5)
                .setAudioAttributes(audioAttributes)
                .build();
        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
            synchronized (SoundPoolManager.this) {
                List<PendingPlay> pending = pendingPlays.get(sampleId);
                pendingPlays.remove(sampleId);
                if (status != 0) {
                    Log.e(TAG, "Failed to load sound: sampleId=" + sampleId + ", status=" + status);
                    return;
                }

                loadedSoundIds.put(sampleId, true);
                Log.d(TAG, "Sound loaded: sampleId=" + sampleId);
                if (pending != null) {
                    for (PendingPlay request : pending) {
                        playLoadedSound(sampleId, request.volume, request.durationMs, request.isImportant);
                    }
                }
            }
        });
    }

    public static SoundPoolManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SoundPoolManager.class) {
                if (instance == null) {
                    instance = new SoundPoolManager(context);
                }
            }
        }
        return instance;
    }

    public synchronized void preload(Context context, int resId) {
        if (soundMap.indexOfKey(resId) < 0) {
            int soundId = soundPool.load(context.getApplicationContext(), resId, 1);
            soundMap.put(resId, soundId);
            Log.d(TAG, "Preloaded sound: resId=" + resId + ", soundId=" + soundId);
        }
    }

    private int lastNormalStreamId = 0;

    public synchronized void play(Context context, final int resId, final float volume, int durationMs) {
        play(context, resId, volume, durationMs, false);
    }

    public synchronized void play(Context context, final int resId, final float volume, int durationMs, boolean isImportant) {
        int soundId = soundMap.get(resId);
        if (soundId == 0) {
            preload(context, resId);
            soundId = soundMap.get(resId);
        }

        if (soundId == 0 || soundPool == null) {
            Log.e(TAG, "Unable to prepare sound: resId=" + resId);
            return;
        }

        if (!loadedSoundIds.get(soundId)) {
            List<PendingPlay> pending = pendingPlays.get(soundId);
            if (pending == null) {
                pending = new ArrayList<>();
                pendingPlays.put(soundId, pending);
            }
            pending.add(new PendingPlay(volume, durationMs, isImportant));
            Log.d(TAG, "Queued sound until loaded: resId=" + resId + ", sampleId=" + soundId);
            return;
        }

        playLoadedSound(soundId, volume, durationMs, isImportant);
    }

    private void playLoadedSound(int soundId, float volume, int durationMs, boolean isImportant) {
        requestAudioFocus();

        int streamId;
        if (!isImportant) {
            if (lastNormalStreamId != 0) {
                try {
                    soundPool.stop(lastNormalStreamId);
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping stream: " + lastNormalStreamId, e);
                }
            }
            streamId = soundPool.play(soundId, volume, volume, 1, 0, 1.0f);
            lastNormalStreamId = streamId;
        } else {
            streamId = soundPool.play(soundId, volume, volume, 2, 0, 1.0f);
        }
        Log.d(TAG, "Played sound: sampleId=" + soundId + ", streamId=" + streamId);

        if (abandonFocusRunnable != null) {
            audioFocusHandler.removeCallbacks(abandonFocusRunnable);
        }
        abandonFocusRunnable = this::abandonAudioFocus;
        audioFocusHandler.postDelayed(abandonFocusRunnable, durationMs + 500L);
    }

    private static final class PendingPlay {
        private final float volume;
        private final int durationMs;
        private final boolean isImportant;

        private PendingPlay(float volume, int durationMs, boolean isImportant) {
            this.volume = volume;
            this.durationMs = durationMs;
            this.isImportant = isImportant;
        }
    }

    private void requestAudioFocus() {
        if (audioManager == null) return;
        try {
            int result;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest == null) {
                    AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();
                    audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(playbackAttributes)
                            .setAcceptsDelayedFocusGain(false)
                            .setOnAudioFocusChangeListener(audioFocusChangeListener)
                            .build();
                }
                result = audioManager.requestAudioFocus(audioFocusRequest);
            } else {
                result = audioManager.requestAudioFocus(audioFocusChangeListener,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            }
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                hasAudioFocus = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to request audio focus", e);
        }
    }

    private synchronized void abandonAudioFocus() {
        if (audioManager != null && hasAudioFocus) {
            try {
                int result;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (audioFocusRequest != null) {
                        result = audioManager.abandonAudioFocusRequest(audioFocusRequest);
                    } else {
                        result = AudioManager.AUDIOFOCUS_REQUEST_FAILED;
                    }
                } else {
                    result = audioManager.abandonAudioFocus(audioFocusChangeListener);
                }
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    hasAudioFocus = false;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to abandon audio focus", e);
            }
        }
    }
}
