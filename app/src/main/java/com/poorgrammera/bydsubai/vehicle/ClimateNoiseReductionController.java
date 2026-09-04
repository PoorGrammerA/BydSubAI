package com.poorgrammera.bydsubai.vehicle;

import android.content.Context;
import android.util.Log;

import com.poorgrammera.bydsubai.data.ConfigData;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Temporarily reduces A/C fan noise while an audio input path is actively listening.
 * The original fan level is restored only after every registered input path is inactive.
 */
public final class ClimateNoiseReductionController {
    private static final String TAG = "ClimateNoiseReduction";
    private static final int QUIET_FAN_LEVEL = 1;
    private static final int MIN_FAN_LEVEL_TO_REDUCE = 2;

    private static volatile ClimateNoiseReductionController instance;

    private final VehicleController vehicleController;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable ->
            new Thread(runnable, "ClimateNoiseReduction-Worker"));
    private final Set<String> activeSources = new HashSet<>();

    private int savedWindLevel = -1;
    private boolean fanTemporarilyReduced;

    private ClimateNoiseReductionController(Context context) {
        this.context = context.getApplicationContext();
        vehicleController = new VehicleController(this.context);
    }

    public static ClimateNoiseReductionController getInstance(Context context) {
        if (instance == null) {
            synchronized (ClimateNoiseReductionController.class) {
                if (instance == null) instance = new ClimateNoiseReductionController(context);
            }
        }
        return instance;
    }

    public void beginListening(String source) {
        executor.execute(() -> {
            if (!isEnabled()) {
                Log.d(TAG, "Fan reduction is disabled in settings");
                return;
            }
            if (!activeSources.add(source) || activeSources.size() != 1) return;

            int powerState = vehicleController.getAirConditionerPowerState();
            int windLevel = vehicleController.getAirWindLevel();
            if (powerState <= 0 || windLevel < MIN_FAN_LEVEL_TO_REDUCE) {
                Log.d(TAG, "Fan reduction skipped: power=" + powerState + ", wind=" + windLevel);
                return;
            }

            savedWindLevel = windLevel;
            fanTemporarilyReduced = true;
            Log.i(TAG, "Audio input started; lowering A/C fan from " + windLevel + " to " + QUIET_FAN_LEVEL);
            vehicleController.controlAirConditioner(null, QUIET_FAN_LEVEL, null, null,
                    null, null, null, null, null, null);
        });
    }

    public void endListening(String source) {
        executor.execute(() -> {
            activeSources.remove(source);
            if (!activeSources.isEmpty()) return;
            restoreSavedWindLevel();
        });
    }

    public void setEnabled(boolean enabled) {
        if (enabled) return;
        executor.execute(() -> {
            activeSources.clear();
            restoreSavedWindLevel();
        });
    }

    private boolean isEnabled() {
        return context.getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(ConfigData.KEY_REDUCE_AC_FAN_DURING_AUDIO_INPUT,
                        ConfigData.DEFAULT_REDUCE_AC_FAN_DURING_AUDIO_INPUT);
    }

    private void restoreSavedWindLevel() {
        if (!fanTemporarilyReduced) return;
        int windLevelToRestore = savedWindLevel;
        savedWindLevel = -1;
        fanTemporarilyReduced = false;
        Log.i(TAG, "Restoring A/C fan to " + windLevelToRestore);
        vehicleController.controlAirConditioner(null, windLevelToRestore, null, null,
                null, null, null, null, null, null);
    }
}
