package com.poorgrammera.bydsubai.integration;

import android.content.Context;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Fetches Google News RSS without provider-specific API credentials. */
public final class GoogleNewsRssHelper {
    private static final String TAG = "GoogleNewsRss";
    private static final int MAX_ITEMS = 10;

    private GoogleNewsRssHelper() { }

    public static String fetchDailyNews(Context context) {
        NewsLocale locale = NewsLocale.from(context);
        StringBuilder result = new StringBuilder("[Top news]\n");
        result.append(fetch(url("https://news.google.com/rss", locale), MAX_ITEMS));
        return result.toString();
    }

    public static String search(Context context, String query) {
        if (query == null || query.trim().isEmpty()) return fetchDailyNews(context);
        NewsLocale locale = NewsLocale.from(context);
        String encodedQuery = encode(query.trim());
        return fetch(url("https://news.google.com/rss/search?q=" + encodedQuery, locale), MAX_ITEMS);
    }

    private static String fetch(String url, int maxItems) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new java.net.URL(url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return "Error: Google News HTTP " + connection.getResponseCode();
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)));
            StringBuilder output = new StringBuilder();
            String title = null;
            boolean inItem = false;
            for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event == XmlPullParser.START_TAG && "item".equals(parser.getName())) { inItem = true; title = null; }
                else if (event == XmlPullParser.START_TAG && inItem) {
                    String tag = parser.getName();
                    if ("title".equals(tag)) title = parser.nextText();
                } else if (event == XmlPullParser.END_TAG && "item".equals(parser.getName())) {
                    output.append("- ").append(title == null ? "" : title);
                    output.append('\n');
                    if (countItems(output.toString()) >= maxItems) break;
                    inItem = false;
                }
            }
            return output.length() == 0 ? "No news items found." : output.toString().trim();
        } catch (Exception e) {
            Log.e(TAG, "Google News RSS request failed", e);
            return "Error: " + e.getMessage();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static int countItems(String text) { return text.split("\\n- ").length - (text.startsWith("- ") ? 0 : 1); }

    private static String url(String base, NewsLocale locale) { return base + (base.contains("?") ? "&" : "?") + "hl=" + locale.language + "&gl=" + locale.country + "&ceid=" + locale.country + ":" + locale.language; }
    private static String encode(String value) {
        try { return URLEncoder.encode(value, StandardCharsets.UTF_8.name()); }
        catch (Exception e) { throw new IllegalArgumentException("Could not encode RSS query", e); }
    }

    private static final class NewsLocale {
        final String language;
        final String country;
        NewsLocale(String language, String country) { this.language = language; this.country = country; }
        static NewsLocale from(Context context) {
            Locale locale = context.getResources().getConfiguration().getLocales().get(0);
            String language = locale.getLanguage();
            String country = locale.getCountry();
            if ("ko".equals(language)) return new NewsLocale("ko", "KR");
            if ("ja".equals(language)) return new NewsLocale("ja", "JP");
            return new NewsLocale(language.isEmpty() ? "en" : language, country.isEmpty() ? "US" : country);
        }
    }
}
