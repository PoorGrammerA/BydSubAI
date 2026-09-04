package com.poorgrammera.bydsubai.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.EditText;
import android.widget.TextView;
import com.poorgrammera.bydsubai.R;
import com.poorgrammera.bydsubai.integration.GpsTracker;
import com.poorgrammera.bydsubai.tool.ExternalToolRegistry;
import com.poorgrammera.bydsubai.tool.ExternalToolResults;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

public class MapControllerBinder {
    private final VehicleControlActivity activity;

    private TextView tvNaverMapStatus;
    private TextView tvTmapAutoStatus;
    private TextView tvCurrentCoords;
    private EditText etMapDestination;

    public MapControllerBinder(VehicleControlActivity activity) {
        this.activity = activity;
    }

    public void bind() {
        tvNaverMapStatus = activity.findViewById(R.id.tv_naver_map_status);
        tvTmapAutoStatus = activity.findViewById(R.id.tv_tmap_auto_status);
        tvCurrentCoords = activity.findViewById(R.id.tv_current_coords);
        etMapDestination = activity.findViewById(R.id.et_map_destination);

        checkMapAppsInstallation();

        activity.findViewById(R.id.btn_fetch_gps).setOnClickListener(v -> updateCurrentLocationUI());
        activity.findViewById(R.id.btn_show_google_maps).setOnClickListener(v -> {
            double[] coords = GpsTracker.getCurrentLocation(activity);
            if (coords != null) {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + coords[0] + "," + coords[1])));
            }
        });

        activity.findViewById(R.id.btn_launch_navigation).setOnClickListener(v -> {
            String dest = etMapDestination.getText().toString().trim();
            if (dest.isEmpty()) return;
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("query", dest);
            double[] current = GpsTracker.getCurrentLocation(activity);
            if (current != null) {
                arguments.put("latitude", current[0]);
                arguments.put("longitude", current[1]);
            }
            new ExternalToolRegistry(activity).executeFirstProviderTool(
                    "search_places", arguments, response -> {
                        Map<String, Object> place = ExternalToolResults.firstItem(response, "places");
                        Double latitude = ExternalToolResults.number(place, "latitude");
                        Double longitude = ExternalToolResults.number(place, "longitude");
                        if (latitude != null && longitude != null) {
                            Object name = place.get("place_name");
                            activity.vehicleController.launchNavigation(
                                    name == null ? dest : name.toString(), latitude, longitude);
                        } else {
                            android.widget.Toast.makeText(activity,
                                    "No approved place-search Tool returned a destination.",
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    private void checkMapAppsInstallation() {
        PackageManager pm = activity.getPackageManager();
        boolean naver = isPkg(pm, "com.nhn.android.nmap");
        boolean tmap = isPkg(pm, "com.tmap.auto.byd") || isPkg(pm, "com.skt.tmap.auto");
        tvNaverMapStatus.setText(naver ? "Installed" : "Not Installed");
        tvTmapAutoStatus.setText(tmap ? "Installed" : "Not Installed");
    }

    private boolean isPkg(PackageManager pm, String pkg) {
        try {
            pm.getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void updateCurrentLocationUI() {
        double[] c = GpsTracker.getCurrentLocation(activity);
        tvCurrentCoords.setText(c != null ? String.format(Locale.US, "%.6f, %.6f", c[0], c[1]) : "Unknown");
    }
}
