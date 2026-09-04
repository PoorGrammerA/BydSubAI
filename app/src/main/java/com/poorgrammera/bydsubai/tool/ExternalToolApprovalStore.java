package com.poorgrammera.bydsubai.tool;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import org.json.JSONException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Comparator;

/** Persists user approval and the last validated manifest for offline session startup. */
public final class ExternalToolApprovalStore {
    private static final String PREFS = "subai_external_tool_approvals";
    private static final String KEY_APPROVED = "approved_identities";
    private static final String KEY_DISMISSED = "dismissed_identities";
    private static final String KEY_DISABLED = "disabled_functions";
    private static final String DESCRIPTOR_PREFIX = "descriptor_";

    private final Context context;
    private final SharedPreferences preferences;

    public ExternalToolApprovalStore(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isApproved(ExternalToolDescriptor descriptor) {
        return approvedIdentities().contains(descriptor.identity());
    }

    public void approve(ExternalToolDescriptor descriptor) throws JSONException {
        Set<String> approved = approvedIdentities();
        approved.add(descriptor.identity());
        Set<String> dismissed = dismissedIdentities();
        dismissed.remove(descriptor.identity());
        preferences.edit()
                .putStringSet(KEY_APPROVED, approved)
                .putStringSet(KEY_DISMISSED, dismissed)
                .putString(descriptorKey(descriptor.identity()), descriptor.toStoredJson())
                .apply();
    }

    public void updateSnapshotIfApproved(ExternalToolDescriptor descriptor) throws JSONException {
        if (!isApproved(descriptor)) return;
        preferences.edit()
                .putString(descriptorKey(descriptor.identity()), descriptor.toStoredJson())
                .apply();
    }

    public void revoke(ExternalToolDescriptor descriptor) {
        Set<String> approved = approvedIdentities();
        approved.remove(descriptor.identity());
        Set<String> dismissed = dismissedIdentities();
        dismissed.add(descriptor.identity());
        preferences.edit()
                .putStringSet(KEY_APPROVED, approved)
                .putStringSet(KEY_DISMISSED, dismissed)
                .remove(descriptorKey(descriptor.identity()))
                .apply();
    }

    public boolean isDismissed(ExternalToolDescriptor descriptor) {
        return dismissedIdentities().contains(descriptor.identity());
    }

    public void dismiss(ExternalToolDescriptor descriptor) {
        Set<String> dismissed = dismissedIdentities();
        dismissed.add(descriptor.identity());
        preferences.edit().putStringSet(KEY_DISMISSED, dismissed).apply();
    }

    public boolean isFunctionEnabled(String providerIdentity, String localName) {
        return !new HashSet<>(preferences.getStringSet(KEY_DISABLED, Collections.emptySet()))
                .contains(functionKey(providerIdentity, localName));
    }

    public void setFunctionEnabled(String providerIdentity, String localName, boolean enabled) {
        Set<String> disabled = new HashSet<>(preferences.getStringSet(KEY_DISABLED, Collections.emptySet()));
        String key = functionKey(providerIdentity, localName);
        if (enabled) disabled.remove(key); else disabled.add(key);
        preferences.edit().putStringSet(KEY_DISABLED, disabled).apply();
    }

    public List<ExternalToolDescriptor> loadInstalledApproved() {
        List<ExternalToolDescriptor> result = new ArrayList<>();
        for (String identity : approvedIdentities()) {
            String stored = preferences.getString(descriptorKey(identity), null);
            if (stored == null) continue;
            try {
                ExternalToolDescriptor descriptor = ExternalToolDescriptor.fromStoredJson(stored);
                String currentCertificate = signingCertificateSha256(descriptor.component.getPackageName());
                if (descriptor.certificateSha256.equals(currentCertificate)) result.add(descriptor);
            } catch (Exception ignored) {
                // Invalid, removed, or re-signed providers remain inactive until the user approves again.
            }
        }
        result.sort(Comparator.comparing(ExternalToolDescriptor::providerId)
                .thenComparing(value -> value.component.flattenToString()));
        return result;
    }

    public String signingCertificateSha256(String packageName) throws Exception {
        PackageManager pm = context.getPackageManager();
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            signatures = info.signingInfo == null ? null
                    : info.signingInfo.getApkContentsSigners();
        } else {
            @SuppressWarnings("deprecation")
            PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            @SuppressWarnings("deprecation")
            Signature[] legacy = info.signatures;
            signatures = legacy;
        }
        if (signatures == null || signatures.length == 0) {
            throw new PackageManager.NameNotFoundException("No signing certificate for " + packageName);
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(signatures[0].toByteArray());
        StringBuilder value = new StringBuilder(hash.length * 2);
        for (byte b : hash) value.append(String.format("%02X", b));
        return value.toString();
    }

    private Set<String> approvedIdentities() {
        return new HashSet<>(preferences.getStringSet(KEY_APPROVED, Collections.emptySet()));
    }

    private Set<String> dismissedIdentities() {
        return new HashSet<>(preferences.getStringSet(KEY_DISMISSED, Collections.emptySet()));
    }

    private static String descriptorKey(String identity) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(hash.length * 2);
            for (byte b : hash) value.append(String.format("%02x", b));
            return DESCRIPTOR_PREFIX + value;
        } catch (Exception impossible) {
            return DESCRIPTOR_PREFIX + Integer.toHexString(identity.hashCode());
        }
    }

    private static String functionKey(String providerIdentity, String localName) {
        return providerIdentity + "|" + localName;
    }
}
