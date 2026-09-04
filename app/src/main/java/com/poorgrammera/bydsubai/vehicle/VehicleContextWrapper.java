package com.poorgrammera.bydsubai.vehicle;
import com.poorgrammera.bydsubai.BuildConfig;
import com.poorgrammera.bydsubai.R;


import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * ContextWrapper that overrides all Android permission check methods to
 * unconditionally return PERMISSION_GRANTED.
 */
public class VehicleContextWrapper extends ContextWrapper {

    public VehicleContextWrapper(Context base) {
        super(base);
    }

    @Override
    public Context getApplicationContext() {
        // Keep OEM SDK permission checks on this wrapper instead of falling back
        // to the original application Context. This matches the working DiLink 5
        // implementation documented in REFERENCES.
        return this;
    }

    @Override
    public int checkPermission(@NonNull String permission, int pid, int uid) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkCallingOrSelfPermission(@NonNull String permission) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkCallingPermission(@NonNull String permission) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkSelfPermission(@NonNull String permission) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void enforceCallingOrSelfPermission(
            @NonNull String permission, @Nullable String message) {
        // no-op
    }

    @Override
    public void enforceCallingPermission(
            @NonNull String permission, @Nullable String message) {
        // no-op
    }

    @Override
    public void enforcePermission(
            @NonNull String permission, int pid, int uid, @Nullable String message) {
        // no-op
    }
}
