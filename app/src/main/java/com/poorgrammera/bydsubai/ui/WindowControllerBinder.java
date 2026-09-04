package com.poorgrammera.bydsubai.ui;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Button;
import com.poorgrammera.bydsubai.R;

public class WindowControllerBinder {
    private static final String TAG = "WindowControllerBinder";

    private final VehicleControlActivity activity;

    public WindowControllerBinder(VehicleControlActivity activity) {
        this.activity = activity;
    }

    public void bind() {
        // 2. Trunk Control
        Button btnTrunkOpen = activity.findViewById(R.id.btn_trunk_open);
        Button btnTrunkStop = activity.findViewById(R.id.btn_trunk_stop);
        Button btnTrunkClose = activity.findViewById(R.id.btn_trunk_close);

        btnTrunkOpen.setOnClickListener(v -> openTailgate());
        btnTrunkStop.setOnClickListener(v -> stopTailgate());
        btnTrunkClose.setOnClickListener(v -> closeTailgate());

        // 3. Windows & Sunroof Control
        Button btnAllWinOpen = activity.findViewById(R.id.btn_all_win_open);
        Button btnAllWinStop = activity.findViewById(R.id.btn_all_win_stop);
        Button btnAllWinClose = activity.findViewById(R.id.btn_all_win_close);
        Button btnAllWinPct10 = activity.findViewById(R.id.btn_all_win_pct10);
        Button btnAllWinPct50 = activity.findViewById(R.id.btn_all_win_pct50);

        btnAllWinOpen.setOnClickListener(v -> setAllWindowsCommand(1));
        btnAllWinClose.setOnClickListener(v -> setAllWindowsCommand(2));
        btnAllWinStop.setOnClickListener(v -> setAllWindowsCommand(3));
        btnAllWinPct10.setOnClickListener(v -> setAllWindowsPercentage(10));
        btnAllWinPct50.setOnClickListener(v -> setAllWindowsPercentage(50));

        // Window Area Binding
        String[] suffixes = {"lf", "rf", "lr", "rr", "sunroof", "sunshade"};
        int[] windowAreas = {1, 2, 3, 4, 5, 6};
        for (int i = 0; i < suffixes.length; i++) {
            final int area = windowAreas[i];
            String s = suffixes[i];
            activity.findViewById(activity.getResources().getIdentifier("btn_win_" + s + "_open", "id", activity.getPackageName())).setOnClickListener(v -> setWindowAreaCommand(area, 1));
            activity.findViewById(activity.getResources().getIdentifier("btn_win_" + s + "_stop", "id", activity.getPackageName())).setOnClickListener(v -> setWindowAreaCommand(area, 3));
            activity.findViewById(activity.getResources().getIdentifier("btn_win_" + s + "_close", "id", activity.getPackageName())).setOnClickListener(v -> setWindowAreaCommand(area, 2));
            activity.findViewById(activity.getResources().getIdentifier("btn_win_" + s + "_pct10", "id", activity.getPackageName())).setOnClickListener(v -> setWindowPercentage(area, 10));
            activity.findViewById(activity.getResources().getIdentifier("btn_win_" + s + "_pct50", "id", activity.getPackageName())).setOnClickListener(v -> setWindowPercentage(area, 50));
        }

        // Window Group Binding (front, rear, left, right)
        String[] groupSuffixes = {"front", "rear", "left", "right"};
        for (String g : groupSuffixes) {
            activity.findViewById(activity.getResources().getIdentifier("btn_win_" + g + "_open", "id", activity.getPackageName())).setOnClickListener(v -> setWindowGroupCommand(g, 1));
            activity.findViewById(activity.getResources().getIdentifier("btn_win_" + g + "_stop", "id", activity.getPackageName())).setOnClickListener(v -> setWindowGroupCommand(g, 3));
            activity.findViewById(activity.getResources().getIdentifier("btn_win_" + g + "_close", "id", activity.getPackageName())).setOnClickListener(v -> setWindowGroupCommand(g, 2));
            activity.findViewById(activity.getResources().getIdentifier("btn_win_" + g + "_pct10", "id", activity.getPackageName())).setOnClickListener(v -> setWindowGroupPercentage(g, 10));
            activity.findViewById(activity.getResources().getIdentifier("btn_win_" + g + "_pct50", "id", activity.getPackageName())).setOnClickListener(v -> setWindowGroupPercentage(g, 50));
        }
    }

    private void openTailgate() {
        activity.vehicleController.controlTrunk("open");
    }

    private void stopTailgate() {
        activity.vehicleController.controlTrunk("stop");
    }

    private void closeTailgate() {
        activity.vehicleController.controlTrunk("close");
    }

    private void setAllWindowsCommand(int command) {
        activity.vehicleController.controlWindow("all", actionForCommand(command), null);
    }

    private void setAllWindowsPercentage(int percent) {
        activity.vehicleController.controlWindow("all", "custom", percent);
    }

    private String actionForCommand(int command) {
        switch (command) {
            case 1:
                return "open";
            case 2:
                return "close";
            case 3:
                return "stop";
            default:
                throw new IllegalArgumentException("Unsupported window command: " + command);
        }
    }

    private void setWindowAreaCommand(int area, int command) {
        activity.vehicleController.controlWindow(areaName(area), actionForCommand(command), null);
    }

    private void setWindowPercentage(int area, int percent) {
        activity.vehicleController.controlWindow(areaName(area), "custom", percent);
    }

    private void setWindowGroupCommand(String group, int command) {
        activity.vehicleController.controlWindow(group, actionForCommand(command), null);
    }

    private void setWindowGroupPercentage(String group, int percent) {
        activity.vehicleController.controlWindow(group, "custom", percent);
    }

    private String areaName(int area) {
        switch (area) {
            case 1: return "driver_front";
            case 2: return "passenger_front";
            case 3: return "driver_rear";
            case 4: return "passenger_rear";
            case 5: return "sunroof";
            case 6: return "sunshade";
            default: throw new IllegalArgumentException("Unsupported window area: " + area);
        }
    }
}
