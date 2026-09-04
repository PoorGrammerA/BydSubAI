package com.poorgrammera.bydsubai.service;
import com.poorgrammera.bydsubai.BuildConfig;
import com.poorgrammera.bydsubai.R;
import com.poorgrammera.bydsubai.audio.AudioPlaybackManager;
import com.poorgrammera.bydsubai.audio.MediaBrowserManager;
import com.poorgrammera.bydsubai.audio.MicrophoneHandler;
import com.poorgrammera.bydsubai.data.ConfigData;
import com.poorgrammera.bydsubai.data.SecureCredentialStore;
import com.poorgrammera.bydsubai.gemini.GeminiLiveClient;
import com.poorgrammera.bydsubai.ui.AsuradaView;
import com.poorgrammera.bydsubai.ui.KittScannerView;
import com.poorgrammera.bydsubai.vehicle.VehicleController;
import com.poorgrammera.bydsubai.vehicle.ClimateNoiseReductionController;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import com.poorgrammera.bydsubai.util.SoundPoolManager;
import android.media.AudioManager;
import android.media.AudioFocusRequest;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseIntArray;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.concurrent.atomic.AtomicBoolean;

public class GeminiLiveService extends Service implements GeminiLiveClient.Listener, 
        MicrophoneHandler.AudioChunkListener, AudioPlaybackManager.PlaybackStateListener {
    
    private static final String TAG = "GeminiLiveService";
    public static volatile boolean isRunning = false;
    private static final String CHANNEL_ID = "GeminiLiveServiceChannel";
    private static final int NOTIFICATION_ID = 3;

    public static final String ACTION_TRIGGER_GEMINI = "com.poorgrammera.bydsubai.TRIGGER_GEMINI";
    public static final String ACTION_STOP_GEMINI = "com.poorgrammera.bydsubai.STOP_GEMINI";
    public static final String ACTION_GEMINI_UPDATE = "com.poorgrammera.bydsubai.GEMINI_UPDATE";
    public static final String ACTION_GEMINI_SESSION_STARTED =
            "com.poorgrammera.bydsubai.GEMINI_SESSION_STARTED";
    public static final String ACTION_GEMINI_SESSION_ENDED =
            "com.poorgrammera.bydsubai.GEMINI_SESSION_ENDED";
    private static final String SUB_AI_LISTENER_PACKAGE = "com.poorgrammera.subailistener";
    public static final String ACTION_SEARCH_UPDATE = "com.poorgrammera.bydsubai.SEARCH_UPDATE";

    private static final long GEMINI_SPEAKING_HOLD_MS = 900L;
    private static final long START_MIC_AFTER_CONNECTED_DELAY_MS = 300L;

    private GeminiLiveClient liveClient;
    private MicrophoneHandler microphoneHandler;
    private AudioPlaybackManager playbackManager;
    private VehicleController vehicleController;
    private static final String CLIMATE_INPUT_SOURCE_GEMINI = "gemini_live";



    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile long lastGeminiAudioAtMs = 0L;
    private final AtomicBoolean geminiSpeaking = new AtomicBoolean(false);
    private final AtomicBoolean endingSession = new AtomicBoolean(false);
    private volatile boolean pendingDisconnect = false;
    private volatile boolean pendingDisconnectOnMediaTrigger = false;

    private volatile boolean isTtsOnlyMode = false;
    private String ttsText = "";
    private volatile boolean isTextOnlyMode = false;
    private String textInputContent = "";

    private volatile boolean useMemory = true;
    private volatile boolean useStartSound = true;
    private volatile boolean useEndSound = true;

    private final java.util.Queue<String> pendingBootBriefingQueries = new java.util.LinkedList<>();
    private boolean isBriefingMode = false;


 
    // Volatile short-term driver context memory (max 5 items, FIFO)
    private static final java.util.List<String> shortTermMemories = new java.util.ArrayList<>();

    public static synchronized void addShortTermMemory(String value) {
        if (value == null || value.trim().isEmpty()) return;
        shortTermMemories.add(value.trim());
        while (shortTermMemories.size() > 5) {
            shortTermMemories.remove(0);
        }
        Log.i("GeminiLiveService", "Short-term memory added: " + value + ", size: " + shortTermMemories.size());
    }

    public static synchronized java.util.List<String> getShortTermMemories() {
        return new java.util.ArrayList<>(shortTermMemories);
    }

    private final Object outputTranscriptLock = new Object();
    private final StringBuilder outputTranscriptBuffer = new StringBuilder();
    private static final int MAX_OUTPUT_TRANSCRIPT_BUFFER_CHARS = 300;

    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean hasAudioFocus = false;
    private android.view.WindowManager windowManager;
    private android.view.View overlayView;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            Log.d(TAG, "onAudioFocusChange: " + focusChange);
        }
    };

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
                Log.d(TAG, "Audio focus granted for Gemini Live.");
            } else {
                Log.w(TAG, "Audio focus request failed for Gemini Live.");
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
                Log.d(TAG, "Audio focus abandoned successfully for Gemini Live.");
            } else {
                Log.w(TAG, "Failed to abandon audio focus for Gemini Live.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to abandon audio focus", e);
        }
    }

    private long sessionTimeoutMs = 10000L; // 10 seconds default timeout for silence
    private volatile long lastActivityTimeMs = 0L;

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (endingSession.get()) {
                return;
            }
            if (liveClient != null && liveClient.isConnected()) {
                boolean isAiActive = geminiSpeaking.get()
                        || (playbackManager != null && playbackManager.isPlaying())
                        || (microphoneHandler != null && microphoneHandler.isInputSuppressed());
                if (isAiActive) {
                    updateActivity();
                }

                long inactiveTime = android.os.SystemClock.elapsedRealtime() - lastActivityTimeMs;
                if (inactiveTime >= sessionTimeoutMs) {
                    Log.i(TAG, "Silence timeout reached (" + inactiveTime + "ms). Disconnecting Gemini Live.");
                    liveClient.disconnect();
                } else {
                    mainHandler.postDelayed(this, 1000);
                }
            }
        }
    };

    private void updateActivity() {
        lastActivityTimeMs = android.os.SystemClock.elapsedRealtime();
    }

    private final Runnable releaseMicHoldRunnable = new Runnable() {
        @Override
        public void run() {
            if (geminiSpeaking.compareAndSet(true, false)) {
                Log.d(TAG, "AudioTrack finished + 400ms echo delay -> RESUME MIC");
                if (microphoneHandler != null) {
                    microphoneHandler.setInputSuppressed(false);
                }
                updateActivity();
            }
        }
    };

    private void markGeminiSpeaking() {
        lastGeminiAudioAtMs = SystemClock.elapsedRealtime();
        mainHandler.removeCallbacks(releaseMicHoldRunnable);

        if (geminiSpeaking.compareAndSet(false, true)) {
            Log.d(TAG, "GEMINI SPEAKING -> SUPPRESS MIC");
            if (microphoneHandler != null) {
                microphoneHandler.setInputSuppressed(true);
            }
        }
    }

    private void playEffect(int resId) {
        float volume = 1.0f;
        try {
            android.content.SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE);
            if (resId == ConfigData.VOICE_STREAM_START || resId == ConfigData.VOICE_STREAM_BUTTON) {
                int volPercent = prefs.getInt(ConfigData.KEY_VOLUME_STREAM_START, ConfigData.DEFAULT_VOLUME_STREAM_START);
                volume = volPercent / 100.0f;
            } else if (resId == ConfigData.VOICE_STREAM_END) {
                int volPercent = prefs.getInt(ConfigData.KEY_VOLUME_STREAM_END, ConfigData.DEFAULT_VOLUME_STREAM_END);
                volume = volPercent / 100.0f;
            }
            volume = Math.max(0.0f, Math.min(1.0f, volume));
        } catch (Exception e) {
            Log.e(TAG, "Failed to read volume preference, defaulting to 1.0f", e);
            volume = 1.0f;
        }
        Log.d(TAG, "playEffect: calculated volume scale=" + volume);
        SoundPoolManager.getInstance(this).play(this, resId, volume, 2000, true);
    }

    private final BroadcastReceiver controlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            Log.i(TAG, "onReceive Action: " + intent.getAction());

            if (ACTION_TRIGGER_GEMINI.equals(intent.getAction())) {
                isBriefingMode = false; // Reset to false for standard user requests
                boolean ttsOnly = intent.getBooleanExtra("is_tts_only", false);
                String ttsText = intent.getStringExtra("tts_text");
                boolean textOnly = intent.getBooleanExtra("is_text_only", false);
                String textInput = intent.getStringExtra("text_input");
 
                boolean defaultUseMemory = context.getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE)
                        .getBoolean(ConfigData.KEY_USE_MEMORY, true);
                boolean mem = intent.getBooleanExtra("use_memory", defaultUseMemory);
                boolean startSound = intent.getBooleanExtra("use_start_sound", true);
                boolean endSound = intent.getBooleanExtra("use_end_sound", true);
                boolean buttonSound = intent.getBooleanExtra("use_button_sound", true);
 
                if (!ttsOnly && !textOnly) {
                    beginGeminiLiveNoiseReduction();
                }
                if (startSound && buttonSound) {
                    playEffect(ConfigData.VOICE_STREAM_BUTTON);
                }
 
                initializeClientAndConnect(ttsOnly, ttsText, textOnly, textInput, mem, startSound, endSound);
            } else if (ACTION_STOP_GEMINI.equals(intent.getAction())) {
                Log.i(TAG, "ACTION_STOP_GEMINI received. Disconnecting session immediately.");
                pendingBootBriefingQueries.clear();
                isBriefingMode = false;
                if (liveClient != null && liveClient.isConnected()) {
                    liveClient.disconnect();
                }
            } else if (ConfigData.ACTION_BOOT_SOUND_COMPLETED.equals(intent.getAction())) {
                Log.i(TAG, "ACTION_BOOT_SOUND_COMPLETED received. Checking boot briefing settings.");
                SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
                boolean weatherBriefing = prefs.getBoolean(ConfigData.KEY_BOOT_WEATHER_BRIEFING, true);
                boolean newsBriefing = prefs.getBoolean(ConfigData.KEY_BOOT_NEWS_BRIEFING, true);
 
                pendingBootBriefingQueries.clear();
 
                if (newsBriefing) {
                    pendingBootBriefingQueries.offer("오늘 뉴스 브리핑을 해줘 인사 없이 브리핑을 시작하고 대화 종료 규칙을 따르지 않고 브리핑만 마치고 바로 대화 종료해줘");
                }
                if (weatherBriefing) {
                    pendingBootBriefingQueries.offer("오늘 날씨 브리핑을 해줘 인사 없이 브리핑을 시작하고 대화 종료 규칙을 따르지 않고 브리핑만 마치고 바로 대화 종료해줘");
                }
 
                String firstQuery = pendingBootBriefingQueries.poll();
                if (firstQuery != null) {
                    Log.i(TAG, "Starting first boot briefing query: " + firstQuery);
                    isBriefingMode = true; // Activate briefing mode
                    initializeClientAndConnect(false, null, true, firstQuery, false, false, false);
                } else {
                    isBriefingMode = false;
                    Log.i(TAG, "All boot briefings are disabled. Bypassing.");
                }
            }
        }
    };

    private void initializeClientAndConnect() {
        initializeClientAndConnect(false, null, false, null, true, true, true);
    }

    private void initializeClientAndConnect(boolean ttsOnly, String ttsText) {
        initializeClientAndConnect(ttsOnly, ttsText, false, null, true, true, true);
    }

    private void initializeClientAndConnect(boolean ttsOnly, String ttsText, boolean textOnly, String textInput) {
        initializeClientAndConnect(ttsOnly, ttsText, textOnly, textInput, true, true, true);
    }

    private void initializeClientAndConnect(boolean ttsOnly, String ttsText, boolean textOnly, String textInput, boolean mem, boolean startSound, boolean endSound) {
        if (liveClient != null && liveClient.isConnected()) {
            Log.i(TAG, "Gemini Live is already connected. Disconnecting existing session and stopping.");
            liveClient.disconnect();
            return;
        }

        this.isTtsOnlyMode = ttsOnly;
        this.ttsText = ttsText;
        this.isTextOnlyMode = textOnly;
        this.textInputContent = textInput;
        this.useMemory = mem;
        this.useStartSound = startSound;
        this.useEndSound = endSound;

        SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
        String apiKey = SecureCredentialStore.getString(this, ConfigData.KEY_GEMINI_API_KEY, ConfigData.DEFAULT_GEMINI_API_KEY);
        String modelName = prefs.getString(ConfigData.KEY_GEMINI_LIVE_MODEL, ConfigData.DEFAULT_GEMINI_LIVE_MODEL);
        if (modelName == null || modelName.trim().isEmpty()) {
            modelName = ConfigData.DEFAULT_GEMINI_LIVE_MODEL;
        }
        Log.i(TAG, "Connecting to Gemini Live with model: " + modelName + " (TTS Only: " + ttsOnly + ", Text Only: " + textOnly + ", Use Memory: " + mem + ", Start Sound: " + startSound + ", End Sound: " + endSound + ")");
        liveClient = new GeminiLiveClient(this, apiKey, modelName, vehicleController, this);
        liveClient.setTtsOnlyMode(ttsOnly);
        liveClient.setTextOnlyMode(textOnly);
        liveClient.setUseMemory(mem);
        liveClient.setBriefingMode(isBriefingMode);
        liveClient.connect();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        Log.i(TAG, "onCreate: Service started");
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Initialize reusable VehicleController
        vehicleController = new VehicleController(this);

        // Initialize MediaBrowserManager to connect to players (FLO, Spotify) in the background service context
        MediaBrowserManager.init(getApplicationContext());
        
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("Gemini Live Voice Service Active"));

        // Preload Gemini Live stream start/end sounds
        SoundPoolManager.getInstance(this).preload(this, ConfigData.VOICE_STREAM_START);
        SoundPoolManager.getInstance(this).preload(this, ConfigData.VOICE_STREAM_END);
        SoundPoolManager.getInstance(this).preload(this, ConfigData.VOICE_STREAM_BUTTON);

        microphoneHandler = new MicrophoneHandler(this, this);
        playbackManager = new AudioPlaybackManager(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TRIGGER_GEMINI);
        filter.addAction(ACTION_STOP_GEMINI);
        filter.addAction(ConfigData.ACTION_BOOT_SOUND_COMPLETED);
        ContextCompat.registerReceiver(
                this,
                controlReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
        );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            controlReceiver.onReceive(this, intent);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        Log.d(TAG, "onDestroy");
        hideOverlay(); // Clean up overlay on destroy
        abandonAudioFocus(); // Abandon focus when service is destroyed
        synchronized (GeminiLiveService.class) {
            shortTermMemories.clear();
        }
        try {
            unregisterReceiver(controlReceiver);
        } catch (Exception ignored) {}
        
        if (liveClient != null) {
            liveClient.disconnect();
        }
        if (microphoneHandler != null) {
            microphoneHandler.stopRecording();
        }
        endGeminiLiveNoiseReduction();
        if (playbackManager != null) {
            playbackManager.stopPcmPlayback();
        }
        if (vehicleController != null) {
            vehicleController.shutdown();
            vehicleController = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }



    // GeminiLiveClient.Listener
    @Override
    public void onAudioData(byte[] data) {
        updateActivity();
        markGeminiSpeaking(); 
        if (playbackManager != null) {
            playbackManager.feedPcmData(data);
        }
    }

    @Override
    public void onTextData(String text) {
        updateActivity();
        Log.i(TAG, "Gemini Text: " + text);

        Intent intent = new Intent(ACTION_GEMINI_UPDATE);
        intent.putExtra("gemini_text", text);
        sendBroadcast(intent);

        SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
        String endMode = prefs.getString(ConfigData.KEY_END_MODE, ConfigData.DEFAULT_END_MODE);
        if (!ConfigData.END_MODE_AUTO.equals(endMode)) {
            // Only search for end markers in auto mode
            return;
        }

        String normalized = normalizeForMatch(text);
        String voiceMode = prefs.getString(ConfigData.KEY_VOICE_MODE, ConfigData.DEFAULT_VOICE_MODE);
        int arrayResId;
        if ("kitt".equals(voiceMode)) {
            arrayResId = R.array.assistant_end_markers_by_kitt;
        } else if ("asurada".equals(voiceMode)) {
            arrayResId = R.array.assistant_end_markers_by_asurada;
        } else {
            arrayResId = R.array.assistant_end_markers;
        }
        String[] endMarkers = getResources().getStringArray(arrayResId);
        for (String marker : endMarkers) {
            if (normalized.contains(normalizeForMatch(marker))) {
                if (!pendingDisconnect) {
                    pendingDisconnect = true;
                    Log.i(TAG, "End marker detected in onTextData: " + marker + ". Pending disconnect on turn complete.");
                }
                return;
            }
        }
    }

    @Override
    public void onInputTranscription(String text) {
        updateActivity();
        Log.i(TAG, "User Transcription: " + text);
        Intent intent = new Intent(ACTION_GEMINI_UPDATE);
        intent.putExtra("user_text", text);
        sendBroadcast(intent);
    }

    @Override
    public void onOutputTranscription(String text) {
        Log.i(TAG, "Gemini Output Transcription: " + text);
        Intent intent = new Intent(ACTION_GEMINI_UPDATE);
        intent.putExtra("gemini_text", text);
        sendBroadcast(intent);

        appendOutputTranscriptionAndCheckEnd(text);
    }

    private void appendOutputTranscriptionAndCheckEnd(String text) {
        if (text == null || text.isEmpty()) return;

        final String normalizedBuffer;

        synchronized (outputTranscriptLock) {
            outputTranscriptBuffer.append(text);

            // Prevent buffer from growing too large
            if (outputTranscriptBuffer.length() > MAX_OUTPUT_TRANSCRIPT_BUFFER_CHARS) {
                outputTranscriptBuffer.delete(
                        0,
                        outputTranscriptBuffer.length() - MAX_OUTPUT_TRANSCRIPT_BUFFER_CHARS
                );
            }

            normalizedBuffer = normalizeForMatch(outputTranscriptBuffer.toString());
        }

        Log.d(TAG, "Normalized output transcription buffer: [" + normalizedBuffer + "]");

        SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
        String endMode = prefs.getString(ConfigData.KEY_END_MODE, ConfigData.DEFAULT_END_MODE);
        if (!ConfigData.END_MODE_AUTO.equals(endMode)) {
            // Only search for end markers in auto-end mode
            return;
        }

        String voiceMode = prefs.getString(ConfigData.KEY_VOICE_MODE, ConfigData.DEFAULT_VOICE_MODE);
        int arrayResId;
        if ("kitt".equals(voiceMode)) {
            arrayResId = R.array.assistant_end_markers_by_kitt;
        } else if ("asurada".equals(voiceMode)) {
            arrayResId = R.array.assistant_end_markers_by_asurada;
        } else {
            arrayResId = R.array.assistant_end_markers;
        }
        String[] endMarkers = getResources().getStringArray(arrayResId);
        for (String marker : endMarkers) {
            String normalizedMarker = normalizeForMatch(marker);

            if (normalizedBuffer.contains(normalizedMarker)) {
                if (!pendingDisconnect) {
                    pendingDisconnect = true;
                    Log.i(TAG, "End marker detected in onOutputTranscription: " + marker + ". Pending disconnect on turn complete.");
                }
                return;
            }
        }
    }

    @Override
    public void onTurnComplete() {
        SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);


        if (isTtsOnlyMode) {
            Log.i(TAG, "TTS Only Mode: Turn Complete. Disconnecting session.");
            disconnectSoonByEndRule("tts_only_turn_complete", 1200L);
            return;
        }

        if (isTextOnlyMode) {
            Log.i(TAG, "Text Only Mode (Single-shot): Turn Complete. Disconnecting session.");
            disconnectSoonByEndRule("text_only_turn_complete", 1200L);
            return;
        }

        if (pendingDisconnectOnMediaTrigger) {
            Log.i(TAG, "pendingDisconnectOnMediaTrigger is true. Triggering disconnect soon.");
            disconnectSoonByEndRule("media_trigger_turn_complete", 1500L);
            return;
        }

        String endMode = prefs.getString(ConfigData.KEY_END_MODE, ConfigData.DEFAULT_END_MODE);

        if (ConfigData.END_MODE_SINGLE.equals(endMode)) {
            Log.i(TAG, "Single response mode active. Triggering disconnect on turn complete.");
            disconnectSoonByEndRule("single_turn_complete", 1200L);
            return;
        }

        if (pendingDisconnect) {
            Log.i(TAG, "pendingDisconnect is true. Triggering disconnect soon.");
            disconnectSoonByEndRule("turn_complete", 1200L);
            return;
        }
        if (endingSession.get()) {
            return;
        }
        updateActivity();
    }

    @Override
    public void onError(Throwable t) {
        Log.e(TAG, "Gemini Error", t);
        mainHandler.removeCallbacks(watchdogRunnable);
        if (liveClient != null) {
            liveClient.disconnect();
        }
    }

    private void sendGeminiSessionEvent(String action) {
        sendBroadcast(new Intent(action).setPackage(SUB_AI_LISTENER_PACKAGE));
        sendBroadcast(new Intent(action).setPackage(getPackageName()));
    }

    @Override
    public void onConnected() {
        Log.i(TAG, "Gemini session CONNECTED successfully!");
        sendGeminiSessionEvent(ACTION_GEMINI_SESSION_STARTED);

        if (!isTtsOnlyMode) {
            requestAudioFocus(); // Request audio focus when connected to pause background music
        }
        showOverlay(); // Show SubAI running overlay on screen

        synchronized (outputTranscriptLock) {
            outputTranscriptBuffer.setLength(0);
        }

        endingSession.set(false);
        geminiSpeaking.set(false);
        pendingDisconnect = false;
        lastGeminiAudioAtMs = 0L;

        if (playbackManager != null) {
            playbackManager.startPcmPlayback(24000); // 24kHz speaker sample rate
        }
        if (useStartSound) {
            playEffect(ConfigData.VOICE_STREAM_START);
        }

        lastActivityTimeMs = android.os.SystemClock.elapsedRealtime();
        mainHandler.removeCallbacks(watchdogRunnable);
        mainHandler.postDelayed(watchdogRunnable, 1000);

        if (isTtsOnlyMode) {
            if (liveClient != null && liveClient.isConnected()) {
                Log.i(TAG, "TTS Only Mode: Sending announcement text -> " + ttsText);
                liveClient.sendText(ttsText);
            }
        } else if (isTextOnlyMode) {
            if (liveClient != null && liveClient.isConnected()) {
                Log.i(TAG, "Text Only Mode: Sending text input -> " + textInputContent);
                liveClient.sendText(textInputContent);
            }
        } else {
            mainHandler.postDelayed(() -> {
                if (liveClient != null && liveClient.isConnected()) {
                    Log.i(TAG, "STARTING MIC RECORDING NOW");
                    if (microphoneHandler != null) {
                        microphoneHandler.startRecording();
                    }
                }
            }, START_MIC_AFTER_CONNECTED_DELAY_MS);
        }
    }

    @Override
    public void onDisconnected() {
        Log.i(TAG, "Gemini session DISCONNECTED.");
        sendGeminiSessionEvent(ACTION_GEMINI_SESSION_ENDED);

        abandonAudioFocus(); // Abandon focus when session ends
        hideOverlay(); // Remove overlay when session ends

        synchronized (outputTranscriptLock) {
            outputTranscriptBuffer.setLength(0);
        }

        mainHandler.removeCallbacks(watchdogRunnable);
        endingSession.set(false);
        geminiSpeaking.set(false);
        pendingDisconnect = false;
        pendingDisconnectOnMediaTrigger = false;
        if (microphoneHandler != null) {
            microphoneHandler.stopRecording();
        }
        if (playbackManager != null) {
            playbackManager.stopPcmPlayback();
        }

        if (useEndSound) {
            endGeminiLiveNoiseReduction();
            playEffect(ConfigData.VOICE_STREAM_END);
        } else {
            endGeminiLiveNoiseReduction();
        }

        // Reset TTS and Text mode states
        isTtsOnlyMode = false;
        ttsText = "";
        isTextOnlyMode = false;
        textInputContent = "";
        useMemory = true;
        useStartSound = true;
        useEndSound = true;

        Intent intent = new Intent(ACTION_GEMINI_UPDATE);
        intent.putExtra("status", "disconnected");
        sendBroadcast(intent);

        // Chain to the next pending boot briefing query if available
        String nextQuery = pendingBootBriefingQueries.poll();
        if (nextQuery != null) {
            Log.i(TAG, "Chaining next boot briefing query: " + nextQuery);
            isBriefingMode = true;
            mainHandler.postDelayed(() -> {
                initializeClientAndConnect(false, null, true, nextQuery, false, false, false);
            }, 1000L); // 1.0s delay for clear UI/audio transition
        } else {
            isBriefingMode = false;
        }
    }

    @Override
    public void onSearchStart(String query) {
        updateActivity();
        Log.i(TAG, "Naver search started for: " + query);
        Intent intent = new Intent(ACTION_SEARCH_UPDATE);
        intent.putExtra("query", query);
        sendBroadcast(intent);
    }

    @Override
    public void onSearchComplete(String result) {
        updateActivity();
        Log.i(TAG, "Naver search completed, result length: " + result.length());
        Log.d(TAG, result);

        Intent intent = new Intent(ACTION_SEARCH_UPDATE);
        intent.putExtra("result", result);
        sendBroadcast(intent);
    }

    @Override
    public void onMediaAppTriggered(String appName) {
        SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE);
        String endMode = prefs.getString(ConfigData.KEY_END_MODE, ConfigData.DEFAULT_END_MODE);
        if (ConfigData.END_MODE_AUTO.equals(endMode)) {
            Log.i(TAG, "Media app " + appName + " triggered in Auto Mode. Suppressing mic and pending disconnect after turn.");
            pendingDisconnectOnMediaTrigger = true;
            if (microphoneHandler != null) {
                microphoneHandler.stopRecording();
            }
        }
    }

    // MicrophoneHandler.AudioChunkListener
    @Override
    public void onAudioChunk(byte[] chunk) {
        if (!geminiSpeaking.get()) {
            updateActivity();
            if (liveClient != null) {
                liveClient.sendAudio(chunk);
            }
        }
    }

    @Override
    public void onAudioStreamEnd() {
        if (liveClient != null) {
            liveClient.sendAudioStreamEnd();
        }
    }

    // AudioPlaybackManager.PlaybackStateListener
    @Override
    public void onPlaybackStarted() {
        Log.d(TAG, "onPlaybackStarted: AudioTrack active -> SUPPRESS MIC");
        mainHandler.removeCallbacks(releaseMicHoldRunnable);
        geminiSpeaking.set(true);
        if (microphoneHandler != null) {
            microphoneHandler.setInputSuppressed(true);
        }
    }

    private void beginGeminiLiveNoiseReduction() {
        ClimateNoiseReductionController.getInstance(this).beginListening(CLIMATE_INPUT_SOURCE_GEMINI);
    }

    private void endGeminiLiveNoiseReduction() {
        ClimateNoiseReductionController.getInstance(this).endListening(CLIMATE_INPUT_SOURCE_GEMINI);
    }

    @Override
    public void onPlaybackFinished() {
        Log.d(TAG, "onPlaybackFinished: Speaker finished. Scheduling 100ms echo hold before resuming MIC.");
        mainHandler.removeCallbacks(releaseMicHoldRunnable);
        mainHandler.postDelayed(releaseMicHoldRunnable, 100L);
    }

    private void disconnectSoonByEndRule(String reason, long delayMs) {
        if (!endingSession.compareAndSet(false, true)) {
            return;
        }
        Log.i(TAG, "End rule detected. Disconnecting soon. reason=" + reason + ", delayMs=" + delayMs);
        mainHandler.removeCallbacks(watchdogRunnable);
        mainHandler.postDelayed(() -> {
            if (liveClient != null && liveClient.isConnected()) {
                liveClient.disconnect();
            }
        }, delayMs);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Gemini Live Service Channel",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String contentText) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Gemini Live")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build();
    }

    private void showOverlay() {
         mainHandler.post(() -> {
             try {
                 if (!android.provider.Settings.canDrawOverlays(this)) {
                     Log.i(TAG, "No overlay permission, cannot show SubAI status overlay.");
                     return;
                 }
                 if (overlayView != null) {
                     return; // already shown
                 }

                 windowManager = (android.view.WindowManager) getSystemService(Context.WINDOW_SERVICE);
                 
                 SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
                 String voiceMode = prefs.getString(ConfigData.KEY_VOICE_MODE, ConfigData.DEFAULT_VOICE_MODE);

                 android.view.WindowManager.LayoutParams params;

                 if ("kitt".equals(voiceMode)) {
                     KittScannerView scanner = new KittScannerView(this);
                     overlayView = scanner;

                     params = new android.view.WindowManager.LayoutParams(
                             dpToPx(200), // 200dp wide
                             dpToPx(16),  // 16dp high
                             android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O ?
                                     android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                                     android.view.WindowManager.LayoutParams.TYPE_PHONE,
                             android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                     android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                                     android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                             android.graphics.PixelFormat.TRANSLUCENT
                     );
                 } else if ("asurada".equals(voiceMode)) {
                      AsuradaView asuradaView = new AsuradaView(this);
                      overlayView = asuradaView;

                      params = new android.view.WindowManager.LayoutParams(
                              dpToPx(320),
                              dpToPx(56),
                              android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O ?
                                      android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                                      android.view.WindowManager.LayoutParams.TYPE_PHONE,
                              android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                      android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                                      android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                              android.graphics.PixelFormat.TRANSLUCENT
                      );
                 } else {
                     // Create the capsule TextView
                     android.widget.TextView tv = new android.widget.TextView(this);
                     tv.setText("SubAI 작동중 🎙️");
                     tv.setTextColor(android.graphics.Color.parseColor("#00E676"));
                     tv.setTextSize(13);
                     tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                     tv.setGravity(android.view.Gravity.CENTER);
                     
                     int paddingHoriz = dpToPx(16);
                     int paddingVert = dpToPx(8);
                     tv.setPadding(paddingHoriz, paddingVert, paddingHoriz, paddingVert);
                     
                     android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                     gd.setColor(android.graphics.Color.parseColor("#F2121212")); // Almost solid dark background
                     gd.setCornerRadius(dpToPx(18));
                     gd.setStroke(dpToPx(1.5f), android.graphics.Color.parseColor("#00E676"));
                     tv.setBackground(gd);

                     overlayView = tv;

                     params = new android.view.WindowManager.LayoutParams(
                             android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                             android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                             android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O ?
                                     android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                                     android.view.WindowManager.LayoutParams.TYPE_PHONE,
                             android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                     android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                                     android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                             android.graphics.PixelFormat.TRANSLUCENT
                     );
                 }

                 params.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
                 params.y = dpToPx(80); // 80dp down from screen top

                 windowManager.addView(overlayView, params);
                 Log.i(TAG, "SubAI status overlay view added to WindowManager.");
             } catch (Exception e) {
                 Log.e(TAG, "Failed to show overlay view", e);
             }
         });
     }

     private void hideOverlay() {
         mainHandler.post(() -> {
             try {
                 if (windowManager != null && overlayView != null) {
                     windowManager.removeView(overlayView);
                     Log.i(TAG, "SubAI status overlay view removed from WindowManager.");
                 }
             } catch (Exception e) {
                 Log.e(TAG, "Failed to hide overlay view", e);
             } finally {
                 overlayView = null;
                 windowManager = null;
             }
         });
     }

     private int dpToPx(float dp) {
         return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
     }

    private static String normalizeForMatch(String text) {
        if (text == null) return "";
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[\\s\\p{P}\\p{S}]+", "");
    }
}
