package android.hardware.bydauto.adas;

import android.hardware.IBYDAutoEvent;
import android.hardware.IBYDAutoListener;
import android.hardware.bydauto.BYDAutoEventValue;

public abstract class AbsBYDAutoADASListener implements IBYDAutoListener {
    @Override
    public void onError(int errCode, String errMessage) {}

    @Override
    public void onDataChanged(IBYDAutoEvent event) {}

    @Override
    public void onDataEventChanged(int eventType, BYDAutoEventValue eventValue) {}
}