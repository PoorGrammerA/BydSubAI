package com.poorgrammera.bydsubai.service;
import com.poorgrammera.bydsubai.BuildConfig;
import com.poorgrammera.bydsubai.R;
import com.poorgrammera.bydsubai.data.ConfigData;
import com.poorgrammera.bydsubai.vehicle.VehicleContextWrapper;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import com.poorgrammera.bydsubai.util.SoundPoolManager;
import com.poorgrammera.bydsubai.util.MediaPlayerManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.media.AudioManager;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import android.hardware.bydauto.adas.AbsBYDAutoADASListener;
import android.hardware.bydauto.gearbox.AbsBYDAutoGearboxListener;
import android.hardware.bydauto.instrument.AbsBYDAutoInstrumentListener;
import android.hardware.bydauto.energy.AbsBYDAutoEnergyListener;
import android.hardware.bydauto.radar.AbsBYDAutoRadarListener;
import android.hardware.bydauto.setting.AbsBYDAutoSettingListener;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BydAutoService extends Service {
    private static final String TAG = "BydAutoService";
    public static volatile boolean isRunning = false;
    private static final String CHANNEL_ID = "BydAutoServiceChannel";
    private static final int NOTIFICATION_ID = 4;
    public static final String ACTION_REFRESH_GPS_TRACKING =
            "com.poorgrammera.bydsubai.action.REFRESH_GPS_TRACKING";
    private static final long OS_LOCATION_MAX_AGE_MS = 120_000L;

    // Sensor Reflection Fields
    private Object sensorDevice;
    private int sensorDevType;
    private Method baseGetMethod;

    private static class ActionTimestamp {
        final String action;
        final long time;
        ActionTimestamp(String action, long time) {
            this.action = action;
            this.time = time;
        }
    }
    private final List<ActionTimestamp> recentBroadcasts = new ArrayList<>();
    private BroadcastReceiver powerScreenSaverReceiver;

    private final List<StateTime> menuStateHistory = new ArrayList<>();

    private static class StateTime {
        int value;
        long timestamp;

        StateTime(int value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }

    private List<Object> devices = new ArrayList<>();
    private List<Object> listeners = new ArrayList<>();
    
    private Map<String, Integer> featureNameToIdMap = new HashMap<>();

    private AudioManager audioManager;

    // State tracking
    private int lastGearValue = -1;
    private int lastAccValue = -1;
    private boolean isAccActive = false;
    private int lastOperationMode = -1;
    private int lastEnergyValue = -1;
    private int lastRadarValue = -1;
    private int lastBsdValue = 1; // 1: Standby
    private int lastEnergyRecycleValue = -1;
    private long lastMenuTriggerTime = 0;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (ConfigData.KEY_VOICE_MODE.equals(key)) {
                String newVoiceMode = sharedPreferences.getString(ConfigData.KEY_VOICE_MODE, ConfigData.DEFAULT_VOICE_MODE);
                Log.d(TAG, "Voice mode changed to " + newVoiceMode + ". Preloading resources.");
                preloadVoiceResources(newVoiceMode);
            }
        }
    };

    private void preloadVoiceResources(String voiceMode) {
        SoundPoolManager spm = SoundPoolManager.getInstance(this);
        String[] prefixes = {voiceMode + "_", ""};
        String[] bases = {
            "cruise_control_on", "cruise_control_off",
            "gear_0_n", "gear_1_r", "gear_2_d", "gear_3_p",
            "adas_blind_spot_assist_0_off", "adas_blind_spot_assist_1_on",
            "energy_recycle_2_standard", "energy_recycle_3_high",
            "energy_road_surface_1_common", "energy_road_surface_2_snow",
            "reverse_radar_0_off", "reverse_radar_1_on",
            "operation_mode_1_sports_mode_off", "operation_mode_2_sports_mode_on",
            "slope_warning", "slope_danger"
        };
        for (String base : bases) {
            for (String prefix : prefixes) {
                int resId = getResources().getIdentifier(prefix + base, "raw", getPackageName());
                if (resId != 0) {
                    spm.preload(this, resId);
                }
            }
        }
    }



    // Periodic GPS updates fields
    private android.location.LocationManager locationManager = null;
    private android.location.LocationListener locationListener = null;

    private final String[] soundBaseNames = {
        "gear_0_n", "gear_1_r", "gear_2_d", "gear_3_p",
        "cruise_control_on", "cruise_control_off",
        "operation_mode_1_sports_mode_off", "operation_mode_2_sports_mode_on",
        "energy_road_surface_1_common", "energy_road_surface_2_snow",
        "reverse_radar_0_off", "reverse_radar_1_on",
        "adas_blind_spot_assist_0_off", "adas_blind_spot_assist_1_on",
        "energy_recycle_2_standard", "energy_recycle_3_high",
        "system_on"
    };

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        Log.d(TAG, "onCreate: Starting BydAutoService");
        
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        
        createNotificationChannel();
        
        // Preload generic SE sound effects
        SoundPoolManager spm = SoundPoolManager.getInstance(this);
        int[] seResIds = {ConfigData.SE_01, ConfigData.SE_02, ConfigData.SE_03, ConfigData.SE_04, ConfigData.SE_05};
        for (int resId : seResIds) {
            spm.preload(this, resId);
        }

        // Preload active character's vehicle sensor alerts and cruise control sounds
        SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE);
        String voiceMode = prefs.getString(ConfigData.KEY_VOICE_MODE, ConfigData.DEFAULT_VOICE_MODE); // "rumi", "kitt", "asurada"
        preloadVoiceResources(voiceMode);

        // Register listener to preload resources when user changes the character setting
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener);
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BYD Vehicle Service")
                .setContentText("Monitoring vehicle state and buttons")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build();
                
        startForeground(NOTIFICATION_ID, notification);
        
        loadFeatureIds();
        initDevices();
        startGpsTracking();

        // Register BroadcastReceiver for Power/ScreenSaver actions
        IntentFilter sequenceFilter = new IntentFilter();
        sequenceFilter.addAction("android.intent.action.ENTER_POWER_SCREEN");
        sequenceFilter.addAction("CLOSE_SCREEN_SAVER");

        powerScreenSaverReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action != null) {
                    Log.d(TAG, "Screen Sequence Broadcast received: " + action);
                    handleBroadcastTrigger(action);
                }
            }
        };
        ContextCompat.registerReceiver(
                this,
                powerScreenSaverReceiver,
                sequenceFilter,
                ContextCompat.RECEIVER_EXPORTED
        );
    }

    private void playVehicleSound(String baseName) {
        SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);

        // Unified Sound Alert Filtering
        if ("gear_0_n".equals(baseName) && !prefs.getBoolean(ConfigData.KEY_ALERT_GEAR_N, true)) return;
        if ("gear_1_r".equals(baseName) && !prefs.getBoolean(ConfigData.KEY_ALERT_GEAR_R, true)) return;
        if ("gear_2_d".equals(baseName) && !prefs.getBoolean(ConfigData.KEY_ALERT_GEAR_D, true)) return;
        if ("gear_3_p".equals(baseName) && !prefs.getBoolean(ConfigData.KEY_ALERT_GEAR_P, true)) return;
        if (baseName.startsWith("cruise_control_") && !prefs.getBoolean(ConfigData.KEY_ALERT_CRUISE, true)) return;
        if (baseName.startsWith("operation_mode_") && !prefs.getBoolean(ConfigData.KEY_ALERT_SPORT, true)) return;
        if (baseName.startsWith("energy_road_surface_") && !prefs.getBoolean(ConfigData.KEY_ALERT_SNOW, true)) return;
        if (baseName.startsWith("reverse_radar_") && !prefs.getBoolean(ConfigData.KEY_ALERT_RADAR, true)) return;
        if (baseName.startsWith("adas_blind_spot_") && !prefs.getBoolean(ConfigData.KEY_ALERT_BSD, true)) return;
        if (baseName.startsWith("energy_recycle_") && !prefs.getBoolean(ConfigData.KEY_ALERT_RECYCLE, true)) return;
        if ("system_on".equals(baseName) && !prefs.getBoolean(ConfigData.KEY_ALERT_BOOT, true)) {
            sendBroadcast(new Intent(ConfigData.ACTION_BOOT_SOUND_COMPLETED));
            Log.d(TAG, "playVehicleSound: Alert boot is disabled. Sent BOOT_SOUND_COMPLETED broadcast immediately.");
            return;
        }

        String soundMode = prefs.getString("pref_sound_mode_" + baseName, "voice"); // "voice", "se_01", "se_02", ...
        int resId = 0;

        if ("voice".equals(soundMode)) {
            String voiceMode = prefs.getString(ConfigData.KEY_VOICE_MODE, ConfigData.DEFAULT_VOICE_MODE); // "rumi", "kitt", "asurada"
            String resName = voiceMode + "_" + baseName;
            resId = getResources().getIdentifier(resName, "raw", getPackageName());
            if (resId == 0) {
                resId = getResources().getIdentifier(baseName, "raw", getPackageName());
            }
        } else {
            if ("se_01".equals(soundMode)) resId = R.raw.se_01;
            else if ("se_02".equals(soundMode)) resId = R.raw.se_02;
            else if ("se_03".equals(soundMode)) resId = R.raw.se_03;
            else if ("se_04".equals(soundMode)) resId = R.raw.se_04;
            else if ("se_05".equals(soundMode)) resId = R.raw.se_05;
        }

        if (resId != 0) {
            if ("system_on".equals(baseName)) {
                // system_on boot animation sound
                MediaPlayerManager.getInstance(this).play(this, resId, 1.0f, mp -> {
                    sendBroadcast(new Intent(ConfigData.ACTION_BOOT_SOUND_COMPLETED));
                    Log.d(TAG, "playVehicleSound (system_on): Sent BOOT_SOUND_COMPLETED broadcast on completion.");
                });
            } else {
                // Everything else (cruise, gear, BSD, recycle, road surface, sports mode, reverse radar, and se_01~se_05) plays via SoundPool
                boolean isImportant = baseName.startsWith("cruise_control_");
                SoundPoolManager.getInstance(this).play(this, resId, 1.0f, 3500, isImportant);
            }
        } else {
            Log.w(TAG, "playVehicleSound: Resource not found for baseName=" + baseName + ", soundMode=" + soundMode);
        }
    }
    
    private void loadFeatureIds() {
        try {
            Class<?> globalClass = Class.forName("android.hardware.bydauto.BYDAutoFeatureIds");
            for (Field field : globalClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) {
                    try {
                        featureNameToIdMap.put(field.getName(), field.getInt(null));
                        // Log.d(TAG, "Loaded feature ID: " + field.getName() + " = " + field.getInt(null));
                    } catch (Exception ignored) {}
                }
            }
            
            String[] subClasses = {"Instrument", "Adas", "Setting", "Energy", "Radar"};
            for (String sub : subClasses) {
                try {
                    Class<?> subClass = Class.forName("android.hardware.bydauto.BYDAutoFeatureIds$" + sub);
                    for (Field field : subClass.getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) {
                            try {
                                featureNameToIdMap.put(field.getName(), field.getInt(null));
                                // Log.d(TAG, "Loaded feature ID: " + field.getName() + " = " + field.getInt(null));
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (ClassNotFoundException ignored) {}
            }
            
            Log.i(TAG, "Loaded " + featureNameToIdMap.size() + " feature IDs.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load feature IDs", e);
        }
    }

    private void initDevices() {
        VehicleContextWrapper wrappedCtx = new VehicleContextWrapper(this);
        
        try {
            Class<?> sensorClass = Class.forName("android.hardware.bydauto.sensor.BYDAutoSensorDevice");
            Method sensorGetInstance = sensorClass.getMethod("getInstance", Context.class);
            sensorDevice = sensorGetInstance.invoke(null, wrappedCtx);
            Method getSensorType = sensorClass.getMethod("getDevicetype");
            sensorDevType = (int) getSensorType.invoke(sensorDevice);

            Class<?> absDeviceClass = sensorClass.getSuperclass();
            baseGetMethod = absDeviceClass.getDeclaredMethod("get", int.class, int.class);
            baseGetMethod.setAccessible(true);
            Log.i(TAG, "Sensor reflection initialized successfully in service");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Sensor reflection in service", e);
        }
        
        // 1. Setup Instrument Device to listen to menu state
        setupDevice(wrappedCtx, "android.hardware.bydauto.instrument.BYDAutoInstrumentDevice", 
            "android.hardware.bydauto.instrument.AbsBYDAutoInstrumentListener", new AbsBYDAutoInstrumentListener() {
                @Override
                public void onDataEventChanged(int eventType, android.hardware.bydauto.BYDAutoEventValue eventValue) {
                    processEvent(eventType, eventValue);
                }
            });

        // 2. Setup ADAS Device
        setupDevice(wrappedCtx, "android.hardware.bydauto.adas.BYDAutoADASDevice", 
            "android.hardware.bydauto.adas.AbsBYDAutoADASListener", new AbsBYDAutoADASListener() {
                @Override
                public void onDataEventChanged(int eventType, android.hardware.bydauto.BYDAutoEventValue eventValue) {
                    processEvent(eventType, eventValue);
                }
            });

        // 3. Setup Gearbox Device
        setupDevice(wrappedCtx, "android.hardware.bydauto.gearbox.BYDAutoGearboxDevice", 
            "android.hardware.bydauto.gearbox.AbsBYDAutoGearboxListener", new AbsBYDAutoGearboxListener() {
                @Override
                public void onCurrentGearChanged(int gear) {
                    handleGearbox(gear);
                }
                @Override
                public void onDataEventChanged(int eventType, android.hardware.bydauto.BYDAutoEventValue eventValue) {
                    processEvent(eventType, eventValue);
                }
            });

        // 4. Setup Energy Device
        setupDevice(wrappedCtx, "android.hardware.bydauto.energy.BYDAutoEnergyDevice", 
            "android.hardware.bydauto.energy.AbsBYDAutoEnergyListener", new AbsBYDAutoEnergyListener() {
                @Override
                public void onOperationModeChanged(int type) {
                    handleOperationMode(type);
                }
                @Override
                public void onEnergyRecycleChanged(int state) {
                    handleEnergyRecycle(state);
                }
                @Override
                public void onDataEventChanged(int eventType, android.hardware.bydauto.BYDAutoEventValue eventValue) {
                    processEvent(eventType, eventValue);
                }
            });

        // 5. Setup Radar Device
        setupDevice(wrappedCtx, "android.hardware.bydauto.radar.BYDAutoRadarDevice", 
            "android.hardware.bydauto.radar.AbsBYDAutoRadarListener", new AbsBYDAutoRadarListener() {
                @Override
                public void onDataEventChanged(int eventType, android.hardware.bydauto.BYDAutoEventValue eventValue) {
                    processEvent(eventType, eventValue);
                }
            });

        // 6. Setup Setting Device
        setupDevice(wrappedCtx, "android.hardware.bydauto.setting.BYDAutoSettingDevice", 
            "android.hardware.bydauto.setting.AbsBYDAutoSettingListener", new AbsBYDAutoSettingListener() {
                @Override
                public void onDataEventChanged(int eventType, android.hardware.bydauto.BYDAutoEventValue eventValue) {
                    processEvent(eventType, eventValue);
                }
            });
            
        Log.i(TAG, "Registered " + devices.size() + " devices in background service.");
    }

    private void setupDevice(VehicleContextWrapper wrappedCtx, String deviceClassName, String listenerClassName, Object listenerObj) {
        try {
            Class<?> clazz = Class.forName(deviceClassName);
            Method getInstanceMethod = clazz.getMethod("getInstance", android.content.Context.class);
            Object device = getInstanceMethod.invoke(null, wrappedCtx);

            if (device != null) {
                Method registerMethod = clazz.getMethod("registerListener", Class.forName(listenerClassName));
                registerMethod.invoke(device, listenerObj);
                
                devices.add(device);
                listeners.add(listenerObj);
                Log.i(TAG, "Registered listener for " + deviceClassName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to setup device: " + deviceClassName, e);
        }
    }

    private void processEvent(int eventType, android.hardware.bydauto.BYDAutoEventValue eventValue) {
        if (eventValue != null) {
            int value = -1;
            try {
                Field intValueField = eventValue.getClass().getField("intValue");
                value = intValueField.getInt(eventValue);
            } catch (Exception ignored) {}
            if (value != -1) {
                handleDeviceEventLogic(eventType, value);
            }
        }
    }
    
    private void handleDeviceEventLogic(int eventType, int value) {
        // Instrument 2-in-1 Menu State
        Integer menuStateId = featureNameToIdMap.get("INSTRUMENT_2IN1_MENU_STATE");
        if (menuStateId != null && eventType == menuStateId) {
            handleMenuState(value);
        }

        // ACC Mode
        Integer accId = featureNameToIdMap.get("ADAS_ACC_MODE");
        if (accId != null && eventType == accId) {
            handleAccMode(value);
        }

        // Energy Road Surface (Snow Mode) (0x24000033 = 603979827)
        if (eventType == 0x24000033) {
            handleEnergyRoadSurface(value);
        }

        // Radar On/Off (0x26700038 = 644874296)
        if (eventType == 0x26700038) {
            handleRadar(value);
        }

        // BSD On/Off (0x41800008)
        Integer bsdId = featureNameToIdMap.get("ADAS_BLIND_SPOT_ASSIST");
        if ((bsdId != null && eventType == bsdId) || eventType == 0x41800008) {
            handleBsd(value);
        }

        // Energy Recycle
        Integer recycleId = featureNameToIdMap.get("ENERGY_RECYCLE");
        Integer energyFbId = featureNameToIdMap.get("SET_DR_ENERGY_FB");
        if ((recycleId != null && eventType == recycleId) || (energyFbId != null && eventType == energyFbId)) {
            handleEnergyRecycle(value);
        }
    }

    private void handleMenuState(int val) {
        Log.d("aaa", "handleMenuState: " + val);
        long now = System.currentTimeMillis();

        // 1. 유효 시간(1초)이 지난 오래된 데이터는 히스토리에서 즉시 제거
        while (!menuStateHistory.isEmpty() && (now - menuStateHistory.get(0).timestamp > 1000)) {
            menuStateHistory.remove(0);
        }

        // 2. 값의 실질적인 '변화'가 일어난 시점만 기록 (동일 값 연속 유입 방지)
        if (menuStateHistory.isEmpty() || menuStateHistory.get(menuStateHistory.size() - 1).value != val) {
            menuStateHistory.add(new StateTime(val, now));
        }

        // 3. 1초 이내에 쌓인 시퀀스 개수가 최소 6개 이상인지 확인
        if (menuStateHistory.size() >= 6) {
            int n = menuStateHistory.size();
            int v1 = menuStateHistory.get(n - 6).value;
            int v2 = menuStateHistory.get(n - 5).value;
            int v3 = menuStateHistory.get(n - 4).value;
            int v4 = menuStateHistory.get(n - 3).value;
            int v5 = menuStateHistory.get(n - 2).value;
            int v6 = menuStateHistory.get(n - 1).value;

            // 1-2-1-2-1-2 패턴 매칭 검사
            boolean pattern1 = (v1 == 1 && v2 == 2 && v3 == 1 && v4 == 2 && v5 == 1 && v6 == 2);
            // 2-1-2-1-2-1 패턴 매칭 검사
            boolean pattern2 = (v1 == 2 && v2 == 1 && v3 == 2 && v4 == 1 && v5 == 2 && v6 == 1);

            if (pattern1 || pattern2) {
                // 첫 번째 이벤트와 마지막(6번째) 이벤트 사이의 시간 간격이 1초 이내인지 다시 한 번 확인
                long duration = menuStateHistory.get(n - 1).timestamp - menuStateHistory.get(n - 6).timestamp;
                if (duration <= 1000) {
                    Log.i(TAG, "2in1MenuState double-click sequence detected (6 changes in " + duration + "ms)! Triggering Gemini Live...");
                    
                    // 중복 발동 방지를 위해 히스토리 초기화
                    menuStateHistory.clear();

                    Intent intent = new Intent(GeminiLiveService.ACTION_TRIGGER_GEMINI);
                    sendBroadcast(intent);
                }
            }
        }
    }

    private void handleBroadcastTrigger(String action) {
        long now = System.currentTimeMillis();
        recentBroadcasts.add(new ActionTimestamp(action, now));

        // Keep only events from the last 1 second (1000ms)
        long threshold = now - 1000;
        while (!recentBroadcasts.isEmpty() && recentBroadcasts.get(0).time < threshold) {
            recentBroadcasts.remove(0);
        }

        // Match sequence: ENTER_POWER_SCREEN -> CLOSE_SCREEN_SAVER within 1 second
        if (recentBroadcasts.size() >= 2) {
            int size = recentBroadcasts.size();
            ActionTimestamp first = recentBroadcasts.get(size - 2);
            ActionTimestamp second = recentBroadcasts.get(size - 1);

            if ("android.intent.action.ENTER_POWER_SCREEN".equals(first.action)
                    && "CLOSE_SCREEN_SAVER".equals(second.action)) {
                recentBroadcasts.clear(); // Clear sequence buffer to avoid double-triggering
                Log.i(TAG, "ENTER_POWER_SCREEN and CLOSE_SCREEN_SAVER sequence matched within 1 second! Triggering Gemini Live...");
                
                Intent triggerIntent = new Intent("com.poorgrammera.bydsubai.TRIGGER_GEMINI");
                sendBroadcast(triggerIntent);
            }
        }
    }

    private void handleGearbox(int gear) {
        if (gear != lastGearValue) {
            lastGearValue = gear;
            switch (gear) {
                case 0: playVehicleSound("gear_0_n"); break;
                case 1: playVehicleSound("gear_1_r"); break;
                case 2: playVehicleSound("gear_2_d"); break;
                case 3:
                    SharedPreferences currentPrefs = getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
                    boolean useSlopeCheck = currentPrefs.getBoolean(ConfigData.KEY_USE_SLOPE_CHECK, ConfigData.DEFAULT_USE_SLOPE_CHECK);
                    if (!useSlopeCheck) {
                        playVehicleSound("gear_3_p");
                        break;
                    }
                    int currentAngle = 0;
                    boolean hasSlope = false;
                    if (sensorDevice != null && baseGetMethod != null) {
                        try {
                            Object res = baseGetMethod.invoke(sensorDevice, sensorDevType, 573571116);
                            if (res instanceof Integer) {
                                currentAngle = (Integer) res;
                                hasSlope = true;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to read slope in handleGearbox", e);
                        }
                    }

                    if (hasSlope) {
                        int absAngle = Math.abs(currentAngle);
                        SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
                        String voiceMode = prefs.getString(ConfigData.KEY_VOICE_MODE, ConfigData.DEFAULT_VOICE_MODE);

                        if (absAngle >= 8 && absAngle <= 12) {
                            Log.i(TAG, "Shifting to P: Steep slope detected (" + currentAngle + " deg). Playing warning alert...");
                            playVoiceAlert(voiceMode, "slope_warning");
                        } else if (absAngle >= 13) {
                            Log.i(TAG, "Shifting to P: Dangerous slope detected (" + currentAngle + " deg). Playing danger alert...");
                            playVoiceAlert(voiceMode, "slope_danger");
                        } else {
                            playVehicleSound("gear_3_p");
                        }
                    } else {
                        playVehicleSound("gear_3_p");
                    }
                    break;
            }
        }
    }

    private void playVoiceAlert(String voiceMode, String baseName) {
        String resName = voiceMode + "_" + baseName;
        int resId = getResources().getIdentifier(resName, "raw", getPackageName());
        if (resId == 0) {
            resId = getResources().getIdentifier(baseName, "raw", getPackageName());
        }
        if (resId != 0) {
            SoundPoolManager.getInstance(this).play(this, resId, 1.0f, 3500, false);
        } else {
            Log.w(TAG, "playVoiceAlert: Audio resource not found for " + resName + " or " + baseName);
        }
    }

    private void handleAccMode(int value) {
        if (value != lastAccValue) {
            lastAccValue = value;
            
            if (value == 3) {
                if (!isAccActive) {
                    playVehicleSound("cruise_control_on");
                    isAccActive = true;
                }
            } else if (value == 2 || value == 4) {
                if (isAccActive) {
                    playVehicleSound("cruise_control_off");
                    isAccActive = false;
                }
            } else if (value == 0 || value == 1) {
                if (isAccActive) {
                    isAccActive = false;
                    playVehicleSound("cruise_control_off");
                }
                isAccActive = false;
            }
        }
    }

    private void handleOperationMode(int type) {
        if (type != lastOperationMode) {
            lastOperationMode = type;
            if (type == 1) {
                playVehicleSound("operation_mode_1_sports_mode_off");
            } else if (type == 2) {
                playVehicleSound("operation_mode_2_sports_mode_on");
            }
        }
    }

    private void handleEnergyRecycle(int state) {
        if (state != lastEnergyRecycleValue) {
            lastEnergyRecycleValue = state;
            if (state == 2) {
                playVehicleSound("energy_recycle_2_standard");
            } else if (state == 3) {
                playVehicleSound("energy_recycle_3_high");
            }
        }
    }

    private void handleEnergyRoadSurface(int value) {
        if (value != lastEnergyValue) {
            lastEnergyValue = value;
            if (value == 1) {
                playVehicleSound("energy_road_surface_1_common");
            } else if (value == 2) {
                playVehicleSound("energy_road_surface_2_snow");
            }
        }
    }

    private void handleRadar(int value) {
        if (value != lastRadarValue) {
            lastRadarValue = value;
            if (value == 1) {
                playVehicleSound("reverse_radar_1_on");
            } else if (value == 0) {
                playVehicleSound("reverse_radar_0_off");
            }
        }
    }

    private void handleBsd(int value) {
        if (value != lastBsdValue) {
            if (value == 0) {
                playVehicleSound("adas_blind_spot_assist_0_off");
            } else if (value == 1) {
                if (lastBsdValue == 0) {
                    playVehicleSound("adas_blind_spot_assist_1_on");
                }
            }
            lastBsdValue = value;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "BYD Auto Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_REFRESH_GPS_TRACKING.equals(intent.getAction())) {
            refreshGpsTrackingAfterPermissionGranted();
        }
        if (intent != null && intent.getBooleanExtra("play_system_on", false)) {
            playVehicleSound("system_on");
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE);
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener);
        MediaPlayerManager.getInstance(this).stop();
        if (powerScreenSaverReceiver != null) {
            unregisterReceiver(powerScreenSaverReceiver);
            powerScreenSaverReceiver = null;
        }
        isRunning = false;
        Log.d(TAG, "onDestroy: Stopping BydAutoService");

        if (locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
                Log.i(TAG, "Location updates removed successfully.");
            } catch (Exception e) {
                Log.e(TAG, "Failed to remove location updates", e);
            }
        }
        
        for (int i = 0; i < devices.size(); i++) {
            Object device = devices.get(i);
            Object listener = listeners.get(i);
            try {
                Class<?> clazz = device.getClass();
                Method[] methods = clazz.getMethods();
                for (Method m : methods) {
                    if (m.getName().equals("unregisterListener") && m.getParameterCount() == 1) {
                        m.invoke(device, listener);
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister listener", e);
            }
        }
        devices.clear();
        listeners.clear();
    }

    private void startGpsTracking() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "ACCESS_FINE_LOCATION permission not granted. Cannot start periodic GPS updates.");
            return;
        }
        try {
            locationManager = (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) return;

            // Populate the cache immediately, but only with a recent fix from the
            // best available provider.
            android.location.Location initialLoc = fetchBestFreshLastKnownLocation();
            if (initialLoc != null) {
                double lat = initialLoc.getLatitude();
                double lon = initialLoc.getLongitude();
                long ageSeconds = (System.currentTimeMillis() - initialLoc.getTime()) / 1000;
                Log.i(TAG, "Fetched initial fresh location (" + ageSeconds + "s old, "
                        + initialLoc.getProvider() + "): " + lat + ", " + lon);
                getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putFloat("gps_last_latitude", (float) lat)
                        .putFloat("gps_last_longitude", (float) lon)
                        .putLong("gps_last_timestamp", System.currentTimeMillis())
                        .apply();
            } else {
                Log.w(TAG, "No fresh last-known location available. Waiting for a location update.");
            }

            locationListener = new android.location.LocationListener() {
                @Override
                public void onLocationChanged(android.location.Location location) {
                    if (location != null) {
                        double lat = location.getLatitude();
                        double lon = location.getLongitude();
                        Log.d(TAG, "Periodic GPS location update: " + lat + ", " + lon);
                        
                        // Cache coordinates to SharedPreferences with current timestamp
                        getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE)
                                .edit()
                                .putFloat("gps_last_latitude", (float) lat)
                                .putFloat("gps_last_longitude", (float) lon)
                                .putLong("gps_last_timestamp", System.currentTimeMillis())
                                .apply();

                    }
                }

                @Override public void onStatusChanged(String provider, int status, android.os.Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            };

            registerLocationListener();

        } catch (Exception e) {
            Log.e(TAG, "Failed to register LocationListener: " + e.getMessage(), e);
        }
    }

    /** Re-registers GPS after runtime location permission has been granted. */
    public void refreshGpsTrackingAfterPermissionGranted() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission is still unavailable; GPS refresh skipped.");
            return;
        }

        if (locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Exception e) {
                Log.w(TAG, "Failed to remove existing location updates before refresh", e);
            }
        }
        startGpsTracking();
    }

    private android.location.Location fetchBestFreshLastKnownLocation() {
        if (locationManager == null) {
            return null;
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        try {
            android.location.Location bestLocation = null;
            String[] providers = {
                    android.location.LocationManager.GPS_PROVIDER,
                    android.location.LocationManager.NETWORK_PROVIDER,
                    android.location.LocationManager.PASSIVE_PROVIDER
            };
            long now = System.currentTimeMillis();

            for (String provider : providers) {
                if (!locationManager.isProviderEnabled(provider)) {
                    continue;
                }

                android.location.Location location = locationManager.getLastKnownLocation(provider);
                if (location == null || now - location.getTime() > OS_LOCATION_MAX_AGE_MS) {
                    continue;
                }
                if (bestLocation == null || location.getTime() > bestLocation.getTime()) {
                    bestLocation = location;
                }
            }
            return bestLocation;
        } catch (Exception e) {
            Log.e(TAG, "Failed to obtain a fresh last-known location", e);
            return null;
        }
    }

    private void registerLocationListener() {
        if (locationManager == null || locationListener == null) return;
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission is unavailable; location listener was not registered.");
            return;
        }
        try {
            locationManager.removeUpdates(locationListener);
        } catch (Exception ignored) {}

        long minTimeMs = 10000L;
        float minDistanceM = 50f;

        try {
            if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, minTimeMs, minDistanceM, locationListener);
                Log.i(TAG, "Requested periodic location updates from GPS_PROVIDER: " + minTimeMs + "ms / " + minDistanceM + "m");
            }
            if (locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(android.location.LocationManager.NETWORK_PROVIDER, minTimeMs, minDistanceM, locationListener);
                Log.i(TAG, "Requested periodic location updates from NETWORK_PROVIDER: " + minTimeMs + "ms / " + minDistanceM + "m");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error requesting location updates: " + e.getMessage(), e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
