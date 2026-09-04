package com.poorgrammera.bydsubai.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;

public class MediaPlayerManager {
    private static final String TAG = "MediaPlayerManager";
    private static volatile MediaPlayerManager instance;

    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean hasAudioFocus = false;

    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = focusChange -> 
        Log.d(TAG, "onAudioFocusChange: " + focusChange);

    private MediaPlayerManager(Context context) {
        audioManager = (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    }

    public static MediaPlayerManager getInstance(Context context) {
        if (instance == null) {
            synchronized (MediaPlayerManager.class) {
                if (instance == null) {
                    instance = new MediaPlayerManager(context);
                }
            }
        }
        return instance;
    }

    public synchronized void play(Context context, int resId, float volume, MediaPlayer.OnCompletionListener listener) {
        stop(); 

        try {
            requestAudioFocus();
            mediaPlayer = MediaPlayer.create(context.getApplicationContext(), resId);
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(volume, volume);
                mediaPlayer.setOnCompletionListener(mp -> {
                    stop();
                    abandonAudioFocus();
                    if (listener != null) {
                        listener.onCompletion(mp);
                    }
                });
                mediaPlayer.start();
                Log.d(TAG, "Started playing resource via MediaPlayer: resId=" + resId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing sound via MediaPlayer", e);
        }
    }

    public synchronized void stop() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception ignored) {}
            try {
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
            Log.d(TAG, "Stopped and released MediaPlayer");
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
