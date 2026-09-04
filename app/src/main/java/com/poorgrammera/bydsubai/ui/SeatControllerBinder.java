package com.poorgrammera.bydsubai.ui;

import com.poorgrammera.bydsubai.R;

public class SeatControllerBinder {
    private static final String TAG = "SeatControllerBinder";

    private final VehicleControlActivity activity;

    public SeatControllerBinder(VehicleControlActivity activity) {
        this.activity = activity;
    }

    public void bind() {
        // 4. Seats Control
        activity.findViewById(R.id.btn_drv_heat_0).setOnClickListener(v -> setSeatHeating(1, 0));
        activity.findViewById(R.id.btn_drv_heat_1).setOnClickListener(v -> setSeatHeating(1, 1));
        activity.findViewById(R.id.btn_drv_heat_2).setOnClickListener(v -> setSeatHeating(1, 2));
        activity.findViewById(R.id.btn_drv_vent_0).setOnClickListener(v -> setSeatVentilation(1, 0));
        activity.findViewById(R.id.btn_drv_vent_1).setOnClickListener(v -> setSeatVentilation(1, 1));
        activity.findViewById(R.id.btn_drv_vent_2).setOnClickListener(v -> setSeatVentilation(1, 2));

        activity.findViewById(R.id.btn_psg_heat_0).setOnClickListener(v -> setSeatHeating(2, 0));
        activity.findViewById(R.id.btn_psg_heat_1).setOnClickListener(v -> setSeatHeating(2, 1));
        activity.findViewById(R.id.btn_psg_heat_2).setOnClickListener(v -> setSeatHeating(2, 2));
        activity.findViewById(R.id.btn_psg_vent_0).setOnClickListener(v -> setSeatVentilation(2, 0));
        activity.findViewById(R.id.btn_psg_vent_1).setOnClickListener(v -> setSeatVentilation(2, 1));
        activity.findViewById(R.id.btn_psg_vent_2).setOnClickListener(v -> setSeatVentilation(2, 2));

        activity.findViewById(R.id.btn_steering_heat_off).setOnClickListener(v -> setSteeringWheelHeating(1));
        activity.findViewById(R.id.btn_steering_heat_on).setOnClickListener(v -> setSteeringWheelHeating(2));
    }

    private void setSteeringWheelHeating(int state) {
        activity.vehicleController.controlSteeringWheelHeating(state == 2);
    }

    private void setSeatHeating(int position, int level) {
        activity.vehicleController.controlSeat(
                position == 1 ? "driver" : "passenger", level, null);
    }

    private void setSeatVentilation(int position, int level) {
        activity.vehicleController.controlSeat(
                position == 1 ? "driver" : "passenger", null, level);
    }
}
