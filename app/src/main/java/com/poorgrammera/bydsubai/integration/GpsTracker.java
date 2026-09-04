package com.poorgrammera.bydsubai.integration;

import com.poorgrammera.bydsubai.data.ConfigData;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

public class GpsTracker {
    private static final String TAG = "GpsTracker";
    private static final long LOCATION_FRESHNESS_THRESHOLD_MS = 30_000L; // 30 seconds
    private static final long OS_LOCATION_MAX_AGE_MS = 60_000L; // 60 seconds

    public static double[] getCurrentLocation(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
        
        float cachedLat = prefs.getFloat("gps_last_latitude", 0.0f);
        float cachedLon = prefs.getFloat("gps_last_longitude", 0.0f);
        long cachedTimestamp = prefs.getLong("gps_last_timestamp", 0L);

        long now = System.currentTimeMillis();

        // 1. If we have fresh cached coordinates (within last 30s), return immediately
        if (cachedLat != 0.0f && cachedLon != 0.0f && cachedTimestamp > 0 && (now - cachedTimestamp) < LOCATION_FRESHNESS_THRESHOLD_MS) {
            Log.d(TAG, "getCurrentLocation: Returning fresh cached coordinates (" + ((now - cachedTimestamp) / 1000) + "s old) -> " + cachedLat + ", " + cachedLon);
            return new double[]{cachedLat, cachedLon};
        }

        // 2. If cached location is missing or stale (> 30s), attempt to fetch real-time location from LocationManager
        Log.i(TAG, "getCurrentLocation: Cached location stale or missing (age: " + (cachedTimestamp > 0 ? (now - cachedTimestamp) / 1000 + "s" : "none") + "). Fetching real-time GPS...");

        double[] realTimeCoords = fetchRealTimeCoordinates(context, prefs);
        if (realTimeCoords != null) {
            Log.i(TAG, "getCurrentLocation: Successfully fetched real-time GPS coordinates -> " + realTimeCoords[0] + ", " + realTimeCoords[1]);
            return realTimeCoords;
        }

        // 3. Fallback: If real-time GPS query failed, but we have a non-zero cached location (even if stale), return it
        if (cachedLat != 0.0f && cachedLon != 0.0f) {
            Log.w(TAG, "getCurrentLocation: Real-time GPS fetch failed. Falling back to stale cached location -> " + cachedLat + ", " + cachedLon);
            return new double[]{cachedLat, cachedLon};
        }

        // 4. Final fallback: Default Seoul coordinates (37.5665, 126.9780)
        Log.w(TAG, "getCurrentLocation: All location queries failed. Using default Seoul coordinates.");
        return new double[]{37.5665, 126.9780};
    }

    private static double[] fetchRealTimeCoordinates(Context context, SharedPreferences prefs) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "fetchRealTimeCoordinates: Location permission not granted.");
            return null;
        }
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                Location bestLocation = null;
                String[] providers = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER};
                long now = System.currentTimeMillis();

                for (String provider : providers) {
                    if (lm.isProviderEnabled(provider)) {
                        Location loc = lm.getLastKnownLocation(provider);
                        if (loc != null) {
                            long locAge = now - loc.getTime();
                            if (locAge <= OS_LOCATION_MAX_AGE_MS) {
                                if (bestLocation == null || loc.getTime() > bestLocation.getTime()) {
                                    bestLocation = loc;
                                }
                            }
                        }
                    }
                }

                if (bestLocation != null) {
                    double lat = bestLocation.getLatitude();
                    double lon = bestLocation.getLongitude();
                    long timestamp = System.currentTimeMillis();

                    // Save to SharedPreferences with current timestamp
                    prefs.edit()
                            .putFloat("gps_last_latitude", (float) lat)
                            .putFloat("gps_last_longitude", (float) lon)
                            .putLong("gps_last_timestamp", timestamp)
                            .apply();

                    return new double[]{lat, lon};
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchRealTimeCoordinates: Error reading location", e);
        }
        return null;
    }
}
