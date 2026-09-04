package com.poorgrammera.bydsubai.vehicle;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.poorgrammera.bydsubai.data.ConfigData;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import dalvik.system.DexClassLoader;

/**
 * Factory and resolver for loading BYD SDK classes across DiLink 3 and DiLink 5 platforms.
 *
 * DiLink 3 exposes android.hardware.bydauto.* classes via the standard system framework/bootclasspath.
 * DiLink 5 packages them inside OEM system apps (e.g. com.byd.hvac, com.byd.carsettings),
 * requiring a dynamic DexClassLoader to instantiate device singletons.
 */
public final class VehicleClassLoaderFactory {
    private static final String TAG = "VehicleClassLoaderFactory";

    private static final String SETTING_DEVICE_CLASS =
            "android.hardware.bydauto.setting.BYDAutoSettingDevice";
    private static final String AC_DEVICE_CLASS =
            "android.hardware.bydauto.ac.BYDAutoAcDevice";
    private static final String BODYWORK_DEVICE_CLASS =
            "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice";

    private static final String[] REQUIRED_CORE_DEVICE_CLASSES = {
            SETTING_DEVICE_CLASS,
            AC_DEVICE_CLASS,
            BODYWORK_DEVICE_CLASS
    };

    public static final String[] DILINK5_SDK_PACKAGES = {
            "com.byd.data.collect",
            "com.byd.hvac",
            "com.byd.carsettings",
            "com.byd.mycar",
            "com.byd.scenemode",
            "com.byd.autoservice"
    };

    private static final AtomicReference<ClassLoader> cachedClassLoader =
            new AtomicReference<>(null);
    private static volatile String activeSdkSource = "unknown";
    private static volatile Boolean isDilink3Cached = null;

    private VehicleClassLoaderFactory() {
    }

    /**
     * Determines whether the current vehicle runs DiLink 3 or DiLink 5.
     */
    public static boolean isDilink3(Context context) {
        if (isDilink3Cached != null) {
            return isDilink3Cached;
        }

        // 1. Check user preference if explicitly saved
        SharedPreferences prefs = context.getSharedPreferences(
                ConfigData.PREF_NAME,
                Context.MODE_PRIVATE
        );
        if (prefs.contains(ConfigData.KEY_IS_DILINK3)) {
            boolean prefValue = prefs.getBoolean(ConfigData.KEY_IS_DILINK3, true);
            isDilink3Cached = prefValue;
            Log.i(TAG, "isDilink3 resolved from SharedPreferences: " + prefValue);
            return prefValue;
        }

        // 2. Check Build Fingerprint
        String fingerprint = Build.FINGERPRINT;
        if (fingerprint != null && fingerprint.contains("DiLink3")) {
            isDilink3Cached = true;
            Log.i(TAG, "isDilink3 resolved from Build.FINGERPRINT (DiLink3 detected): "
                    + fingerprint);
            return true;
        }

        // 3. Detect DiLink 5 by checking for OEM system apps
        PackageManager pm = context.getPackageManager();
        //boolean hasDiLink5App = false;
        for (String pkg : DILINK5_SDK_PACKAGES) {
            try {
                pm.getApplicationInfo(pkg, 0);
                isDilink3Cached = false;
                Log.i(TAG, "DiLink 5 OEM package detected. Treating platform as DiLink 5.");
                return false;
            } catch (PackageManager.NameNotFoundException ignored) {
                // Try the next candidate.
            }
        }
        
        // 4. Default to DiLink 3
        isDilink3Cached = true;
        Log.i(TAG, "No DiLink 5 package detected. Defaulting platform to DiLink 3.");
        return true;
    }

    /**
     * Obtains the appropriate ClassLoader for the current environment.
     *
     * A loader is cached only after Setting, AC and Bodywork device singletons can all be
     * instantiated through it. This mirrors the working DiLink 5 reference implementation and
     * prevents a package that happens to contain only one BYD class from poisoning the cache.
     */
    public static synchronized ClassLoader getClassLoader(Context context) {
        ClassLoader existing = cachedClassLoader.get();
        if (existing != null) {
            return existing;
        }

        Context appContext = context.getApplicationContext();
        boolean dilink3 = isDilink3(appContext);
        Throwable lastFailure = null;
        ClassLoader defaultLoader = appContext.getClassLoader();

        // DiLink 3 normally exposes the SDK through the system/app ClassLoader.
        if (dilink3) {
            try {
                validateCoreDevices(appContext, defaultLoader);
                return cacheResolvedLoader(
                        defaultLoader,
                        "DiLink3 (System/App ClassLoader)"
                );
            } catch (Throwable t) {
                lastFailure = unwrap(t);
                logCandidateFailure("DiLink 3 system ClassLoader", lastFailure);
                Log.w(TAG, "DiLink 3 loader failed core-device validation. "
                        + "Attempting DiLink 5 package fallback.");
            }
        }

        // DiLink 5: test each OEM APK as a complete SDK source before caching it.
        PackageManager pm = appContext.getPackageManager();
        for (String pkg : DILINK5_SDK_PACKAGES) {
            ApplicationInfo appInfo;
            try {
                appInfo = pm.getApplicationInfo(pkg, 0);
            } catch (Throwable t) {
                lastFailure = unwrap(t);
                logCandidateFailure("DiLink 5 package " + pkg, lastFailure);
                continue;
            }

            // Preferred path: preserves split/multi-dex APK handling and OEM dependencies.
            try {
                Context packageContext = appContext.createPackageContext(
                        pkg,
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY
                );
                ClassLoader packageLoader = packageContext.getClassLoader();
                validateCoreDevices(appContext, packageLoader);
                return cacheResolvedLoader(
                        packageLoader,
                        "DiLink5 (" + pkg + " @ " + appInfo.sourceDir + ")"
                );
            } catch (Throwable t) {
                lastFailure = unwrap(t);
                logCandidateFailure("DiLink 5 package " + pkg, lastFailure);
            }

            // Compatibility fallback for firmware that blocks/limits package Context loading.
            try {
                DexClassLoader dexLoader = new DexClassLoader(
                        appInfo.sourceDir,
                        appContext.getCodeCacheDir().getAbsolutePath(),
                        appInfo.nativeLibraryDir,
                        defaultLoader
                );
                validateCoreDevices(appContext, dexLoader);
                return cacheResolvedLoader(
                        dexLoader,
                        "DiLink5 (" + pkg + ", dex @ " + appInfo.sourceDir + ")"
                );
            } catch (Throwable t) {
                lastFailure = unwrap(t);
                logCandidateFailure(pkg + " direct APK loader", lastFailure);
            }
        }

        // If platform detection was wrong, still allow the normal loader to prove itself.
        if (!dilink3) {
            try {
                validateCoreDevices(appContext, defaultLoader);
                return cacheResolvedLoader(
                        defaultLoader,
                        "Fallback (Context ClassLoader)"
                );
            } catch (Throwable t) {
                lastFailure = unwrap(t);
                logCandidateFailure("Fallback context ClassLoader", lastFailure);
            }
        }

        activeSdkSource = "unavailable";
        String detail = describeThrowable(lastFailure);
        throw new IllegalStateException(
                "BYD SDK unavailable after validating all loader candidates"
                        + (detail.isEmpty() ? "" : ": " + detail),
                lastFailure
        );
    }

    private static ClassLoader cacheResolvedLoader(ClassLoader loader, String source) {
        cachedClassLoader.set(loader);
        activeSdkSource = source;
        Log.i(TAG, "Successfully validated BYD SDK via: " + source);
        return loader;
    }

    private static void validateCoreDevices(Context context, ClassLoader loader) throws Exception {
        for (String className : REQUIRED_CORE_DEVICE_CLASSES) {
            Object device = instantiateDevice(context, loader, className);
            if (device == null) {
                throw new IllegalStateException(className + ".getInstance(Context) returned null");
            }
        }
    }

    /**
     * Loads a class by fully qualified name using the resolved vehicle ClassLoader.
     */
    public static Class<?> loadClass(Context context, String className)
            throws ClassNotFoundException {
        return getClassLoader(context).loadClass(className);
    }

    /**
     * Loads a BYD device class and creates an instance via {@code getInstance(Context)},
     * passing a {@link VehicleContextWrapper} to satisfy OEM permission checks.
     */
    public static Object loadDevice(Context context, String className) throws Exception {
        Context appContext = context.getApplicationContext();
        return instantiateDevice(appContext, getClassLoader(appContext), className);
    }

    private static Object instantiateDevice(
            Context context,
            ClassLoader loader,
            String className
    ) throws Exception {
        Class<?> clazz = loader.loadClass(className);
        Method getInstanceMethod = clazz.getMethod("getInstance", Context.class);
        try {
            return getInstanceMethod.invoke(null, new VehicleContextWrapper(context));
        } catch (InvocationTargetException e) {
            Throwable cause = unwrap(e);
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new IllegalStateException(
                    "Vehicle SDK getInstance failed for " + className + ": "
                            + describeThrowable(cause),
                    cause
            );
        }
    }

    public static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof InvocationTargetException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public static String describeThrowable(Throwable throwable) {
        if (throwable == null) {
            return "";
        }

        Throwable root = unwrap(throwable);
        String message = root.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return root.getClass().getSimpleName();
        }
        return root.getClass().getSimpleName() + ": " + message;
    }

    private static void logCandidateFailure(String candidate, Throwable failure) {
        Log.w(TAG, candidate + " not usable: " + describeThrowable(failure), failure);
    }

    public static String getActiveSdkSource() {
        return activeSdkSource;
    }

    /**
     * Resets cached state for runtime switching or re-initialization.
     */
    public static synchronized void resetCache() {
        cachedClassLoader.set(null);
        isDilink3Cached = null;
        activeSdkSource = "unknown";
    }
}