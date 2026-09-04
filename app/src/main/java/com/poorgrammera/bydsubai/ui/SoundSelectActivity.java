package com.poorgrammera.bydsubai.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.poorgrammera.bydsubai.R;
import com.poorgrammera.bydsubai.data.ConfigData;
import android.util.Log;
import com.poorgrammera.bydsubai.util.MediaPlayerManager;

import java.util.ArrayList;
import java.util.List;

public class SoundSelectActivity extends Activity {
    private static final String TAG = "SoundSelectActivity";

    private static class SoundSettingItem {
        final int containerId;
        final String soundKey;
        final String title;

        SoundSettingItem(int containerId, String soundKey, String title) {
            this.containerId = containerId;
            this.soundKey = soundKey;
            this.title = title;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sound_select);

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        List<SoundSettingItem> items = new ArrayList<>();
        items.add(new SoundSettingItem(R.id.item_gear_d, "gear_2_d", "기어 D 변경음"));
        items.add(new SoundSettingItem(R.id.item_gear_r, "gear_1_r", "기어 R 변경음"));
        items.add(new SoundSettingItem(R.id.item_gear_n, "gear_0_n", "기어 N 변경음"));
        items.add(new SoundSettingItem(R.id.item_gear_p, "gear_3_p", "기어 P 변경음"));
        items.add(new SoundSettingItem(R.id.item_cruise_on, "cruise_control_on", "크루즈 컨트롤 ON"));
        items.add(new SoundSettingItem(R.id.item_cruise_off, "cruise_control_off", "크루즈 컨트롤 OFF"));
        items.add(new SoundSettingItem(R.id.item_adas_on, "adas_blind_spot_assist_1_on", "사각지대 어시스트 ON"));
        items.add(new SoundSettingItem(R.id.item_adas_off, "adas_blind_spot_assist_0_off", "사각지대 어시스트 OFF"));
        items.add(new SoundSettingItem(R.id.item_radar_on, "reverse_radar_1_on", "후방 레이더 ON"));
        items.add(new SoundSettingItem(R.id.item_radar_off, "reverse_radar_0_off", "후방 레이더 OFF"));
        items.add(new SoundSettingItem(R.id.item_sports_on, "operation_mode_2_sports_mode_on", "스포츠 모드 ON"));
        items.add(new SoundSettingItem(R.id.item_sports_off, "operation_mode_1_sports_mode_off", "스포츠 모드 OFF"));
        items.add(new SoundSettingItem(R.id.item_road_common, "energy_road_surface_1_common", "도로 표면 일반"));
        items.add(new SoundSettingItem(R.id.item_road_snow, "energy_road_surface_2_snow", "도로 표면 눈길"));
        items.add(new SoundSettingItem(R.id.item_recycle_std, "energy_recycle_2_standard", "회생제동 표준"));
        items.add(new SoundSettingItem(R.id.item_recycle_high, "energy_recycle_3_high", "회생제동 강하게"));

        for (SoundSettingItem item : items) {
            setupItem(item);
        }
    }

    private void setupItem(SoundSettingItem item) {
        View container = findViewById(item.containerId);
        if (container == null) return;
        
        TextView tvTitle = container.findViewById(R.id.tv_setting_title);
        TextView tvValue = container.findViewById(R.id.tv_setting_value);

        tvTitle.setText(item.title);
        
        SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE);
        String currentVal = prefs.getString("pref_sound_mode_" + item.soundKey, "voice");
        tvValue.setText(formatValue(currentVal));

        container.setOnClickListener(v -> showSelectionDialog(item, tvValue));
    }

    private String formatValue(String value) {
        if ("voice".equals(value)) return "보이스(기본값)";
        if ("se_01".equals(value)) return "SE 01";
        if ("se_02".equals(value)) return "SE 02";
        if ("se_03".equals(value)) return "SE 03";
        if ("se_04".equals(value)) return "SE 04";
        if ("se_05".equals(value)) return "SE 05";
        return value;
    }

    private void showSelectionDialog(SoundSettingItem item, TextView tvValue) {
        SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE);
        String currentVal = prefs.getString("pref_sound_mode_" + item.soundKey, "voice");

        String[] options = {"보이스(기본값)", "SE 01", "SE 02", "SE 03", "SE 04", "SE 05"};
        final String[] values = {"voice", "se_01", "se_02", "se_03", "se_04", "se_05"};

        int checkedItem = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(currentVal)) {
                checkedItem = i;
                break;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle(item.title + " 선택");
        builder.setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
            String selectedVal = values[which];
            playPreview(item.soundKey, selectedVal);
        });

        builder.setPositiveButton("저장", (dialog, which) -> {
            int selectedPosition = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
            String selectedVal = values[selectedPosition];
            prefs.edit().putString("pref_sound_mode_" + item.soundKey, selectedVal).apply();
            tvValue.setText(formatValue(selectedVal));
        });
        builder.setNegativeButton("취소", null);
        builder.show();
    }

    private void playPreview(String soundKey, String selectedVal) {
        if ("voice".equals(selectedVal)) {
            SharedPreferences prefs = getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE);
            String voiceMode = prefs.getString(ConfigData.KEY_VOICE_MODE, ConfigData.DEFAULT_VOICE_MODE);
            String resName = voiceMode + "_" + soundKey;
            int resId = getResources().getIdentifier(resName, "raw", getPackageName());
            if (resId == 0) {
                resId = getResources().getIdentifier(soundKey, "raw", getPackageName());
            }
            if (resId != 0) {
                MediaPlayerManager.getInstance(this).play(this, resId, 1.0f, null);
            } else {
                Log.w(TAG, "playPreview: Voice resource not found for: " + resName);
            }
        } else {
            int resId = 0;
            if ("se_01".equals(selectedVal)) resId = R.raw.se_01;
            else if ("se_02".equals(selectedVal)) resId = R.raw.se_02;
            else if ("se_03".equals(selectedVal)) resId = R.raw.se_03;
            else if ("se_04".equals(selectedVal)) resId = R.raw.se_04;
            else if ("se_05".equals(selectedVal)) resId = R.raw.se_05;

            if (resId != 0) {
                MediaPlayerManager.getInstance(this).play(this, resId, 1.0f, null);
            } else {
                Log.w(TAG, "playPreview: SE resource not found for: " + selectedVal);
            }
        }
    }
}
