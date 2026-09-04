package com.poorgrammera.bydsubai.integration;
import com.poorgrammera.bydsubai.BuildConfig;
import com.poorgrammera.bydsubai.R;


import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class AppIntentConfigHelper {
    private static final String TAG = "AppIntentConfigHelper";
    private static final Map<String, AppIntentConfig> configMap = new HashMap<>();

    private static class AppIntentConfig {
        String packageName;
        String intentUriTemplate;
        boolean usePackageTarget;
        String mediaType;
        boolean mediaSessionSupported;

        AppIntentConfig(String packageName, String intentUriTemplate, boolean usePackageTarget, String mediaType, boolean mediaSessionSupported) {
            this.packageName = packageName;
            this.intentUriTemplate = intentUriTemplate;
            this.usePackageTarget = usePackageTarget;
            this.mediaType = mediaType;
            this.mediaSessionSupported = mediaSessionSupported;
        }
    }

    /**
     * assets 폴더에서 app_intent_configs.json 파일을 읽어와 인메모리 맵에 적재합니다.
     */
    public static void init(Context context) {
        if (context == null) return;
        try {
            configMap.clear();
            InputStream is = context.getAssets().open("app_intent_configs.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            int read = is.read(buffer);
            is.close();

            if (read > 0) {
                String jsonStr = new String(buffer, StandardCharsets.UTF_8);
                JSONArray array = new JSONArray(jsonStr);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    String pkg = obj.optString("package", "").trim();
                    String template = obj.optString("intent_uri_template", "").trim();
                    boolean useTarget = obj.optBoolean("use_package_target", true);
                    String mediaType = obj.optString("media_type", "").trim();
                    boolean mediaSessionSupported = obj.optBoolean("media_session_supported", false);

                    if (!pkg.isEmpty()) {
                        configMap.put(pkg, new AppIntentConfig(pkg, template, useTarget, mediaType.isEmpty() ? null : mediaType, mediaSessionSupported));
                    }
                }
                Log.i(TAG, "Successfully loaded " + configMap.size() + " app intent configurations from assets.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize AppIntentConfigHelper", e);
        }
    }

    private static boolean isAppInstalled(Context context, String packageName) {
        if (context == null || packageName == null) return false;
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 패키지명과 쿼리를 받아 최적의 Launch Intent를 빌드합니다.
     */
    public static Intent buildLaunchIntent(Context context, String packageName, String query) {
        if (context == null || packageName == null) return null;
        packageName = packageName.trim();

        // Check if the requested package is installed, fallback if not
        if (!isAppInstalled(context, packageName)) {
            Log.w(TAG, "Requested package " + packageName + " is not installed. Checking for fallback package via mediaType.");
            AppIntentConfig originalConfig = configMap.get(packageName);
            if (originalConfig != null && originalConfig.mediaType != null) {
                String targetMediaType = originalConfig.mediaType;
                String fallbackPackage = null;
                
                for (Map.Entry<String, AppIntentConfig> entry : configMap.entrySet()) {
                    AppIntentConfig candidate = entry.getValue();
                    if (targetMediaType.equalsIgnoreCase(candidate.mediaType) && isAppInstalled(context, candidate.packageName)) {
                        fallbackPackage = candidate.packageName;
                        Log.i(TAG, "Redirecting launch to installed package [" + fallbackPackage + "] for mediaType [" + targetMediaType + "]");
                        break;
                    }
                }
                
                if (fallbackPackage != null) {
                    packageName = fallbackPackage;
                }
            }
        }

        AppIntentConfig config = configMap.get(packageName);

        if (config != null && query != null && !query.trim().isEmpty() && config.intentUriTemplate != null && !config.intentUriTemplate.isEmpty()) {
            try {
                String formattedUri;
                if (config.intentUriTemplate.startsWith("intent:")) {
                    formattedUri = config.intentUriTemplate.replace("{query}", query.trim());
                } else {
                    String encodedQuery = Uri.encode(query.trim());
                    formattedUri = config.intentUriTemplate.replace("{query}", encodedQuery);
                }
                Log.i(TAG, "Building deep link intent for package: " + packageName + ", URI: " + formattedUri);

                Intent intent;
                if (formattedUri.startsWith("intent:")) {
                    intent = Intent.parseUri(formattedUri, Intent.URI_INTENT_SCHEME);
                } else {
                    intent = new Intent(Intent.ACTION_VIEW, Uri.parse(formattedUri));
                }
                if (config.usePackageTarget) {
                    intent.setPackage(packageName);
                }
                return intent;
            } catch (Exception e) {
                Log.e(TAG, "Failed to build deep link intent for: " + packageName + ", fallback to default launcher.", e);
            }
        }

        // 기본 런처 실행 인텐트 반환
        try {
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                return launchIntent;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get launch intent for package: " + packageName, e);
        }
        return null;
    }

    /**
     * 패키지명에 할당된 미디어 유형을 획득합니다.
     */
    public static String getMediaType(String packageName) {
        if (packageName == null) return null;
        AppIntentConfig config = configMap.get(packageName.trim());
        return config != null ? config.mediaType : null;
    }

    /**
     * 특정 패키지명 또는 미디어 타입의 구성이 존재하는지 확인합니다.
     */
    public static boolean hasConfig(String key) {
        if (key == null) return false;
        key = key.trim();
        if (configMap.containsKey(key)) return true;
        for (AppIntentConfig config : configMap.values()) {
            if (key.equalsIgnoreCase(config.mediaType)) return true;
        }
        return false;
    }

    /**
     * 미디어 타입을 기준으로 설치되었거나 매핑된 최선의 패키지명을 찾습니다.
     */
    public static String getPackageNameByMediaType(String mediaType) {
        if (mediaType == null) return null;
        mediaType = mediaType.trim();
        for (AppIntentConfig config : configMap.values()) {
            if (mediaType.equalsIgnoreCase(config.mediaType)) {
                return config.packageName;
            }
        }
        return null;
    }

    /**
     * 해당 패키지명 또는 미디어 타입이 MediaSession 제어를 명시적으로 타야 하는지 여부.
     */
    public static boolean isMediaSessionSupported(String packageNameOrMediaType) {
        if (packageNameOrMediaType == null) return false;
        packageNameOrMediaType = packageNameOrMediaType.trim();
        AppIntentConfig config = configMap.get(packageNameOrMediaType);
        if (config != null) {
            return config.mediaSessionSupported;
        }
        for (AppIntentConfig cfg : configMap.values()) {
            if (packageNameOrMediaType.equalsIgnoreCase(cfg.mediaType)) {
                return cfg.mediaSessionSupported;
            }
        }
        return false;
    }
}
