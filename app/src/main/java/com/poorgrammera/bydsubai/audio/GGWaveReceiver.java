package com.poorgrammera.bydsubai.audio;
import com.poorgrammera.bydsubai.BuildConfig;
import com.poorgrammera.bydsubai.R;


import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

public class GGWaveReceiver {
    private static final String TAG = "GGWaveReceiver";
    private static final int SAMPLE_RATE = 48000;

    static {
        try {
            System.loadLibrary("ggwave-jni");
            Log.d(TAG, "Successfully loaded ggwave-jni library");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load ggwave-jni library", e);
        }
    }

    public interface Listener {
        void onMessageReceived(String message);
        void onError(String error);
    }

    private final Listener listener;
    private Thread captureThread;
    private boolean isCapturing = false;

    private final Context context;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean hasAudioFocus = false;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            Log.d(TAG, "onAudioFocusChange: " + focusChange);
        }
    };

    public GGWaveReceiver(Context context, Listener listener) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.listener = listener;
        if (this.context != null) {
            this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        }
    }

    // Keep the old constructor for compatibility, context will be null
    public GGWaveReceiver(Listener listener) {
        this(null, listener);
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
                Log.d(TAG, "Audio focus granted for GGWave listening.");
            } else {
                Log.w(TAG, "Audio focus request failed for GGWave listening.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to request audio focus", e);
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null || !hasAudioFocus) return;
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
                Log.d(TAG, "Audio focus abandoned successfully for GGWave.");
            } else {
                Log.w(TAG, "Failed to abandon audio focus for GGWave.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to abandon audio focus", e);
        }
    }

    public synchronized void start() {
        if (isCapturing) return;
        if (context != null && context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            if (listener != null) {
                listener.onError("Microphone permission is required to start the receiver.");
            }
            return;
        }
        isCapturing = true;

        requestAudioFocus(); // Request audio focus when starting to listen

        try {
            initNative();
            captureThread = new Thread(this::captureLoop, "GGWave-Capture");
            captureThread.start();
        } catch (Exception e) {
            Log.e(TAG, "Error starting native GGWave receiver", e);
            abandonAudioFocus(); // Clean up focus if startup fails
            if (listener != null) {
                listener.onError("Failed to start receiver: " + e.getMessage());
            }
        }
    }

    public synchronized void stop() {
        if (!isCapturing) return;
        isCapturing = false;

        abandonAudioFocus(); // Abandon focus when stopping the listener

        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
        try {
            cleanupNative();
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up native GGWave receiver", e);
        }
    }

    public boolean isListening() {
        return isCapturing;
    }

    private void captureLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

        if (context != null && context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            isCapturing = false;
            return;
        }

        int bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = 4096;
        }
        bufferSize = Math.max(bufferSize, 4096);

        short[] audioBuffer = new short[bufferSize / 2];

        AudioRecord record = null;
        try {
            int retries = 3;
            while (retries > 0 && isCapturing && !Thread.currentThread().isInterrupted()) {
                try {
                    record = new AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize
                    );

                    if (record.getState() == AudioRecord.STATE_INITIALIZED) {
                        record.startRecording();
                        break;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed attempt to initialize or start AudioRecord: " + e.getMessage());
                }

                if (record != null) {
                    try {
                        record.stop();
                    } catch (Exception ignored) {}
                    record.release();
                    record = null;
                }

                retries--;
                if (retries > 0) {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            if (record == null || record.getState() != AudioRecord.STATE_INITIALIZED) {
                if (listener != null) {
                    listener.onError("Failed to initialize AudioRecord after retries");
                }
                return;
            }
            Log.d(TAG, "Audio recording started successfully for GGWave");

            int zeroBlocksCount = 0;
            int frameCount = 0;
            while (isCapturing && !Thread.currentThread().isInterrupted()) {
                int readSamples = record.read(audioBuffer, 0, audioBuffer.length);
                if (readSamples > 0) {
                    frameCount++;
                    short maxVal = 0;
                    double sum = 0;
                    for (int i = 0; i < readSamples; i++) {
                        short val = audioBuffer[i];
                        if (Math.abs(val) > maxVal) {
                            maxVal = (short) Math.abs(val);
                        }
                        sum += val * val;
                    }
                    double rms = Math.sqrt(sum / readSamples);

                    if (maxVal == 0) {
                        zeroBlocksCount++;
                        if (zeroBlocksCount % 20 == 1) { // Log every 20 silent frames to prevent spamming
                            Log.w(TAG, "Captured buffer contains absolute zeros (silent). Android might be muting mic.");
                        }
                    } else {
                        zeroBlocksCount = 0;
                        if (frameCount % 50 == 1) { // Log every ~2 seconds (approx. 50 * 42ms)
                            Log.d(TAG, "Audio read OK. Samples: " + readSamples + ", Max Amp: " + maxVal + ", RMS: " + String.format("%.2f", rms));
                        }
                    }
                    processCaptureData(audioBuffer);
                } else if (readSamples < 0) {
                    Log.e(TAG, "AudioRecord.read returned error: " + readSamples);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in capture loop", e);
            if (listener != null) {
                listener.onError(e.getMessage());
            }
        } finally {
            if (record != null) {
                try {
                    record.stop();
                    record.release();
                } catch (Exception ignored) {}
            }
            Log.d(TAG, "Audio recording stopped for GGWave");
            abandonAudioFocus(); // Make sure focus is abandoned if the capture thread exits
        }
    }

    // JNI Native methods
    private native void initNative();
    private native void cleanupNative();
    private native void processCaptureData(short[] data);

    // Called from C++ JNI code
    private void onNativeReceivedMessage(byte[] cMessage) {
        if (cMessage == null) return;
        String message = new String(cMessage);
        Log.d(TAG, "Decoded message from GGWave JNI: " + message);
        if (listener != null) {
            listener.onMessageReceived(message);
        }
    }
}
