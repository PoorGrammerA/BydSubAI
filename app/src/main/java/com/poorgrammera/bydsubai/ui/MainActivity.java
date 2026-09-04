package com.poorgrammera.bydsubai.ui;
import com.poorgrammera.bydsubai.BuildConfig;
import com.poorgrammera.bydsubai.R;
import com.poorgrammera.bydsubai.audio.GGWaveReceiver;
import com.poorgrammera.bydsubai.audio.MediaBrowserManager;
import com.poorgrammera.bydsubai.data.ConfigData;
import com.poorgrammera.bydsubai.data.SecureCredentialStore;
import com.poorgrammera.bydsubai.data.DestinationDbHelper;
import com.poorgrammera.bydsubai.data.SavedDestination;
import com.poorgrammera.bydsubai.integration.AppIntentConfigHelper;
import com.poorgrammera.bydsubai.service.BydAutoService;
import com.poorgrammera.bydsubai.service.GeminiLiveService;
import com.poorgrammera.bydsubai.tool.ExternalToolApprovalStore;
import com.poorgrammera.bydsubai.tool.ExternalToolDescriptor;
import com.poorgrammera.bydsubai.tool.ExternalToolDiscovery;
import com.poorgrammera.bydsubai.tool.ExternalToolRegistry;
import com.poorgrammera.bydsubai.tool.ExternalToolResults;
import com.poorgrammera.bydsubai.vehicle.ClimateNoiseReductionController;
import com.poorgrammera.bydsubai.vehicle.VehicleClassLoaderFactory;
import com.poorgrammera.bydsubai.adb.AdbShellExecutor;


import android.content.ComponentName;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.View;
import com.poorgrammera.bydsubai.util.MediaPlayerManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.CheckBox;
import android.widget.SeekBar;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final String CLIMATE_INPUT_SOURCE_GGWAVE = "ggwave";

    private static final String TAG = "MainActivity";
    private static final int RECORD_AUDIO_REQUEST_CODE = 2001;
    private static final int GGWAVE_RECORD_AUDIO_REQUEST_CODE = 2002;
    private static final int PERMISSION_REQUEST_CODE = 2003;
    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 3001;

    private static final String[] BYD_VEHICLE_PERMISSIONS = {
            "android.permission.BYDAUTO_SETTING_COMMON",
            "android.permission.BYDAUTO_SETTING_GET",
            "android.permission.BYDAUTO_SETTING_SET",
            "android.permission.BYDAUTO_AC_COMMON",
            "android.permission.BYDAUTO_AC_GET",
            "android.permission.BYDAUTO_BODYWORK_COMMON",
            "android.permission.BYDAUTO_BODYWORK_GET",
            "android.permission.BYDAUTO_BODYWORK_SET"
    };

    private final ExecutorService executorService = Executors.newSingleThreadExecutor(runnable ->
            new Thread(runnable, "MainActivity-Worker"));

    private EditText etGeminiApiKey;
    private EditText etUserProfileName;
    private TextView tvUserNameLabel;
    private boolean isUpdatingNameProgrammatically = false;
    private EditText etGeminiLiveModel;
    private EditText etGeminiNormalModel;
    private RadioGroup rgVoiceMode;
    private RadioButton rbVoiceRumi;
    private RadioButton rbVoiceKitt;
    private RadioButton rbVoiceAsurada;
    private RadioGroup rgMapProvider;
    private RadioButton rbMapNaver;
    private RadioButton rbMapTmap;
    private RadioGroup rgEndMode;
    private RadioButton rbEndAuto;
    private RadioButton rbEndSingle;
    private RadioButton rbEndContinuous;

    private TextView tvServiceStatus;
    private CheckBox cbAlertGearN;
    private CheckBox cbAlertGearR;
    private CheckBox cbAlertGearD;
    private CheckBox cbAlertGearP;
    private CheckBox cbAlertCruise;
    private CheckBox cbAlertSport;
    private CheckBox cbAlertSnow;
    private CheckBox cbAlertRadar;
    private CheckBox cbAlertBsd;
    private CheckBox cbAlertRecycle;
    private CheckBox cbAlertBoot;
    private CheckBox cbBootWeatherBriefing;
    private CheckBox cbBootNewsBriefing;
    private CheckBox cbUseMemory;
    private CheckBox cbReduceAcFanDuringAudioInput;
    private CheckBox cbUseSlopeCheck;
    private CheckBox cbToolAc;
    private CheckBox cbToolSeat;
    private CheckBox cbToolWheel;
    private CheckBox cbToolTrunk;
    private CheckBox cbToolWindow;
    private CheckBox cbToolSunroof;
    private CheckBox cbToolSunshade;

    private SeekBar sbStreamStartVol;
    private TextView tvStreamStartVolValue;
    private SeekBar sbStreamEndVol;
    private TextView tvStreamEndVolValue;
    private TextView tvUserText;
    private TextView tvGeminiText;
    private TextView tvSearchStatus;
    private TextView tvGGWaveStatus;
    private Button btnToggleGGWave;

    private AdbShellExecutor adbExecutor;
    private Button btnAdbConnect;
    private volatile String adbStatusText = "DISCONNECTED";
    private LinearLayout layoutDestinationsContainer;

    private GGWaveReceiver ggWaveReceiver;
    private boolean pendingStartGGWave = false;
    private boolean isFirstStart = true;
    private boolean isFirstStartSequenceRunning = false;
    private long lastExternalToolScanTime;

    private String accumulatedGeminiKey = null;
    private String accumulatedGeminiLiveModel = null;
    private String accumulatedGeminiNormalModel = null;

    private View layoutDeveloperOptions;
    private int devModeClickCount = 0;
    private long lastDevModeClickTime = 0;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private final Runnable statusPollRunnable = new Runnable() {
        @Override
        public void run() {
            updateServiceStatusUI();
            mainHandler.postDelayed(this, 2000);
        }
    };

    private final Runnable firstStartStep1 = new Runnable() {
        @Override
        public void run() {
            if (!isFirstStartSequenceRunning) return;
            if (ggWaveReceiver != null && !ggWaveReceiver.isListening()) {
                startGGWaveReceiver();
            }
            mainHandler.postDelayed(firstStartStep2, 1200);
        }
    };

    private final Runnable firstStartStep2 = new Runnable() {
        @Override
        public void run() {
            if (!isFirstStartSequenceRunning) return;
            if (ggWaveReceiver != null && ggWaveReceiver.isListening()) {
                stopGGWaveReceiver();
            }
            mainHandler.postDelayed(firstStartStep3, 800);
        }
    };

    private final Runnable firstStartStep3 = new Runnable() {
        @Override
        public void run() {
            if (!isFirstStartSequenceRunning) return;
            isFirstStartSequenceRunning = false;
            if (ggWaveReceiver != null && !ggWaveReceiver.isListening()) {
                startGGWaveReceiver();
                if (btnToggleGGWave != null) {
                    btnToggleGGWave.setEnabled(true);
                    btnToggleGGWave.setText(R.string.btn_stop_listening);
                    btnToggleGGWave.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E53935")));
                }
                if (tvGGWaveStatus != null) {
                    tvGGWaveStatus.setText(R.string.receiver_status_listening);
                }
            }
        }
    };

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;

            if (GeminiLiveService.ACTION_GEMINI_UPDATE.equals(intent.getAction())) {
                if (intent.hasExtra("user_text")) {
                    String userText = intent.getStringExtra("user_text");
                    tvUserText.setText(getString(R.string.user_speech_prefix) + userText);
                }
                if (intent.hasExtra("gemini_text")) {
                    String geminiText = intent.getStringExtra("gemini_text");
                    tvGeminiText.setText(getString(R.string.gemini_answer_prefix) + geminiText);
                }
                if (intent.hasExtra("status")) {
                    String status = intent.getStringExtra("status");
                    if ("disconnected".equals(status)) {
                        tvSearchStatus.setText(R.string.naver_search_status_idle);
                    }
                }
            } else if (GeminiLiveService.ACTION_SEARCH_UPDATE.equals(intent.getAction())) {
                if (intent.hasExtra("query")) {
                    String query = intent.getStringExtra("query");
                    tvSearchStatus.setText(getString(R.string.naver_search_status_searching, query));
                }
                if (intent.hasExtra("result")) {
                    String result = intent.getStringExtra("result");
                    tvSearchStatus.setText(getString(R.string.naver_search_status_completed, result.length()));
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String fingerprint = android.os.Build.FINGERPRINT;
        Log.d("BYD_INFO", "Fingerprint: " + fingerprint);
        boolean isDilink3 = fingerprint != null && fingerprint.contains("DiLink3");
        getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE).edit()
                .putBoolean(ConfigData.KEY_IS_DILINK3, isDilink3)
                .apply();

        MediaBrowserManager.init(this);
        AppIntentConfigHelper.init(this);
        SecureCredentialStore.removeRetiredCredentials(this);

        etGeminiApiKey = findViewById(R.id.et_gemini_api_key);
        etGeminiLiveModel = findViewById(R.id.et_gemini_live_model);
        etGeminiNormalModel = findViewById(R.id.et_gemini_normal_model);
        rgVoiceMode = findViewById(R.id.rg_voice_mode);
        rbVoiceRumi = findViewById(R.id.rb_voice_rumi);
        rbVoiceKitt = findViewById(R.id.rb_voice_kitt);
        rbVoiceAsurada = findViewById(R.id.rb_voice_asurada);
        rgMapProvider = findViewById(R.id.rg_map_provider);
        rbMapNaver = findViewById(R.id.rb_map_naver);
        rbMapTmap = findViewById(R.id.rb_map_tmap);
        rgEndMode = findViewById(R.id.rg_end_mode);
        rbEndAuto = findViewById(R.id.rb_end_auto);
        rbEndSingle = findViewById(R.id.rb_end_single);
        rbEndContinuous = findViewById(R.id.rb_end_continuous);
        tvServiceStatus = findViewById(R.id.tv_service_status);
        btnAdbConnect = findViewById(R.id.btn_adb_connect);

        adbExecutor = new AdbShellExecutor(this);
        AdbShellExecutor.setAuthCallback(new AdbShellExecutor.AdbAuthCallback() {
            @Override
            public void onAuthPending() {
                adbStatusText = "AUTH PENDING";
                runOnUiThread(() -> updateServiceStatusUI());
            }

            @Override
            public void onAuthGranted() {
                adbStatusText = "CONNECTED";
                runOnUiThread(() -> {
                    updateServiceStatusUI();
                    grantReadLogsPermission();
                    grantVehiclePermissionsIfNeeded();
                });
            }

            @Override
            public void onAuthFailed(String error) {
                adbStatusText = "FAILED: " + error;
                runOnUiThread(() -> updateServiceStatusUI());
            }
        });

        btnAdbConnect.setOnClickListener(v -> triggerAdbConnection());
        triggerAdbConnection();
        tvUserText = findViewById(R.id.tv_user_text);
        tvGeminiText = findViewById(R.id.tv_gemini_text);
        tvSearchStatus = findViewById(R.id.tv_search_status);
        tvGGWaveStatus = findViewById(R.id.tv_ggwave_status);
        btnToggleGGWave = findViewById(R.id.btn_toggle_ggwave);

        cbAlertGearN = findViewById(R.id.cb_alert_gear_n);
        cbAlertGearR = findViewById(R.id.cb_alert_gear_r);
        cbAlertGearD = findViewById(R.id.cb_alert_gear_d);
        cbAlertGearP = findViewById(R.id.cb_alert_gear_p);
        cbAlertCruise = findViewById(R.id.cb_alert_cruise);
        cbAlertSport = findViewById(R.id.cb_alert_sport);
        cbAlertSnow = findViewById(R.id.cb_alert_snow);
        cbAlertRadar = findViewById(R.id.cb_alert_radar);
        cbAlertBsd = findViewById(R.id.cb_alert_bsd);
        cbAlertRecycle = findViewById(R.id.cb_alert_recycle);
        cbAlertBoot = findViewById(R.id.cb_alert_boot);
        cbBootWeatherBriefing = findViewById(R.id.cb_boot_weather_briefing);
        cbBootNewsBriefing = findViewById(R.id.cb_boot_news_briefing);
        cbUseMemory = findViewById(R.id.cb_use_memory);
        cbReduceAcFanDuringAudioInput = findViewById(R.id.cb_reduce_ac_fan_during_audio_input);
        cbUseSlopeCheck = findViewById(R.id.cb_use_slope_check);
        cbToolAc = findViewById(R.id.cb_tool_ac);
        cbToolSeat = findViewById(R.id.cb_tool_seat);
        cbToolWheel = findViewById(R.id.cb_tool_wheel);
        cbToolTrunk = findViewById(R.id.cb_tool_trunk);
        cbToolWindow = findViewById(R.id.cb_tool_window);
        cbToolSunroof = findViewById(R.id.cb_tool_sunroof);
        cbToolSunshade = findViewById(R.id.cb_tool_sunshade);


        initAlertCheckBox(cbAlertGearN, ConfigData.KEY_ALERT_GEAR_N);
        initAlertCheckBox(cbAlertGearR, ConfigData.KEY_ALERT_GEAR_R);
        initAlertCheckBox(cbAlertGearD, ConfigData.KEY_ALERT_GEAR_D);
        initAlertCheckBox(cbAlertGearP, ConfigData.KEY_ALERT_GEAR_P);
        initAlertCheckBox(cbAlertCruise, ConfigData.KEY_ALERT_CRUISE);
        initAlertCheckBox(cbAlertSport, ConfigData.KEY_ALERT_SPORT);
        initAlertCheckBox(cbAlertSnow, ConfigData.KEY_ALERT_SNOW);
        initAlertCheckBox(cbAlertRadar, ConfigData.KEY_ALERT_RADAR);
        initAlertCheckBox(cbAlertBsd, ConfigData.KEY_ALERT_BSD);
        initAlertCheckBox(cbAlertRecycle, ConfigData.KEY_ALERT_RECYCLE);
        initAlertCheckBox(cbAlertBoot, ConfigData.KEY_ALERT_BOOT);
        initAlertCheckBox(cbBootWeatherBriefing, ConfigData.KEY_BOOT_WEATHER_BRIEFING);
        initAlertCheckBox(cbBootNewsBriefing, ConfigData.KEY_BOOT_NEWS_BRIEFING);
        initAlertCheckBox(cbUseMemory, ConfigData.KEY_USE_MEMORY);
        initAlertCheckBox(cbReduceAcFanDuringAudioInput, ConfigData.KEY_REDUCE_AC_FAN_DURING_AUDIO_INPUT);
        initAlertCheckBox(cbUseSlopeCheck, ConfigData.KEY_USE_SLOPE_CHECK);
        initAlertCheckBox(cbToolAc, ConfigData.KEY_ENABLE_TOOL_AC);
        initAlertCheckBox(cbToolSeat, ConfigData.KEY_ENABLE_TOOL_SEAT);
        initAlertCheckBox(cbToolWheel, ConfigData.KEY_ENABLE_TOOL_WHEEL);
        initAlertCheckBox(cbToolTrunk, ConfigData.KEY_ENABLE_TOOL_TRUNK);
        initAlertCheckBox(cbToolWindow, ConfigData.KEY_ENABLE_TOOL_WINDOW);
        initAlertCheckBox(cbToolSunroof, ConfigData.KEY_ENABLE_TOOL_SUNROOF);
        initAlertCheckBox(cbToolSunshade, ConfigData.KEY_ENABLE_TOOL_SUNSHADE);


        // Bind and initialize start/end sound volume seekbars
        sbStreamStartVol = findViewById(R.id.sb_stream_start_vol);
        tvStreamStartVolValue = findViewById(R.id.tv_stream_start_vol_value);
        sbStreamEndVol = findViewById(R.id.sb_stream_end_vol);
        tvStreamEndVolValue = findViewById(R.id.tv_stream_end_vol_value);

        initVolumeSeekBar(sbStreamStartVol, tvStreamStartVolValue, ConfigData.KEY_VOLUME_STREAM_START, ConfigData.DEFAULT_VOLUME_STREAM_START);
        initVolumeSeekBar(sbStreamEndVol, tvStreamEndVolValue, ConfigData.KEY_VOLUME_STREAM_END, ConfigData.DEFAULT_VOLUME_STREAM_END);

        layoutDestinationsContainer = findViewById(R.id.layout_destinations_container);
        findViewById(R.id.btn_add_destination).setOnClickListener(v -> showAddEditDialog(null));
        findViewById(R.id.btn_sound_select).setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(MainActivity.this, SoundSelectActivity.class);
            startActivity(intent);
        });
        findViewById(R.id.btn_manage_external_tools).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ToolManagerActivity.class)));
        loadSavedDestinations();
        findViewById(R.id.btn_open_autostart).setOnClickListener(v -> openAutoStartSettings(MainActivity.this));

        TextView tvTitle = findViewById(R.id.tv_title);
        tvTitle.setText(getString(R.string.main_title) + " V" + BuildConfig.VERSION_CODE);
        layoutDeveloperOptions = findViewById(R.id.layout_developer_options);
        SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE);
        boolean isDevMode = prefs.getBoolean(ConfigData.KEY_DEVELOPER_MODE, false);
        if(!isDilink3) {
            isDevMode = true;
        }
        layoutDeveloperOptions.setVisibility(isDevMode ? View.VISIBLE : View.GONE);

        tvTitle.setOnClickListener(v -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastDevModeClickTime > 2000) {
                devModeClickCount = 0;
            }
            lastDevModeClickTime = currentTime;
            devModeClickCount++;

            if (prefs.getBoolean(ConfigData.KEY_DEVELOPER_MODE, false)) {
                Toast.makeText(MainActivity.this, R.string.toast_dev_mode_already_active, Toast.LENGTH_SHORT).show();
                return;
            }

            if (devModeClickCount >= 5) {
                prefs.edit().putBoolean(ConfigData.KEY_DEVELOPER_MODE, true).apply();
                layoutDeveloperOptions.setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, R.string.toast_dev_mode_activated, Toast.LENGTH_SHORT).show();
                devModeClickCount = 0;
            } else {
                int remaining = 5 - devModeClickCount;
                Toast.makeText(MainActivity.this, getString(R.string.toast_dev_mode_steps_remaining, remaining), Toast.LENGTH_SHORT).show();
            }
        });

        ggWaveReceiver = new GGWaveReceiver(this, new GGWaveReceiver.Listener() {
            @Override
            public void onMessageReceived(String message) {
                runOnUiThread(() -> handleDecodedMessage(message));
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (tvGGWaveStatus != null) {
                        tvGGWaveStatus.setText(getString(R.string.receiver_status_error, error));
                    }
                    Toast.makeText(MainActivity.this, getString(R.string.toast_receiver_error, error), Toast.LENGTH_SHORT).show();
                    stopGGWaveListening();
                });
            }
        });

        btnToggleGGWave.setOnClickListener(v -> {
            if (ggWaveReceiver.isListening() || isFirstStartSequenceRunning) {
                stopGGWaveListening();
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, GGWAVE_RECORD_AUDIO_REQUEST_CODE);
                } else {
                    accumulatedGeminiKey = null;
                    accumulatedGeminiLiveModel = null;
                    accumulatedGeminiNormalModel = null;

                    if (isFirstStart) {
                        isFirstStart = false;
                        runFirstStartSequence(100);
                    } else {
                        startGGWaveReceiver();
                        btnToggleGGWave.setText(R.string.btn_stop_listening);
                        btnToggleGGWave.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E53935")));
                        if (tvGGWaveStatus != null) {
                            tvGGWaveStatus.setText(R.string.receiver_status_listening);
                        }
                    }
                }
            }
        });

        Button btnTriggerSnow = findViewById(R.id.btn_trigger_snow);
        Button btnStopGemini = findViewById(R.id.btn_stop_gemini);
        Button btnVehicleControl = findViewById(R.id.btn_vehicle_control);

        loadConfig();

        rgVoiceMode.setOnCheckedChangeListener((group, checkedId) -> {
            String voiceMode;
            if (checkedId == R.id.rb_voice_rumi) {
                voiceMode = "rumi";
                playBgm(R.raw.rumi_bgm);
            } else if (checkedId == R.id.rb_voice_kitt) {
                voiceMode = "kitt";
                playBgm(R.raw.kitt_bgm);
            } else if (checkedId == R.id.rb_voice_asurada) {
                voiceMode = "asurada";
                playBgm(R.raw.asurada_bgm);
            } else {
                voiceMode = "rumi";
                stopBgm();
            }
            getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE).edit()
                    .putString(ConfigData.KEY_VOICE_MODE, voiceMode)
                    .apply();
            updateUserProfileNameField();
        });

        rgEndMode.setOnCheckedChangeListener((group, checkedId) -> {
            String endMode;
            if (checkedId == R.id.rb_end_single) {
                endMode = ConfigData.END_MODE_SINGLE;
            } else if (checkedId == R.id.rb_end_continuous) {
                endMode = ConfigData.END_MODE_CONTINUOUS;
            } else {
                endMode = ConfigData.END_MODE_AUTO;
            }
            getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE).edit()
                    .putString(ConfigData.KEY_END_MODE, endMode)
                    .apply();
        });

        rgMapProvider.setOnCheckedChangeListener((group, checkedId) -> {
            String mapProvider = checkedId == R.id.rb_map_tmap ? "tmap" : "naver";
            getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE).edit()
                    .putString(ConfigData.KEY_MAP_PROVIDER, mapProvider)
                    .apply();
            Log.d(TAG, "Map provider set to: " + mapProvider);
        });

        initAutoSaveEditText(etGeminiApiKey, ConfigData.KEY_GEMINI_API_KEY);
        initAutoSaveEditText(etGeminiLiveModel, ConfigData.KEY_GEMINI_LIVE_MODEL);
        initAutoSaveEditText(etGeminiNormalModel, ConfigData.KEY_GEMINI_NORMAL_MODEL);

        tvUserNameLabel = findViewById(R.id.tv_user_name_label);
        etUserProfileName = findViewById(R.id.et_user_profile_name);

        etUserProfileName.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (isUpdatingNameProgrammatically) return;

                SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE);
                String voiceMode = prefs.getString(ConfigData.KEY_VOICE_MODE, ConfigData.DEFAULT_VOICE_MODE);
                String lang = getResources().getConfiguration().getLocales().get(0).toLanguageTag();
                String key;

                if ("kitt".equals(voiceMode)) {
                    if (lang.startsWith("ko")) key = ConfigData.KEY_USER_NAME_KITT_KO;
                    else if (lang.startsWith("ja")) key = ConfigData.KEY_USER_NAME_KITT_JA;
                    else key = ConfigData.KEY_USER_NAME_KITT_EN;
                } else if ("asurada".equals(voiceMode)) {
                    if (lang.startsWith("ko")) key = ConfigData.KEY_USER_NAME_ASURADA_KO;
                    else if (lang.startsWith("ja")) key = ConfigData.KEY_USER_NAME_ASURADA_JA;
                    else key = ConfigData.KEY_USER_NAME_ASURADA_EN;
                } else {
                    if (lang.startsWith("ko")) key = ConfigData.KEY_USER_NAME_RUMI_KO;
                    else if (lang.startsWith("ja")) key = ConfigData.KEY_USER_NAME_RUMI_JA;
                    else key = ConfigData.KEY_USER_NAME_RUMI_EN;
                }

                prefs.edit().putString(key, s.toString().trim()).apply();
            }
        });

        setupSecretFieldMasking(etGeminiApiKey);
        btnTriggerSnow.setOnClickListener(v -> {
            Toast.makeText(this, R.string.toast_starting_gemini, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(GeminiLiveService.ACTION_TRIGGER_GEMINI);
            sendBroadcast(intent);
        });
        btnStopGemini.setOnClickListener(v -> {
            Intent intent = new Intent(GeminiLiveService.ACTION_STOP_GEMINI);
            sendBroadcast(intent);
        });
        btnVehicleControl.setOnClickListener(v -> {
            Intent intent = new Intent(this, VehicleControlActivity.class);
            startActivity(intent);
        });

        Button btnLogView = findViewById(R.id.btn_log_view);
        btnLogView.setOnClickListener(v -> {
            Intent intent = new Intent(this, LogViewActivity.class);
            startActivity(intent);
        });

        Button btnAddShortcutRumi = findViewById(R.id.btn_add_shortcut_rumi);
        btnAddShortcutRumi.setOnClickListener(v -> requestPinnedShortcut("rumi", getString(R.string.shortcut_label), getString(R.string.shortcut_long_label), R.mipmap.ic_shortcut_rumi));

        Button btnAddShortcutKitt = findViewById(R.id.btn_add_shortcut_kitt);
        btnAddShortcutKitt.setOnClickListener(v -> requestPinnedShortcut("kitt", getString(R.string.shortcut_kitt_label), getString(R.string.shortcut_kitt_long_label), R.mipmap.ic_shortcut_kitt));

        Button btnAddShortcutAsurada = findViewById(R.id.btn_add_shortcut_asurada);
        btnAddShortcutAsurada.setOnClickListener(v -> requestPinnedShortcut("asurada", getString(R.string.shortcut_asurada_label), getString(R.string.shortcut_asurada_long_label), R.mipmap.ic_shortcut_asurada));

        updateUserProfileNameField();
        checkPermissions();
    }

    private void scanForNewExternalTools() {
        ExternalToolApprovalStore approvalStore = new ExternalToolApprovalStore(this);
        List<ExternalToolDescriptor> pendingApproval = new ArrayList<>();
        new ExternalToolDiscovery(this).discover(new ExternalToolDiscovery.Listener() {
            @Override
            public void onProvider(ExternalToolDescriptor descriptor, android.app.PendingIntent settingsIntent) {
                if (!approvalStore.isApproved(descriptor) && !approvalStore.isDismissed(descriptor)) {
                    pendingApproval.add(descriptor);
                }
            }

            @Override
            public void onProviderError(ComponentName component, String message) {
                Log.w(TAG, "Unable to inspect external Tool app " + component + ": " + message);
            }

            @Override
            public void onComplete() {
                showExternalToolApproval(pendingApproval, 0, approvalStore);
            }
        });
    }

    private void showExternalToolApproval(List<ExternalToolDescriptor> descriptors, int index,
                                          ExternalToolApprovalStore approvalStore) {
        if (index >= descriptors.size() || isFinishing() || isDestroyed()) return;
        ExternalToolDescriptor descriptor = descriptors.get(index);
        List<String> toolNames = new ArrayList<>();
        for (ExternalToolDescriptor.Function function : descriptor.functions()) {
            toolNames.add("• " + function.displayName);
        }
        String fingerprint = descriptor.certificateSha256;
        if (fingerprint.length() > 16) fingerprint = fingerprint.substring(0, 16) + "…";
        String message = getString(R.string.external_tools_approval_message,
                descriptor.component.getPackageName(), fingerprint,
                android.text.TextUtils.join("\n", toolNames))
                + "\n\n" + getString(R.string.external_tools_trust_warning);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(descriptor.providerName())
                .setMessage(message)
                .setPositiveButton(R.string.external_tools_approve, (ignored, which) -> {
                    try { approvalStore.approve(descriptor); }
                    catch (Exception e) { Log.e(TAG, "Failed to approve external Tool app", e); }
                    showExternalToolApproval(descriptors, index + 1, approvalStore);
                })
                .setNegativeButton(R.string.external_tools_reject, (ignored, which) -> {
                    approvalStore.dismiss(descriptor);
                    showExternalToolApproval(descriptors, index + 1, approvalStore);
                })
                .create();
        dialog.setOnCancelListener(ignored -> {
            approvalStore.dismiss(descriptor);
            showExternalToolApproval(descriptors, index + 1, approvalStore);
        });
        dialog.show();
    }

    private void loadConfig() {
        SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE);
        if (!prefs.contains(ConfigData.KEY_GEMINI_LIVE_MODEL)) {
            prefs.edit()
                    .putString(ConfigData.KEY_GEMINI_LIVE_MODEL, ConfigData.DEFAULT_GEMINI_LIVE_MODEL)
                    .putString(ConfigData.KEY_GEMINI_NORMAL_MODEL, ConfigData.DEFAULT_GEMINI_NORMAL_MODEL)
                    .putString(ConfigData.KEY_VOICE_MODE, ConfigData.DEFAULT_VOICE_MODE)
                    .putString(ConfigData.KEY_MAP_PROVIDER, ConfigData.DEFAULT_MAP_PROVIDER)
                    .putString(ConfigData.KEY_END_MODE, ConfigData.DEFAULT_END_MODE)
                    .putString(ConfigData.KEY_USER_NAME_RUMI_KO, ConfigData.DEFAULT_USER_NAME_RUMI_KO)
                    .putString(ConfigData.KEY_USER_NAME_RUMI_EN, ConfigData.DEFAULT_USER_NAME_RUMI_EN)
                    .putString(ConfigData.KEY_USER_NAME_RUMI_JA, ConfigData.DEFAULT_USER_NAME_RUMI_JA)
                    .putString(ConfigData.KEY_USER_NAME_KITT_KO, ConfigData.DEFAULT_USER_NAME_KITT_KO)
                    .putString(ConfigData.KEY_USER_NAME_KITT_EN, ConfigData.DEFAULT_USER_NAME_KITT_EN)
                    .putString(ConfigData.KEY_USER_NAME_KITT_JA, ConfigData.DEFAULT_USER_NAME_KITT_JA)
                    .putString(ConfigData.KEY_USER_NAME_ASURADA_KO, ConfigData.DEFAULT_USER_NAME_ASURADA_KO)
                    .putString(ConfigData.KEY_USER_NAME_ASURADA_EN, ConfigData.DEFAULT_USER_NAME_ASURADA_EN)
                    .putString(ConfigData.KEY_USER_NAME_ASURADA_JA, ConfigData.DEFAULT_USER_NAME_ASURADA_JA)
                    .apply();
        }
        etGeminiApiKey.setText(SecureCredentialStore.getString(this, ConfigData.KEY_GEMINI_API_KEY, ""));
        etGeminiLiveModel.setText(prefs.getString(ConfigData.KEY_GEMINI_LIVE_MODEL, ConfigData.DEFAULT_GEMINI_LIVE_MODEL));
        etGeminiNormalModel.setText(prefs.getString(ConfigData.KEY_GEMINI_NORMAL_MODEL, ConfigData.DEFAULT_GEMINI_NORMAL_MODEL));



        String voiceMode = prefs.getString(ConfigData.KEY_VOICE_MODE, "rumi");
        if ("kitt".equals(voiceMode)) rbVoiceKitt.setChecked(true);
        else if ("asurada".equals(voiceMode)) rbVoiceAsurada.setChecked(true);
        else rbVoiceRumi.setChecked(true);

        String mapProvider = prefs.getString(ConfigData.KEY_MAP_PROVIDER, "naver");
        if ("tmap".equals(mapProvider)) rbMapTmap.setChecked(true);
        else rbMapNaver.setChecked(true);

        String endMode = prefs.getString(ConfigData.KEY_END_MODE, ConfigData.END_MODE_AUTO);
        if (ConfigData.END_MODE_SINGLE.equals(endMode)) rbEndSingle.setChecked(true);
        else if (ConfigData.END_MODE_CONTINUOUS.equals(endMode)) rbEndContinuous.setChecked(true);
        else rbEndAuto.setChecked(true);
    }

    private void handleDecodedMessage(String message) {
        try {
            org.json.JSONObject json = new org.json.JSONObject(message);
            if (json.length() == 1 && json.has("?")) {
                String plainText = json.optString("?", "");
                if (plainText.isEmpty()) {
                    Toast.makeText(this, R.string.toast_clipboard_payload_empty,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                ClipboardManager clipboard =
                        (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("SubAI transferred text", plainText);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PersistableBundle extras = new PersistableBundle();
                    extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
                    clip.getDescription().setExtras(extras);
                }
                clipboard.setPrimaryClip(clip);
                if (tvGGWaveStatus != null) {
                    tvGGWaveStatus.setText(R.string.receiver_status_clipboard_copied);
                }
                Toast.makeText(this, R.string.toast_plain_text_copied,
                        Toast.LENGTH_LONG).show();
                stopGGWaveListening();
                return;
            }
            if (json.has("g")) {
                accumulatedGeminiKey = json.getString("g");
                etGeminiApiKey.setText(accumulatedGeminiKey);
            }
            if (json.has("m")) {
                accumulatedGeminiLiveModel = json.getString("m");
                etGeminiLiveModel.setText(accumulatedGeminiLiveModel);
            }
            if (json.has("n")) {
                accumulatedGeminiNormalModel = json.getString("n");
                etGeminiNormalModel.setText(accumulatedGeminiNormalModel);
            }
            if (accumulatedGeminiKey != null && accumulatedGeminiLiveModel != null
                    && accumulatedGeminiNormalModel != null) {
                if (tvGGWaveStatus != null) tvGGWaveStatus.setText(R.string.receiver_status_success);
                Toast.makeText(this, R.string.toast_keys_populated_success, Toast.LENGTH_LONG).show();
                stopGGWaveListening();

                SecureCredentialStore.putString(this, ConfigData.KEY_GEMINI_API_KEY, accumulatedGeminiKey);
                getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE).edit()
                        .putString(ConfigData.KEY_GEMINI_LIVE_MODEL, accumulatedGeminiLiveModel)
                        .putString(ConfigData.KEY_GEMINI_NORMAL_MODEL, accumulatedGeminiNormalModel)
                        .apply();
            } else {
                int count = 0;
                StringBuilder sb = new StringBuilder(getString(R.string.receiver_status_listening).replace("...", " ("));
                if (accumulatedGeminiKey != null) { count++; sb.append("g: OK"); } else { sb.append("g: -"); }
                sb.append(", ");
                if (accumulatedGeminiLiveModel != null) { count++; sb.append("m: OK"); } else { sb.append("m: -"); }
                sb.append(", ");
                if (accumulatedGeminiNormalModel != null) { count++; sb.append("n: OK"); } else { sb.append("n: -"); }
                sb.append(")");

                if (tvGGWaveStatus != null) {
                    tvGGWaveStatus.setText(sb.toString());
                }
                Toast.makeText(this, getString(R.string.toast_keys_received_partial, count), Toast.LENGTH_SHORT).show();
            }
        } catch (org.json.JSONException e) {
            Log.e(TAG, "Failed to parse JSON message", e);
        }
    }

    private void runFirstStartSequence(int initialDelay) {
        isFirstStartSequenceRunning = true;
        mainHandler.postDelayed(firstStartStep1, initialDelay);
    }

    private void stopGGWaveListening() {
        isFirstStartSequenceRunning = false;
        stopGGWaveReceiver();
        if (btnToggleGGWave != null) {
            btnToggleGGWave.setText(R.string.btn_start_listening);
            btnToggleGGWave.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF9800")));
        }
    }

    private void startGGWaveReceiver() {
        if (ggWaveReceiver == null || ggWaveReceiver.isListening()) return;
        ggWaveReceiver.start();
        ClimateNoiseReductionController.getInstance(this).beginListening(CLIMATE_INPUT_SOURCE_GGWAVE);
    }

    private void stopGGWaveReceiver() {
        if (ggWaveReceiver != null && ggWaveReceiver.isListening()) ggWaveReceiver.stop();
        ClimateNoiseReductionController.getInstance(this).endListening(CLIMATE_INPUT_SOURCE_GGWAVE);
    }

    private void checkPermissions() {
        boolean hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean hasFineLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasAudio || !hasFineLoc) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
            return;
        }

        // Check overlay permission (draw over other apps)
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.toast_overlay_permission_required, Toast.LENGTH_LONG).show();
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch overlay settings with package URI", e);
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
                } catch (Exception ex) {
                    Toast.makeText(this, R.string.toast_overlay_settings_unavailable, Toast.LENGTH_LONG).show();
                }
            }
            return;
        }

        startServices();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE || requestCode == RECORD_AUDIO_REQUEST_CODE) {
            boolean hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            boolean hasFineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (hasAudio && hasFineLocation) {
                startServices();
                Intent refreshGpsIntent = new Intent(this, BydAutoService.class)
                        .setAction(BydAutoService.ACTION_REFRESH_GPS_TRACKING);
                ContextCompat.startForegroundService(this, refreshGpsIntent);
            }
        } else if (requestCode == GGWAVE_RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingStartGGWave = true;
            }
        }
    }

    private void startServices() {
        ContextCompat.startForegroundService(this, new Intent(this, BydAutoService.class));
        ContextCompat.startForegroundService(this, new Intent(this, GeminiLiveService.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mainHandler.post(statusPollRunnable);
        checkPermissions();
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastExternalToolScanTime > 2_000L) {
            lastExternalToolScanTime = now;
            scanForNewExternalTools();
        }
        if (pendingStartGGWave) {
            pendingStartGGWave = false;
            runFirstStartSequence(1000);
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(GeminiLiveService.ACTION_GEMINI_UPDATE);
        filter.addAction(GeminiLiveService.ACTION_SEARCH_UPDATE);
        ContextCompat.registerReceiver(
                this,
                updateReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopBgm();
        stopGGWaveListening();
        mainHandler.removeCallbacks(statusPollRunnable);
        try { unregisterReceiver(updateReceiver); } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    private void playBgm(int rawResId) {
        MediaPlayerManager.getInstance(this).play(this, rawResId, 1.0f, mp -> Log.d(TAG, "BGM playback completed."));
    }

    private void stopBgm() {
        MediaPlayerManager.getInstance(this).stop();
    }

    private void triggerAdbConnection() {
        adbStatusText = "CONNECTING...";
        runOnUiThread(() -> updateServiceStatusUI());
        executorService.submit(() -> {
            try {
                adbExecutor.getOrCreateConnection();
            } catch (Exception e) {
                Log.e(TAG, "ADB connection trigger failed", e);
                adbStatusText = "FAILED: " + e.getMessage();
                runOnUiThread(() -> updateServiceStatusUI());
            }
        });
    }

    private boolean checkReadLogsPermission() {
        return checkSelfPermission(android.Manifest.permission.READ_LOGS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void grantReadLogsPermission() {
        if (checkReadLogsPermission()) {
            Log.d(TAG, "READ_LOGS permission already granted.");
            return;
        }

        Log.i(TAG, "Attempting to grant READ_LOGS permission via local ADB...");
        adbExecutor.execute("pm grant com.poorgrammera.bydsubai android.permission.READ_LOGS", new AdbShellExecutor.ShellCallback() {
            @Override
            public void onSuccess(String output) {
                Log.i(TAG, "READ_LOGS permission granted successfully: " + output);
                runOnUiThread(() -> updateServiceStatusUI());
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to grant READ_LOGS permission via ADB: " + error);
                runOnUiThread(() -> updateServiceStatusUI());
            }
        });
    }

    private void grantVehiclePermissionsIfNeeded() {
        boolean isDilink3 = getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE)
                .getBoolean(ConfigData.KEY_IS_DILINK3, true);
        if (isDilink3) {
            Log.d(TAG, "Skipping DiLink 5 BYDAUTO permission grants on DiLink 3.");
            return;
        }

        String packageName = getPackageName();
        StringBuilder script = new StringBuilder();
        script.append("failed=0\n");
        for (String permission : BYD_VEHICLE_PERMISSIONS) {
            script.append("pm grant ")
                    .append(packageName)
                    .append(' ')
                    .append(permission)
                    .append(" >/dev/null 2>&1 || failed=$((failed+1))\n");
        }
        script.append("echo BYDAUTO_GRANTS_FAILED=$failed\n");
        script.append("[ \"$failed\" -eq 0 ]");

        Log.i(TAG, "Attempting to grant DiLink 5 BYDAUTO permissions via local ADB...");
        adbExecutor.executeScript(script.toString(), new AdbShellExecutor.ShellCallback() {
            @Override
            public void onSuccess(String output) {
                Log.i(TAG, "DiLink 5 BYDAUTO permissions granted: " + output.trim());
                // A VehicleController may already have failed before ADB authorization.
                // Force the next initialization attempt to rediscover the now-authorized SDK.
                VehicleClassLoaderFactory.resetCache();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to grant one or more DiLink 5 BYDAUTO permissions: " + error);
            }
        });
    }

    private void updateServiceStatusUI() {
        boolean hasReadLogs = checkReadLogsPermission();
        boolean adbConnected = "CONNECTED".equals(adbStatusText);
        
        if (btnAdbConnect != null) {
            btnAdbConnect.setVisibility(adbConnected ? View.GONE : View.VISIBLE);
        }

        tvServiceStatus.setText(String.format(
                "• BydAutoService: %s\n• GeminiLiveService: %s\n• ADB Local Connection: %s\n• READ_LOGS Permission: %s", 
                BydAutoService.isRunning ? "RUNNING" : "STOPPED", 
                GeminiLiveService.isRunning ? "RUNNING" : "STOPPED",
                adbStatusText,
                hasReadLogs ? "GRANTED" : "NOT GRANTED"
        ));
    }

    private void initAlertCheckBox(CheckBox cb, String key) {
        boolean defaultValue = true;
        if (ConfigData.KEY_USE_SLOPE_CHECK.equals(key)) {
            defaultValue = ConfigData.DEFAULT_USE_SLOPE_CHECK;
        } else if (ConfigData.KEY_REDUCE_AC_FAN_DURING_AUDIO_INPUT.equals(key)) {
            defaultValue = ConfigData.DEFAULT_REDUCE_AC_FAN_DURING_AUDIO_INPUT;
        }

        cb.setChecked(getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE).getBoolean(key, defaultValue));
        cb.setOnCheckedChangeListener((v, isChecked) -> {
            getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE).edit().putBoolean(key, isChecked).apply();
            if (ConfigData.KEY_REDUCE_AC_FAN_DURING_AUDIO_INPUT.equals(key)) {
                ClimateNoiseReductionController.getInstance(this).setEnabled(isChecked);
            }
        });
    }

    private void initVolumeSeekBar(SeekBar sb, TextView tvValue, String key, int defaultValue) {
        SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE);
        int vol = prefs.getInt(key, defaultValue);
        sb.setProgress(vol);
        tvValue.setText(vol + "%");

        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvValue.setText(progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int finalProgress = seekBar.getProgress();
                prefs.edit().putInt(key, finalProgress).apply();
                Log.d(TAG, "Volume setting saved: key=" + key + ", val=" + finalProgress);
            }
        });
    }

    private void initAutoSaveEditText(EditText et, String key) {
        et.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (SecureCredentialStore.isCredentialKey(key)) {
                    SecureCredentialStore.putString(MainActivity.this, key, s.toString().trim());
                } else {
                    getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE).edit().putString(key, s.toString().trim()).apply();
                }
            }
        });
    }

    private void setupSecretFieldMasking(EditText et) {
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setOnFocusChangeListener((v, hasFocus) -> {
            et.setInputType(hasFocus ? android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            et.setSelection(et.getText().length());
        });
    }

    private void loadSavedDestinations() {
        layoutDestinationsContainer.removeAllViews();
        List<SavedDestination> list = DestinationDbHelper.getInstance(this).getAllDestinations();
        
        if (list.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText(R.string.destinations_empty);
            tvEmpty.setTextColor(0xFF888888);
            tvEmpty.setTextSize(14);
            tvEmpty.setPadding(0, 10, 0, 10);
            layoutDestinationsContainer.addView(tvEmpty);
            return;
        }
        
        for (final SavedDestination d : list) {
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setPadding(0, 12, 0, 12);
            itemLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
            
            View divider = new View(this);
            divider.setBackgroundColor(0xFF333333);
            LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
            
            LinearLayout textContainer = new LinearLayout(this);
            textContainer.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            textContainer.setLayoutParams(textParams);
            
            TextView tvAlias = new TextView(this);
            tvAlias.setText(d.getAlias());
            tvAlias.setTextColor(0xFF00E676);
            tvAlias.setTextSize(16);
            tvAlias.setTypeface(null, android.graphics.Typeface.BOLD);
            
            TextView tvAddress = new TextView(this);
            tvAddress.setText(d.getAddress());
            tvAddress.setTextColor(0xFFB0BEC5);
            tvAddress.setTextSize(13);
            tvAddress.setPadding(0, 4, 0, 0);
            
            textContainer.addView(tvAlias);
            textContainer.addView(tvAddress);
            itemLayout.addView(textContainer);
            
            LinearLayout buttonContainer = new LinearLayout(this);
            buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
            
            Button btnMap = new Button(this);
            btnMap.setText(R.string.btn_map);
            btnMap.setTextSize(11);
            btnMap.setBackgroundColor(0xFF00BCD4);
            btnMap.setTextColor(0xFFFFFFFF);
            LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, 80);
            mapParams.setMargins(6, 0, 6, 0);
            btnMap.setLayoutParams(mapParams);
            btnMap.setPadding(12, 0, 12, 0);
            btnMap.setOnClickListener(v -> viewOnGoogleMaps(d));
            
            Button btnEdit = new Button(this);
            btnEdit.setText(R.string.btn_edit);
            btnEdit.setTextSize(11);
            btnEdit.setBackgroundColor(0xFFFFA726);
            btnEdit.setTextColor(0xFFFFFFFF);
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, 80);
            editParams.setMargins(6, 0, 6, 0);
            btnEdit.setLayoutParams(editParams);
            btnEdit.setPadding(12, 0, 12, 0);
            btnEdit.setOnClickListener(v -> showAddEditDialog(d));
            
            Button btnDelete = new Button(this);
            btnDelete.setText(R.string.btn_delete);
            btnDelete.setTextSize(11);
            btnDelete.setBackgroundColor(0xFFE53935);
            btnDelete.setTextColor(0xFFFFFFFF);
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, 80);
            deleteParams.setMargins(6, 0, 0, 0);
            btnDelete.setLayoutParams(deleteParams);
            btnDelete.setPadding(12, 0, 12, 0);
            btnDelete.setOnClickListener(v -> deleteDestination(d));
            
            buttonContainer.addView(btnMap);
            buttonContainer.addView(btnEdit);
            buttonContainer.addView(btnDelete);
            itemLayout.addView(buttonContainer);
            
            layoutDestinationsContainer.addView(itemLayout);
            layoutDestinationsContainer.addView(divider, divParams);
        }
    }

    private void viewOnGoogleMaps(SavedDestination d) {
        try {
            String uri = String.format(java.util.Locale.US, "geo:%f,%f?q=%f,%f(%s)", 
                    d.getLatitude(), d.getLongitude(), 
                    d.getLatitude(), d.getLongitude(), 
                    java.net.URLEncoder.encode(d.getAlias(), "UTF-8"));
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri));
            intent.setPackage("com.google.android.apps.maps");
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Intent webIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                        "https://www.google.com/maps/search/?api=1&query=" + d.getLatitude() + "," + d.getLongitude()));
                startActivity(webIntent);
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.toast_google_maps_unavailable, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void showAddEditDialog(final SavedDestination destinationToEdit) {
        final boolean isEdit = destinationToEdit != null;
        
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(40, 40, 40, 40);
        dialogLayout.setBackgroundColor(0xFF1E1E1E);
        
        TextView tvLabelAlias = new TextView(this);
        tvLabelAlias.setText(R.string.destination_alias_label);
        tvLabelAlias.setTextColor(0xFFB0BEC5);
        tvLabelAlias.setTextSize(14);
        
        final EditText etAlias = new EditText(this);
        etAlias.setTextColor(0xFFFFFFFF);
        if (isEdit) {
            etAlias.setText(destinationToEdit.getAlias());
        }
        
        TextView tvLabelAddress = new TextView(this);
        tvLabelAddress.setText(R.string.destination_address_label);
        tvLabelAddress.setTextColor(0xFFB0BEC5);
        tvLabelAddress.setTextSize(14);
        tvLabelAddress.setPadding(0, 20, 0, 0);
        
        final EditText etAddress = new EditText(this);
        etAddress.setTextColor(0xFFFFFFFF);
        etAddress.setSingleLine(false);
        if (isEdit) {
            etAddress.setText(destinationToEdit.getAddress());
        }
        
        Button btnSearchAddress = new Button(this);
        btnSearchAddress.setText(R.string.btn_search_address);
        btnSearchAddress.setBackgroundColor(0xFF00E676);
        btnSearchAddress.setTextColor(0xFF121212);
        
        final double[] coords = new double[2];
        if (isEdit) {
            coords[0] = destinationToEdit.getLatitude();
            coords[1] = destinationToEdit.getLongitude();
        }
        btnSearchAddress.setOnClickListener(v -> {
            String address = etAddress.getText().toString().trim();
            if (address.isEmpty()) {
                etAddress.setError(getString(R.string.error_destination_address_required));
                return;
            }
            resolveAddressToGps(address, coords);
        });
        
        dialogLayout.addView(tvLabelAlias);
        dialogLayout.addView(etAlias);
        dialogLayout.addView(tvLabelAddress);
        dialogLayout.addView(etAddress);
        dialogLayout.addView(btnSearchAddress);
        
        final androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle(isEdit ? R.string.destination_edit_title : R.string.destination_add_title)
                .setView(dialogLayout)
                .setPositiveButton(R.string.btn_save, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
                
        dialog.setOnShowListener(dialogInterface -> {
            Button btnSave = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            btnSave.setOnClickListener(v -> {
                String alias = etAlias.getText().toString().trim();
                String address = etAddress.getText().toString().trim();
                
                if (alias.isEmpty()) {
                    etAlias.setError(getString(R.string.error_destination_alias_required));
                    return;
                }
                if (address.isEmpty()) {
                    etAddress.setError(getString(R.string.error_destination_address_required));
                    return;
                }
                
                if (coords[0] == 0.0 && coords[1] == 0.0) {
                    Toast.makeText(MainActivity.this, R.string.toast_destination_coordinates_unavailable, Toast.LENGTH_SHORT).show();
                    return;
                }
                
                DestinationDbHelper db = DestinationDbHelper.getInstance(MainActivity.this);
                if (isEdit) {
                    db.updateDestination(destinationToEdit.getId(), alias, address, coords[0], coords[1]);
                    Toast.makeText(MainActivity.this, R.string.toast_destination_updated, Toast.LENGTH_SHORT).show();
                } else {
                    long rowId = db.addDestination(alias, address, coords[0], coords[1]);
                    if (rowId == -1) {
                        Toast.makeText(MainActivity.this, R.string.toast_destination_alias_exists, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(MainActivity.this, R.string.toast_destination_added, Toast.LENGTH_SHORT).show();
                }
                
                loadSavedDestinations();
                dialog.dismiss();
            });
        });
        
        dialog.show();
        
    }

    private void resolveAddressToGps(final String address, final double[] outCoords) {
        final android.app.ProgressDialog progress = new android.app.ProgressDialog(this);
        progress.setMessage(getString(R.string.destination_coordinates_loading));
        progress.setCancelable(false);
        progress.show();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("address", address);
        new ExternalToolRegistry(this).executeFirstProviderTool(
                "address_to_coordinates", arguments, response -> {
                    progress.dismiss();
                    Map<String, Object> document = ExternalToolResults.firstItem(response, "documents");
                    Double longitude = ExternalToolResults.number(document, "x");
                    Double latitude = ExternalToolResults.number(document, "y");
                    if (longitude == null || latitude == null) {
                        Toast.makeText(MainActivity.this,
                                R.string.toast_external_geocoder_required, Toast.LENGTH_LONG).show();
                        return;
                    }
                    outCoords[0] = latitude;
                    outCoords[1] = longitude;
                    Toast.makeText(MainActivity.this,
                            getString(R.string.toast_coordinates_resolved, latitude, longitude),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void deleteDestination(final SavedDestination d) {
        new androidx.appcompat.app.AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle(R.string.destination_delete_title)
                .setMessage(getString(R.string.destination_delete_message, d.getAlias()))
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> {
                    DestinationDbHelper.getInstance(MainActivity.this).deleteDestination(d.getId());
                    Toast.makeText(MainActivity.this, R.string.toast_destination_deleted, Toast.LENGTH_SHORT).show();
                    loadSavedDestinations();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void requestPinnedShortcut(String character, String label, String longLabel, int iconResId) {
        if (androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            Intent intent = new Intent("com.poorgrammera.bydsubai.ACTION_TRIGGER_SHORTCUT");
            intent.setPackage(getPackageName());
            intent.setClass(this, ShortcutActivity.class);
            intent.putExtra("character", character);

            androidx.core.content.pm.ShortcutInfoCompat shortcutInfo = new androidx.core.content.pm.ShortcutInfoCompat.Builder(this, "trigger_gemini_" + character)
                    .setShortLabel(label)
                    .setLongLabel(longLabel)
                    .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(this, iconResId))
                    .setIntent(intent)
                    .build();

            androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(this, shortcutInfo, null);
            Toast.makeText(this, getString(R.string.toast_shortcut_requested, label), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.toast_shortcut_not_supported, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUserProfileNameField() {
        if (etUserProfileName == null || tvUserNameLabel == null) return;

        SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE);
        String voiceMode = prefs.getString(ConfigData.KEY_VOICE_MODE, ConfigData.DEFAULT_VOICE_MODE);
        String lang = getResources().getConfiguration().getLocales().get(0).toLanguageTag();

        String key;
        String defaultVal;
        String labelText;

        if ("kitt".equals(voiceMode)) {
            labelText = getString(R.string.user_name_label_kitt);
            if (lang.startsWith("ko")) {
                key = ConfigData.KEY_USER_NAME_KITT_KO;
                defaultVal = ConfigData.DEFAULT_USER_NAME_KITT_KO;
            } else if (lang.startsWith("ja")) {
                key = ConfigData.KEY_USER_NAME_KITT_JA;
                defaultVal = ConfigData.DEFAULT_USER_NAME_KITT_JA;
            } else {
                key = ConfigData.KEY_USER_NAME_KITT_EN;
                defaultVal = ConfigData.DEFAULT_USER_NAME_KITT_EN;
            }
        } else if ("asurada".equals(voiceMode)) {
            labelText = getString(R.string.user_name_label_asurada);
            if (lang.startsWith("ko")) {
                key = ConfigData.KEY_USER_NAME_ASURADA_KO;
                defaultVal = ConfigData.DEFAULT_USER_NAME_ASURADA_KO;
            } else if (lang.startsWith("ja")) {
                key = ConfigData.KEY_USER_NAME_ASURADA_JA;
                defaultVal = ConfigData.DEFAULT_USER_NAME_ASURADA_JA;
            } else {
                key = ConfigData.KEY_USER_NAME_ASURADA_EN;
                defaultVal = ConfigData.DEFAULT_USER_NAME_ASURADA_EN;
            }
        } else { // rumi
            labelText = getString(R.string.user_name_label_rumi);
            if (lang.startsWith("ko")) {
                key = ConfigData.KEY_USER_NAME_RUMI_KO;
                defaultVal = ConfigData.DEFAULT_USER_NAME_RUMI_KO;
            } else if (lang.startsWith("ja")) {
                key = ConfigData.KEY_USER_NAME_RUMI_JA;
                defaultVal = ConfigData.DEFAULT_USER_NAME_RUMI_JA;
            } else {
                key = ConfigData.KEY_USER_NAME_RUMI_EN;
                defaultVal = ConfigData.DEFAULT_USER_NAME_RUMI_EN;
            }
        }

        int languageName = lang.startsWith("ko") ? R.string.language_korean
                : lang.startsWith("ja") ? R.string.language_japanese : R.string.language_english;
        tvUserNameLabel.setText(getString(R.string.user_name_label_with_language, labelText, getString(languageName)));

        isUpdatingNameProgrammatically = true;
        etUserProfileName.setText(prefs.getString(key, defaultVal));
        etUserProfileName.setHint(getString(R.string.user_name_default_hint, defaultVal));
        isUpdatingNameProgrammatically = false;
    }

    private static void openAutoStartSettings(Context context) {
        // 1) Canonical BYD deep link.
        try {
            Intent direct = new Intent();
            direct.setComponent(new ComponentName(
                    "com.byd.appstartmanagement",
                    "com.byd.appstartmanagement.frame.AppStartManagement"));
            direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(direct);
            return;
        } catch (Exception e) {
            Log.w(TAG, "BYD AppStartManagement deep link failed: " + e.getMessage());
        }

        // 2) Launch the BYD app via its default activity.
        try {
            Intent launch = context.getPackageManager()
                    .getLaunchIntentForPackage("com.byd.appstartmanagement");
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launch);
                return;
            }
        } catch (Exception e) {
            Log.w(TAG, "BYD AppStartManagement launch intent failed: " + e.getMessage());
        }

        // 3) Generic app-info page (expose "auto-start" toggle on generic ROMs)
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        } catch (Exception e) {
            Log.w(TAG, "ACTION_APPLICATION_DETAILS_SETTINGS failed: " + e.getMessage());
        }

        // 4) Last resort (General app settings or main system settings)
        try {
            Intent intentApp = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
            intentApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intentApp);
        } catch (Exception e) {
            try {
                Intent intentSys = new Intent(Settings.ACTION_SETTINGS);
                intentSys.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intentSys);
            } catch (Exception ignored) {}
        }
    }
}
