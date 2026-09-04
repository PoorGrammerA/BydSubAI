package com.poorgrammera.bydsubai.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import com.poorgrammera.bydsubai.data.ConfigData;
import com.poorgrammera.bydsubai.service.GeminiLiveService;

public class ShortcutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            Intent incoming = getIntent();
            String character = null;
            if (incoming != null) {
                character = incoming.getStringExtra("character");
            }

            if (character != null && (character.equals("rumi") || character.equals("kitt") || character.equals("asurada"))) {
                getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE).edit()
                        .putString(ConfigData.KEY_VOICE_MODE, character)
                        .apply();
            } else {
                character = getSharedPreferences(ConfigData.PREF_NAME, MODE_PRIVATE)
                        .getString(ConfigData.KEY_VOICE_MODE, "rumi");
            }

            Intent intent = new Intent(this, GeminiLiveService.class);
            intent.setAction(GeminiLiveService.ACTION_TRIGGER_GEMINI);
            androidx.core.content.ContextCompat.startForegroundService(this, intent);

            String label = "루미";
            if ("kitt".equals(character)) {
                label = "키트";
            } else if ("asurada".equals(character)) {
                label = "아스라다";
            }
            Toast.makeText(this, label + " 음성 제어를 시작합니다.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        
        finish();
    }
}
