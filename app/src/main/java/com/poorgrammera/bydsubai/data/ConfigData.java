package com.poorgrammera.bydsubai.data;
import com.poorgrammera.bydsubai.BuildConfig;
import com.poorgrammera.bydsubai.R;


import java.util.Arrays;
import java.util.List;

public class ConfigData {
    public static final String PREF_NAME = "BydSubAiPrefs";
    public static final String KEY_GEMINI_API_KEY = "pref_gemini_api_key";
    public static final String KEY_GEMINI_LIVE_MODEL = "pref_gemini_model";
    public static final String KEY_GEMINI_NORMAL_MODEL = "pref_gemini_normal_model";
    public static final String KEY_DEVELOPER_MODE = "pref_developer_mode";
    public static final String KEY_KITT_MODE = "pref_kitt_mode";
    public static final String KEY_VOICE_MODE = "pref_voice_mode";
    public static final String KEY_MAP_PROVIDER = "pref_map_provider";
    public static final String KEY_END_MODE = "pref_end_mode";
    public static final String KEY_USE_MEMORY = "pref_use_memory";
    public static final String KEY_IS_DILINK3 = "isDilink3";
 
    // User Name Configurations
    public static final String KEY_USER_NAME_RUMI_KO = "pref_user_name_rumi_ko";
    public static final String KEY_USER_NAME_RUMI_EN = "pref_user_name_rumi_en";
    public static final String KEY_USER_NAME_RUMI_JA = "pref_user_name_rumi_ja";
    public static final String KEY_USER_NAME_KITT_KO = "pref_user_name_kitt_ko";
    public static final String KEY_USER_NAME_KITT_EN = "pref_user_name_kitt_en";
    public static final String KEY_USER_NAME_KITT_JA = "pref_user_name_kitt_ja";
    public static final String KEY_USER_NAME_ASURADA_KO = "pref_user_name_asurada_ko";
    public static final String KEY_USER_NAME_ASURADA_EN = "pref_user_name_asurada_en";
    public static final String KEY_USER_NAME_ASURADA_JA = "pref_user_name_asurada_ja";

    // Sound Alert Configurations
    public static final String KEY_ALERT_GEAR_N = "pref_alert_gear_n";
    public static final String KEY_ALERT_GEAR_R = "pref_alert_gear_r";
    public static final String KEY_ALERT_GEAR_D = "pref_alert_gear_d";
    public static final String KEY_ALERT_GEAR_P = "pref_alert_gear_p";
    public static final String KEY_ALERT_CRUISE = "pref_alert_cruise";
    public static final String KEY_ALERT_SPORT = "pref_alert_sport";
    public static final String KEY_ALERT_SNOW = "pref_alert_snow";
    public static final String KEY_ALERT_RADAR = "pref_alert_radar";
    public static final String KEY_ALERT_BSD = "pref_alert_bsd";
    public static final String KEY_ALERT_RECYCLE = "pref_alert_recycle";
    public static final String KEY_ALERT_BOOT = "pref_alert_boot";
    public static final String KEY_BOOT_WEATHER_BRIEFING = "pref_boot_weather_briefing";
    public static final String KEY_BOOT_NEWS_BRIEFING = "pref_boot_news_briefing";
    public static final String ACTION_BOOT_SOUND_COMPLETED = "com.poorgrammera.bydsubai.BOOT_SOUND_COMPLETED";

    // Vehicle Control Tool Enable Configurations (default: true)
    public static final String KEY_ENABLE_TOOL_AC = "pref_enable_tool_ac";
    public static final String KEY_ENABLE_TOOL_SEAT = "pref_enable_tool_seat";
    public static final String KEY_ENABLE_TOOL_WHEEL = "pref_enable_tool_wheel";
    public static final String KEY_ENABLE_TOOL_TRUNK = "pref_enable_tool_trunk";
    public static final String KEY_ENABLE_TOOL_WINDOW = "pref_enable_tool_window";
    public static final String KEY_ENABLE_TOOL_SUNROOF = "pref_enable_tool_sunroof";
    public static final String KEY_ENABLE_TOOL_SUNSHADE = "pref_enable_tool_sunshade";

    public static final String DEFAULT_GEMINI_API_KEY = "";
    public static final String DEFAULT_GEMINI_LIVE_MODEL = "gemini-3.1-flash-live-preview";
    public static final String DEFAULT_GEMINI_NORMAL_MODEL = "gemma-4-31b-it";
    public static final boolean DEFAULT_KITT_MODE = false;
    public static final String DEFAULT_VOICE_MODE = "rumi";
    public static final String DEFAULT_MAP_PROVIDER = "tmap";
    public static final String DEFAULT_END_MODE = "auto";

    // User Name Default Values
    public static final String DEFAULT_USER_NAME_RUMI_KO = "운전자님";
    public static final String DEFAULT_USER_NAME_RUMI_EN = "Driver";
    public static final String DEFAULT_USER_NAME_RUMI_JA = "運転手様";
    public static final String DEFAULT_USER_NAME_KITT_KO = "마이클";
    public static final String DEFAULT_USER_NAME_KITT_EN = "Michael";
    public static final String DEFAULT_USER_NAME_KITT_JA = "マイケル";
    public static final String DEFAULT_USER_NAME_ASURADA_KO = "하야토";
    public static final String DEFAULT_USER_NAME_ASURADA_EN = "Hayato";
    public static final String DEFAULT_USER_NAME_ASURADA_JA = "ハヤト";

    public static final String END_MODE_AUTO = "auto";
    public static final String END_MODE_SINGLE = "single";
    public static final String END_MODE_CONTINUOUS = "continuous";

    public static final String KEY_VOLUME_STREAM_START = "pref_volume_stream_start";
    public static final String KEY_VOLUME_STREAM_END = "pref_volume_stream_end";
    public static final int DEFAULT_VOLUME_STREAM_START = 100;
    public static final int DEFAULT_VOLUME_STREAM_END = 100;
    public static final String KEY_USE_SLOPE_CHECK = "pref_use_slope_check";
    public static final boolean DEFAULT_USE_SLOPE_CHECK = true;
    public static final String KEY_REDUCE_AC_FAN_DURING_AUDIO_INPUT = "pref_reduce_ac_fan_during_audio_input";
    public static final boolean DEFAULT_REDUCE_AC_FAN_DURING_AUDIO_INPUT = false;

    public static final int VOICE_STREAM_START = R.raw.stream_start;
    public static final int VOICE_STREAM_END = R.raw.stream_end;
    public static final int VOICE_STREAM_BUTTON = R.raw.stream_button;
    public static final int SE_01 = R.raw.se_01;
    public static final int SE_02 = R.raw.se_02;
    public static final int SE_03 = R.raw.se_03;
    public static final int SE_04 = R.raw.se_04;
    public static final int SE_05 = R.raw.se_05;
}
