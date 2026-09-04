package com.poorgrammera.bydsubai.vehicle;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;

import dalvik.system.DexClassLoader;

/**
 * Dedicated external temperature provider for DiLink 5 platforms.
 * Ported and adapted from verified field implementations (BydAutoStart 2.3.4+).
 *
 * DiLink 5 can exhibit instability reading ambient temperature from the default AcDevice instance.
 * This provider dynamically resolves an isolated BYDAutoAcDevice instance from data-collection
 * or HVAC candidate packages and keeps it statically cached.
 */
public final class DiLink5TemperatureProvider {
    private static final String TAG = "DiLink5TempProvider";

    private static final String[] SDK_PACKAGES = {
            "com.byd.data.collect",
            "com.byd.hvac",
            "com.byd.carsettings",
            "com.byd.mycar",
            "com.byd.scenemode",
            "com.byd.autoservice"
    };

    private static Object acDevice;
    private static long connectedAtElapsed;
    private static String sdkSource = "none";

    private DiLink5TemperatureProvider() {
    }

    /**
     * Reads the external ambient temperature in Celsius.
     * Area code 4 corresponds to outside car temperature.
     * Valid range: -40°C to 50°C.
     */
    public static synchronized Integer read(Context context) throws Exception {
        if (acDevice == null) {
            connect(context.getApplicationContext());
        }

        try {
            Object result = acDevice.getClass().getMethod("getTemprature", int.class).invoke(acDevice, 4);
            if (result instanceof Number) {
                int temp = ((Number) result).intValue();
                if (temp >= -40 && temp <= 50) {
                    return temp;
                }
                Log.w(TAG, "Temperature read out of reasonable range: " + temp + "°C");
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
        return null;
    }

    public static synchronized String getSource() {
        return sdkSource;
    }

    public static synchronized long getConnectedAtElapsed() {
        return connectedAtElapsed;
    }

    private static void connect(Context context) throws Exception {
        Throwable lastError = null;
        for (String pkg : SDK_PACKAGES) {
            try {
                ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(pkg, 0);
                DexClassLoader loader = new DexClassLoader(
                        appInfo.sourceDir,
                        context.getCodeCacheDir().getAbsolutePath(),
                        appInfo.nativeLibraryDir,
                        context.getClassLoader()
                );
                Class<?> acClass = loader.loadClass("android.hardware.bydauto.ac.BYDAutoAcDevice");
                Object instance = acClass.getMethod("getInstance", Context.class)
                        .invoke(null, new VehicleContextWrapper(context));
                if (instance != null) {
                    acDevice = instance;
                    sdkSource = pkg + " (" + appInfo.sourceDir + ")";
                    connectedAtElapsed = SystemClock.elapsedRealtime();
                    Log.i(TAG, "Connected DiLink 5 temperature provider via " + sdkSource);
                    return;
                }
            } catch (Throwable t) {
                lastError = unwrap(t);
                Log.d(TAG, "Candidate " + pkg + " failed for temperature provider: " + t.getMessage());
            }
        }
        throw new IllegalStateException("DiLink 5 temperature SDK unavailable across all candidates", lastError);
    }

    private static Throwable unwrap(Throwable th) {
        while (th instanceof InvocationTargetException) {
            InvocationTargetException ite = (InvocationTargetException) th;
            if (ite.getCause() == null) break;
            th = ite.getCause();
        }
        return th;
    }
}
