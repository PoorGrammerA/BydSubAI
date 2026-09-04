package com.poorgrammera.bydsubai.integration;
import com.poorgrammera.bydsubai.BuildConfig;
import com.poorgrammera.bydsubai.R;


import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class OpenMeteoWeatherHelper {
    private static final String TAG = "WeatherHelper";

    public static String getWeatherDescription(int code) {
        switch (code) {
            case 0: return "맑음 ☀️";
            case 1: return "대체로 맑음 🌤️";
            case 2: return "구름 조금 ⛅";
            case 3: return "흐림 ☁️";
            case 45: case 48: return "안개 🌫️";
            case 51: case 53: case 55: return "이슬비 🌧️";
            case 56: case 57: return "얼어붙는 이슬비 ❄️🌧️";
            case 61: case 63: case 65: return "비 ☔";
            case 66: case 67: return "진눈깨비 🌧️❄️";
            case 71: case 73: case 75: return "눈 ❄️";
            case 77: return "싸락눈 🌨️";
            case 80: case 81: case 82: return "소나기 🌦️";
            case 85: case 86: return "소낙눈 🌨️";
            case 95: case 96: case 99: return "뇌우 및 우박 ⚡";
            default: return "정보 없음 (코드: " + code + ")";
        }
    }

    public static String fetchWeather(double latitude, double longitude) {
        return fetchWeather(latitude, longitude, null);
    }

    public static String fetchWeather(double latitude, double longitude, Double outCarTemp) {
        String urlString = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%.6f&longitude=%.6f&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto",
                latitude, longitude
        );
        Log.i(TAG, "Requesting weather from Open-Meteo: " + urlString);

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream is = conn.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();
                is.close();

                JSONObject root = new JSONObject(response.toString());
                
                // Current weather
                JSONObject current = root.getJSONObject("current");
                double temp = current.getDouble("temperature_2m");
                double humidity = current.getDouble("relative_humidity_2m");
                double apparentTemp = current.getDouble("apparent_temperature");
                int weatherCode = current.getInt("weather_code");
                double windSpeed = current.getDouble("wind_speed_10m");

                // Daily forecast (today)
                JSONObject daily = root.getJSONObject("daily");
                JSONArray maxTemps = daily.getJSONArray("temperature_2m_max");
                JSONArray minTemps = daily.getJSONArray("temperature_2m_min");
                JSONArray precProbs = daily.getJSONArray("precipitation_probability_max");

                double maxTemp = maxTemps.length() > 0 ? maxTemps.getDouble(0) : temp;
                double minTemp = minTemps.length() > 0 ? minTemps.getDouble(0) : temp;
                int precProb = precProbs.length() > 0 ? precProbs.getInt(0) : 0;

                String weatherDesc = getWeatherDescription(weatherCode);

                String result = String.format(
                        "현재 날씨: %s\n" +
                        "현재 기온: %.1f°C (체감 온도: %.1f°C)\n" +
                        "습도: %.0f%%\n" +
                        "풍속: %.1f km/h\n" +
                        "오늘 최저 기온: %.1f°C / 최고 기온: %.1f°C\n" +
                        "오늘 최대 강수 확률: %d%%",
                        weatherDesc, temp, apparentTemp, humidity, windSpeed, minTemp, maxTemp, precProb
                );

                if (outCarTemp != null) {
                    result += String.format("\n차량 외부 온도 센서 측정값: %.1f°C", outCarTemp);
                }
                Log.i(TAG, "Weather response: " + result);
                return result;
            } else {
                return "날씨 정보를 가져오지 못했습니다. HTTP 에러 코드: " + responseCode;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching weather data", e);
            return "날씨 데이터를 조회하는 중 오류가 발생했습니다: " + e.getMessage();
        }
    }
}
