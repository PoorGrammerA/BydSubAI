package com.poorgrammera.bydsubai.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores API credentials encrypted with a non-exportable Android Keystore AES key. */
public final class SecureCredentialStore {
    private static final String TAG = "SecureCredentialStore";
    private static final String PREF_NAME = "BydSubAiCredentials";
    private static final String KEY_ALIAS = "byd_subai_credential_key";
    private static final String IV_SUFFIX = "_iv";
    private static final String DATA_SUFFIX = "_data";
    private static final List<String> CREDENTIAL_KEYS = Arrays.asList(
            ConfigData.KEY_GEMINI_API_KEY
    );

    private SecureCredentialStore() {}

    public static boolean isCredentialKey(String key) {
        return CREDENTIAL_KEYS.contains(key);
    }

    /** Removes credentials retired from the base app after integrations moved out or were removed. */
    public static void removeRetiredCredentials(Context context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .remove("pref_naver_client_id" + IV_SUFFIX)
                .remove("pref_naver_client_id" + DATA_SUFFIX)
                .remove("pref_naver_client_secret" + IV_SUFFIX)
                .remove("pref_naver_client_secret" + DATA_SUFFIX)
                .remove("pref_kakao_api_key" + IV_SUFFIX)
                .remove("pref_kakao_api_key" + DATA_SUFFIX)
                .apply();
        context.getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE).edit()
                .remove("pref_naver_client_id")
                .remove("pref_naver_client_secret")
                .remove("pref_kakao_api_key")
                .apply();
    }

    public static String getString(Context context, String key, String defaultValue) {
        migrateLegacyCredential(context, key);
        SharedPreferences encryptedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String iv = encryptedPrefs.getString(key + IV_SUFFIX, null);
        String encrypted = encryptedPrefs.getString(key + DATA_SUFFIX, null);
        if (iv == null || encrypted == null) return defaultValue;
        try {
            return decrypt(iv, encrypted);
        } catch (Exception e) {
            Log.e(TAG, "Could not decrypt credential: " + key, e);
            return defaultValue;
        }
    }

    public static void putString(Context context, String key, String value) {
        if (!isCredentialKey(key)) throw new IllegalArgumentException("Not a credential key: " + key);
        try {
            EncryptedValue encrypted = encrypt(value == null ? "" : value);
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                    .putString(key + IV_SUFFIX, encrypted.iv)
                    .putString(key + DATA_SUFFIX, encrypted.data)
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "Could not encrypt credential: " + key, e);
        }
    }

    private static void migrateLegacyCredential(Context context, String key) {
        if (!isCredentialKey(key)) return;
        SharedPreferences legacy = context.getSharedPreferences(ConfigData.PREF_NAME, Context.MODE_PRIVATE);
        if (!legacy.contains(key)) return;
        SharedPreferences encryptedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        try {
            if (!encryptedPrefs.contains(key + DATA_SUFFIX)) {
                EncryptedValue encrypted = encrypt(legacy.getString(key, ""));
                if (!encryptedPrefs.edit()
                        .putString(key + IV_SUFFIX, encrypted.iv)
                        .putString(key + DATA_SUFFIX, encrypted.data)
                        .commit()) return;
            }
            legacy.edit().remove(key).apply();
        } catch (Exception e) {
            Log.e(TAG, "Legacy credential migration failed: " + key, e);
        }
    }

    private static EncryptedValue encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        // Some Android Keystore implementations reject caller-provided IVs for encryption.
        // Let the Keystore generate the unique GCM IV, then persist it with the ciphertext.
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] iv = cipher.getIV();
        if (iv == null || iv.length == 0) {
            throw new IllegalStateException("Android Keystore did not provide a GCM IV");
        }
        return new EncryptedValue(
                Base64.encodeToString(iv, Base64.NO_WRAP),
                Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP)
        );
    }

    private static String decrypt(String ivText, String encryptedText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(
                128, Base64.decode(ivText, Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(encryptedText, Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            generator.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
    }

    private static final class EncryptedValue {
        final String iv;
        final String data;
        EncryptedValue(String iv, String data) { this.iv = iv; this.data = data; }
    }
}
