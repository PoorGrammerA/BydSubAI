package android.hardware;

import android.hardware.bydauto.BYDAutoEventValue;

public interface IBYDAutoListener {
    void onDataChanged(IBYDAutoEvent event);
    void onDataEventChanged(int eventType, BYDAutoEventValue eventValue);
    void onError(int errCode, String errMessage);
}