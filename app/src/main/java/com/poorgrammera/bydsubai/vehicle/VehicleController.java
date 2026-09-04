package com.poorgrammera.bydsubai.vehicle;
import com.poorgrammera.bydsubai.BuildConfig;
import com.poorgrammera.bydsubai.R;
import com.poorgrammera.bydsubai.data.ConfigData;
import com.poorgrammera.bydsubai.data.DestinationDbHelper;
import com.poorgrammera.bydsubai.data.SavedDestination;
import com.poorgrammera.bydsubai.integration.MapLauncher;


import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VehicleController {
    private static final String TAG = "VehicleController";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(runnable -> 
            new Thread(runnable, "VehicleController-Worker")
    );

    // Reflective Devices & Types
    private Object acDevice;
    private int acDevType;

    private Object bodyworkDevice;
    private int bodyworkDevType;

    private Object settingDevice;
    private int settingDevType;

    private Object audioDevice;
    private int audioDevType;

    private Object tyreDevice;
    private int tyreDevType;

    private Object instrumentDevice;
    private int instrumentDevType;

    private Object lightDevice;
    private int lightDevType;

    private Object statisticDevice;
    private int statisticDevType;

    private Object gearboxDevice;
    private Method getCurrentGearMethod;

    public static final int LIGHT_ATMOSPHERE_MAIN_SWITCH_SET = 1276153924;
    public static final int LIGHT_ATMOSPHERE_CUSTOM_BRIGHTNESS_SET = 1276194858;
    public static final int LIGHT_ATMOSPHERE_CUSTOM_COLOR_SET = 1276194864;

    // Base Reflection methods
    private Method baseSetMethod;
    private Method baseArraySetMethod;
    private Method baseGetMethod;
    private Class<?> featureClass;

    private boolean isInitialized = false;

    public VehicleController(Context context) {
        this.context = context.getApplicationContext();
        initReflection();
    }

    private void initReflection() {
        executorService.submit(() -> {
            try {
                Log.d(TAG, "Initializing BYD Devices reflection...");
                
                // Initialize AC Device
                Class<?> acClass = Class.forName("android.hardware.bydauto.ac.BYDAutoAcDevice");
                Method acGetInstance = acClass.getMethod("getInstance", Context.class);
                acDevice = acGetInstance.invoke(null, new VehicleContextWrapper(context));
                Method getAcType = acClass.getMethod("getDevicetype");
                acDevType = (int) getAcType.invoke(acDevice);

                // Initialize Feature IDs class
                featureClass = Class.forName("android.hardware.bydauto.BYDAutoFeatureIds");

                // Initialize Bodywork Device
                Class<?> bodyworkClass = Class.forName("android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice");
                Method bodyworkGetInstance = bodyworkClass.getMethod("getInstance", Context.class);
                bodyworkDevice = bodyworkGetInstance.invoke(null, new VehicleContextWrapper(context));
                Method getBodyworkType = bodyworkClass.getMethod("getDevicetype");
                bodyworkDevType = (int) getBodyworkType.invoke(bodyworkDevice);

                // Initialize Setting Device
                Class<?> settingClass = Class.forName("android.hardware.bydauto.setting.BYDAutoSettingDevice");
                Method settingGetInstance = settingClass.getMethod("getInstance", Context.class);
                settingDevice = settingGetInstance.invoke(null, new VehicleContextWrapper(context));
                Method getSettingType = settingClass.getMethod("getDevicetype");
                settingDevType = (int) getSettingType.invoke(settingDevice);

                // Initialize Audio Device
                Class<?> audioClass = Class.forName("android.hardware.bydauto.audio.BYDAutoAudioDevice");
                Method audioGetInstance = audioClass.getMethod("getInstance", Context.class);
                audioDevice = audioGetInstance.invoke(null, new VehicleContextWrapper(context));
                Method getAudioType = audioClass.getMethod("getDevicetype");
                audioDevType = (int) getAudioType.invoke(audioDevice);

                // Initialize Tyre Device
                try {
                    Class<?> tyreClass = Class.forName("android.hardware.bydauto.tyre.BYDAutoTyreDevice");
                    Method tyreGetInstance = tyreClass.getMethod("getInstance", Context.class);
                    tyreDevice = tyreGetInstance.invoke(null, new VehicleContextWrapper(context));
                    Method getTyreType = tyreClass.getMethod("getDevicetype");
                    tyreDevType = (int) getTyreType.invoke(tyreDevice);
                    Log.i(TAG, "Tyre Device successfully initialized.");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to initialize Tyre Device reflection", e);
                }

                // Initialize Instrument Device
                try {
                    Class<?> instrumentClass = Class.forName("android.hardware.bydauto.instrument.BYDAutoInstrumentDevice");
                    Method instrumentGetInstance = instrumentClass.getMethod("getInstance", Context.class);
                    instrumentDevice = instrumentGetInstance.invoke(null, new VehicleContextWrapper(context));
                    Method getInstrumentType = instrumentClass.getMethod("getDevicetype");
                    instrumentDevType = (int) getInstrumentType.invoke(instrumentDevice);
                    Log.i(TAG, "Instrument Device successfully initialized.");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to initialize Instrument Device reflection", e);
                }

                // Initialize Light Device
                try {
                    Class<?> lightClass = Class.forName("android.hardware.bydauto.light.BYDAutoLightDevice");
                    Method lightGetInstance = lightClass.getMethod("getInstance", Context.class);
                    lightDevice = lightGetInstance.invoke(null, new VehicleContextWrapper(context));
                    Method getLightType = lightClass.getMethod("getDevicetype");
                    lightDevType = (int) getLightType.invoke(lightDevice);
                    Log.i(TAG, "Light Device successfully initialized.");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to initialize Light Device reflection", e);
                }

                // Initialize Statistic Device
                try {
                    Class<?> statisticClass = Class.forName("android.hardware.bydauto.statistic.BYDAutoStatisticDevice");
                    Method statisticGetInstance = statisticClass.getMethod("getInstance", Context.class);
                    statisticDevice = statisticGetInstance.invoke(null, new VehicleContextWrapper(context));
                    try {
                        Method getStatisticType = statisticClass.getMethod("getDevicetype");
                        statisticDevType = (int) getStatisticType.invoke(statisticDevice);
                    } catch (Exception ignored) {}
                    Log.i(TAG, "Statistic Device successfully initialized.");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to initialize Statistic Device reflection", e);
                }

                // Initialize Gearbox Device for safety checks on vehicle controls.
                try {
                    Class<?> gearboxClass = Class.forName("android.hardware.bydauto.gearbox.BYDAutoGearboxDevice");
                    Method gearboxGetInstance = gearboxClass.getMethod("getInstance", Context.class);
                    gearboxDevice = gearboxGetInstance.invoke(null, new VehicleContextWrapper(context));
                    getCurrentGearMethod = gearboxClass.getMethod("getCurrentGear");
                    Log.i(TAG, "Gearbox Device successfully initialized.");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to initialize Gearbox Device reflection", e);
                }

                // Extract Base Set Methods (from AbsBYDAutoDevice)
                Class<?> absDeviceClass = acClass.getSuperclass();
                baseSetMethod = absDeviceClass.getDeclaredMethod("set", int.class, int.class, int.class);
                baseSetMethod.setAccessible(true);

                baseArraySetMethod = absDeviceClass.getDeclaredMethod("set", int.class, int[].class, int[].class);
                baseArraySetMethod.setAccessible(true);

                baseGetMethod = absDeviceClass.getDeclaredMethod("get", int.class, int.class);
                baseGetMethod.setAccessible(true);

                isInitialized = true;
                Log.i(TAG, "Successfully initialized BYD Devices reflection in VehicleController.");
                dumpClassMethodsAndFields(acClass, featureClass);
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize BYD Devices via reflection", e);
                showToast(context.getString(R.string.toast_reflection_init_failed, e.getMessage()));
            }
        });
    }

    private int sendSetCommand(Object device, int devType, int featureId, int value) throws Exception {
        if (device == null || baseSetMethod == null) {
            throw new IllegalStateException("Device reflection not initialized");
        }
        Object result = baseSetMethod.invoke(device, devType, featureId, value);
        return result instanceof Integer ? (Integer) result : -1;
    }

    private Object callMethod(Object device, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        if (device == null) {
            throw new IllegalStateException("Device not initialized");
        }
        Method m = device.getClass().getMethod(methodName, parameterTypes);
        return m.invoke(device, args);
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    /** Returns the current A/C fan level, or -1 when the vehicle API cannot provide it. */
    public int getAirWindLevel() {
        if (!ensureAcDeviceAvailable()) {
            Log.w(TAG, "getAirWindLevel: A/C device is not initialized");
            return -1;
        }
        try {
            Object result = callMethod(acDevice, "getAcWindLevel", new Class<?>[0]);
            return result instanceof Number ? ((Number) result).intValue() : -1;
        } catch (Exception e) {
            Log.w(TAG, "getAirWindLevel: Failed to read current fan level", e);
            return -1;
        }
    }

    /** Returns 1 when A/C is running, 0 when stopped, or -1 when unavailable. */
    public int getAirConditionerPowerState() {
        if (!ensureAcDeviceAvailable()) {
            Log.w(TAG, "getAirConditionerPowerState: A/C device is not initialized");
            return -1;
        }
        try {
            Object result = callMethod(acDevice, "getAcStartState", new Class<?>[0]);
            return result instanceof Number ? ((Number) result).intValue() : -1;
        } catch (Exception e) {
            Log.w(TAG, "getAirConditionerPowerState: Failed to read A/C power state", e);
            return -1;
        }
    }

    private boolean ensureAcDeviceAvailable() {
        if (isInitialized && acDevice != null) return true;
        try {
            initReflectionSync();
        } catch (Exception e) {
            Log.w(TAG, "Could not initialize A/C device for state read", e);
        }
        return acDevice != null;
    }

    // --- Package Checks ---
    public boolean isNaverMapInstalled() {
        return isPackageInstalled("com.nhn.android.nmap");
    }

    public boolean isTmapInstalled() {
        return isPackageInstalled("com.tmap.auto.byd")
                || isPackageInstalled("com.skt.tmap.auto.TmapAutoApplication")
                || isPackageInstalled("com.skt.tmap.auto")
                || isPackageInstalled("com.skt.tmap.ku");
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    // --- Control Methods ---

    // 1. Climate / A/C controls
    public void controlAirConditioner(Boolean power, Integer windLevel, Double temperature, String cycleMode, Boolean frontDefrost, Boolean rearDefrost, String mode, Boolean windDirFace, Boolean windDirFoot, Boolean windDirScreen) {
        final Double finalTemp;
        if (temperature != null) {
            finalTemp = temperature;
        } else if ("cool".equalsIgnoreCase(mode)) {
            finalTemp = 17.0;
        } else if ("heat".equalsIgnoreCase(mode)) {
            finalTemp = 27.0;
        } else {
            finalTemp = null;
        }

        Log.i(TAG, "controlAirConditioner API invoked with parameters -> " +
                "power: " + power + ", windLevel: " + windLevel + ", temperature: " + temperature +
                " (resolved to: " + finalTemp + "), cycleMode: " + cycleMode + ", frontDefrost: " + frontDefrost +
                ", rearDefrost: " + rearDefrost + ", mode: " + mode + ", windDirFace: " + windDirFace +
                ", windDirFoot: " + windDirFoot + ", windDirScreen: " + windDirScreen);
        executorService.submit(() -> {
            try {
                // Ensure reflection initialization is completed
                int waitCount = 0;
                while (!isInitialized && waitCount < 30) { // Wait up to 3 seconds
                    Log.d(TAG, "controlAirConditioner: Waiting for reflection initialization...");
                    Thread.sleep(100);
                    waitCount++;
                }

                if (!isInitialized) {
                    Log.w(TAG, "controlAirConditioner: Reflection still not initialized. Attempting synchronous recovery initialization.");
                    try {
                        initReflectionSync();
                    } catch (Exception e) {
                        Log.e(TAG, "controlAirConditioner: Synchronous recovery initialization failed", e);
                    }
                }

                if (acDevice == null) {
                    Log.e(TAG, "controlAirConditioner: acDevice is null! Cannot control A/C.");
                    showToast("차량 제어 장치를 초기화할 수 없어 공조기를 제어할 수 없습니다.");
                    return;
                }

                // 1. Power control
                if (power != null) {
                    int state = power ? 1 : 0;
                    String methodName = state == 1 ? "start" : "stop";
                    Method m = acDevice.getClass().getMethod(methodName, int.class);
                    Object result = m.invoke(acDevice, 0);
                    int code = result instanceof Integer ? (Integer) result : -1;
                    if (code != 0) {
                        int acPowerSetId = featureClass.getField("AC_POWER_STATE_SET").getInt(null);
                        sendSetCommand(acDevice, acDevType, acPowerSetId, state);
                    }
                    showToast(context.getString(R.string.toast_ac_power_set, power ? context.getString(R.string.state_on) : context.getString(R.string.state_off)));
                    Thread.sleep(1000);
                }

                // 2. Mode control (Sequential - no new thread started here)
                if (mode != null) {
                    final String finalMode = mode;
                    try {
                        try {
                            Method getPowerState = acDevice.getClass().getMethod("getAcStartState");
                            int powerState = (int) getPowerState.invoke(acDevice);
                            if (powerState == 0) {
                                Log.i(TAG, "controller setMode: A/C is OFF. Turning it ON first.");
                                Method startM = acDevice.getClass().getMethod("start", int.class);
                                Object startRes = startM.invoke(acDevice, 0);
                                int code = startRes instanceof Integer ? (Integer) startRes : -1;
                                if (code != 0) {
                                    int acPowerSetId = featureClass.getField("AC_POWER_STATE_SET").getInt(null);
                                    sendSetCommand(acDevice, acDevType, acPowerSetId, 1);
                                }
                                Thread.sleep(1500);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "controller setMode: Failed to check/start AC via reflection, attempting fallback key", e);
                            try {
                                sendSetCommand(acDevice, acDevType, 501219364, 1);
                                Thread.sleep(1500);
                            } catch (Exception ex) {
                                Log.e(TAG, "controller setMode: Fallback power-on key also failed", ex);
                            }
                        }

                        try {
                            Method getControlMode = acDevice.getClass().getMethod("getAcControlMode");
                            int controlMode = (int) getControlMode.invoke(acDevice);
                            boolean wantAuto = "heat".equalsIgnoreCase(finalMode) || "cool".equalsIgnoreCase(finalMode) || "auto".equalsIgnoreCase(finalMode);
                            int targetCtrlMode = wantAuto ? 0 : 1; 
                            
                            if (controlMode != targetCtrlMode) {
                                int ret = sendSetCommand(acDevice, acDevType, 501219352, targetCtrlMode);
                                Thread.sleep(800);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "controller setMode: Failed to adjust AUTO/manual mode", e);
                        }

                        if (!"auto".equalsIgnoreCase(finalMode) && !"heat".equalsIgnoreCase(finalMode)) {
                            try {
                                Method getWindLevel = acDevice.getClass().getMethod("getAcWindLevel");
                                int curWind = (int) getWindLevel.invoke(acDevice);
                                if (curWind <= 0) {
                                    Log.d(TAG, "controller setMode: Wind level is 0 -> setting to 3");
                                    int ret = sendSetCommand(acDevice, acDevType, 501219340, 3);
                                    Thread.sleep(800);
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "controller setMode: Failed to set wind level", e);
                            }
                        }

                        boolean directSuccess = false;
                        try {
                            if ("cool".equalsIgnoreCase(finalMode)) {
                                setTemperatureInternal(17.0);
                                boolean r1 = invokeAcMethod("setAcCompressorMode", new Class<?>[]{int.class, int.class}, 0, 1);
                                boolean r2 = invokeAcMethod("setAcWarmState", new Class<?>[]{int.class}, 0);
                                boolean r3 = invokeAcMethod("setAcVentilationState", new Class<?>[]{int.class, int.class}, 0, 0);
                                directSuccess = r1 && r2 && r3;
                            } else if ("heat".equalsIgnoreCase(finalMode)) {
                                setTemperatureInternal(27.0);
                                boolean r1 = invokeAcMethod("setAcWarmState", new Class<?>[]{int.class}, 1);
                                boolean r2 = invokeAcMethod("setAcCompressorMode", new Class<?>[]{int.class, int.class}, 0, 0);
                                boolean r3 = invokeAcMethod("setAcVentilationState", new Class<?>[]{int.class, int.class}, 0, 0);
                                directSuccess = r1 && r2 && r3;
                            } else if ("fan".equalsIgnoreCase(finalMode)) {
                                boolean r1 = invokeAcMethod("setAcCompressorMode", new Class<?>[]{int.class, int.class}, 0, 0);
                                Thread.sleep(300);
                                boolean r2 = invokeAcMethod("setAcWarmState", new Class<?>[]{int.class}, 0);
                                Thread.sleep(300);
                                boolean r3 = invokeAcMethod("setAcVentilationState", new Class<?>[]{int.class, int.class}, 0, 1);
                                Thread.sleep(300);
                                boolean r4 = invokeAcMethod("setAcWindLevel", new Class<?>[]{int.class, int.class}, 0, 3);
                                directSuccess = r1 && r2 && r3 && r4;
                            } else if ("auto".equalsIgnoreCase(finalMode)) {
                                directSuccess = invokeAcMethod("setAcControlMode", new Class<?>[]{int.class, int.class}, 0, 0);
                            }
                        } catch (Exception ex) {
                            Log.w(TAG, "Direct API control exception", ex);
                        }

                        Log.d(TAG, "controller setMode finalMode=" + finalMode + ", directSuccess=" + directSuccess);

                        if (!directSuccess) {
                            if ("cool".equalsIgnoreCase(finalMode)) {
                                setTemperatureInternal(17.0);
                                sendSetCommand(acDevice, acDevType, 501219344, 1);
                                sendSetCommand(acDevice, acDevType, 1337983018, 0);
                                sendSetCommand(acDevice, acDevType, 501219394, 0);
                            } else if ("heat".equalsIgnoreCase(finalMode)) {
                                setTemperatureInternal(27.0);
                                sendSetCommand(acDevice, acDevType, 1337983018, 1);
                                sendSetCommand(acDevice, acDevType, 501219344, 0);
                                sendSetCommand(acDevice, acDevType, 501219394, 0);
                            } else if ("fan".equalsIgnoreCase(finalMode)) {
                                sendSetCommand(acDevice, acDevType, 501219344, 0);
                                Thread.sleep(500);
                                sendSetCommand(acDevice, acDevType, 1337983018, 0);
                                Thread.sleep(500);
                                sendSetCommand(acDevice, acDevType, 501219394, 1);
                                Thread.sleep(500);
                                sendSetCommand(acDevice, acDevType, 501219340, 3);
                            } else if ("auto".equalsIgnoreCase(finalMode)) {
                                sendSetCommand(acDevice, acDevType, 501219352, 0);
                            }
                        }
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        Log.e(TAG, "controller setMode: Failed for mode=" + finalMode, e);
                    }
                }

                // 3. Wind Level control
                if (windLevel != null) {
                    int level = Math.max(1, Math.min(7, windLevel));
                    Object result = callMethod(acDevice, "setAcWindLevel", new Class<?>[]{int.class, int.class}, 0, level);
                    int code = result instanceof Integer ? (Integer) result : -1;
                    if (code != 0) {
                        int windSetId = featureClass.getField("AC_WIND_LEVEL_SET").getInt(null);
                        sendSetCommand(acDevice, acDevType, windSetId, level);
                    }
                    showToast(context.getString(R.string.toast_wind_level_set, level));
                    Thread.sleep(500);
                }

                // 4. Temperature control
                if (finalTemp != null) {
                    int unitSetId = featureClass.getField("AC_TEMPERATURE_UNIT_SET").getInt(null);
                    int tempMainSetId = featureClass.getField("AC_TEMP_MAIN_SET").getInt(null);
                    int tempDeputySetId = featureClass.getField("AC_TEMP_DEPUTY_SET").getInt(null);
                    int tempRearSetId = featureClass.getField("AC_TEMP_REAR_SET").getInt(null);
                    int ctrlSourceSetId = featureClass.getField("AC_CTRL_SOURCE_SET").getInt(null);

                    int[] area = {unitSetId, tempMainSetId, tempDeputySetId, tempRearSetId, ctrlSourceSetId};
                    int value = (int) (finalTemp * 2);
                    int[] param = {0, value, value, 0, 1};
                    baseArraySetMethod.invoke(acDevice, acDevType, area, param);
                    showToast(context.getString(R.string.toast_temp_set_format, finalTemp, 0));
                    Thread.sleep(500);
                }

                // 5. Cycle Mode control
                if (cycleMode != null) {
                    int modeVal = "recycle".equalsIgnoreCase(cycleMode) ? 1 : 0;
                    sendSetCommand(acDevice, acDevType, 501219355, modeVal);
                    showToast(context.getString(R.string.toast_ac_cycle_set, modeVal == 1 ? context.getString(R.string.btn_cycle_internal) : context.getString(R.string.btn_cycle_external), 0));
                    Thread.sleep(500);
                }

                // 6. Defrost control
                if (frontDefrost != null) {
                    sendSetCommand(acDevice, acDevType, 501219362, frontDefrost ? 1 : 0);
                    showToast(context.getString(R.string.toast_front_defrost_toggle, frontDefrost ? context.getString(R.string.state_on) : context.getString(R.string.state_off), 0));
                    Thread.sleep(500);
                }
                if (rearDefrost != null) {
                    sendSetCommand(acDevice, acDevType, 501219357, rearDefrost ? 1 : 0);
                    showToast(context.getString(R.string.toast_rear_defrost_toggle, rearDefrost ? context.getString(R.string.state_on) : context.getString(R.string.state_off), 0));
                    Thread.sleep(500);
                }

                // 7. Wind Direction control
                if (windDirFace != null || windDirFoot != null || windDirScreen != null) {
                    try {
                        Method getAcWindMode = acDevice.getClass().getMethod("getAcWindMode");
                        int currentMode = (int) getAcWindMode.invoke(acDevice);
                        boolean face = (currentMode == 1 || currentMode == 2 || currentMode == 6 || currentMode == 7);
                        boolean foot = (currentMode == 2 || currentMode == 3 || currentMode == 4 || currentMode == 6);
                        boolean screen = (currentMode == 4 || currentMode == 5 || currentMode == 6 || currentMode == 7);
                        if (windDirFace != null) face = windDirFace;
                        if (windDirFoot != null) foot = windDirFoot;
                        if (windDirScreen != null) screen = windDirScreen;
                        int active = 0;
                        if (face) active++;
                        if (foot) active++;
                        if (screen) active++;
                        if (active == 0) { face = true; }
                        int modeValue = 1;
                        if (face && !foot && !screen) modeValue = 1;
                        else if (face && foot && !screen) modeValue = 2;
                        else if (!face && foot && !screen) modeValue = 3;
                        else if (!face && foot && screen) modeValue = 4;
                        else if (!face && !foot && screen) modeValue = 5;
                        else if (face && !foot && screen) modeValue = 7;
                        else if (face && foot && screen) modeValue = 6;
                        boolean directSuccess = false;
                        try {
                            Method setAcWindMode = acDevice.getClass().getMethod("setAcWindMode", int.class, int.class);
                            setAcWindMode.invoke(acDevice, 0, modeValue);
                            directSuccess = true;
                        } catch (Exception ex) {
                            Log.w(TAG, "Direct setAcWindMode failed", ex);
                        }
                        if (!directSuccess) {
                            sendSetCommand(acDevice, acDevType, 501219336, modeValue);
                        }
                        showToast("바람 방향 모드 " + modeValue + " 적용 완료");
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to apply wind direction", e);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to control Air Conditioner", e);
                showToast("에어컨 제어 실패: " + e.getMessage());
            }
        });
    }



    // 2. Trunk controls
    public void controlTrunk(String action) {
        executorService.submit(() -> {
            try {
                // action: "open" -> 1, "stop" -> 2 (or 0 fallback), "close" -> 3
                int val;
                String toastMsg;
                if ("open".equalsIgnoreCase(action)) {
                    if (isTrunkOpenBlockedByGear()) {
                        Log.w(TAG, "Blocked trunk open command because gear is not P or unavailable.");
                        showToast(context.getString(R.string.toast_trunk_open_blocked_drive));
                        return;
                    }
                    val = 1;
                    toastMsg = context.getString(R.string.toast_trunk_open_sent);
                } else if ("close".equalsIgnoreCase(action)) {
                    val = 3;
                    toastMsg = context.getString(R.string.toast_trunk_close_sent);
                } else {
                    val = 2; // stop
                    toastMsg = context.getString(R.string.toast_trunk_stop_sent);
                }

                try {
                    Object result = callMethod(settingDevice, "voiceCtlBackDoor", new Class<?>[]{int.class}, val);
                    if (result != null && ((Integer) result) == 0) {
                        showToast(toastMsg);
                        return;
                    }
                } catch (Exception ignored) {}

                int fallbackVal = val == 2 ? 0 : val;
                sendSetCommand(bodyworkDevice, bodyworkDevType, 489689126, fallbackVal);
                showToast(toastMsg);
            } catch (Exception e) {
                Log.e(TAG, "Failed to control Trunk", e);
            }
        });
    }

    /**
     * Allows trunk opening only in P (gear value 3). All other gear values and an
     * unavailable gear state are blocked as a safety precaution.
     */
    public boolean isTrunkOpenBlockedByGear() {
        if (gearboxDevice == null || getCurrentGearMethod == null) {
            Log.w(TAG, "Cannot read current gear; blocking trunk open command.");
            return true;
        }

        try {
            Object value = getCurrentGearMethod.invoke(gearboxDevice);
            if (!(value instanceof Number)) {
                Log.w(TAG, "Unexpected current gear value: " + value);
                return true;
            }

            int gear = ((Number) value).intValue();
            return gear != 3;
        } catch (Exception e) {
            Log.w(TAG, "Failed to read current gear for trunk safety check", e);
            return true;
        }
    }

    // 3. Windows & Sunroof controls
    public void controlWindow(String area, String action, Integer customPercent) {
        executorService.submit(() -> {
            try {
                // area: "all", "driver_front" (1), "passenger_front" (2), "driver_rear" (3), "passenger_rear" (4), "sunroof" (5), "sunshade" (6)
                int areaNum = 0;
                if ("driver_front".equalsIgnoreCase(area)) areaNum = 1;
                else if ("passenger_front".equalsIgnoreCase(area)) areaNum = 2;
                else if ("driver_rear".equalsIgnoreCase(area)) areaNum = 3;
                else if ("passenger_rear".equalsIgnoreCase(area)) areaNum = 4;
                else if ("sunroof".equalsIgnoreCase(area)) areaNum = 5;
                else if ("sunshade".equalsIgnoreCase(area)) areaNum = 6;

                // action: "open", "close", "stop", "ventilation" (10%), "half" (50%)
                int command = 0;
                if ("open".equalsIgnoreCase(action)) command = 1;
                else if ("close".equalsIgnoreCase(action)) command = 2;
                else if ("stop".equalsIgnoreCase(action)) command = 3;
                else if ("ventilation".equalsIgnoreCase(action)) command = 4;
                else if ("half".equalsIgnoreCase(action)) command = 5;

                java.util.List<Integer> windowsToControl = new java.util.ArrayList<>();
                if ("all".equalsIgnoreCase(area)) {
                    windowsToControl.add(1);
                    windowsToControl.add(2);
                    windowsToControl.add(3);
                    windowsToControl.add(4);
                } else if ("front".equalsIgnoreCase(area)) {
                    windowsToControl.add(1);
                    windowsToControl.add(2);
                } else if ("rear".equalsIgnoreCase(area)) {
                    windowsToControl.add(3);
                    windowsToControl.add(4);
                } else if ("left".equalsIgnoreCase(area)) {
                    windowsToControl.add(1);
                    windowsToControl.add(3);
                } else if ("right".equalsIgnoreCase(area)) {
                    windowsToControl.add(2);
                    windowsToControl.add(4);
                }

                if (!windowsToControl.isEmpty()) {
                    if (command >= 1 && command <= 3) {
                        for (int w : windowsToControl) {
                            int lf = w == 1 ? command : 0;
                            int rf = w == 2 ? command : 0;
                            int lr = w == 3 ? command : 0;
                            int rr = w == 4 ? command : 0;
                            callMethod(bodyworkDevice, "setAllWindowState", 
                                    new Class<?>[]{int.class, int.class, int.class, int.class}, 
                                    lf, rf, lr, rr);
                            Thread.sleep(150);
                        }
                    } else {
                        int percent = customPercent != null ? customPercent : (command == 4 ? 10 : 50);
                        for (int w : windowsToControl) {
                            setWindowPercentageInternal(w, percent);
                            Thread.sleep(150);
                        }
                    }
                    return;
                }

                if (areaNum == 0) return;

                if (command == 4 || command == 5 || customPercent != null) {
                    setWindowPercentageInternal(areaNum, customPercent != null ? customPercent : (command == 4 ? 10 : 50));
                } else if (command >= 1 && command <= 3) {
                    if (areaNum >= 5) {
                        String cmdName = areaNum == 5 ? "voiceCtlMoonRoof" : "voiceCtlSunshadePanel";
                        int sdkCmd = command == 3 ? 4 : command;
                        callMethod(bodyworkDevice, cmdName, new Class<?>[]{int.class}, sdkCmd);
                    } else {
                        int lf = areaNum == 1 ? command : 0;
                        int rf = areaNum == 2 ? command : 0;
                        int lr = areaNum == 3 ? command : 0;
                        int rr = areaNum == 4 ? command : 0;
                        callMethod(bodyworkDevice, "setAllWindowState",
                                new Class<?>[]{int.class, int.class, int.class, int.class},
                                lf, rf, lr, rr);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to control Window", e);
            }
        });
    }

    private void setWindowPercentageInternal(int area, int percent) throws Exception {
        if (area >= 1 && area <= 4) {
            if (percent == 10) {
                callMethod(bodyworkDevice, "setBodyWindowCtrlState", new Class<?>[]{int.class, int.class}, area, 5);
            } else if (percent == 50) {
                callMethod(bodyworkDevice, "setBodyWindowCtrlState", new Class<?>[]{int.class, int.class}, area, 4);
            } else {
                int featureId;
                switch (area) {
                    case 1: featureId = featureClass.getField("BODYWORK_LF_WINDOW_TARGET_POSITION_SET").getInt(null); break;
                    case 2: featureId = featureClass.getField("BODYWORK_RF_WINDOW_TARGET_POSITION_SET").getInt(null); break;
                    case 3: featureId = featureClass.getField("BODYWORK_LR_WINDOW_TARGET_POSITION_SET").getInt(null); break;
                    case 4: featureId = featureClass.getField("BODYWORK_RR_WINDOW_TARGET_POSITION_SET").getInt(null); break;
                    default: return;
                }
                sendSetCommand(bodyworkDevice, bodyworkDevType, featureId, percent);
            }
        } else if (area == 5) {
            if (percent == 10) callMethod(bodyworkDevice, "voiceCtlMoonRoof", new Class<?>[]{int.class}, 5);
            else callMethod(bodyworkDevice, "setMoonRoofState", new Class<?>[]{int.class}, percent == 0 ? 0 : Math.max(21, percent));
        } else if (area == 6) {
            callMethod(bodyworkDevice, "setSunshadeState", new Class<?>[]{int.class}, percent);
        }
    }

    // 4. Seats controls
    public void controlSeat(String position, Integer heatingLevel, Integer ventilationLevel) {
        executorService.submit(() -> {
            try {
                int pos = "passenger".equalsIgnoreCase(position) ? 2 : 1;
                if (heatingLevel != null) {
                    callMethod(settingDevice, "setSeatHeatingState", new Class<?>[]{int.class, int.class}, pos, heatingLevel + 1);
                }
                if (ventilationLevel != null) {
                    callMethod(settingDevice, "setSeatVentilatingState", new Class<?>[]{int.class, int.class}, pos, ventilationLevel + 1);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to control Seat", e);
            }
        });
    }

    // 5. Steering Wheel heating
    public void controlSteeringWheelHeating(Boolean power) {
        executorService.submit(() -> {
            try {
                int state = power ? 2 : 1;
                try {
                    callMethod(settingDevice, "setSteeringWheelHeatingState", new Class<?>[]{int.class}, state);
                } catch (Exception e) {
                    sendSetCommand(settingDevice, settingDevType, 944767029, state);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to control Steering Wheel Heating", e);
            }
        });
    }

    // 6. Audio controls
    public void controlVolume(String action, Integer value) {
        executorService.submit(() -> {
            try {
                if ("mute".equalsIgnoreCase(action) || "unmute".equalsIgnoreCase(action)) {
                    callMethod(audioDevice, "setMuteState", new Class<?>[]{int.class}, "mute".equalsIgnoreCase(action) ? 1 : 0);
                } else if ("set".equalsIgnoreCase(action) && value != null) {
                    callMethod(audioDevice, "setVolume", new Class<?>[]{int.class}, value);
                } else if ("up".equalsIgnoreCase(action) || "down".equalsIgnoreCase(action)) {
                    int currentVol = (Integer) callMethod(audioDevice, "getVolume", new Class<?>[]{});
                    int delta = "up".equalsIgnoreCase(action) ? 5 : -5;
                    callMethod(audioDevice, "setVolume", new Class<?>[]{int.class}, Math.max(0, Math.min(39, currentVol + delta)));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to control Volume", e);
            }
        });
    }

    // 7. Navigation controls
    public void launchNavigation(String destination) {
        launchNavigation(destination, null, null);
    }

    public void launchNavigation(String destination, Double latitude, Double longitude) {
        executorService.submit(() -> {
            try {
                android.content.SharedPreferences prefs = context.getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
                String mapProvider = prefs.getString(ConfigData.KEY_MAP_PROVIDER, ConfigData.DEFAULT_MAP_PROVIDER);

                double dLat, dLng;
                String title = destination;

                SavedDestination saved = DestinationDbHelper.getInstance(context).findMatchingDestination(destination);
                if (saved != null) {
                    dLat = saved.getLatitude();
                    dLng = saved.getLongitude();
                    title = saved.getAlias();
                } else if (latitude != null && longitude != null) {
                    dLat = latitude;
                    dLng = longitude;
                } else {
                    showToast("Destination coordinates are required. Use an approved place-search Tool first.");
                    return;
                }

                final double fLat = dLat; final double fLng = dLng; final String fTitle = title;
                mainHandler.post(() -> {
                    if ("tmap".equals(mapProvider)) MapLauncher.startTmapNavigation(context, fTitle, fLat, fLng);
                    else MapLauncher.startNaverNavigation(context, fTitle, fLat, fLng);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch navigation", e);
            }
        });
    }



    public int getVolume() {
        try { return (Integer) callMethod(audioDevice, "getVolume", new Class<?>[]{}); }
        catch (Exception e) { return 0; }
    }

    public int getMuteState() {
        try { return (Integer) callMethod(audioDevice, "getMuteState", new Class<?>[]{}); }
        catch (Exception e) { return 0; }
    }

    public int getAirWindMode() {
        try {
            return (Integer) callMethod(acDevice, "getAcWindMode", new Class<?>[]{});
        } catch (Exception e) {
            Log.w(TAG, "Failed to read air direction mode", e);
            return -1;
        }
    }

    public int sendRawAcCommand(int fid, int val) throws Exception {
        return sendSetCommand(acDevice, acDevType, fid, val);
    }

    public void shutdown() {
        executorService.shutdown();
    }

    private void setAcTemperature(double celsius) {
        setTemperatureInternal(celsius);
    }

    private void setTemperatureInternal(double celsius) {
        try {
            if (acDevice == null || baseArraySetMethod == null || featureClass == null) return;
            int unitSetId = featureClass.getField("AC_TEMPERATURE_UNIT_SET").getInt(null);
            int tempMainSetId = featureClass.getField("AC_TEMP_MAIN_SET").getInt(null);
            int tempDeputySetId = featureClass.getField("AC_TEMP_DEPUTY_SET").getInt(null);
            int tempRearSetId = featureClass.getField("AC_TEMP_REAR_SET").getInt(null);
            int ctrlSourceSetId = featureClass.getField("AC_CTRL_SOURCE_SET").getInt(null);

            int[] area = {unitSetId, tempMainSetId, tempDeputySetId, tempRearSetId, ctrlSourceSetId};
            int value = (int) (celsius * 2);
            int[] param = {0, value, value, 0, 1}; // unit=0 (Celsius), main=value, deputy=value, rear=0, source=1

            int arrayResult = (int) baseArraySetMethod.invoke(acDevice, acDevType, area, param);
            Log.d(TAG, "setTemperatureInternal: Set temperature to " + celsius + ", result=" + arrayResult);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set temperature internally to " + celsius, e);
        }
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

    private boolean invokeAcMethod(String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Method m = acDevice.getClass().getMethod(methodName, paramTypes);
            Object res = m.invoke(acDevice, args);
            if (res instanceof Integer) {
                return (Integer) res == 0;
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Failed to invoke reflection method " + methodName, e);
            return false;
        }
    }

    private synchronized void initReflectionSync() throws Exception {
        if (isInitialized) return;
        Log.d(TAG, "initReflectionSync: Running synchronous initialization...");
        
        Class<?> acClass = Class.forName("android.hardware.bydauto.ac.BYDAutoAcDevice");
        Method acGetInstance = acClass.getMethod("getInstance", Context.class);
        acDevice = acGetInstance.invoke(null, new VehicleContextWrapper(context));
        Method getAcType = acClass.getMethod("getDevicetype");
        acDevType = (int) getAcType.invoke(acDevice);

        featureClass = Class.forName("android.hardware.bydauto.BYDAutoFeatureIds");

        Class<?> bodyworkClass = Class.forName("android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice");
        Method bodyworkGetInstance = bodyworkClass.getMethod("getInstance", Context.class);
        bodyworkDevice = bodyworkGetInstance.invoke(null, new VehicleContextWrapper(context));
        Method getBodyworkType = bodyworkClass.getMethod("getDevicetype");
        bodyworkDevType = (int) getBodyworkType.invoke(bodyworkDevice);

        Class<?> settingClass = Class.forName("android.hardware.bydauto.setting.BYDAutoSettingDevice");
        Method settingGetInstance = settingClass.getMethod("getInstance", Context.class);
        settingDevice = settingGetInstance.invoke(null, new VehicleContextWrapper(context));
        Method getSettingType = settingClass.getMethod("getDevicetype");
        settingDevType = (int) getSettingType.invoke(settingDevice);

        Class<?> audioClass = Class.forName("android.hardware.bydauto.audio.BYDAutoAudioDevice");
        Method audioGetInstance = audioClass.getMethod("getInstance", Context.class);
        audioDevice = audioGetInstance.invoke(null, new VehicleContextWrapper(context));
        Method getAudioType = audioClass.getMethod("getDevicetype");
        audioDevType = (int) getAudioType.invoke(audioDevice);

        try {
            Class<?> tyreClass = Class.forName("android.hardware.bydauto.tyre.BYDAutoTyreDevice");
            Method tyreGetInstance = tyreClass.getMethod("getInstance", Context.class);
            tyreDevice = tyreGetInstance.invoke(null, new VehicleContextWrapper(context));
            Method getTyreType = tyreClass.getMethod("getDevicetype");
            tyreDevType = (int) getTyreType.invoke(tyreDevice);
            Log.i(TAG, "initReflectionSync: Tyre Device successfully initialized.");
        } catch (Exception e) {
            Log.w(TAG, "initReflectionSync: Failed to initialize Tyre Device reflection", e);
        }

        try {
            Class<?> instrumentClass = Class.forName("android.hardware.bydauto.instrument.BYDAutoInstrumentDevice");
            Method instrumentGetInstance = instrumentClass.getMethod("getInstance", Context.class);
            instrumentDevice = instrumentGetInstance.invoke(null, new VehicleContextWrapper(context));
            Method getInstrumentType = instrumentClass.getMethod("getDevicetype");
            instrumentDevType = (int) getInstrumentType.invoke(instrumentDevice);
            Log.i(TAG, "initReflectionSync: Instrument Device successfully initialized.");
        } catch (Exception e) {
            Log.w(TAG, "initReflectionSync: Failed to initialize Instrument Device reflection", e);
        }

        Class<?> absDeviceClass = acClass.getSuperclass();
        baseSetMethod = absDeviceClass.getDeclaredMethod("set", int.class, int.class, int.class);
        baseSetMethod.setAccessible(true);

        baseArraySetMethod = absDeviceClass.getDeclaredMethod("set", int.class, int[].class, int[].class);
        baseArraySetMethod.setAccessible(true);

        isInitialized = true;
        Log.i(TAG, "initReflectionSync: Successfully completed synchronous initialization.");
    }

    public Map<String, Integer> getTyrePressures() {
        Map<String, Integer> map = new HashMap<>();
        map.put("FL", -1);
        map.put("FR", -1);
        map.put("RL", -1);
        map.put("RR", -1);

        if (tyreDevice == null) {
            Log.w(TAG, "getTyrePressures: tyreDevice is null, reflection failed or unsupported on this system.");
            return map;
        }

        try {
            Method getPressureMethod = tyreDevice.getClass().getMethod("getTyrePressureValue", int.class);
            int invalidVal = -1;
            try {
                invalidVal = tyreDevice.getClass().getField("TYRE_COMMAND_INVALID_VALUE").getInt(null);
            } catch (Exception ignored) {}

            String[] positions = {"FL", "FR", "RL", "RR"};
            for (int i = 0; i < positions.length; i++) {
                int tyreIndex = i + 1; // 1: FL, 2: FR, 3: RL, 4: RR
                Object res = getPressureMethod.invoke(tyreDevice, tyreIndex);
                if (res instanceof Integer) {
                    int val = (Integer) res;
                    if (val != invalidVal) {
                        map.put(positions[i], val);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get tyre pressures", e);
        }
        return map;
    }

    public Map<String, Integer> getTyreTemperatures() {
        Map<String, Integer> map = new HashMap<>();
        map.put("FL", -1);
        map.put("FR", -1);
        map.put("RL", -1);
        map.put("RR", -1);

        if (instrumentDevice == null) {
            Log.w(TAG, "getTyreTemperatures: instrumentDevice is null, reflection failed or unsupported.");
            return map;
        }

        try {
            Method getTempMethod = instrumentDevice.getClass().getMethod("getWheelTemperature", int.class);
            
            // BYDAutoInstrumentDevice position mapping:
            // 3: LF (FL), 1: RF (FR), 4: LB (RL), 2: RB (RR)
            int[] positions = {3, 1, 4, 2};
            String[] keys = {"FL", "FR", "RL", "RR"};
            
            for (int i = 0; i < keys.length; i++) {
                Object res = getTempMethod.invoke(instrumentDevice, positions[i]);
                if (res instanceof Integer) {
                    int val = (Integer) res;
                    // Sentinel value: -2147482645 is invalid/error
                    if (val != -2147482645) {
                        if (val >= -40 && val <= 150) {
                            map.put(keys[i], val);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get tyre temperatures", e);
        }
        return map;
    }

    public Double getOutCarTemperature() {
        if (instrumentDevice == null) {
            Log.w(TAG, "getOutCarTemperature: instrumentDevice is null, reflection failed or unsupported.");
            return null;
        }
        try {
            Method getOutTempMethod = instrumentDevice.getClass().getMethod("getOutCarTemperature");
            Object res = getOutTempMethod.invoke(instrumentDevice);
            if (res instanceof Number) {
                double tempVal = ((Number) res).doubleValue();
                if (tempVal >= -50.0 && tempVal <= 60.0) {
                    Log.d(TAG, "getOutCarTemperature: " + tempVal + "C");
                    return tempVal;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get external car temperature via reflection", e);
        }
        return null;
    }

    private int getFeatureId(String name, int fallback) {
        if (featureClass != null) {
            try {
                return featureClass.getField(name).getInt(null);
            } catch (Exception e) {
                Log.w(TAG, "Failed to get feature ID: " + name + ", using fallback: " + fallback, e);
            }
        }
        return fallback;
    }

    private int sendSetCommandBatch(Object device, int devType, int[] featureIds, int[] values) throws Exception {
        if (device == null || baseArraySetMethod == null) {
            throw new IllegalStateException("Device reflection not initialized");
        }
        Object result = baseArraySetMethod.invoke(device, devType, featureIds, values);
        return result instanceof Integer ? (Integer) result : -1;
    }

    public boolean setAmbientLightEnabled(boolean on) {
        if (settingDevice != null) {
            try {
                int panelStateFid = getFeatureId("SET_ATMOSPHERE_LAMP_PANEL_STATE_SET", 1276153872);
                int state = on ? 1 : 2; // 1: OPEN (ON), 2: CLOSE (OFF)
                int ret = sendSetCommand(settingDevice, settingDevType, panelStateFid, state);
                Log.i(TAG, "setAmbientLightEnabled (settingDevice panel state): " + on + ", state: " + state + ", fid: " + panelStateFid + ", ret: " + ret);

                // If turning ON, also force the color/brightness/source settings to custom manual color mode
                if (on && ret == 0) {
                    int colorSetId = getFeatureId("SET_INTERIOR_ATMOSPHERE_LAMP_COLOR_SET", 1069547528);
                    int brightnessSetId = getFeatureId("SET_INTERIOR_ATMOSPHERE_LAMP_BRIGHTNESS_SET", 1069547533);
                    int areaSetId = getFeatureId("SET_INTERIOR_ATMOSPHERE_LAMP_AREA_SET", 1069547568);
                    int sourceSetId = getFeatureId("SET_INTERIOR_ATMOSPHERE_LAMP_SOURCE_SET", 1069547575);

                    int[] features = {colorSetId, brightnessSetId, areaSetId, sourceSetId};
                    int[] values = {0, 0, 3, 0}; // 0, 0 are placeholders (do not change); 3: All areas, 0: Custom manual mode
                    int batchRet = sendSetCommandBatch(settingDevice, settingDevType, features, values);
                    Log.i(TAG, "setAmbientLightEnabled (settingDevice batch config): ret: " + batchRet);
                }

                if (ret == 0) {
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "setAmbientLightEnabled via settingDevice failed, trying fallback to lightDevice", e);
            }
        }

        if (lightDevice == null) {
            Log.w(TAG, "setAmbientLightEnabled: lightDevice is null.");
            return false;
        }
        try {
            int ret = sendSetCommand(lightDevice, lightDevType, LIGHT_ATMOSPHERE_MAIN_SWITCH_SET, on ? 1 : 0);
            Log.i(TAG, "setAmbientLightEnabled (lightDevice fallback): " + on + ", ret: " + ret);
            return ret == 0;
        } catch (Exception e) {
            Log.e(TAG, "setAmbientLightEnabled fallback failed", e);
            return false;
        }
    }

    public boolean setAmbientBrightness(int level) {
        if (level < 0 || level > 100) return false;

        if (settingDevice != null) {
            try {
                int colorSetId = getFeatureId("SET_INTERIOR_ATMOSPHERE_LAMP_COLOR_SET", 1069547528);
                int brightnessSetId = getFeatureId("SET_INTERIOR_ATMOSPHERE_LAMP_BRIGHTNESS_SET", 1069547533);
                int areaSetId = getFeatureId("SET_INTERIOR_ATMOSPHERE_LAMP_AREA_SET", 1069547568);
                int sourceSetId = getFeatureId("SET_INTERIOR_ATMOSPHERE_LAMP_SOURCE_SET", 1069547575);

                int settingLevel = (int) Math.round(level / 20.0);
                if (settingLevel < 0) settingLevel = 0;
                if (settingLevel > 5) settingLevel = 5;
                int val = settingLevel + 1; // 1 to 6

                int[] features = {colorSetId, brightnessSetId, areaSetId, sourceSetId};
                int[] values = {0, val, 3, 0}; // 0: color placeholder, val: new brightness, 3: All areas, 0: Custom manual mode

                int ret = sendSetCommandBatch(settingDevice, settingDevType, features, values);
                Log.i(TAG, "setAmbientBrightness (settingDevice batch): level=" + level + "%, val=" + val + ", ret: " + ret);
                if (ret == 0) {
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "setAmbientBrightness via settingDevice batch failed, trying fallback to lightDevice", e);
            }
        }

        if (lightDevice == null) {
            Log.w(TAG, "setAmbientBrightness: lightDevice is null.");
            return false;
        }
        try {
            int ret = sendSetCommand(lightDevice, lightDevType, LIGHT_ATMOSPHERE_CUSTOM_BRIGHTNESS_SET, level);
            Log.d(TAG, "setAmbientBrightness (lightDevice fallback): " + level + ", ret: " + ret);
            return ret == 0;
        } catch (Exception e) {
            Log.e(TAG, "setAmbientBrightness fallback failed", e);
            return false;
        }
    }

    private int mapColorToPaletteIndex(int colorValue) {
        return com.poorgrammera.bydsubai.data.BydAmbientPalette.fromRgb(colorValue).getIndex();
    }

    public boolean setAmbientColor(int colorOrIndex) {
        if (settingDevice != null) {
            try {
                int colorSetId = getFeatureId("SET_INTERIOR_ATMOSPHERE_LAMP_COLOR_SET", 1069547528);
                int brightnessSetId = getFeatureId("SET_INTERIOR_ATMOSPHERE_LAMP_BRIGHTNESS_SET", 1069547533);
                int areaSetId = getFeatureId("SET_INTERIOR_ATMOSPHERE_LAMP_AREA_SET", 1069547568);
                int sourceSetId = getFeatureId("SET_INTERIOR_ATMOSPHERE_LAMP_SOURCE_SET", 1069547575);

                int colorIndex;
                if (colorOrIndex >= 1 && colorOrIndex <= 31) {
                    colorIndex = colorOrIndex;
                } else {
                    colorIndex = mapColorToPaletteIndex(colorOrIndex);
                }

                int[] features = {colorSetId, brightnessSetId, areaSetId, sourceSetId};
                int[] values = {colorIndex, 0, 3, 0}; // 3: All areas, 0: Custom manual mode

                int ret = sendSetCommandBatch(settingDevice, settingDevType, features, values);
                Log.i(TAG, "setAmbientColor (settingDevice palette only): input=" + colorOrIndex + ", colorIndex=" + colorIndex + ", ret: " + ret);
                return ret == 0;
            } catch (Exception e) {
                Log.e(TAG, "setAmbientColor settingDevice failed", e);
            }
        }
        return false;
    }

    public double getElecDrivingRangeValue() {
        if (statisticDevice == null) {
            try {
                Class<?> statisticClass = Class.forName("android.hardware.bydauto.statistic.BYDAutoStatisticDevice");
                Method statisticGetInstance = statisticClass.getMethod("getInstance", Context.class);
                statisticDevice = statisticGetInstance.invoke(null, new VehicleContextWrapper(context));
            } catch (Exception e) {
                Log.e(TAG, "Failed to lazy init statisticDevice", e);
            }
        }
        if (statisticDevice != null) {
            try {
                Method method = statisticDevice.getClass().getMethod("getElecDrivingRangeValue");
                Object val = method.invoke(statisticDevice);
                if (val instanceof Number) {
                    return ((Number) val).doubleValue();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to invoke getElecDrivingRangeValue", e);
            }
        }
        return -1.0;
    }

    public double getSOCBatteryPercentage() {
        if (statisticDevice == null) {
            try {
                Class<?> statisticClass = Class.forName("android.hardware.bydauto.statistic.BYDAutoStatisticDevice");
                Method statisticGetInstance = statisticClass.getMethod("getInstance", Context.class);
                statisticDevice = statisticGetInstance.invoke(null, new VehicleContextWrapper(context));
            } catch (Exception e) {
                Log.e(TAG, "Failed to lazy init statisticDevice", e);
            }
        }
        if (statisticDevice != null) {
            try {
                Method method = statisticDevice.getClass().getMethod("getSOCBatteryPercentage");
                Object val = method.invoke(statisticDevice);
                if (val instanceof Number) {
                    return ((Number) val).doubleValue();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to invoke getSOCBatteryPercentage", e);
            }
        }
        return -1.0;
    }
}
