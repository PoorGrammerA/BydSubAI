package com.poorgrammera.bydsubai.gemini;
import com.poorgrammera.bydsubai.BuildConfig;
import com.poorgrammera.bydsubai.R;
import com.poorgrammera.bydsubai.audio.MediaBrowserManager;
import com.poorgrammera.bydsubai.data.ConfigData;
import com.poorgrammera.bydsubai.data.SecureCredentialStore;
import com.poorgrammera.bydsubai.data.DestinationDbHelper;
import com.poorgrammera.bydsubai.data.SavedDestination;
import com.poorgrammera.bydsubai.integration.AppIntentConfigHelper;
import com.poorgrammera.bydsubai.integration.GpsTracker;
import com.poorgrammera.bydsubai.integration.GoogleNewsRssHelper;
import com.poorgrammera.bydsubai.integration.OpenMeteoWeatherHelper;
import com.poorgrammera.bydsubai.service.GeminiLiveService;
import com.poorgrammera.bydsubai.tool.ExternalToolRegistry;
import com.poorgrammera.bydsubai.vehicle.VehicleController;


import android.util.Log;
import com.poorgrammera.bydsubai.util.MediaPlayerManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import android.media.session.MediaController;
import android.media.session.PlaybackState;

import com.google.genai.AsyncSession;
import com.google.genai.Client;
import com.google.genai.types.AudioTranscriptionConfig;
import com.google.genai.types.Blob;
import com.google.genai.types.Content;
import com.google.genai.types.ContextWindowCompressionConfig;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.LiveConnectConfig;
import com.google.genai.types.LiveSendRealtimeInputParameters;
import com.google.genai.types.LiveSendToolResponseParameters;
import com.google.genai.types.LiveServerContent;
import com.google.genai.types.LiveServerMessage;
import com.google.genai.types.LiveServerToolCall;
import com.google.genai.types.Modality;
import com.google.genai.types.Part;
import com.google.genai.types.PrebuiltVoiceConfig;
import com.google.genai.types.Schema;
import com.google.genai.types.SlidingWindow;
import com.google.genai.types.SpeechConfig;
import com.google.genai.types.Tool;
import com.google.genai.types.VoiceConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GeminiLiveClient {
    private static final String TAG = "GeminiLiveClient";

    public interface Listener {
        void onAudioData(byte[] data);
        void onTextData(String text);
        void onInputTranscription(String text);
        void onOutputTranscription(String text);
        void onTurnComplete();
        void onError(Throwable t);
        void onConnected();
        void onDisconnected();
        void onSearchStart(String query);
        void onSearchComplete(String result);
        void onMediaAppTriggered(String appName);
    }

    private final Context context;
    private final String apiKey;
    private final String modelName;
    private final Listener listener;
    private final Client client;
    private final VehicleController vehicleController;
    private final ExternalToolRegistry externalToolRegistry;

    private AsyncSession session;
    private volatile boolean isConnected = false;
    private String lastUserTranscription = "";
    private final GeminiConversationTranscript sessionTranscript = new GeminiConversationTranscript();

    private volatile boolean isTtsOnlyMode = false;
    public void setTtsOnlyMode(boolean ttsOnly) {
        this.isTtsOnlyMode = ttsOnly;
    }

    private volatile boolean isTextOnlyMode = false;
    public void setTextOnlyMode(boolean textOnly) {
        this.isTextOnlyMode = textOnly;
    }

    private volatile boolean useMemory = true;
    public void setUseMemory(boolean useMemory) {
        this.useMemory = useMemory;
    }
    private volatile boolean isBriefingMode = false;
    public void setBriefingMode(boolean briefingMode) {
        this.isBriefingMode = briefingMode;
    }
    private final java.util.concurrent.ExecutorService backgroundExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(
            runnable -> new Thread(runnable, "GeminiLiveClient-Worker"));

    private long lastAudioLogTime = 0;
    private int audioSendCount = 0;
    private int audioStreamEndCount = 0;

    public GeminiLiveClient(Context context, String apiKey, String modelName, VehicleController vehicleController, Listener listener) {
        this.context = context.getApplicationContext();
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.listener = listener;
        this.client = Client.builder()
                .apiKey(apiKey)
                .build();
        this.vehicleController = vehicleController;
        this.externalToolRegistry = new ExternalToolRegistry(this.context);
    }

    public void connect() {
        if (isConnected || session != null) {
            Log.i(TAG, "Already connected or connecting, skipping.");
            return;
        }

        Log.i(TAG, "Starting connection attempt to Gemini Live API...");
        try {
            List<FunctionDeclaration> declarations = new ArrayList<>();

            // 1. Define provider-neutral news search tool
            Map<String, Schema> newsSearchProps = new HashMap<>();
            newsSearchProps.put("query", Schema.builder()
                    .type("STRING")
                    .description("Optional news topic. Leave empty for today's top and local news.")
                    .build());
            Schema newsSearchParameters = Schema.builder()
                    .type("OBJECT")
                    .properties(newsSearchProps)
                    .build();
            FunctionDeclaration newsSearchDecl = FunctionDeclaration.builder()
                    .name("search_news")
                    .description("Searches Google News RSS. With an empty query, returns today's top headlines; with a query, returns matching news.")
                    .parameters(newsSearchParameters)
                    .build();
            declarations.add(newsSearchDecl);

            // 2. Define control_air_conditioner tool
            Map<String, Schema> acProps = new HashMap<>();
            acProps.put("power", Schema.builder()
                    .type("BOOLEAN")
                    .description("Air conditioner power: ON (true) or OFF (false).")
                    .build());
            acProps.put("wind_level", Schema.builder()
                    .type("INTEGER")
                    .description("Air conditioner fan speed level, from 1 to 7.")
                    .build());
            acProps.put("temperature", Schema.builder()
                    .type("NUMBER")
                    .description("Requested cabin temperature in degrees Celsius, from 17.0 to 33.0.")
                    .build());
            acProps.put("cycle_mode", Schema.builder()
                    .type("STRING")
                    .description("Air circulation mode: 'recycle' for recirculation or 'fresh' for outside air.")
                    .build());
            acProps.put("mode", Schema.builder()
                    .type("STRING")
                    .description("Climate mode: 'cool', 'heat', 'fan', or 'auto'.")
                    .build());
            acProps.put("front_defrost", Schema.builder()
                    .type("BOOLEAN")
                    .description("Enable (true) or disable (false) the front defroster.")
                    .build());
            acProps.put("rear_defrost", Schema.builder()
                    .type("BOOLEAN")
                    .description("Enable (true) or disable (false) the rear defroster.")
                    .build());
            acProps.put("wind_direction_face", Schema.builder()
                    .type("BOOLEAN")
                    .description("Enable (true) or disable (false) face-level air vents.")
                    .build());
            acProps.put("wind_direction_foot", Schema.builder()
                    .type("BOOLEAN")
                    .description("Enable (true) or disable (false) footwell air vents.")
                    .build());
            acProps.put("wind_direction_screen", Schema.builder()
                    .type("BOOLEAN")
                    .description("Enable (true) or disable (false) windshield air vents.")
                    .build());
            Schema acParams = Schema.builder()
                    .type("OBJECT")
                    .properties(acProps)
                    .build();
            FunctionDeclaration controlAcDecl = FunctionDeclaration.builder()
                    .name("control_air_conditioner")
                    .description("Controls climate power, fan speed, temperature, air circulation, defrosters, operating mode, and vent directions.")
                    .parameters(acParams)
                    .build();
            declarations.add(controlAcDecl);

            // 3. Define control_seat tool
            Map<String, Schema> seatProps = new HashMap<>();
            seatProps.put("position", Schema.builder()
                    .type("STRING")
                    .description("Seat to control: 'driver' or 'passenger'.")
                    .build());
            seatProps.put("heating_level", Schema.builder()
                    .type("INTEGER")
                    .description("Seat heating level: 0 for off, 1 for low, or 2 for high.")
                    .build());
            seatProps.put("ventilation_level", Schema.builder()
                    .type("INTEGER")
                    .description("Seat ventilation level: 0 for off, 1 for low, or 2 for high.")
                    .build());
            Schema seatParams = Schema.builder()
                    .type("OBJECT")
                    .properties(seatProps)
                    .required(Collections.singletonList("position"))
                    .build();
            FunctionDeclaration controlSeatDecl = FunctionDeclaration.builder()
                    .name("control_seat")
                    .description("Controls heating and ventilation levels for the driver or passenger seat.")
                    .parameters(seatParams)
                    .build();
            declarations.add(controlSeatDecl);

            // 4. Define control_steering_wheel_heating tool
            Map<String, Schema> wheelProps = new HashMap<>();
            wheelProps.put("power", Schema.builder()
                    .type("BOOLEAN")
                    .description("Enable (true) or disable (false) steering wheel heating.")
                    .build());
            wheelProps.put("level", Schema.builder()
                    .type("INTEGER")
                    .description("Steering wheel heating level (1: low, 2: medium, 3: high). Optional, supported on DiLink 5 models.")
                    .build());
            Schema wheelParams = Schema.builder()
                    .type("OBJECT")
                    .properties(wheelProps)
                    .required(Collections.singletonList("power"))
                    .build();
            FunctionDeclaration controlWheelDecl = FunctionDeclaration.builder()
                    .name("control_steering_wheel_heating")
                    .description("Turns steering wheel heating on or off, with optional heating level (1-3) on supported models.")
                    .parameters(wheelParams)
                    .build();
            declarations.add(controlWheelDecl);

            // 5. Define control_trunk tool
            Map<String, Schema> trunkProps = new HashMap<>();
            trunkProps.put("action", Schema.builder()
                    .type("STRING")
                    .description("Trunk action: 'open', 'close', or 'stop'.")
                    .build());
            Schema trunkParams = Schema.builder()
                    .type("OBJECT")
                    .properties(trunkProps)
                    .required(Collections.singletonList("action"))
                    .build();
            FunctionDeclaration controlTrunkDecl = FunctionDeclaration.builder()
                    .name("control_trunk")
                    .description("Opens, closes, or stops the trunk tailgate.")
                    .parameters(trunkParams)
                    .build();
            declarations.add(controlTrunkDecl);

            // 6-1. Define control_window tool
            Map<String, Schema> winProps = new HashMap<>();
            winProps.put("area", Schema.builder()
                    .type("STRING")
                    .description("Window area: 'all', 'front', 'rear', 'left', 'right', 'driver_front', 'passenger_front', 'driver_rear', or 'passenger_rear'.")
                    .build());
            winProps.put("action", Schema.builder()
                    .type("STRING")
                    .description("Window action: 'open', 'close', 'stop', 'ventilation' (10% open), or 'half' (50% open).")
                    .build());
            winProps.put("custom_percent", Schema.builder()
                    .type("INTEGER")
                    .description("Exact opening percentage from 0 to 100. Use for a precise position instead of an action.")
                    .build());
            Schema winParams = Schema.builder()
                    .type("OBJECT")
                    .properties(winProps)
                    .required(Arrays.asList("area", "action"))
                    .build();
            FunctionDeclaration controlWinDecl = FunctionDeclaration.builder()
                    .name("control_window")
                    .description("Controls an individual, grouped, or all windows using a preset action or an exact opening percentage.")
                    .parameters(winParams)
                    .build();
            declarations.add(controlWinDecl);

            // 6-2. Define control_sunroof tool
            Map<String, Schema> sunroofProps = new HashMap<>();
            sunroofProps.put("action", Schema.builder()
                    .type("STRING")
                    .description("Sunroof action: 'open', 'close', 'stop', 'ventilation' (10% open), or 'half' (50% open).")
                    .build());
            sunroofProps.put("custom_percent", Schema.builder()
                    .type("INTEGER")
                    .description("Exact opening percentage from 0 to 100. Use for a precise position instead of an action.")
                    .build());
            Schema sunroofParams = Schema.builder()
                    .type("OBJECT")
                    .properties(sunroofProps)
                    .required(Collections.singletonList("action"))
                    .build();
            FunctionDeclaration controlSunroofDecl = FunctionDeclaration.builder()
                    .name("control_sunroof")
                    .description("Opens, closes, or stops the sunroof, or sets its exact opening percentage.")
                    .parameters(sunroofParams)
                    .build();
            declarations.add(controlSunroofDecl);

            // 6-3. Define control_sunshade tool
            Map<String, Schema> sunshadeProps = new HashMap<>();
            sunshadeProps.put("action", Schema.builder()
                    .type("STRING")
                    .description("Sunshade action: 'open', 'close', 'stop', or 'half' (50% open).")
                    .build());
            sunshadeProps.put("custom_percent", Schema.builder()
                    .type("INTEGER")
                    .description("Exact opening percentage from 0 to 100. Use for a precise position instead of an action.")
                    .build());
            Schema sunshadeParams = Schema.builder()
                    .type("OBJECT")
                    .properties(sunshadeProps)
                    .required(Collections.singletonList("action"))
                    .build();
            FunctionDeclaration controlSunshadeDecl = FunctionDeclaration.builder()
                    .name("control_sunshade")
                    .description("Opens, closes, or stops the sunshade, or sets its exact opening percentage.")
                    .parameters(sunshadeParams)
                    .build();
            declarations.add(controlSunshadeDecl);

            // 7. Define control_volume tool
            Map<String, Schema> volProps = new HashMap<>();
            volProps.put("action", Schema.builder()
                    .type("STRING")
                    .description("Volume action: 'set', 'up' (increase by 5), 'down' (decrease by 5), 'mute', or 'unmute'.")
                    .build());
            volProps.put("value", Schema.builder()
                    .type("INTEGER")
                    .description("Volume level from 0 to 39. Required when action is 'set'.")
                    .build());
            Schema volParams = Schema.builder()
                    .type("OBJECT")
                    .properties(volProps)
                    .required(Collections.singletonList("action"))
                    .build();
            FunctionDeclaration controlVolDecl = FunctionDeclaration.builder()
                    .name("control_volume")
                    .description("Controls vehicle system volume (maximum 39) and mute state.")
                    .parameters(volParams)
                    .build();
            declarations.add(controlVolDecl);

            // 8. Define launch_tmap / launch_naver_map based on active provider (if Naver Map or Tmap is installed)
            if (vehicleController.isNaverMapInstalled() || vehicleController.isTmapInstalled()) {
                Map<String, Schema> naverMapProps = new HashMap<>();
                naverMapProps.put("destination", Schema.builder()
                        .type("STRING")
                        .description("Destination name to search for, for example 'Seoul City Hall' or 'Incheon Airport'.")
                        .build());
                naverMapProps.put("latitude", Schema.builder()
                        .type("NUMBER")
                        .description("Destination latitude in WGS84. Provide it when coordinates have already been obtained from place search.")
                        .build());
                naverMapProps.put("longitude", Schema.builder()
                        .type("NUMBER")
                        .description("Destination longitude in WGS84. Provide it when coordinates have already been obtained from place search.")
                        .build());
                Schema naverMapParams = Schema.builder()
                        .type("OBJECT")
                        .properties(naverMapProps)
                        .required(Collections.singletonList("destination"))
                        .build();

                android.content.SharedPreferences prefs = context.getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
                String mapProvider = prefs.getString(ConfigData.KEY_MAP_PROVIDER, ConfigData.DEFAULT_MAP_PROVIDER);
                String toolName = "tmap".equals(mapProvider) ? "launch_tmap" : "launch_naver_map";
                String mapLabel = "tmap".equals(mapProvider) ? "티맵 오토" : "네이버 지도";

                FunctionDeclaration launchMapDecl = FunctionDeclaration.builder()
                        .name(toolName)
                        .description("Launches the configured navigation app and starts route guidance. When latitude and longitude are provided, starts guidance directly to those coordinates.")
                        .parameters(naverMapParams)
                        .build();
                declarations.add(launchMapDecl);
                Log.i(TAG, "Map navigation app is installed. Registering " + toolName + " tool.");
            }

            // 9. Define get_current_location tool
            Schema locationParams = Schema.builder()
                    .type("OBJECT")
                    .properties(new java.util.HashMap<>())
                    .build();
            FunctionDeclaration getLocDecl = FunctionDeclaration.builder()
                    .name("get_current_location")
                    .description("Returns the vehicle's current GPS latitude and longitude in real time.")
                    .parameters(locationParams)
                    .build();
            declarations.add(getLocDecl);

            // 10. Define get_installed_apps tool
            Schema getAppsParams = Schema.builder()
                    .type("OBJECT")
                    .properties(new java.util.HashMap<>())
                    .build();
            FunctionDeclaration getAppsDecl = FunctionDeclaration.builder()
                    .name("get_installed_apps")
                    .description("Returns names and package names for all launchable installed apps. Call this before launching an app requested by the user to find its correct package name.")
                    .parameters(getAppsParams)
                    .build();
            declarations.add(getAppsDecl);

            // 12. Define launch_app tool
            Map<String, Schema> launchAppProps = new HashMap<>();
            launchAppProps.put("package_name", Schema.builder()
                    .type("STRING")
                    .description("Android package name of the app to launch, for example 'com.google.android.youtube'.")
                    .build());
            launchAppProps.put("query", Schema.builder()
                    .type("STRING")
                    .description("Optional query or keyword to search or play within the app.")
                    .build());
            Schema launchAppParams = Schema.builder()
                    .type("OBJECT")
                    .properties(launchAppProps)
                    .required(Collections.singletonList("package_name"))
                    .build();
            FunctionDeclaration launchAppDecl = FunctionDeclaration.builder()
                    .name("launch_app")
                    .description("Launches an app on the device by its package name.")
                    .parameters(launchAppParams)
                    .build();
            declarations.add(launchAppDecl);

            // 13. Define control_media tool
            Map<String, Schema> controlMediaProps = new HashMap<>();
            controlMediaProps.put("action", Schema.builder()
                    .type("STRING")
                    .description("Media action: 'play', 'pause', 'skip', 'previous', or 'play_from_search'.")
                    .build());
            controlMediaProps.put("query", Schema.builder()
                    .type("STRING")
                    .description("Song title or artist to search or play. Required for 'play_from_search'.")
                    .build());
            controlMediaProps.put("app_name", Schema.builder()
                    .type("STRING")
                    .description("Optional target media application name: 'flo', 'spotify', 'karaoke', or 'melon'.")
                    .build());
            Schema controlMediaParams = Schema.builder()
                    .type("OBJECT")
                    .properties(controlMediaProps)
                    .required(Collections.singletonList("action"))
                    .build();
            FunctionDeclaration controlMediaDecl = FunctionDeclaration.builder()
                    .name("control_media")
                    .description("Controls the active music player or media app: play, pause, next, previous, or search and play audio.")
                    .parameters(controlMediaParams)
                    .build();
            declarations.add(controlMediaDecl);

            // 23. get_current_weather 추가
            FunctionDeclaration getWeatherDecl = FunctionDeclaration.builder()
                    .name("get_current_weather")
                    .description("Uses the vehicle's current GPS coordinates to return current weather, temperature, low and high temperatures, and precipitation probability.")
                    .build();
            declarations.add(getWeatherDecl);

            // 24. get_driving_range 추가
            FunctionDeclaration getDrivingRangeDecl = FunctionDeclaration.builder()
                    .name("get_driving_range")
                    .description("Returns current EV driving range in km and high-voltage battery state of charge (SOC) percentage.")
                    .parameters(Schema.builder().type("OBJECT").properties(new HashMap<>()).build())
                    .build();
            declarations.add(getDrivingRangeDecl);

            /*
            // 24. Define control_ambient_light tool (Disabled)
            Map<String, Schema> controlAmbientProps = new HashMap<>();
            controlAmbientProps.put("action", Schema.builder()
                    .type("STRING")
                    .description("Ambient-light action: 'enable', 'disable', 'set_brightness', or 'set_color'.")
                    .build());
            controlAmbientProps.put("brightness", Schema.builder()
                    .type("INTEGER")
                    .description("Ambient-light brightness from 0 to 100. Required for 'set_brightness'.")
                    .build());
            controlAmbientProps.put("color_name", Schema.builder()
                    .type("STRING")
                    .description("Color name to set, for example pink, red, blue, green, yellow, or white.")
                    .build());
            Schema controlAmbientParams = Schema.builder()
                    .type("OBJECT")
                    .properties(controlAmbientProps)
                    .required(Collections.singletonList("action"))
                    .build();
            FunctionDeclaration controlAmbientDecl = FunctionDeclaration.builder()
                    .name("control_ambient_light")
                    .description("Controls vehicle interior ambient lighting: power, brightness, and color.")
                    .parameters(controlAmbientParams)
                    .build();
            declarations.add(controlAmbientDecl);
            */

            Set<String> reservedToolNames = new HashSet<>();
            for (FunctionDeclaration declaration : declarations) {
                declaration.name().ifPresent(reservedToolNames::add);
            }
            List<FunctionDeclaration> externalDeclarations =
                    externalToolRegistry.buildDeclarations(reservedToolNames);
            declarations.addAll(externalDeclarations);
            String externalPlaceSearchToolName =
                    externalToolRegistry.findGeminiNameForLocalTool("search_places");
            if (!externalDeclarations.isEmpty()) {
                Log.i(TAG, "Registered " + externalDeclarations.size()
                        + " approved external Tool app functions");
            }

            Tool vehicleTools = Tool.builder()
                    .functionDeclarations(declarations)
                    .build();

            String languageCode = context.getResources().getConfiguration().getLocales().get(0).toLanguageTag();
            Log.i(TAG, "Connecting to Gemini Live with languageCode: " + languageCode);

            SharedPreferences prefs = context.getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
            String voiceMode = prefs.getString(ConfigData.KEY_VOICE_MODE, ConfigData.DEFAULT_VOICE_MODE);

            int promptResId;
            String voiceName;
            if ("kitt".equals(voiceMode)) {
                promptResId = R.string.system_prompt_with_vehicle_control_by_kitt;
                voiceName = "Orus";
            } else if ("asurada".equals(voiceMode)) {
                promptResId = R.string.system_prompt_with_vehicle_control_by_asurada;
                voiceName = "Fenrir";  // 낮고 침착한 남성 목소리
            } else {
                promptResId = R.string.system_prompt_with_vehicle_control_by_rumi;
                voiceName = "Aoede";
            }

            List<String> savedAliases = DestinationDbHelper.getInstance(context).getAllAliases();
            String systemInstructionText = context.getString(promptResId);
            systemInstructionText += "\n\nFor current news or a news briefing, use the search_news tool. "
                    + "With an empty query it returns top headlines; with a query it searches Google News RSS.";
            if (externalPlaceSearchToolName != null) {
                systemInstructionText = systemInstructionText.replace(
                        "search_nearby_places", externalPlaceSearchToolName);
                systemInstructionText += "\n\nFor nearby-place searches, use the approved external tool '"
                        + externalPlaceSearchToolName + "'. Obtain current coordinates with "
                        + "get_current_location first when proximity matters, then pass the selected "
                        + "result's latitude and longitude to the navigation tool.";
            } else {
                systemInstructionText += "\n\nNo external place-search tool is currently available. "
                        + "Do not claim that a nearby place was searched, and do not call the "
                        + "unavailable search_nearby_places function.";
            }

            // Determine custom driver name based on voiceMode and languageCode
            String userNameKey;
            String defaultUserName;
            if ("kitt".equals(voiceMode)) {
                if (languageCode.startsWith("ko")) {
                    userNameKey = ConfigData.KEY_USER_NAME_KITT_KO;
                    defaultUserName = ConfigData.DEFAULT_USER_NAME_KITT_KO;
                } else if (languageCode.startsWith("ja")) {
                    userNameKey = ConfigData.KEY_USER_NAME_KITT_JA;
                    defaultUserName = ConfigData.DEFAULT_USER_NAME_KITT_JA;
                } else {
                    userNameKey = ConfigData.KEY_USER_NAME_KITT_EN;
                    defaultUserName = ConfigData.DEFAULT_USER_NAME_KITT_EN;
                }
            } else if ("asurada".equals(voiceMode)) {
                if (languageCode.startsWith("ko")) {
                    userNameKey = ConfigData.KEY_USER_NAME_ASURADA_KO;
                    defaultUserName = ConfigData.DEFAULT_USER_NAME_ASURADA_KO;
                } else if (languageCode.startsWith("ja")) {
                    userNameKey = ConfigData.KEY_USER_NAME_ASURADA_JA;
                    defaultUserName = ConfigData.DEFAULT_USER_NAME_ASURADA_JA;
                } else {
                    userNameKey = ConfigData.KEY_USER_NAME_ASURADA_EN;
                    defaultUserName = ConfigData.DEFAULT_USER_NAME_ASURADA_EN;
                }
            } else { // rumi
                if (languageCode.startsWith("ko")) {
                    userNameKey = ConfigData.KEY_USER_NAME_RUMI_KO;
                    defaultUserName = ConfigData.DEFAULT_USER_NAME_RUMI_KO;
                } else if (languageCode.startsWith("ja")) {
                    userNameKey = ConfigData.KEY_USER_NAME_RUMI_JA;
                    defaultUserName = ConfigData.DEFAULT_USER_NAME_RUMI_JA;
                } else {
                    userNameKey = ConfigData.KEY_USER_NAME_RUMI_EN;
                    defaultUserName = ConfigData.DEFAULT_USER_NAME_RUMI_EN;
                }
            }

            String userName = prefs.getString(userNameKey, defaultUserName);
            if (userName != null && !userName.trim().isEmpty()) {
                userName = userName.trim();
                if ("kitt".equals(voiceMode)) {
                    systemInstructionText = systemInstructionText
                            .replace("마이클", userName)
                            .replace("Michael", userName)
                            .replace("マイケル", userName);
                } else if ("asurada".equals(voiceMode)) {
                    systemInstructionText = systemInstructionText
                            .replace("하야토", userName)
                            .replace("Hayato", userName)
                            .replace("ハヤト", userName);
                } else {
                    systemInstructionText = systemInstructionText
                            .replace("운전자님", userName)
                            .replace("Driver", userName)
                            .replace("運転手様", userName);
                }
                Log.i(TAG, "Replaced driver name in system prompt with custom name: " + userName);
            }

            String mapProvider = prefs.getString(ConfigData.KEY_MAP_PROVIDER, ConfigData.DEFAULT_MAP_PROVIDER);
            String toolName = "tmap".equals(mapProvider) ? "launch_tmap" : "launch_naver_map";
            systemInstructionText = systemInstructionText.replace("launch_naver_map", toolName);
            if ("tmap".equals(mapProvider)) {
                systemInstructionText = systemInstructionText
                        .replace("네이버 지도로", "티맵 오토로")
                        .replace("ネイバー地図で", "Tmapで")
                        .replace("ネイバーマップで", "Tmapで")
                        .replace("Naver Map", "Tmap Auto");
            }

            if (!savedAliases.isEmpty()) {
                systemInstructionText += "\n\n# 사용자 등록 목적지 목록\n"
                        + "사용자가 미리 등록해둔 목적지 별칭 목록은 다음과 같습니다: " + savedAliases.toString() + "\n"
                        + "사용자가 이 목록에 있는 목적지(별칭)로 길안내를 요청할 경우(예: '집으로 안내해줘', '회사로 가자' 등), "
                        + (externalPlaceSearchToolName == null ? "주변 장소 검색을 수행하지 마시고, "
                            : "'" + externalPlaceSearchToolName + "' 도구를 호출하여 주변 검색을 수행하지 마시고, ")
                        + "해당 별칭명을 그대로 '" + toolName + "' 도구의 'destination' 인자로 넘겨서 즉시 내비게이션을 호출하십시오. "
                        + "(예: " + toolName + "(destination='집'))";
            }

            // Load and inject compiled long-term and short-term memories (guarded by useMemory)
            if (useMemory) {
                List<String> longTermMemories = DestinationDbHelper.getInstance(context).getRecentLongTermMemories();
                List<String> shortTermMemories = GeminiLiveService.getShortTermMemories();

                if (!longTermMemories.isEmpty() || !shortTermMemories.isEmpty()) {
                    StringBuilder memoryPrompt = new StringBuilder();
                    memoryPrompt.append("\n\n# 운전자 대화 기억 (Memory Profile)\n");
                    memoryPrompt.append("운전자와의 과거 대화 분석을 통해 파악된 상태 및 선향 정보는 다음과 같습니다. ");
                    memoryPrompt.append("대화 시 이 기억 정보를 적극 참고하여 어울리는 공감 멘트를 던지거나 조치해 주십시오:\n");

                    if (!longTermMemories.isEmpty()) {
                        memoryPrompt.append("- 장기 기억 (선호도 및 성향):\n");
                        for (String m : longTermMemories) {
                            memoryPrompt.append("  * ").append(m).append("\n");
                        }
                    }

                    if (!shortTermMemories.isEmpty()) {
                        memoryPrompt.append("- 단기 기억 (현재 주행 세션 특이사항):\n");
                        for (String m : shortTermMemories) {
                            memoryPrompt.append("  * ").append(m).append("\n");
                        }
                    }

                    systemInstructionText += memoryPrompt.toString();
                    Log.i(TAG, "Injected memory context into system instructions:\n" + memoryPrompt.toString());
                }
            }

            // Inject conversation end rules if NOT in TTS-only mode and termination mode is either SINGLE or AUTO
            String endMode = prefs.getString(ConfigData.KEY_END_MODE, ConfigData.DEFAULT_END_MODE);
            if (!isBriefingMode && !isTtsOnlyMode && (ConfigData.END_MODE_SINGLE.equals(endMode) || ConfigData.END_MODE_AUTO.equals(endMode))) {
                int ruleResId;
                if ("kitt".equals(voiceMode)) {
                    ruleResId = R.string.system_prompt_end_conversation_rule_kitt;
                } else if ("asurada".equals(voiceMode)) {
                    ruleResId = R.string.system_prompt_end_conversation_rule_asurada;
                } else {
                    ruleResId = R.string.system_prompt_end_conversation_rule_rumi;
                }
                
                String endRuleText = context.getString(ruleResId);
                if (userName != null && !userName.trim().isEmpty()) {
                    if ("kitt".equals(voiceMode)) {
                        endRuleText = endRuleText
                                .replace("마이클", userName)
                                .replace("Michael", userName)
                                .replace("マイケル", userName);
                    } else if ("asurada".equals(voiceMode)) {
                        endRuleText = endRuleText
                                .replace("하야토", userName)
                                .replace("Hayato", userName)
                                .replace("ハヤト", userName);
                    } else {
                        endRuleText = endRuleText
                                .replace("운전자님", userName)
                                .replace("Driver", userName)
                                .replace("運転手様", userName);
                    }
                }
                systemInstructionText += endRuleText;
                Log.i(TAG, "Injected end conversation rules into system instruction (endMode=" + endMode + ")");
            }

            if (isTtsOnlyMode) {
                // Load localized TTS announcement instruction template and append ONLY the character's AUDIO PROFILE (Director's notes)
                // This prevents the AI from launching into conversational roleplay and makes it act purely as a reader.
                String ttsTemplate = context.getString(R.string.system_prompt_tts_only_mode);
                String rawCharacterPrompt = context.getString(promptResId);
                String characterAudioProfile = extractAudioProfile(rawCharacterPrompt);

                systemInstructionText = ttsTemplate + "\n\n---\n\n" + characterAudioProfile;
                Log.i(TAG, "TTS Only Mode active. Overriding system prompt to strict reading engine with localized instructions and targeted character audio profile.");
            }

            LiveConnectConfig.Builder configBuilder = LiveConnectConfig.builder()
                    .responseModalities(Modality.Known.AUDIO)
                    .outputAudioTranscription(AudioTranscriptionConfig.builder().build())
                    .systemInstruction(Content.builder()
                            .parts(Arrays.asList(Part.builder().text(systemInstructionText).build()))
                            .build())
                    .speechConfig(SpeechConfig.builder()
                            .voiceConfig(VoiceConfig.builder()
                                    .prebuiltVoiceConfig(PrebuiltVoiceConfig.builder()
                                            .voiceName(voiceName)
                                            .build())
                                    .build())
                            .languageCode(languageCode)
                            .build())
                    .contextWindowCompression(ContextWindowCompressionConfig.builder()
                            .triggerTokens(104857L)
                            .slidingWindow(SlidingWindow.builder().targetTokens(52428L).build())
                            .build());

            if (!isTtsOnlyMode) {
                configBuilder.tools(Collections.singletonList(vehicleTools));
            }

            LiveConnectConfig config = configBuilder.build();

            CompletableFuture<AsyncSession> sessionFuture =
                    client.async.live.connect(modelName, config);

            sessionFuture.thenAccept(s -> {
                this.session = s;
                this.isConnected = true;
                Log.d(TAG, "Connected to Gemini Live");
                listener.onConnected();
                s.receive(this::handleMessage);
            }).exceptionally(ex -> {
                Log.e(TAG, "Connection error", ex);

                Log.e(TAG, ex.getMessage());
                String errorMessage = ex.getMessage();
                if (errorMessage != null) {
                    SharedPreferences localPrefs = context.getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
                    String localVoiceMode = localPrefs.getString(ConfigData.KEY_VOICE_MODE, ConfigData.DEFAULT_VOICE_MODE); // "rumi", "kitt", "asurada"
                    String soundBaseName = null;

                    if(errorMessage.contains("not found for API version")) {
                        Log.d(TAG, "Gemini live 모델을 확인해 주세요");
                        soundBaseName = "error_gemini_live_check_model";
                    } else if(errorMessage.contains("invalid authentication credentials")) {
                        Log.d(TAG, "Gemini API 키를 확인해 주세요");
                        soundBaseName = "error_gemini_live_check_api_key";
                    } else if(errorMessage.contains("No address associated")) {
                        Log.d(TAG, "인터넷 연결을 확인해 주세요");
                        soundBaseName = "error_gemini_live_check_network";
                    } else if(errorMessage.contains("Your project has exceeded")) {
                        Log.d(TAG, "AI 사용량을 초과하였습니다.");
                        soundBaseName = "error_gemini_live_check_cap";
                    }

                    if (soundBaseName != null) {
                        String resName = localVoiceMode + "_" + soundBaseName;
                        int resId = context.getResources().getIdentifier(resName, "raw", context.getPackageName());
                        if (resId == 0) {
                            resId = context.getResources().getIdentifier(soundBaseName, "raw", context.getPackageName());
                        }
                        if (resId != 0) {
                            MediaPlayerManager.getInstance(context).play(context, resId, 1.0f, null);
                        } else {
                            Log.w(TAG, "Connection error sound not found: " + resName + " or " + soundBaseName);
                        }
                    }
                }

                cleanup();
                listener.onError(ex);
                return null;
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to initiate connection", e);
            cleanup();
            listener.onError(e);
        }
    }

    private void handleMessage(LiveServerMessage message) {
        if (message.setupComplete().isPresent()) {
            Log.i(TAG, "Server Message: SetupComplete");
        }

        if (message.toolCall().isPresent()) {
            LiveServerToolCall toolCall = message.toolCall().get();
            handleToolCall(toolCall);
        }

        if (message.serverContent().isPresent()) {
            LiveServerContent content = message.serverContent().get();

            if (content.inputTranscription().isPresent()) {
                content.inputTranscription().get().text().ifPresent(text -> {
                    Log.i(TAG, "Input Transcription: " + text);
                    lastUserTranscription = text;
                    listener.onInputTranscription(text);
                    sessionTranscript.appendDriver(text);
                });
            }

            if (content.outputTranscription().isPresent()) {
                content.outputTranscription().get().text().ifPresent(text -> {
                    Log.i(TAG, "Output Transcription: " + text);
                    listener.onOutputTranscription(text);
                    sessionTranscript.appendAssistant(text);
                });
            }

            if (content.modelTurn().isPresent()) {
                content.modelTurn().get().parts().ifPresent(parts -> {
                    parts.forEach(part -> {
                        if (part.inlineData().isPresent() && part.inlineData().get().data().isPresent()) {
                            byte[] audio = part.inlineData().get().data().get();
                            listener.onAudioData(audio);
                        }

                        if (part.text().isPresent()) {
                            String text = part.text().get();
                            Log.i(TAG, "Received Model Text Part: " + text);
                            listener.onTextData(text);
                        }
                    });
                });
            }

            if (content.turnComplete().orElse(false)) {
                Log.d(TAG, "Server Message: TurnComplete");
                listener.onTurnComplete();
            }

            if (content.interrupted().orElse(false)) {
                Log.w(TAG, "Server Message: Interrupted");
            }
        }
    }

    private void handleToolCall(LiveServerToolCall toolCall) {
        if (!toolCall.functionCalls().isPresent()) {
            return;
        }

        List<FunctionCall> functionCalls = toolCall.functionCalls().get();
        for (FunctionCall call : functionCalls) {
            String callId = call.id().orElse("");
            String name = call.name().orElse("");
            Map<String, Object> args = call.args().orElse(Collections.emptyMap());

            if (externalToolRegistry.handles(name)) {
                externalToolRegistry.execute(name, args,
                        response -> sendToolResponseData(callId, name, response));
            } else if ("search_news".equals(name)) {
                String query = (String) args.get("query");
                if (query == null) {
                    query = "";
                }

                final String searchQuery = query;
                listener.onSearchStart(searchQuery);

                CompletableFuture.runAsync(() -> {
                    String searchResult;
                    try {

                        Log.i(TAG, "Search Query: " + searchQuery);
                        searchResult = GoogleNewsRssHelper.search(context, searchQuery);
                    } catch (Exception e) {
                        Log.e(TAG, "Error executing news search", e);
                        searchResult = "Error executing news search: " + e.getMessage();
                    }

                    Log.i(TAG, "Search Result: " + searchResult);

                    listener.onSearchComplete(searchResult);

                    Map<String, Object> responseMap = new HashMap<>();
                    responseMap.put("result", searchResult);

                    FunctionResponse functionResponse = FunctionResponse.builder()
                            .id(callId)
                            .name(name)
                            .response(responseMap)
                            .build();

                    LiveSendToolResponseParameters responseParams = LiveSendToolResponseParameters.builder()
                            .functionResponses(Collections.singletonList(functionResponse))
                            .build();

                    if (isConnected && session != null) {
                        try {
                            session.sendToolResponse(responseParams).thenAccept(v -> {
                                Log.d(TAG, "Successfully sent news search tool response back to Gemini");
                            }).exceptionally(ex -> {
                                Log.e(TAG, "Failed to send tool response", ex);
                                return null;
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Exception sending tool response", e);
                        }
                    }
                });
            } else if ("control_air_conditioner".equals(name)) {
                SharedPreferences toolCallPrefs = context.getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
                if (!toolCallPrefs.getBoolean(ConfigData.KEY_ENABLE_TOOL_AC, true)) {
                    Log.w(TAG, "control_air_conditioner tool is disabled in settings. Returning error response.");
                    sendToolErrorResponse(callId, name, "사용자가 앱 설정에서 에어컨/공조기 제어 기능을 비활성화했습니다. 운전자에게 앱 설정에서 에어컨/공조기 제어 기능이 꺼져 있어 실행할 수 없다고 안내하세요.");
                    return;
                }
                Boolean power = (Boolean) args.get("power");
                Integer windLevel = args.get("wind_level") instanceof Number ? ((Number) args.get("wind_level")).intValue() : null;
                Double temperature = args.get("temperature") instanceof Number ? ((Number) args.get("temperature")).doubleValue() : null;
                String cycleMode = (String) args.get("cycle_mode");
                Boolean frontDefrost = (Boolean) args.get("front_defrost");
                Boolean rearDefrost = (Boolean) args.get("rear_defrost");
                String mode = (String) args.get("mode");
                Boolean windDirFace = (Boolean) args.get("wind_direction_face");
                Boolean windDirFoot = (Boolean) args.get("wind_direction_foot");
                Boolean windDirScreen = (Boolean) args.get("wind_direction_screen");

                vehicleController.controlAirConditioner(power, windLevel, temperature, cycleMode, frontDefrost, rearDefrost, mode, windDirFace, windDirFoot, windDirScreen);
                sendToolSuccessResponse(callId, name);
            } else if ("control_seat".equals(name)) {
                SharedPreferences toolCallPrefs = context.getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
                if (!toolCallPrefs.getBoolean(ConfigData.KEY_ENABLE_TOOL_SEAT, true)) {
                    Log.w(TAG, "control_seat tool is disabled in settings. Returning error response.");
                    sendToolErrorResponse(callId, name, "사용자가 앱 설정에서 시트 제어 기능을 비활성화했습니다. 운전자에게 앱 설정에서 시트 제어 기능이 꺼져 있어 실행할 수 없다고 안내하세요.");
                    return;
                }
                String position = (String) args.get("position");
                Integer heatingLevel = args.get("heating_level") instanceof Number ? ((Number) args.get("heating_level")).intValue() : null;
                Integer ventilationLevel = args.get("ventilation_level") instanceof Number ? ((Number) args.get("ventilation_level")).intValue() : null;

                vehicleController.controlSeat(position, heatingLevel, ventilationLevel);
                sendToolSuccessResponse(callId, name);
            } else if ("control_steering_wheel_heating".equals(name)) {
                SharedPreferences toolCallPrefs = context.getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
                if (!toolCallPrefs.getBoolean(ConfigData.KEY_ENABLE_TOOL_WHEEL, true)) {
                    Log.w(TAG, "control_steering_wheel_heating tool is disabled in settings. Returning error response.");
                    sendToolErrorResponse(callId, name, "사용자가 앱 설정에서 핸들 열선 제어 기능을 비활성화했습니다. 운전자에게 앱 설정에서 핸들 열선 제어 기능이 꺼져 있어 실행할 수 없다고 안내하세요.");
                    return;
                }
                Boolean power = (Boolean) args.get("power");
                Integer level = null;
                Object levelObj = args.get("level");
                if (levelObj instanceof Number) {
                    level = ((Number) levelObj).intValue();
                }
                if (power != null) {
                    vehicleController.controlSteeringWheelHeating(power, level);
                }
                sendToolSuccessResponse(callId, name);
            } else if ("control_trunk".equals(name)) {
                SharedPreferences toolCallPrefs = context.getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
                if (!toolCallPrefs.getBoolean(ConfigData.KEY_ENABLE_TOOL_TRUNK, true)) {
                    Log.w(TAG, "control_trunk tool is disabled in settings. Returning error response.");
                    sendToolErrorResponse(callId, name, "사용자가 앱 설정에서 트렁크 제어 기능을 비활성화했습니다. 운전자에게 앱 설정에서 트렁크 제어 기능이 꺼져 있어 실행할 수 없다고 안내하세요.");
                    return;
                }
                String action = (String) args.get("action");
                if ("open".equalsIgnoreCase(action) && vehicleController.isTrunkOpenBlockedByGear()) {
                    Log.w(TAG, "Blocked control_trunk open request because gear is not P or unavailable.");
                    sendToolErrorResponse(callId, name, "안전을 위해 트렁크는 P 기어에서만 열 수 있습니다. 기어를 P로 변경한 뒤 다시 요청하세요.");
                    return;
                }
                if (action != null) {
                    vehicleController.controlTrunk(action);
                }
                sendToolSuccessResponse(callId, name);
            /*
            } else if ("control_ambient_light".equals(name)) {
                String action = (String) args.get("action");
                Integer brightness = args.get("brightness") instanceof Number ? ((Number) args.get("brightness")).intValue() : null;
                String colorName = (String) args.get("color_name");

                Log.i(TAG, "control_ambient_light tool called: action=" + action + ", brightness=" + brightness + ", colorName=" + colorName);
                
                if ("enable".equals(action)) {
                    vehicleController.setAmbientLightEnabled(true);
                } else if ("disable".equals(action)) {
                    vehicleController.setAmbientLightEnabled(false);
                } else if ("set_brightness".equals(action)) {
                    if (brightness != null) {
                        vehicleController.setAmbientBrightness(brightness);
                    }
                } else if ("set_color".equals(action)) {
                    if (colorName != null) {
                        int colorValue = 0;
                        String lower = colorName.toLowerCase().replaceAll("[^0-9a-zA-Z가-힣]", "").trim();
                        
                        // Check if it is a direct palette number
                        try {
                            int num = Integer.parseInt(lower);
                            if (num >= 1 && num <= 31) {
                                colorValue = num;
                            }
                        } catch (NumberFormatException ignored) {}

                        if (colorValue == 0) {
                            if (lower.contains("pink") || lower.contains("핑크") || lower.contains("분홍")) {
                                colorValue = (255 << 16) | (20 << 8) | 147;
                            } else if (lower.contains("red") || lower.contains("빨강") || lower.contains("붉은")) {
                                colorValue = (255 << 16) | (0 << 8) | 0;
                            } else if (lower.contains("blue") || lower.contains("파랑") || lower.contains("푸른")) {
                                colorValue = (0 << 16) | (0 << 8) | 255;
                            } else if (lower.contains("green") || lower.contains("초록") || lower.contains("녹색")) {
                                colorValue = (0 << 16) | (255 << 8) | 0;
                            } else if (lower.contains("yellow") || lower.contains("노랑") || lower.contains("황색")) {
                                colorValue = (255 << 16) | (255 << 8) | 0;
                            } else if (lower.contains("white") || lower.contains("하양") || lower.contains("흰색")) {
                                colorValue = (255 << 16) | (255 << 8) | 255;
                            } else {
                                colorValue = (255 << 16) | (255 << 8) | 255;
                            }
                        }
                        vehicleController.setAmbientColor(colorValue);
                    }
                }
                sendToolSuccessResponse(callId, name);
            */
            } else if ("control_window".equals(name)) {
                SharedPreferences toolCallPrefs = context.getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
                if (!toolCallPrefs.getBoolean(ConfigData.KEY_ENABLE_TOOL_WINDOW, true)) {
                    Log.w(TAG, "control_window tool is disabled in settings. Returning error response.");
                    sendToolErrorResponse(callId, name, "사용자가 앱 설정에서 창문 제어 기능을 비활성화했습니다. 운전자에게 앱 설정에서 창문 제어 기능이 꺼져 있어 실행할 수 없다고 안내하세요.");
                    return;
                }
                String area = (String) args.get("area");
                String action = (String) args.get("action");
                Integer customPercent = args.get("custom_percent") instanceof Number ? ((Number) args.get("custom_percent")).intValue() : null;

                if (area != null && action != null) {
                    vehicleController.controlWindow(area, action, customPercent);
                }
                sendToolSuccessResponse(callId, name);
            } else if ("control_sunroof".equals(name)) {
                SharedPreferences toolCallPrefs = context.getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
                if (!toolCallPrefs.getBoolean(ConfigData.KEY_ENABLE_TOOL_SUNROOF, true)) {
                    Log.w(TAG, "control_sunroof tool is disabled in settings. Returning error response.");
                    sendToolErrorResponse(callId, name, "사용자가 앱 설정에서 선루프 제어 기능을 비활성화했습니다. 운전자에게 앱 설정에서 선루프 제어 기능이 꺼져 있어 실행할 수 없다고 안내하세요.");
                    return;
                }
                String action = (String) args.get("action");
                Integer customPercent = args.get("custom_percent") instanceof Number ? ((Number) args.get("custom_percent")).intValue() : null;

                if (action != null) {
                    vehicleController.controlWindow("sunroof", action, customPercent);
                }
                sendToolSuccessResponse(callId, name);
            } else if ("control_sunshade".equals(name)) {
                SharedPreferences toolCallPrefs = context.getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
                if (!toolCallPrefs.getBoolean(ConfigData.KEY_ENABLE_TOOL_SUNSHADE, true)) {
                    Log.w(TAG, "control_sunshade tool is disabled in settings. Returning error response.");
                    sendToolErrorResponse(callId, name, "사용자가 앱 설정에서 썬쉐이드(햇빛 가리개) 제어 기능을 비활성화했습니다. 운전자에게 앱 설정에서 썬쉐이드 제어 기능이 꺼져 있어 실행할 수 없다고 안내하세요.");
                    return;
                }
                String action = (String) args.get("action");
                Integer customPercent = args.get("custom_percent") instanceof Number ? ((Number) args.get("custom_percent")).intValue() : null;

                if (action != null) {
                    vehicleController.controlWindow("sunshade", action, customPercent);
                }
                sendToolSuccessResponse(callId, name);
            } else if ("get_driving_range".equals(name)) {
                double elecRange = vehicleController.getElecDrivingRangeValue();
                double soc = vehicleController.getSOCBatteryPercentage();
                Map<String, Object> responseMap = new HashMap<>();
                if (elecRange >= 0) {
                    responseMap.put("status", "success");
                    responseMap.put("elec_driving_range_km", (int) elecRange);
                    if (soc >= 0) {
                        responseMap.put("battery_soc_percent", (int) soc);
                    }
                    responseMap.put("message", "현재 전기차 잔여 주행 가능 거리는 약 " + (int) elecRange + "km입니다." + (soc >= 0 ? " (고전압 배터리 잔량: " + (int) soc + "%)" : ""));
                } else {
                    responseMap.put("status", "error");
                    responseMap.put("message", "차량 계기판 통계 장치(BYDAutoStatisticDevice)로부터 주행 가능 거리 정보를 읽어오지 못했습니다.");
                }
                sendToolResponseData(callId, name, responseMap);
            } else if ("control_volume".equals(name)) {
                String action = (String) args.get("action");
                Integer value = args.get("value") instanceof Number ? ((Number) args.get("value")).intValue() : null;

                if (action != null) {
                    vehicleController.controlVolume(action, value);
                }
                sendToolSuccessResponse(callId, name);
            } else if ("launch_naver_map".equals(name) || "launch_tmap".equals(name)) {
                String destination = (String) args.get("destination");
                Double latitude = args.get("latitude") instanceof Number ? ((Number) args.get("latitude")).doubleValue() : null;
                Double longitude = args.get("longitude") instanceof Number ? ((Number) args.get("longitude")).doubleValue() : null;
                if (destination != null) {
                    SavedDestination saved = DestinationDbHelper.getInstance(context)
                            .findMatchingDestination(destination);
                    if ((latitude == null || longitude == null) && saved == null) {
                        sendToolErrorResponse(callId, name,
                                "Destination coordinates are required. Search with an installed external place-search Tool first, then pass its latitude and longitude.");
                        continue;
                    }
                    vehicleController.launchNavigation(destination, latitude, longitude);
                }
                sendToolSuccessResponse(callId, name);
            } else if ("get_current_location".equals(name)) {
                double[] coords = GpsTracker.getCurrentLocation(context);
                Map<String, Object> responseMap = new HashMap<>();
                if (coords != null) {
                    responseMap.put("latitude", coords[0]);
                    responseMap.put("longitude", coords[1]);
                    responseMap.put("status", "success");
                } else {
                    responseMap.put("status", "error");
                    responseMap.put("message", "GPS coordinates not available");
                }
                sendToolResponseData(callId, name, responseMap);
            } else if ("get_current_weather".equals(name)) {
                backgroundExecutor.submit(() -> {
                    double[] coords = GpsTracker.getCurrentLocation(context);
                    Map<String, Object> responseMap = new HashMap<>();
                    if (coords != null) {
                        Double outCarTemp = vehicleController != null ? vehicleController.getOutCarTemperature() : null;
                        String weatherResult = OpenMeteoWeatherHelper.fetchWeather(coords[0], coords[1], outCarTemp);
                        responseMap.put("weather_report", weatherResult);
                        responseMap.put("latitude", coords[0]);
                        responseMap.put("longitude", coords[1]);
                        responseMap.put("status", "success");
                    } else {
                        responseMap.put("status", "error");
                        responseMap.put("message", "GPS 위치 획득 실패. 날씨 정보를 가져올 수 없습니다.");
                    }
                    sendToolResponseData(callId, name, responseMap);
                });
            } else if ("get_installed_apps".equals(name)) {
                Map<String, Object> responseMap = new HashMap<>();
                try {
                    PackageManager pm = context.getPackageManager();
                    Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);
                    boolean hasReVanced = false;
                    for (ResolveInfo info : apps) {
                        if ("app.revanced.android.youtube".equals(info.activityInfo.packageName)) {
                            hasReVanced = true;
                            break;
                        }
                    }

                    List<Map<String, String>> appList = new ArrayList<>();
                    Set<String> seenPackages = new HashSet<>();

                    for (ResolveInfo info : apps) {
                        String packageName = info.activityInfo.packageName;
                        
                        // Hide standard/BYD YouTube if ReVanced is installed to prioritize ReVanced
                        if (hasReVanced && ("com.google.android.youtube".equals(packageName) || "com.byd.youtube".equals(packageName))) {
                            continue;
                        }

                        if (!seenPackages.contains(packageName)) {
                            seenPackages.add(packageName);
                            String appName = info.loadLabel(pm).toString();
                            Map<String, String> appMap = new HashMap<>();
                            appMap.put("name", appName);
                            appMap.put("package", packageName);
                            appList.add(appMap);
                        }
                    }
                    
                    // Artificially inject YouTube package if it was uninstalled to bypass Gemini's pre-check
                    if (!hasReVanced && !seenPackages.contains("com.google.android.youtube") && !seenPackages.contains("com.byd.youtube")) {
                        Map<String, String> youtubeMap = new HashMap<>();
                        youtubeMap.put("name", "유튜브");
                        youtubeMap.put("package", "com.google.android.youtube");
                        appList.add(youtubeMap);
                    }
                    
                    responseMap.put("apps", appList);
                    responseMap.put("status", "success");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to get installed apps", e);
                    responseMap.put("status", "error");
                    responseMap.put("message", "Failed to retrieve app list: " + e.getMessage());
                }
                sendToolResponseData(callId, name, responseMap);
            } else if ("launch_app".equals(name)) {
                String packageName = (String) args.get("package_name");
                String query = (String) args.get("query");
                Map<String, Object> responseMap = new HashMap<>();
                if (packageName != null && !packageName.trim().isEmpty()) {
                    try {
                        Intent launchIntent = AppIntentConfigHelper.buildLaunchIntent(context, packageName, query);
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(launchIntent);
                            responseMap.put("status", "success");
                            responseMap.put("message", "App launched successfully: " + packageName + (query != null ? " with query: " + query : ""));
                            
                            // JSON 설정 테이블에서 정의한 미디어 타입 동적 매핑 호출 (하드코딩 제거)
                            String mediaType = AppIntentConfigHelper.getMediaType(packageName);
                            if (mediaType != null) {
                                listener.onMediaAppTriggered(mediaType);
                            }
                        } else {
                            responseMap.put("status", "error");
                            responseMap.put("message", "App launch intent is null (app might not be installed): " + packageName);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to launch app: " + packageName, e);
                        responseMap.put("status", "error");
                        responseMap.put("message", "Exception launching app: " + e.getMessage());
                    }
                } else {
                    responseMap.put("status", "error");
                    responseMap.put("message", "package_name parameter is missing or empty.");
                }
                sendToolResponseData(callId, name, responseMap);
            } else if ("control_media".equals(name)) {
                String action = (String) args.get("action");
                String query = (String) args.get("query");
                String appName = (String) args.get("app_name");
                Map<String, Object> responseMap = new HashMap<>();
                Log.i(TAG, "control_media tool called: action=" + action + ", query=" + query + ", app_name=" + appName);
                if (appName != null) {
                    appName = appName.trim().toLowerCase();
                } else {
                    appName = "";
                }

                if (!appName.isEmpty()) {
                    // 1. Resolve package name and media type dynamically
                    String targetPackage = AppIntentConfigHelper.getPackageNameByMediaType(appName);
                    if (targetPackage == null && AppIntentConfigHelper.hasConfig(appName)) {
                        targetPackage = appName;
                    }

                    if (targetPackage == null) {
                        responseMap.put("status", "error");
                        responseMap.put("message", "지원하지 않는 미디어 애플리케이션입니다: " + appName);
                        sendToolResponseData(callId, name, responseMap);
                        return;
                    }

                    String mediaType = AppIntentConfigHelper.getMediaType(targetPackage);
                    if (mediaType == null) {
                        mediaType = appName;
                    }

                    boolean mediaSessionSupported = AppIntentConfigHelper.isMediaSessionSupported(targetPackage);

                    if ("play_from_search".equals(action)) {
                        if (query == null || query.trim().isEmpty()) {
                            responseMap.put("status", "error");
                            responseMap.put("message", "play_from_search 명령에는 query 매개변수가 필수입니다.");
                            sendToolResponseData(callId, name, responseMap);
                            return;
                        }

                        if (mediaSessionSupported) {
                            MediaBrowserManager manager = MediaBrowserManager.getInstance(context);
                            if (manager != null) {
                                MediaController controller = manager.getController(mediaType);
                                if (controller != null) {
                                    executeMediaAction(controller, action, query, responseMap);
                                    responseMap.put("message", mediaType + "에서 " + responseMap.get("message"));
                                    listener.onMediaAppTriggered(mediaType);
                                    sendToolResponseData(callId, name, responseMap);
                                    return;
                                } else {
                                    manager.reconnect(mediaType);
                                }
                            }
                        }

                        Intent intent = AppIntentConfigHelper.buildLaunchIntent(context, targetPackage, query);
                        if (intent != null) {
                            try {
                                if ("karaoke".equals(mediaType)) {
                                    // Stingray Karaoke specific overlay force launch delay
                                    try {
                                        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(targetPackage);
                                        if (launchIntent != null) {
                                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            context.startActivity(launchIntent);
                                        }
                                    } catch (Exception e) {
                                        Log.w(TAG, "Failed foreground launch for Karaoke", e);
                                    }
                                    final Intent finalIntent = intent;
                                    new Thread(() -> {
                                        try {
                                            Thread.sleep(500);
                                            finalIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                            context.startActivity(finalIntent);
                                        } catch (Exception e) {
                                            Log.e(TAG, "Failed delayed launch for Karaoke", e);
                                        }
                                    }).start();
                                } else {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    context.startActivity(intent);
                                }
                                responseMap.put("status", "success");
                                responseMap.put("message", mediaType + " 앱에서 '" + query + "' 검색 재생을 실행했습니다.");
                                listener.onMediaAppTriggered(mediaType);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to launch media app intent: " + targetPackage, e);
                                responseMap.put("status", "error");
                                responseMap.put("message", mediaType + " 기동 실패: " + e.getMessage());
                            }
                        } else {
                            // Web fallback for YouTube if the physical package is not installed
                            if ("youtube".equals(mediaType)) {
                                try {
                                    String queryEncoded = java.net.URLEncoder.encode(query, "UTF-8");
                                    String searchUrl = "https://www.youtube.com/results?search_query=" + queryEncoded;
                                    Intent webIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(searchUrl));
                                    webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    webIntent.addCategory(Intent.CATEGORY_BROWSABLE);
                                    Intent selector = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://"));
                                    selector.addCategory(Intent.CATEGORY_BROWSABLE);
                                    webIntent.setSelector(selector);
                                    context.startActivity(webIntent);
                                    responseMap.put("status", "success");
                                    responseMap.put("message", "웹 브라우저를 통해 유튜브 검색결과 페이지를 기동했습니다.");
                                    listener.onMediaAppTriggered("youtube");
                                } catch (Exception e) {
                                    Log.e(TAG, "Failed to launch YouTube web intent", e);
                                    responseMap.put("status", "error");
                                    responseMap.put("message", "유튜브 웹 페이지 기동 실패: " + e.getMessage());
                                }
                            } else {
                                responseMap.put("status", "error");
                                responseMap.put("message", mediaType + " 실행 인텐트를 생성할 수 없습니다. 앱이 설치되어 있는지 확인해 주세요.");
                            }
                        }
                    } else {
                        // Playback actions (play, pause, skip, previous)
                        if (mediaSessionSupported) {
                            MediaBrowserManager manager = MediaBrowserManager.getInstance(context);
                            if (manager == null) {
                                responseMap.put("status", "error");
                                responseMap.put("message", "MediaBrowserManager가 초기화되지 않았습니다.");
                            } else {
                                MediaController controller = manager.getController(mediaType);
                                if (controller == null) {
                                    manager.reconnect(mediaType);
                                    try {
                                        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(targetPackage);
                                        if (launchIntent != null) {
                                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            context.startActivity(launchIntent);
                                            responseMap.put("status", "success");
                                            responseMap.put("message", mediaType + " 플레이어 기동 및 재연결을 시도합니다. 기동 후 다시 요청해 주세요.");
                                            listener.onMediaAppTriggered(mediaType);
                                        } else {
                                            responseMap.put("status", "error");
                                            responseMap.put("message", mediaType + " 실행 인텐트를 찾을 수 없습니다.");
                                        }
                                    } catch (Exception e) {
                                        responseMap.put("status", "error");
                                        responseMap.put("message", mediaType + " 제어 실패: " + e.getMessage());
                                    }
                                } else {
                                    executeMediaAction(controller, action, query, responseMap);
                                    responseMap.put("message", mediaType + "에서 " + responseMap.get("message"));
                                    if (!"pause".equals(action)) {
                                        listener.onMediaAppTriggered(mediaType);
                                    }
                                }
                            }
                        } else {
                            // Fallback to Global Media Key
                            if ("spotify".equals(mediaType)) {
                                try {
                                    Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(targetPackage);
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        context.startActivity(launchIntent);
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "Spotify launch failed", e);
                                }
                                responseMap.put("status", "success");
                                responseMap.put("message", "차량용 Spotify는 보안 정책상 직접적인 음성 제어를 지원하지 않을 수 있어 스크린 조작을 위해 앱을 실행했습니다.");
                            } else {
                                int keyCode = -1;
                                if ("play".equals(action)) {
                                    keyCode = android.view.KeyEvent.KEYCODE_MEDIA_PLAY;
                                } else if ("pause".equals(action)) {
                                    keyCode = android.view.KeyEvent.KEYCODE_MEDIA_PAUSE;
                                } else if ("skip".equals(action)) {
                                    keyCode = android.view.KeyEvent.KEYCODE_MEDIA_NEXT;
                                } else if ("previous".equals(action)) {
                                    keyCode = android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS;
                                }

                                if (keyCode != -1) {
                                    dispatchGlobalMediaKey(keyCode);
                                    responseMap.put("status", "success");
                                    responseMap.put("message", mediaType + " 제어 신호를 전달했습니다.");
                                    if (!"pause".equals(action)) {
                                        listener.onMediaAppTriggered(mediaType);
                                    }
                                } else {
                                    responseMap.put("status", "error");
                                    responseMap.put("message", "지원하지 않는 제어 동작입니다: " + action);
                                }
                            }
                        }
                    }
                } else {
                    MediaBrowserManager manager = MediaBrowserManager.getInstance(context);
                    if (manager == null) {
                        responseMap.put("status", "error");
                        responseMap.put("message", "MediaBrowserManager가 초기화되지 않았습니다.");
                    } else {
                        if ("pause".equals(action)) {
                            boolean success = false;
                            MediaController controller = manager.getController("flo");
                            if (controller != null) {
                                try {
                                    controller.getTransportControls().pause();
                                    success = true;
                                } catch (Exception e) {
                                    Log.e(TAG, "Failed to pause flo", e);
                                }
                            }
                            if (success) {
                                responseMap.put("status", "success");
                                responseMap.put("message", "모든 음악 플레이어를 일시정지합니다.");
                            } else {
                                responseMap.put("status", "error");
                                responseMap.put("message", "제어 가능한 활성 미디어 플레이어가 없습니다.");
                            }
                        } else {
                            MediaController targetController = null;
                            String targetApp = "";
                            MediaController mc = manager.getController("flo");
                            if (mc != null) {
                                PlaybackState state = mc.getPlaybackState();
                                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                                    targetController = mc;
                                    targetApp = "flo";
                                }
                            }
                            if (targetController == null) {
                                if (mc != null) {
                                    targetController = mc;
                                    targetApp = "flo";
                                }
                            }

                            if (targetController == null) {
                                manager.reconnect("flo");
                                try {
                                    Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage("skplanet.musicmate");
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        context.startActivity(launchIntent);
                                        responseMap.put("status", "success");
                                        responseMap.put("message", "구동 중인 음악 플레이어가 없어 FLO 플레이어를 강제 기동하고 재연결을 시도합니다. 잠시 후 다시 재생을 요청해 주세요.");
                                        listener.onMediaAppTriggered("flo");
                                    } else {
                                        Intent mediaIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                                        mediaIntent.putExtra(Intent.EXTRA_KEY_EVENT, new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY));
                                        context.sendBroadcast(mediaIntent);
                                        responseMap.put("status", "success");
                                        responseMap.put("message", "구동 중인 플레이어가 없고 실행 인텐트를 찾을 수 없어, 미디어 재생 이벤트를 브로드캐스트했습니다. 잠시 후 다시 요청해 주세요.");
                                        listener.onMediaAppTriggered("flo");
                                    }
                                } catch (Exception e) {
                                    responseMap.put("status", "error");
                                    responseMap.put("message", "플레이어 기동 실패 및 서비스 제어 오류: " + e.getMessage());
                                }
                            } else {
                                executeMediaAction(targetController, action, query, responseMap);
                                responseMap.put("message", targetApp.toUpperCase() + "에서 " + responseMap.get("message"));
                                if (!"pause".equals(action)) {
                                    listener.onMediaAppTriggered(targetApp);
                                }
                            }
                        }
                    }
                }
                Log.i(TAG, "control_media response sent: " + responseMap.toString());
                sendToolResponseData(callId, name, responseMap);
            }
        }
    }

    private void sendToolSuccessResponse(String callId, String name) {
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("status", "success");

        FunctionResponse functionResponse = FunctionResponse.builder()
                .id(callId)
                .name(name)
                .response(responseMap)
                .build();

        LiveSendToolResponseParameters responseParams = LiveSendToolResponseParameters.builder()
                .functionResponses(Collections.singletonList(functionResponse))
                .build();

        if (isConnected && session != null) {
            try {
                session.sendToolResponse(responseParams).thenAccept(v -> {
                    Log.d(TAG, "Successfully sent tool success response for: " + name);
                }).exceptionally(ex -> {
                    Log.e(TAG, "Failed to send tool response for: " + name, ex);
                    return null;
                });
            } catch (Exception e) {
                Log.e(TAG, "Exception sending tool response for: " + name, e);
            }
        }
    }

    private void sendToolErrorResponse(String callId, String name, String errorMessage) {
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("status", "error");
        responseMap.put("message", errorMessage);

        FunctionResponse functionResponse = FunctionResponse.builder()
                .id(callId)
                .name(name)
                .response(responseMap)
                .build();

        LiveSendToolResponseParameters responseParams = LiveSendToolResponseParameters.builder()
                .functionResponses(Collections.singletonList(functionResponse))
                .build();

        if (isConnected && session != null) {
            try {
                session.sendToolResponse(responseParams).thenAccept(v -> {
                    Log.d(TAG, "Successfully sent tool error response for: " + name + " - " + errorMessage);
                }).exceptionally(ex -> {
                    Log.e(TAG, "Failed to send tool response for: " + name, ex);
                    return null;
                });
            } catch (Exception e) {
                Log.e(TAG, "Exception sending tool response for: " + name, e);
            }
        }
    }

    private void sendToolResponseData(String callId, String name, Map<String, Object> responseData) {
        FunctionResponse functionResponse = FunctionResponse.builder()
                .id(callId)
                .name(name)
                .response(responseData)
                .build();

        LiveSendToolResponseParameters responseParams = LiveSendToolResponseParameters.builder()
                .functionResponses(Collections.singletonList(functionResponse))
                .build();

        if (isConnected && session != null) {
            try {
                session.sendToolResponse(responseParams).thenAccept(v -> {
                    Log.d(TAG, "Successfully sent tool response data for: " + name);
                }).exceptionally(ex -> {
                    Log.e(TAG, "Failed to send tool response data for: " + name, ex);
                    return null;
                });
            } catch (Exception e) {
                Log.e(TAG, "Exception sending tool response data for: " + name, e);
            }
        }
    }

    private void executeMediaAction(MediaController controller, String action, String query, Map<String, Object> responseMap) {
        try {
            if ("play".equals(action)) {
                controller.getTransportControls().play();
                responseMap.put("status", "success");
                responseMap.put("message", "재생을 시작합니다.");
            } else if ("pause".equals(action)) {
                controller.getTransportControls().pause();
                responseMap.put("status", "success");
                responseMap.put("message", "일시정지합니다.");
            } else if ("skip".equals(action)) {
                controller.getTransportControls().skipToNext();
                responseMap.put("status", "success");
                responseMap.put("message", "다음 곡으로 넘어갑니다.");
            } else if ("previous".equals(action)) {
                controller.getTransportControls().skipToPrevious();
                responseMap.put("status", "success");
                responseMap.put("message", "이전 곡으로 돌아갑니다.");
            } else if ("play_from_search".equals(action)) {
                if (query != null && !query.trim().isEmpty()) {
                    controller.getTransportControls().playFromSearch(query, null);
                    responseMap.put("status", "success");
                    responseMap.put("message", "'" + query + "' 검색 및 재생 명령을 전송했습니다.");
                } else {
                    responseMap.put("status", "error");
                    responseMap.put("message", "play_from_search 명령에는 query 매개변수가 필수입니다.");
                }
            } else {
                responseMap.put("status", "error");
                responseMap.put("message", "알 수 없는 미디어 제어 명령: " + action);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send media control command", e);
            responseMap.put("status", "error");
            responseMap.put("message", "명령 송신 실패: " + e.getMessage());
        }
    }

    public void sendAudio(byte[] audioData) {
        if (!isConnected || session == null) return;
        try {
            audioSendCount++;
            if (System.currentTimeMillis() - lastAudioLogTime > 2000) {
                Log.d(TAG, "Sending audio chunk " + audioSendCount + ", size: " + audioData.length);
                lastAudioLogTime = System.currentTimeMillis();
            }
            session.sendRealtimeInput(
                    LiveSendRealtimeInputParameters.builder()
                            .audio(Blob.builder()
                                    .data(audioData)
                                    .mimeType("audio/pcm;rate=16000")
                                    .build())
                            .build()
            );
        } catch (Exception e) {
            Log.e(TAG, "Error sending audio", e);
        }
    }

    public void sendAudioStreamEnd() {
        if (!isConnected || session == null) return;
        try {
            audioStreamEndCount++;
            session.sendRealtimeInput(
                    LiveSendRealtimeInputParameters.builder()
                            .audioStreamEnd(true)
                            .build()
            );
            Log.d(TAG, "Sent audioStreamEnd, count: " + audioStreamEndCount);
        } catch (Exception e) {
            Log.e(TAG, "Error sending audioStreamEnd", e);
        }
    }

    public void sendText(String text) {
        if (!isConnected || session == null) return;
        try {
            Log.d(TAG, "Sending client text input: " + text);
            if (isTextOnlyMode) {
                sessionTranscript.appendDriver(text);
            }
            session.sendRealtimeInput(
                    LiveSendRealtimeInputParameters.builder()
                            .text(text)
                            .build()
            );
        } catch (Exception e) {
            Log.e(TAG, "Error sending text input", e);
        }
    }

    public void disconnect() {
        cleanup();
        listener.onDisconnected();

        // Trigger memory extraction pipeline on disconnection (only if NOT in TTS-only mode)
        String dialogueText = sessionTranscript.drain();

        if (useMemory && !isTtsOnlyMode && dialogueText.trim().length() > 15) {
            final String textToAnalyze = dialogueText;
            backgroundExecutor.submit(() -> {
                try {
                    analyzeAndSaveDialogueMemory(textToAnalyze);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to run dialogue memory pipeline", e);
                }
            });
        }
        // A GeminiLiveClient is discarded after disconnect; let queued weather/memory work finish
        // without leaving its worker thread alive for the lifetime of the service.
        backgroundExecutor.shutdown();
    }

    private void cleanup() {
        AsyncSession currentSession = session;
        session = null;
        if (currentSession != null) {
            try { currentSession.close(); } catch (Exception ignored) {}
        }
        isConnected = false;
    }

    public boolean isConnected() {
        return isConnected;
    }

    private void dispatchGlobalMediaKey(int keyCode) {
        try {
            android.media.AudioManager am = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                long eventTime = android.os.SystemClock.uptimeMillis();
                android.view.KeyEvent downEvent = new android.view.KeyEvent(eventTime, eventTime, android.view.KeyEvent.ACTION_DOWN, keyCode, 0);
                android.view.KeyEvent upEvent = new android.view.KeyEvent(eventTime, eventTime, android.view.KeyEvent.ACTION_UP, keyCode, 0);
                am.dispatchMediaKeyEvent(downEvent);
                am.dispatchMediaKeyEvent(upEvent);
                Log.i(TAG, "Dispatched global media key event: " + keyCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to dispatch global media key", e);
        }
    }

    private boolean isPackageInstalled(String pkg) {
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String extractYouTubeQuery(String transcription) {
        if (transcription == null || transcription.isEmpty()) {
            return "";
        }
        
        String query = transcription.trim();
        
        // Remove trailing punctuation
        if (query.endsWith(".")) {
            query = query.substring(0, query.length() - 1).trim();
        }
        
        // Remove prefix "유튜브에서"
        int idx = query.indexOf("유튜브에서");
        if (idx != -1) {
            query = query.substring(idx + "유튜브에서".length()).trim();
        }
        
        // Remove common suffixes
        String[] suffixes = {
            "노래 틀어 줘", "노래 틀어줘", "노래 틀어", "노래 들려줘", "노래 들려 줘",
            "영상 틀어 줘", "영상 틀어줘", "영상 틀어", "틀어 줘", "틀어줘", "틀어",
            "들려 줘", "들려줘", "틀어라", "틀어주라", "재생해 줘", "재생해줘", "재생"
        };
        
        for (String suffix : suffixes) {
            if (query.endsWith(suffix)) {
                query = query.substring(0, query.length() - suffix.length()).trim();
                break;
            }
        }
        
        return query;
    }

    private void analyzeAndSaveDialogueMemory(String dialogue) {
        SharedPreferences prefs = context.getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
        String apiKey = SecureCredentialStore.getString(context, ConfigData.KEY_GEMINI_API_KEY, "");
        String modelName = prefs.getString(ConfigData.KEY_GEMINI_NORMAL_MODEL, "gemma-4-31b-it");
        if (apiKey.isEmpty()) {
            Log.w(TAG, "Gemini API key is empty. Skipping memory extraction.");
            return;
        }

        String systemPrompt = "너는 BYD 차량용 AI 에이전트의 백엔드 메모리 관리자(Memory Extractor)야. "
                + "너의 임무는 운전자와 AI가 주고받은 최근 대화 내용을 분석하여, 운전자의 성향, 상태, 선호도 등 앞으로 대화나 차량 제어에 유용한 핵심 정보를 추출하고 분류하는 것이다.\n\n"
                + "대화 내용을 기반으로 정보를 다음 두 가지 트랙으로 분류하여 추출해라.\n"
                + "1. 단기 기억 (short_term): 이번 주행 세션 동안에만 유효한 일시적인 정보. (예: 기분, 졸림/피곤함, 임시 경유지)\n"
                + "2. 장기 기억 (long_term): 운전자의 고유한 성향이나 반복적인 취향. (예: 에어컨 선호 온도, 음악 취향)\n"
                + "* 단, 사용자가 직접 설정 액티비티에 입력하는 '집 주소', '회사 주소'는 추출 대상에서 제외한다.\n\n"
                + "반드시 아래 JSON 구조로만 응답해라. 마크다운 블록(```json ```)을 포함하지 말고, 오직 순수한 JSON 텍스트만 출력해야 한다. 새로 추출된 정보가 없다면 해당 key의 value는 null로 비워두어라.\n\n"
                + "{\n"
                + "  \"short_term\": {\n"
                + "    \"driver_state\": \"운전자의 현재 상태나 기분 (예: sleepy, happy, none)\",\n"
                + "    \"temporary_context\": \"이번 주행 중 참고할 특이사항 (예: stopby_mart, none)\"\n"
                + "  },\n"
                + "  \"long_term\": {\n"
                + "    \"air_preference\": \"에어컨/히터 관련 선호도 (예: 23도 선호, 무풍 선호, none)\",\n"
                + "    \"music_preference\": \"음악 취향 (예: 90s_ballad, rock, none)\",\n"
                + "    \"general_habit\": \"그 외 기억해야 할 핵심 습관이나 취향 (none)\"\n"
                + "  }\n"
                + "}";

        try {
            java.net.URL url = new java.net.URL("https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);

            org.json.JSONObject requestJson = new org.json.JSONObject();
            
            org.json.JSONObject systemInstruction = new org.json.JSONObject();
            org.json.JSONArray systemParts = new org.json.JSONArray();
            org.json.JSONObject systemTextPart = new org.json.JSONObject();
            systemTextPart.put("text", systemPrompt);
            systemParts.put(systemTextPart);
            systemInstruction.put("parts", systemParts);
            requestJson.put("systemInstruction", systemInstruction);

            org.json.JSONArray contentsArray = new org.json.JSONArray();
            org.json.JSONObject contentObj = new org.json.JSONObject();
            org.json.JSONArray partsArray = new org.json.JSONArray();
            org.json.JSONObject userTextPart = new org.json.JSONObject();
            userTextPart.put("text", "대화 내역:\n" + dialogue);
            partsArray.put(userTextPart);
            contentObj.put("parts", partsArray);
            contentsArray.put(contentObj);
            requestJson.put("contents", contentsArray);

            org.json.JSONObject config = new org.json.JSONObject();
            config.put("responseMimeType", "application/json");
            requestJson.put("generationConfig", config);

            byte[] input = requestJson.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                java.io.InputStream is = conn.getInputStream();
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();
                is.close();

                Log.d(TAG, "Gemma dialogue analysis raw result: " + response.toString());
                parseAndApplyMemoryJson(response.toString());
            } else {
                Log.e(TAG, "Failed to call Gemma API for memory extraction. Response Code: " + responseCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during Gemma dialogue memory pipeline execution", e);
        }
    }

    private void parseAndApplyMemoryJson(String rawResponse) {
        try {
            org.json.JSONObject responseJson = new org.json.JSONObject(rawResponse);
            org.json.JSONArray candidates = responseJson.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) return;

            org.json.JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
            if (content == null) return;

            org.json.JSONArray parts = content.optJSONArray("parts");
            if (parts == null || parts.length() == 0) return;

            String jsonText = "";
            for (int i = parts.length() - 1; i >= 0; i--) {
                org.json.JSONObject partObj = parts.optJSONObject(i);
                if (partObj == null) continue;

                // Skip reasoning/thought blocks
                if (partObj.optBoolean("thought", false)) {
                    continue;
                }

                String text = partObj.optString("text", "").trim();

                // Strip markdown code fences if output by the model
                if (text.startsWith("```json")) {
                    text = text.substring(7).trim();
                }
                if (text.endsWith("```")) {
                    text = text.substring(0, text.length() - 3).trim();
                }

                if (text.startsWith("{") && text.endsWith("}")) {
                    jsonText = text;
                    break;
                }
            }

            if (jsonText.isEmpty()) {
                Log.w(TAG, "No valid JSON part found in Gemma response.");
                return;
            }

            org.json.JSONObject memoryJson = new org.json.JSONObject(jsonText);

            // 1. 단기 기억 파싱 및 적재 (옵션 A - 자연어 가공)
            org.json.JSONObject shortTerm = memoryJson.optJSONObject("short_term");
            if (shortTerm != null) {
                String driverState = shortTerm.optString("driver_state", "none").trim();
                String temporaryContext = shortTerm.optString("temporary_context", "none").trim();

                if (!driverState.isEmpty() && !driverState.equalsIgnoreCase("none") && !driverState.equalsIgnoreCase("null")) {
                    String stateText = convertDriverStateToNaturalLanguage(driverState);
                    GeminiLiveService.addShortTermMemory(stateText);
                }
                if (!temporaryContext.isEmpty() && !temporaryContext.equalsIgnoreCase("none") && !temporaryContext.equalsIgnoreCase("null")) {
                    String contextText = convertTemporaryContextToNaturalLanguage(temporaryContext);
                    GeminiLiveService.addShortTermMemory(contextText);
                }
            }

            // 2. 장기 기억 파싱 및 적재 (SQLite UPSERT)
            org.json.JSONObject longTerm = memoryJson.optJSONObject("long_term");
            if (longTerm != null) {
                String[] keys = {"air_preference", "music_preference", "general_habit"};
                for (String k : keys) {
                    String val = longTerm.optString(k, "none").trim();
                    if (!val.isEmpty() && !val.equalsIgnoreCase("none") && !val.equalsIgnoreCase("null")) {
                        DestinationDbHelper.getInstance(context).saveLongTermMemory(k, val);
                        Log.i(TAG, "Saved Long-term Memory -> key: " + k + ", value: " + val);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse Gemma memory extraction response", e);
        }
    }

    private String convertDriverStateToNaturalLanguage(String state) {
        if (state == null) return "";
        state = state.trim().toLowerCase();
        switch (state) {
            case "sleepy":
                return "운전자가 현재 졸린 상태임";
            case "tired":
                return "운전자가 피곤해 하는 상태임";
            case "happy":
                return "운전자의 기분이 좋아 보임";
            case "angry":
                return "운전자가 짜증나거나 화가 나 보임";
            case "relaxed":
                return "운전자가 편안하고 차분한 상태임";
            default:
                return "운전자 현재 상태: " + state;
        }
    }

    private String convertTemporaryContextToNaturalLanguage(String context) {
        if (context == null) return "";
        context = context.trim().toLowerCase();
        if (context.startsWith("stopby_")) {
            String destination = context.substring(7);
            return "주행 중 " + destination + "에 들를 예정임";
        }
        return "이번 주행 특이사항: " + context;
    }

    private String extractAudioProfile(String fullPrompt) {
        if (fullPrompt == null || fullPrompt.isEmpty()) {
            return "";
        }

        String[] headers = {
            "### AUDIO PROFILE (Director\'s Notes)",
            "### AUDIO PROFILE (Director's Notes)",
            "### AUDIO PROFILE",
            "# 오디오 프로필 (대화 스타일)",
            "# 오디오 프로필"
        };

        int startIdx = -1;
        String matchedHeader = "";
        for (String h : headers) {
            startIdx = fullPrompt.indexOf(h);
            if (startIdx != -1) {
                matchedHeader = h;
                break;
            }
        }

        if (startIdx == -1) {
            return "";
        }

        String sub = fullPrompt.substring(startIdx);
        int nextSectionIdx = -1;
        int searchOffset = matchedHeader.length();
        for (int i = searchOffset; i < sub.length(); i++) {
            char c = sub.charAt(i);
            if (c == '#') {
                nextSectionIdx = i;
                break;
            }
        }

        if (nextSectionIdx != -1) {
            return sub.substring(0, nextSectionIdx).trim();
        } else {
            return sub.trim();
        }
    }
}
