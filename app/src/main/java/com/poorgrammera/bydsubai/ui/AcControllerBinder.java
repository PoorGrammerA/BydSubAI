package com.poorgrammera.bydsubai.ui;

import android.graphics.Color;
import android.util.Log;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.poorgrammera.bydsubai.R;

/** Binds the climate dashboard to the shared VehicleController command queue. */
public class AcControllerBinder {
    private static final String TAG = "AcControllerBinder";

    private final VehicleControlActivity activity;
    private TextView tvWindLevelDisplay;
    private TextView tvTempDisplay;
    private TextView tvTempRawDisplay;
    private Button btnWindDirFace;
    private Button btnWindDirFoot;
    private Button btnWindDirScreen;

    private boolean isFrontDefrostOn;
    private boolean isRearDefrostOn;
    private boolean isWindFaceOn;
    private boolean isWindFootOn;
    private boolean isWindScreenOn;
    private double currentTempCelsius = 20.0;

    public AcControllerBinder(VehicleControlActivity activity) {
        this.activity = activity;
    }

    public void bind() {
        Button btnAcOn = activity.findViewById(R.id.btn_ac_on);
        Button btnAcOff = activity.findViewById(R.id.btn_ac_off);
        tvWindLevelDisplay = activity.findViewById(R.id.tv_wind_level_display);
        TextView tvCurrentWindLevel = activity.findViewById(R.id.tv_current_wind_level);
        TextView tvCurrentAcPower = activity.findViewById(R.id.tv_current_ac_power);
        SeekBar sbWindLevel = activity.findViewById(R.id.sb_wind_level);
        tvTempDisplay = activity.findViewById(R.id.tv_temp_display);
        tvTempRawDisplay = activity.findViewById(R.id.tv_temp_raw_display);
        Button btnTempDown = activity.findViewById(R.id.btn_temp_down);
        Button btnTempUp = activity.findViewById(R.id.btn_temp_up);
        Button btnApplyTemp = activity.findViewById(R.id.btn_apply_temp);

        btnAcOn.setOnClickListener(v -> setAcPower(true));
        btnAcOff.setOnClickListener(v -> setAcPower(false));
        activity.findViewById(R.id.btn_read_wind_level).setOnClickListener(v ->
                readCurrentClimateStatus(tvCurrentWindLevel, tvCurrentAcPower));
        sbWindLevel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvWindLevelDisplay.setText("Wind Level: " + Math.max(1, progress));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                setAcWindLevel(Math.max(1, seekBar.getProgress()));
            }
        });

        updateTempUI();
        btnTempDown.setOnClickListener(v -> {
            if (currentTempCelsius > 17.0) {
                currentTempCelsius -= 0.5;
                updateTempUI();
            }
        });
        btnTempUp.setOnClickListener(v -> {
            if (currentTempCelsius < 33.0) {
                currentTempCelsius += 0.5;
                updateTempUI();
            }
        });
        btnApplyTemp.setOnClickListener(v -> setTemperature(currentTempCelsius));

        activity.findViewById(R.id.btn_ac_mode_cool).setOnClickListener(v -> setAcMode("cool"));
        activity.findViewById(R.id.btn_ac_mode_heat).setOnClickListener(v -> setAcMode("heat"));
        activity.findViewById(R.id.btn_ac_mode_fan).setOnClickListener(v -> setAcMode("fan"));

        btnWindDirFace = activity.findViewById(R.id.btn_wind_dir_face);
        btnWindDirFoot = activity.findViewById(R.id.btn_wind_dir_foot);
        btnWindDirScreen = activity.findViewById(R.id.btn_wind_dir_screen);
        btnWindDirFace.setOnClickListener(v -> toggleWindDirection("face"));
        btnWindDirFoot.setOnClickListener(v -> toggleWindDirection("foot"));
        btnWindDirScreen.setOnClickListener(v -> toggleWindDirection("screen"));

        activity.findViewById(R.id.btn_defrost_front).setOnClickListener(v -> toggleFrontDefrost());
        activity.findViewById(R.id.btn_defrost_rear).setOnClickListener(v -> toggleRearDefrost());
        activity.findViewById(R.id.btn_cycle_internal).setOnClickListener(v -> setCycleMode("recycle"));
        activity.findViewById(R.id.btn_cycle_external).setOnClickListener(v -> setCycleMode("fresh"));
    }

    private void updateTempUI() {
        tvTempDisplay.setText(String.format("%.1f°C", currentTempCelsius));
        tvTempRawDisplay.setText(String.format("(raw: %d)", (int) (currentTempCelsius * 2)));
    }

    private void setAcPower(boolean enabled) {
        activity.vehicleController.controlAirConditioner(
                enabled, null, null, null, null, null, null, null, null, null);
    }

    private void setAcWindLevel(int level) {
        activity.vehicleController.controlAirConditioner(
                null, level, null, null, null, null, null, null, null, null);
    }

    private void readCurrentClimateStatus(TextView windDisplay, TextView powerDisplay) {
        windDisplay.setText(R.string.current_wind_level_reading);
        powerDisplay.setText(R.string.current_ac_power_reading);
        new Thread(() -> {
            int level = activity.vehicleController.getAirWindLevel();
            int powerState = activity.vehicleController.getAirConditionerPowerState();
            activity.runOnUiThread(() -> {
                windDisplay.setText(level >= 0
                        ? activity.getString(R.string.current_wind_level_format, level)
                        : activity.getString(R.string.current_wind_level_unavailable));
                powerDisplay.setText(powerState >= 0
                        ? activity.getString(powerState == 0
                                ? R.string.current_ac_power_off : R.string.current_ac_power_on)
                        : activity.getString(R.string.current_ac_power_unavailable));
            });
        }, "ClimateStatus-Reader").start();
    }

    private void setTemperature(double celsius) {
        activity.vehicleController.controlAirConditioner(
                null, null, celsius, null, null, null, null, null, null, null);
    }

    public void updateWindDirectionUI() {
        new Thread(() -> {
            int mode = activity.vehicleController.getAirWindMode();
            if (mode < 0) {
                Log.w(TAG, "Unable to read current air direction mode");
                return;
            }
            isWindFaceOn = mode == 1 || mode == 2 || mode == 6 || mode == 7;
            isWindFootOn = mode == 2 || mode == 3 || mode == 4 || mode == 6;
            isWindScreenOn = mode == 4 || mode == 5 || mode == 6 || mode == 7;
            activity.runOnUiThread(this::updateWindDirectionButtons);
        }, "ClimateDirection-Reader").start();
    }

    private void toggleWindDirection(String direction) {
        if ("face".equals(direction)) isWindFaceOn = !isWindFaceOn;
        else if ("foot".equals(direction)) isWindFootOn = !isWindFootOn;
        else if ("screen".equals(direction)) isWindScreenOn = !isWindScreenOn;

        if (!isWindFaceOn && !isWindFootOn && !isWindScreenOn) {
            activity.showToastOnUi("At least one air direction must remain enabled.");
            updateWindDirectionUI();
            return;
        }

        activity.vehicleController.controlAirConditioner(
                null, null, null, null, null, null, null,
                isWindFaceOn, isWindFootOn, isWindScreenOn);
        updateWindDirectionButtons();
    }

    private void updateWindDirectionButtons() {
        if (btnWindDirFace != null) {
            btnWindDirFace.setBackgroundColor(isWindFaceOn ? Color.parseColor("#00BCD4") : Color.parseColor("#9E9E9E"));
        }
        if (btnWindDirFoot != null) {
            btnWindDirFoot.setBackgroundColor(isWindFootOn ? Color.parseColor("#FF9800") : Color.parseColor("#9E9E9E"));
        }
        if (btnWindDirScreen != null) {
            btnWindDirScreen.setBackgroundColor(isWindScreenOn ? Color.parseColor("#4CAF50") : Color.parseColor("#9E9E9E"));
        }
    }

    private void toggleFrontDefrost() {
        isFrontDefrostOn = !isFrontDefrostOn;
        activity.vehicleController.controlAirConditioner(
                null, null, null, null, isFrontDefrostOn, null, null, null, null, null);
    }

    private void toggleRearDefrost() {
        isRearDefrostOn = !isRearDefrostOn;
        activity.vehicleController.controlAirConditioner(
                null, null, null, null, null, isRearDefrostOn, null, null, null, null);
    }

    private void setCycleMode(String mode) {
        activity.vehicleController.controlAirConditioner(
                null, null, null, mode, null, null, null, null, null, null);
    }

    private void setAcMode(String mode) {
        activity.vehicleController.controlAirConditioner(
                null, null, null, null, null, null, mode, null, null, null);
    }
}
