package com.poorgrammera.bydsubai.ui;
import com.poorgrammera.bydsubai.BuildConfig;
import com.poorgrammera.bydsubai.R;
import com.poorgrammera.bydsubai.data.ConfigData;
import com.poorgrammera.bydsubai.gemini.GemmaNewsBriefingHelper;
import com.poorgrammera.bydsubai.integration.GpsTracker;
import com.poorgrammera.bydsubai.integration.OpenMeteoWeatherHelper;
import com.poorgrammera.bydsubai.service.GeminiLiveService;
import com.poorgrammera.bydsubai.vehicle.VehicleContextWrapper;
import com.poorgrammera.bydsubai.vehicle.VehicleController;


import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.net.Uri;

import androidx.appcompat.app.AppCompatActivity;

import java.lang.reflect.Method;

public class VehicleControlActivity extends AppCompatActivity {
    private static final String TAG = "VehicleControlActivity";

    // Reflection Device Instances (package-private for binder access)
    Object acDevice;
    int acDevType;
    Object bodyworkDevice;
    int bodyworkDevType;
    Object settingDevice;
    int settingDevType;
    Object audioDevice;
    int audioDevType;
    Object sensorDevice;
    int sensorDevType;
    Object lightDevice;
    int lightDevType;
    Method baseSetMethod;
    Method baseGetMethod;
    Method baseArraySetMethod;
    Class<?> featureClass;

    private TextView tvSlopeStatus;
    private TextView tvSlopeResult;

    // Binders
    AcControllerBinder acBinder;
    WindowControllerBinder windowBinder;
    SeatControllerBinder seatBinder;
    TpmsControllerBinder tpmsBinder;
    MapControllerBinder mapBinder;
    
    // Diagnostics widgets
    private EditText etRawFeatureId;
    private EditText etRawValue;
    
    VehicleController vehicleController;

    // Audio Control widgets
    TextView tvVolumeDisplay;
    TextView tvMuteDisplay;
    SeekBar sbVolume;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicle_control);

        vehicleController = new VehicleController(this);

        // Initialize reflection on background thread to keep UI smooth
        new Thread(this::initBydDevicesReflection).start();

        // Bind domains using dedicated Binders
        acBinder = new AcControllerBinder(this);
        acBinder.bind();

        windowBinder = new WindowControllerBinder(this);
        windowBinder.bind();

        seatBinder = new SeatControllerBinder(this);
        seatBinder.bind();

        tpmsBinder = new TpmsControllerBinder(this);
        tpmsBinder.bind();

        mapBinder = new MapControllerBinder(this);
        mapBinder.bind();

        // 5. Diagnostics
        etRawFeatureId = findViewById(R.id.et_raw_feature_id);
        etRawValue = findViewById(R.id.et_raw_value);
        findViewById(R.id.btn_send_raw).setOnClickListener(v -> sendRawFeatureEvent());
        findViewById(R.id.btn_back_to_main).setOnClickListener(v -> finish());

        // 6. Audio Control
        tvVolumeDisplay = findViewById(R.id.tv_volume_display);
        tvMuteDisplay = findViewById(R.id.tv_mute_display);
        sbVolume = findViewById(R.id.sb_volume);
        findViewById(R.id.btn_volume_down).setOnClickListener(v -> adjustVolume(-5));
        findViewById(R.id.btn_volume_up).setOnClickListener(v -> adjustVolume(5));
        findViewById(R.id.btn_volume_mute).setOnClickListener(v -> toggleMute());

        sbVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean f) { tvVolumeDisplay.setText("Volume: " + p); }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) { setVolumeLevel(sb.getProgress()); }
        });

        // 10. Weather Forecast UI Binding
        Button btnGetWeather = findViewById(R.id.btn_get_weather);
        TextView tvWeatherResult = findViewById(R.id.tv_weather_result);
        btnGetWeather.setOnClickListener(v -> {
            tvWeatherResult.setText(R.string.weather_location_loading);
            double[] coords = GpsTracker.getCurrentLocation(this);
            if (coords == null) {
                tvWeatherResult.setText(R.string.weather_location_unavailable);
                return;
            }
            tvWeatherResult.setText(getString(R.string.weather_loading_coordinates, coords[0], coords[1]));
            new Thread(() -> {
                String result = OpenMeteoWeatherHelper.fetchWeather(coords[0], coords[1]);
                runOnUiThread(() -> tvWeatherResult.setText(result));
            }).start();
        });

        // 11. Virtual News Briefing UI Binding (Gemma Offline Test)
        // NOTE: This is exclusively for local testing on this activity dashboard using Gemma.
        // It is NOT registered with Gemini Live declarations to maintain the default Naver News Search implementation.
        Button btnGetNewsBriefing = findViewById(R.id.btn_get_news_briefing);
        TextView tvNewsBriefingResult = findViewById(R.id.tv_news_briefing_result);
        btnGetNewsBriefing.setOnClickListener(v -> {
            tvNewsBriefingResult.setText(R.string.news_briefing_loading);
            new Thread(() -> {
                String result = GemmaNewsBriefingHelper.fetchNewsBriefing(this);
                runOnUiThread(() -> tvNewsBriefingResult.setText(result));
            }).start();
        });

        // 11.2 Electric Driving Range UI Binding
        Button btnGetElecRange = findViewById(R.id.btn_get_elec_range);
        TextView tvElecRangeResult = findViewById(R.id.tv_elec_range_result);
        if (btnGetElecRange != null && tvElecRangeResult != null) {
            btnGetElecRange.setOnClickListener(v -> {
                tvElecRangeResult.setText(R.string.elec_range_loading);
                new Thread(() -> {
                    double elecRange = vehicleController.getElecDrivingRangeValue();
                    double soc = vehicleController.getSOCBatteryPercentage();
                    runOnUiThread(() -> {
                        if (elecRange >= 0) {
                            String msg = getString(R.string.elec_range_result, (int) elecRange, elecRange);
                            if (soc >= 0) {
                                msg += getString(R.string.elec_range_soc, (int) soc);
                            }
                            tvElecRangeResult.setText(msg);
                        } else {
                            tvElecRangeResult.setText(R.string.elec_range_unavailable);
                        }
                    });
                }).start();
            });
        }

        // 11.5 Connection Options Binding
        android.widget.CheckBox cbUseMemory = findViewById(R.id.cb_use_memory);
        cbUseMemory.setChecked(getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE).getBoolean(ConfigData.KEY_USE_MEMORY, true));
        android.widget.CheckBox cbUseStartSound = findViewById(R.id.cb_use_start_sound);
        android.widget.CheckBox cbUseEndSound = findViewById(R.id.cb_use_end_sound);

        // 12. Gemini Live TTS UI Binding
        EditText etTtsInput = findViewById(R.id.et_tts_input);
        Button btnSendTts = findViewById(R.id.btn_send_tts);
        btnSendTts.setOnClickListener(v -> {
            String text = etTtsInput.getText().toString().trim();
            if (text.isEmpty()) {
                android.widget.Toast.makeText(this, R.string.toast_tts_text_required, android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Fling an intent to start the session in TTS-only mode
            Intent triggerIntent = new Intent(GeminiLiveService.ACTION_TRIGGER_GEMINI);
            triggerIntent.putExtra("is_tts_only", true);
            triggerIntent.putExtra("tts_text", text);
            triggerIntent.putExtra("use_memory", cbUseMemory.isChecked());
            triggerIntent.putExtra("use_start_sound", cbUseStartSound.isChecked());
            triggerIntent.putExtra("use_end_sound", cbUseEndSound.isChecked());
            triggerIntent.setPackage(getPackageName());
            sendBroadcast(triggerIntent);
            
            android.widget.Toast.makeText(this, getString(R.string.toast_tts_started, text), android.widget.Toast.LENGTH_SHORT).show();
        });

        // 13. Gemini Live Text Command (Single-shot) UI Binding
        EditText etTextCommandInput = findViewById(R.id.et_text_command_input);
        Button btnSendTextCommand = findViewById(R.id.btn_send_text_command);
        btnSendTextCommand.setOnClickListener(v -> {
            String text = etTextCommandInput.getText().toString().trim();
            if (text.isEmpty()) {
                android.widget.Toast.makeText(this, R.string.toast_command_text_required, android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            Intent triggerIntent = new Intent(GeminiLiveService.ACTION_TRIGGER_GEMINI);
            triggerIntent.putExtra("is_text_only", true);
            triggerIntent.putExtra("text_input", text);
            triggerIntent.putExtra("use_memory", cbUseMemory.isChecked());
            triggerIntent.putExtra("use_start_sound", cbUseStartSound.isChecked());
            triggerIntent.putExtra("use_end_sound", cbUseEndSound.isChecked());
            triggerIntent.setPackage(getPackageName());
            sendBroadcast(triggerIntent);

            android.widget.Toast.makeText(this, getString(R.string.toast_command_sent, text), android.widget.Toast.LENGTH_SHORT).show();
        });

        // Weather Direct Query Button Click Handler
        findViewById(R.id.btn_query_weather_direct).setOnClickListener(v -> {
            String weatherQuery = getString(R.string.weather_briefing_prompt);
            Intent triggerIntent = new Intent(GeminiLiveService.ACTION_TRIGGER_GEMINI);
            triggerIntent.putExtra("is_text_only", true);
            triggerIntent.putExtra("text_input", weatherQuery);
            triggerIntent.putExtra("use_memory", false);
            triggerIntent.putExtra("use_start_sound", false);
            triggerIntent.putExtra("use_end_sound", false);
            triggerIntent.setPackage(getPackageName());
            sendBroadcast(triggerIntent);
            android.widget.Toast.makeText(this, R.string.toast_weather_briefing_started, android.widget.Toast.LENGTH_SHORT).show();
        });

        // News Direct Query Button Click Handler
        findViewById(R.id.btn_query_news_direct).setOnClickListener(v -> {
            String newsQuery = getString(R.string.news_briefing_prompt);
            Intent triggerIntent = new Intent(GeminiLiveService.ACTION_TRIGGER_GEMINI);
            triggerIntent.putExtra("is_text_only", true);
            triggerIntent.putExtra("text_input", newsQuery);
            triggerIntent.putExtra("use_memory", false);
            triggerIntent.putExtra("use_start_sound", false);
            triggerIntent.putExtra("use_end_sound", false);
            triggerIntent.setPackage(getPackageName());
            sendBroadcast(triggerIntent);
            android.widget.Toast.makeText(this, R.string.toast_news_briefing_started, android.widget.Toast.LENGTH_SHORT).show();
        });

        // 14. Slope Monitoring UI Binding
        tvSlopeStatus = findViewById(R.id.tv_slope_status);
        tvSlopeResult = findViewById(R.id.tv_slope_result);
        findViewById(R.id.btn_get_slope).setOnClickListener(v -> queryVehicleSlope());

        // 15. Ambient Light Manual Control UI Binding
        TextView tvAmbientBrightnessLabel = findViewById(R.id.tv_ambient_brightness_label);
        SeekBar sbAmbientBrightness = findViewById(R.id.sb_ambient_brightness);

        findViewById(R.id.btn_ambient_on).setOnClickListener(v -> {
            if (vehicleController != null) {
                boolean ok = vehicleController.setAmbientLightEnabled(true);
                Toast.makeText(this, getString(R.string.toast_ambient_on, getString(ok ? R.string.result_success : R.string.result_failure)), Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_ambient_off).setOnClickListener(v -> {
            if (vehicleController != null) {
                boolean ok = vehicleController.setAmbientLightEnabled(false);
                Toast.makeText(this, getString(R.string.toast_ambient_off, getString(ok ? R.string.result_success : R.string.result_failure)), Toast.LENGTH_SHORT).show();
            }
        });

        sbAmbientBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvAmbientBrightnessLabel.setText(getString(R.string.ambient_brightness, progress));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                if (vehicleController != null) {
                    boolean ok = vehicleController.setAmbientBrightness(progress);
                    Toast.makeText(VehicleControlActivity.this, getString(R.string.toast_ambient_brightness, progress, getString(ok ? R.string.result_success : R.string.result_failure)), Toast.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.btn_ambient_color_pink).setOnClickListener(v -> {
            if (vehicleController != null) {
                // Pink: RGB(255, 20, 147)
                int colorValue = (255 << 16) | (20 << 8) | 147;
                boolean ok = vehicleController.setAmbientColor(colorValue);
                Toast.makeText(this, getString(R.string.toast_ambient_color, getString(R.string.color_pink), getString(ok ? R.string.result_success : R.string.result_failure)), Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_ambient_color_red).setOnClickListener(v -> {
            if (vehicleController != null) {
                // Red: RGB(255, 0, 0)
                int colorValue = (255 << 16) | (0 << 8) | 0;
                boolean ok = vehicleController.setAmbientColor(colorValue);
                Toast.makeText(this, getString(R.string.toast_ambient_color, getString(R.string.color_red), getString(ok ? R.string.result_success : R.string.result_failure)), Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_ambient_color_blue).setOnClickListener(v -> {
            if (vehicleController != null) {
                // Blue: RGB(0, 0, 255)
                int colorValue = (0 << 16) | (0 << 8) | 255;
                boolean ok = vehicleController.setAmbientColor(colorValue);
                Toast.makeText(this, getString(R.string.toast_ambient_color, getString(R.string.color_blue), getString(ok ? R.string.result_success : R.string.result_failure)), Toast.LENGTH_SHORT).show();
            }
        });

        // Initialize 31 Preset Colors Grid
        android.widget.GridLayout gridAmbientPresets = findViewById(R.id.grid_ambient_presets);
        if (gridAmbientPresets != null) {
            for (final com.poorgrammera.bydsubai.data.BydAmbientPalette color : com.poorgrammera.bydsubai.data.BydAmbientPalette.values()) {
                android.widget.Button btn = new android.widget.Button(this);
                
                int sizeDp = 40;
                int sizePx = (int) (sizeDp * getResources().getDisplayMetrics().density + 0.5f);
                android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
                params.width = sizePx;
                params.height = sizePx;
                
                int marginPx = (int) (2 * getResources().getDisplayMetrics().density + 0.5f);
                params.setMargins(marginPx, marginPx, marginPx, marginPx);
                btn.setLayoutParams(params);
                
                btn.setBackgroundColor(color.getRgbColor());
                btn.setText(String.valueOf(color.getIndex()));
                btn.setTextSize(10);
                
                int rgb = color.getRgbColor();
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
                btn.setTextColor(luminance > 0.5 ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
                
                btn.setPadding(0, 0, 0, 0);
                
                btn.setOnClickListener(v -> {
                    if (vehicleController != null) {
                        boolean ok = vehicleController.setAmbientColor(color.getIndex());
                        Toast.makeText(this, getString(R.string.toast_ambient_palette_color, color.getIndex(), getString(ok ? R.string.result_success : R.string.result_failure)), Toast.LENGTH_SHORT).show();
                    }
                });
                
                gridAmbientPresets.addView(btn);
            }
        }
    }



    private void initBydDevicesReflection() {
        try {
            // Load Base Feature IDs
            featureClass = Class.forName("android.hardware.bydauto.BYDAutoFeatureIds");

            // Initialize AC Device
            Class<?> acClass = Class.forName("android.hardware.bydauto.ac.BYDAutoAcDevice");
            Method acGetInstance = acClass.getMethod("getInstance", Context.class);
            acDevice = acGetInstance.invoke(null, new VehicleContextWrapper(this));
            Method getAcType = acClass.getMethod("getDevicetype");
            acDevType = (int) getAcType.invoke(acDevice);

            // Initialize Bodywork Device
            Class<?> bodyworkClass = Class.forName("android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice");
            Method bodyworkGetInstance = bodyworkClass.getMethod("getInstance", Context.class);
            bodyworkDevice = bodyworkGetInstance.invoke(null, new VehicleContextWrapper(this));
            Method getBodyworkType = bodyworkClass.getMethod("getDevicetype");
            bodyworkDevType = (int) getBodyworkType.invoke(bodyworkDevice);

            // Initialize Setting Device
            Class<?> settingClass = Class.forName("android.hardware.bydauto.setting.BYDAutoSettingDevice");
            Method settingGetInstance = settingClass.getMethod("getInstance", Context.class);
            settingDevice = settingGetInstance.invoke(null, new VehicleContextWrapper(this));
            Method getSettingType = settingClass.getMethod("getDevicetype");
            settingDevType = (int) getSettingType.invoke(settingDevice);

            // Initialize Audio Device
            Class<?> audioClass = Class.forName("android.hardware.bydauto.audio.BYDAutoAudioDevice");
            Method audioGetInstance = audioClass.getMethod("getInstance", Context.class);
            audioDevice = audioGetInstance.invoke(null, new VehicleContextWrapper(this));
            Method getAudioType = audioClass.getMethod("getDevicetype");
            audioDevType = (int) getAudioType.invoke(audioDevice);

            // Initialize Sensor Device
            Class<?> sensorClass = Class.forName("android.hardware.bydauto.sensor.BYDAutoSensorDevice");
            Method sensorGetInstance = sensorClass.getMethod("getInstance", Context.class);
            sensorDevice = sensorGetInstance.invoke(null, new VehicleContextWrapper(this));
            Method getSensorType = sensorClass.getMethod("getDevicetype");
            sensorDevType = (int) getSensorType.invoke(sensorDevice);
 
            // Initialize Light Device
            try {
                Class<?> lightClass = Class.forName("android.hardware.bydauto.light.BYDAutoLightDevice");
                Method lightGetInstance = lightClass.getMethod("getInstance", Context.class);
                lightDevice = lightGetInstance.invoke(null, new VehicleContextWrapper(this));
                Method getLightType = lightClass.getMethod("getDevicetype");
                lightDevType = (int) getLightType.invoke(lightDevice);
                Log.i(TAG, "Light Device successfully initialized.");
            } catch (Exception e) {
                Log.w(TAG, "Failed to initialize Light Device reflection in activity", e);
            }

            // Extract Base Set Methods (from AbsBYDAutoDevice)
            Class<?> absDeviceClass = acClass.getSuperclass();
            baseSetMethod = absDeviceClass.getDeclaredMethod("set", int.class, int.class, int.class);
            baseSetMethod.setAccessible(true);

            baseGetMethod = absDeviceClass.getDeclaredMethod("get", int.class, int.class);
            baseGetMethod.setAccessible(true);

            baseArraySetMethod = absDeviceClass.getDeclaredMethod("set", int.class, int[].class, int[].class);
            baseArraySetMethod.setAccessible(true);

            Log.i(TAG, "Successfully initialized BYD Devices: AC, Bodywork, Setting, Audio, Sensor. reflection loaded.");
            dumpClassMethodsAndFields(acClass, featureClass);
            showToastOnUi(getString(R.string.toast_reflection_success));

            // Refresh initial volume and mute status on UI
            runOnUiThread(this::refreshAudioStatus);
            
            // Refresh tpms values using binder
            if (tpmsBinder != null) {
                runOnUiThread(() -> tpmsBinder.refreshTyrePressures());
            }
            // Update wind direction UI backgrounds using binder
            if (acBinder != null) {
                acBinder.updateWindDirectionUI();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize BYD Devices via reflection", e);
            runOnUiThread(() -> Toast.makeText(this, "Reflection Init Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }


    // Package-private reflection tools for Binders
    int sendSetCommand(Object device, int devType, int featureId, int value) throws Exception {
        if (device == null || baseSetMethod == null) {
            throw new IllegalStateException("Device reflection not initialized");
        }
        Object result = baseSetMethod.invoke(device, devType, featureId, value);
        return result instanceof Integer ? (Integer) result : -1;
    }

    Object callMethod(Object device, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        if (device == null) {
            throw new IllegalStateException("Device not initialized");
        }
        Method m = device.getClass().getMethod(methodName, parameterTypes);
        return m.invoke(device, args);
    }

    void showToastOnUi(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }


    // 5. Diagnostics
    private void sendRawFeatureEvent() {
        try {
            int id = Integer.parseInt(etRawFeatureId.getText().toString().trim());
            int val = Integer.parseInt(etRawValue.getText().toString().trim());
            int res = vehicleController.sendRawAcCommand(id, val);
            Toast.makeText(this, "Result: " + res, Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Log.e(TAG, "Raw event failed", e); }
    }

    private void adjustVolume(int d) { vehicleController.controlVolume(d > 0 ? "up" : "down", null); refreshAudioStatus(); }
    private void setVolumeLevel(int l) { vehicleController.controlVolume("set", l); refreshAudioStatus(); }
    private void toggleMute() { vehicleController.controlVolume(vehicleController.getMuteState() == 1 ? "unmute" : "mute", null); refreshAudioStatus(); }

    private void refreshAudioStatus() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            int vol = vehicleController.getVolume();
            tvVolumeDisplay.setText("Volume: " + vol);
            sbVolume.setProgress(vol);
            tvMuteDisplay.setText("Mute: " + (vehicleController.getMuteState() == 1 ? "ON" : "OFF"));
        }, 300);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (vehicleController != null) vehicleController.shutdown();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void dumpClassMethodsAndFields(Class<?> acClass, Class<?> featureClass) {
        new Thread(() -> {
            try {
                Log.d(TAG, "===== DUMPING BYDAutoAcDevice METHODS =====");
                for (Method m : acClass.getDeclaredMethods()) {
                    StringBuilder params = new StringBuilder();
                    for (Class<?> p : m.getParameterTypes()) {
                        params.append(p.getSimpleName()).append(", ");
                    }
                    Log.d(TAG, "Method: " + m.getName() + "(" + params.toString() + ")");
                }
                Log.d(TAG, "===========================================");

                if (featureClass != null) {
                    Log.d(TAG, "===== DUMPING BYDAutoFeatureIds FIELDS =====");
                    for (java.lang.reflect.Field f : featureClass.getDeclaredFields()) {
                        f.setAccessible(true);
                        Log.d(TAG, "Field: " + f.getName() + " = " + f.get(null));
                    }
                    Log.d(TAG, "===========================================");
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to dump class methods/fields", e);
            }
        }).start();
    }

    private void queryVehicleSlope() {
        if (sensorDevice == null || baseGetMethod == null) {
            tvSlopeResult.setText(R.string.slope_sensor_unavailable);
            return;
        }

        try {
            // AbsBYDAutoDevice.get(sensorDevType, 573571116) 우회 호출
            Object res = baseGetMethod.invoke(sensorDevice, sensorDevType, 573571116);
            if (res instanceof Integer) {
                int currentAngle = (Integer) res;
                tvSlopeStatus.setText(getString(R.string.slope_angle, currentAngle));
                
                int absAngle = Math.abs(currentAngle);
                String resultText;
                
                // 수도코드 요구사항 분석 결과 텍스트 적용 (1~4단계)
                if (absAngle <= 3) {
                    resultText = getString(R.string.slope_status_normal);
                } else if (absAngle >= 4 && absAngle <= 7) {
                    resultText = getString(R.string.slope_status_caution);
                } else if (absAngle >= 8 && absAngle <= 12) {
                    resultText = getString(R.string.slope_status_warning);
                } else {
                    resultText = getString(R.string.slope_status_danger);
                }
                
                tvSlopeResult.setText(resultText);
            } else {
                tvSlopeResult.setText(R.string.slope_value_unavailable);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to query vehicle slope", e);
            tvSlopeResult.setText(getString(R.string.toast_error_occurred, e.getMessage()));
        }
    }
}
