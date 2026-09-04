package com.poorgrammera.bydsubai.ui;

import android.graphics.Color;
import android.widget.TextView;
import com.poorgrammera.bydsubai.R;
import java.util.Map;

public class TpmsControllerBinder {
    private final VehicleControlActivity activity;

    public TpmsControllerBinder(VehicleControlActivity activity) {
        this.activity = activity;
    }

    public void bind() {
        activity.findViewById(R.id.btn_refresh_tyre).setOnClickListener(v -> refreshTyrePressures());
    }

    public void refreshTyrePressures() {
        if (activity.vehicleController == null) return;
        
        Map<String, Integer> pressures = activity.vehicleController.getTyrePressures();
        Map<String, Integer> temps = activity.vehicleController.getTyreTemperatures();
        
        TextView tvFL = activity.findViewById(R.id.tv_tyre_fl);
        TextView tvFR = activity.findViewById(R.id.tv_tyre_fr);
        TextView tvRL = activity.findViewById(R.id.tv_tyre_rl);
        TextView tvRR = activity.findViewById(R.id.tv_tyre_rr);

        updateTyreTextView(tvFL, pressures.get("FL"));
        updateTyreTextView(tvFR, pressures.get("FR"));
        updateTyreTextView(tvRL, pressures.get("RL"));
        updateTyreTextView(tvRR, pressures.get("RR"));

        TextView tvFLTemp = activity.findViewById(R.id.tv_tyre_fl_temp);
        TextView tvFRTemp = activity.findViewById(R.id.tv_tyre_fr_temp);
        TextView tvRLTemp = activity.findViewById(R.id.tv_tyre_rl_temp);
        TextView tvRRTemp = activity.findViewById(R.id.tv_tyre_rr_temp);

        updateTyreTempTextView(tvFLTemp, temps.get("FL"));
        updateTyreTempTextView(tvFRTemp, temps.get("FR"));
        updateTyreTempTextView(tvRLTemp, temps.get("RL"));
        updateTyreTempTextView(tvRRTemp, temps.get("RR"));
    }

    private void updateTyreTextView(TextView tv, Integer val) {
        if (tv == null) return;
        if (val == null || val == -1) {
            tv.setText("-- kPa");
            tv.setTextColor(Color.parseColor("#B0BEC5"));
        } else {
            tv.setText(val + " kPa");
            if (val < 200 || val > 300) {
                tv.setTextColor(Color.parseColor("#FF3D00"));
            } else {
                tv.setTextColor(Color.parseColor("#00E676"));
            }
        }
    }

    private void updateTyreTempTextView(TextView tv, Integer val) {
        if (tv == null) return;
        if (val == null || val == -1) {
            tv.setText("Temp: -- °C");
            tv.setTextColor(Color.parseColor("#B0BEC5"));
        } else {
            tv.setText("Temp: " + val + " °C");
            if (val < -10 || val > 75) {
                tv.setTextColor(Color.parseColor("#FF3D00"));
            } else {
                tv.setTextColor(Color.parseColor("#B0BEC5"));
            }
        }
    }
}
