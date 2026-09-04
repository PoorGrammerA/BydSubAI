package com.poorgrammera.bydsubai.audio;
import com.poorgrammera.bydsubai.BuildConfig;
import com.poorgrammera.bydsubai.R;


import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

public class MicrophoneHandler {
    private static final String TAG = "MicrophoneHandler";

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private static final int NOISE_THRESHOLD = 500;
    private static final int SILENCE_CHUNKS_BEFORE_STREAM_END = 7; // ~448ms silence threshold for fast turn completion

    public interface AudioChunkListener {
        void onAudioChunk(byte[] chunk);
        void onAudioStreamEnd();
    }

    private final Context context;
    private final AudioChunkListener listener;
    private AudioRecord audioRecord;
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private final AtomicBoolean inputSuppressed = new AtomicBoolean(false);
    private Thread recordingThread;

    public MicrophoneHandler(Context context, AudioChunkListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void setInputSuppressed(boolean suppressed) {
        inputSuppressed.set(suppressed);
    }

    public boolean isInputSuppressed() {
        return inputSuppressed.get();
    }

    public void startRecording() {
        if (isRecording.get()) return;

        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        int bufferSize = Math.max(minBufferSize, 2048);

        boolean isDilink3 = context.getSharedPreferences("BydSubAiPrefs", Context.MODE_PRIVATE)
                .getBoolean("isDilink3", false);
        int audioSource = isDilink3 ? MediaRecorder.AudioSource.VOICE_RECOGNITION : MediaRecorder.AudioSource.MIC;
        Log.i(TAG, "Initializing AudioRecord with audioSource: " + (audioSource == MediaRecorder.AudioSource.VOICE_RECOGNITION ? "VOICE_RECOGNITION" : "MIC") + " (isDilink3: " + isDilink3 + ")");

        try {
            audioRecord = new AudioRecord(
                    audioSource,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
            );
        } catch (SecurityException e) {
            Log.e(TAG, "RECORD_AUDIO permission not granted", e);
            return;
        }

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed");
            return;
        }

        isRecording.set(true);
        audioRecord.startRecording();

        recordingThread = new Thread(() -> {
            byte[] buffer = new byte[2048];
            int consecutiveSilentChunks = 0;
            boolean streamEndedForCurrentSilence = false;
            boolean hasSentSpeechInCurrentTurn = false;

            long lastLogTime = 0;
            while (isRecording.get()) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                
                if (read < 0) {
                    Log.e(TAG, "AudioRecord read error: " + read);
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    continue;
                }
                if (read == 0) continue;

                int maxAmplitude = calculateMaxAmplitude(buffer, read);
                
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastLogTime > 2000) { 
                    Log.i(TAG, "Mic Amplitude: " + maxAmplitude + (maxAmplitude < NOISE_THRESHOLD ? " (Silent)" : " (Voice)") + (inputSuppressed.get() ? " [SUPPRESSED]" : ""));
                    lastLogTime = currentTime;
                }

                if (inputSuppressed.get()) {
                    consecutiveSilentChunks = 0;
                    streamEndedForCurrentSilence = false;
                    hasSentSpeechInCurrentTurn = false;
                    continue;
                }

                if (maxAmplitude < NOISE_THRESHOLD) {
                    consecutiveSilentChunks++;
                    if (hasSentSpeechInCurrentTurn && !streamEndedForCurrentSilence 
                            && consecutiveSilentChunks >= SILENCE_CHUNKS_BEFORE_STREAM_END) {
                        listener.onAudioStreamEnd();
                        streamEndedForCurrentSilence = true;
                        hasSentSpeechInCurrentTurn = false;
                    }
                    continue;
                }

                consecutiveSilentChunks = 0;
                streamEndedForCurrentSilence = false;
                hasSentSpeechInCurrentTurn = true;

                byte[] chunk = new byte[read];
                System.arraycopy(buffer, 0, chunk, 0, read);
                listener.onAudioChunk(chunk);
            }
        });
        recordingThread.start();
    }

    public void stopRecording() {
        isRecording.set(false);
        if (recordingThread != null) {
            try { recordingThread.join(1000); } catch (InterruptedException ignored) {}
            recordingThread = null;
        }
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception ignored) {}
            audioRecord = null;
        }
    }

    private int calculateMaxAmplitude(byte[] buffer, int read) {
        int maxAmplitude = 0;
        for (int i = 0; i < read - 1; i += 2) {
            short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            maxAmplitude = Math.max(maxAmplitude, Math.abs(sample));
        }
        return maxAmplitude;
    }
}
