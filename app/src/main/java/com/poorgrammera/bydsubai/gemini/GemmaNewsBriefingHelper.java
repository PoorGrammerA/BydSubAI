package com.poorgrammera.bydsubai.gemini;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.poorgrammera.bydsubai.data.ConfigData;
import com.poorgrammera.bydsubai.data.SecureCredentialStore;
import com.poorgrammera.bydsubai.integration.GoogleNewsRssHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Creates the dashboard news briefing from Google News RSS top and local feeds. */
public final class GemmaNewsBriefingHelper {
    private static final String TAG = "GemmaNewsHelper";

    private GemmaNewsBriefingHelper() { }

    public static String fetchNewsBriefing(Context context) {
        String rssNews = GoogleNewsRssHelper.fetchDailyNews(context);
        SharedPreferences prefs = context.getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
        String apiKey = SecureCredentialStore.getString(context, ConfigData.KEY_GEMINI_API_KEY, "");
        String modelName = prefs.getString(ConfigData.KEY_GEMINI_NORMAL_MODEL, ConfigData.DEFAULT_GEMINI_NORMAL_MODEL);
        if (apiKey.isEmpty()) return "Gemini API key is not configured.\n\n" + rssNews;

        try {
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            connection.setDoOutput(true);

            JSONObject request = new JSONObject();
            request.put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put(
                    "text", "Summarize the supplied top and local news for a driver. Use only supplied facts, prioritize safety and relevance, and give three concise items."))));
            request.put("contents", new JSONArray().put(new JSONObject().put("parts", new JSONArray().put(
                    new JSONObject().put("text", "News RSS data:\n" + rssNews)))));
            try (java.io.OutputStream stream = connection.getOutputStream()) {
                stream.write(request.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return "News briefing request failed: HTTP " + connection.getResponseCode() + "\n\n" + rssNews;
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
            }
            JSONArray parts = new JSONObject(response.toString()).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts");
            for (int i = parts.length() - 1; i >= 0; i--) {
                JSONObject part = parts.getJSONObject(i);
                if (!part.optBoolean("thought", false) && !part.optString("text").trim().isEmpty()) return part.optString("text").trim();
            }
        } catch (Exception e) {
            Log.e(TAG, "News briefing generation failed", e);
        }
        return rssNews;
    }
}
