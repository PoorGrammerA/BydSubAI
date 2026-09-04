package com.poorgrammera.bydsubai.adb;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import dadb.AdbKeyPair;
import dadb.Dadb;
import dadb.AdbShellResponse;

import java.io.File;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class AdbShellExecutor {

    private static final String TAG = "AdbShellExecutor";
    private static final int ADB_PORT = 5555;
    private static final String ADB_KEY_FILE = "adbkey";
    private static final String ADB_PUB_KEY_FILE = "adbkey.pub";
    
    private static volatile AdbKeyPair cachedKeyPair = null;
    private static final Object keyPairLock = new Object();
    
    private static volatile Dadb sharedDadb = null;
    private static final Object sharedDadbLock = new Object();
    
    private static final AtomicBoolean isAuthPending = new AtomicBoolean(false);
    private static final AtomicBoolean wasAuthGranted = new AtomicBoolean(false);
    private static final AtomicBoolean pollingStarted = new AtomicBoolean(false);
    private static volatile AdbAuthCallback authCallback = null;
    
    private static final ExecutorService pollingExecutor = Executors.newSingleThreadExecutor();
    private static final AtomicLong scriptSeq = new AtomicLong(0);

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface AdbAuthCallback {
        void onAuthPending();
        void onAuthGranted();
        void onAuthFailed(String error);
    }

    public interface ShellCallback {
        void onSuccess(String output);
        void onError(String error);
    }

    public static class ShellResult {
        public final int exitCode;
        public final String output;

        public ShellResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    public AdbShellExecutor(Context context) {
        this.context = context.getApplicationContext();
    }

    public static void setAuthCallback(AdbAuthCallback callback) {
        authCallback = callback;
    }

    public static boolean checkAndClearAuthGranted() {
        return wasAuthGranted.getAndSet(false);
    }

    public static boolean isAuthPending() {
        return isAuthPending.get();
    }

    public static boolean isConnected() {
        synchronized (sharedDadbLock) {
            return sharedDadb != null;
        }
    }

    public void execute(final String command, final ShellCallback callback) {
        try {
            executor.execute(() -> {
                try {
                    Log.d(TAG, "Executing async: " + command);
                    Dadb dadb = getOrCreateConnection();
                    AdbShellResponse result = dadb.shell(command);

                    if (result.getExitCode() == 0) {
                        callback.onSuccess(result.getAllOutput());
                    } else {
                        callback.onError("Exit code " + result.getExitCode() + ": " + result.getAllOutput());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Command execution failed: " + command, e);
                    callback.onError("Execution failed: " + e.getMessage());
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            Log.w(TAG, "execute() rejected (executor shutting down): " + command);
        }
    }

    public ShellResult executeSync(String command) {
        Log.d(TAG, "Executing sync: " + command);
        try {
            Dadb dadb = getOrCreateConnection();
            AdbShellResponse result = dadb.shell(command);
            return new ShellResult(result.getExitCode(), result.getAllOutput());
        } catch (Exception e) {
            Log.e(TAG, "Sync execution failed: " + command, e);
            return new ShellResult(-1, e.getMessage());
        }
    }

    public void executeScript(final String scriptBody, final ShellCallback callback) {
        executor.execute(() -> {
            String nonce = System.nanoTime() + "_" + scriptSeq.incrementAndGet();
            String scriptPath = "/data/local/tmp/.adb_script_" + nonce + ".sh";
            String eofMarker = "__ADB_SCRIPT_EOF_" + nonce + "__";
            try {
                Log.d(TAG, "Executing script via " + scriptPath + " (" + scriptBody.length() + " bytes)");
                Dadb dadb = getOrCreateConnection();

                String writeCmd = "cat > " + scriptPath + " <<'" + eofMarker + "'\n" +
                        scriptBody +
                        "\n" + eofMarker;
                AdbShellResponse writeResult = dadb.shell(writeCmd);
                if (writeResult.getExitCode() != 0) {
                    try { dadb.shell("rm -f " + scriptPath + " 2>/dev/null"); } catch (Exception ignored) {}
                    callback.onError("script-write failed: " + writeResult.getAllOutput());
                    return;
                }

                AdbShellResponse runResult = dadb.shell(
                    "trap 'rm -f " + scriptPath + "' EXIT; sh " + scriptPath
                );

                if (runResult.getExitCode() == 0) {
                    callback.onSuccess(runResult.getAllOutput());
                } else {
                    callback.onError("Exit code " + runResult.getExitCode() + ": " + runResult.getAllOutput());
                }
            } catch (Exception e) {
                Log.e(TAG, "Script execution failed", e);
                callback.onError("Execution failed: " + e.getMessage());
                try {
                    Dadb dadb = getOrCreateConnection();
                    dadb.shell("rm -f " + scriptPath + " 2>/dev/null");
                } catch (Exception ignored) {}
            }
        });
    }

    public Integer checkProcessRunning(String processName) {
        try {
            Dadb dadb = getOrCreateConnection();
            AdbShellResponse result = dadb.shell("pgrep -f '" + processName + "'");
            if (result.getExitCode() == 0 && !result.getAllOutput().trim().isEmpty()) {
                String firstLine = result.getAllOutput().trim().split("\n")[0];
                return Integer.parseInt(firstLine.trim());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to check process: " + processName, e);
        }
        return null;
    }

    public boolean killProcess(String processName) {
        try {
            Dadb dadb = getOrCreateConnection();
            String cmd = "MY_PID=$$; ps -A -o PID,ARGS | grep -F '" + processName + "' | grep -v grep " +
                "| awk '{print $1}' | while read pid; do " +
                "if [ \"$pid\" != \"$MY_PID\" ]; then kill -9 $pid 2>/dev/null; fi; done; " +
                "echo done";
            AdbShellResponse result = dadb.shell(cmd);
            return result.getExitCode() == 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to kill process: " + processName, e);
            return false;
        }
    }

    public Dadb getOrCreateConnection() throws Exception {
        synchronized (sharedDadbLock) {
            Dadb dadb = sharedDadb;
            if (dadb != null) {
                try {
                    AdbShellResponse result = dadb.shell("echo ok");
                    if (result.getExitCode() == 0) {
                        if (isAuthPending.getAndSet(false)) {
                            wasAuthGranted.set(true);
                            Log.i(TAG, "ADB auth granted! Connection established.");
                            if (authCallback != null) {
                                new Handler(Looper.getMainLooper()).post(() -> authCallback.onAuthGranted());
                            }
                        }
                        return dadb;
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Existing ADB connection dead, reconnecting...");
                }
                try { dadb.close(); } catch (Exception ignored) {}
                sharedDadb = null;
            }
            
            if (!isAdbPortOpen()) {
                Log.w(TAG, "ADB port " + ADB_PORT + " not open - ADB not enabled?");
                throw new Exception("ADB port not open");
            }
            
            AdbKeyPair adbKeyPair = getOrCreateAdbKeyPair();
            Log.i(TAG, "Attempting ADB connection...");
            
            if (!isAuthPending.get() && pollingStarted.compareAndSet(false, true)) {
                isAuthPending.set(true);
                if (authCallback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> authCallback.onAuthPending());
                }
                startAuthPollingInternal(adbKeyPair);
            }
            
            dadb = tryConnectWithTimeout(adbKeyPair, 2000);
            
            if (dadb != null) {
                sharedDadb = dadb;
                isAuthPending.set(false);
                wasAuthGranted.set(true);
                pollingStarted.set(false);
                Log.i(TAG, "ADB connection established successfully");
                if (authCallback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> authCallback.onAuthGranted());
                }
                return dadb;
            } else {
                throw new Exception("ADB auth pending - waiting for user to accept");
            }
        }
    }

    public boolean isAdbPortOpen() {
        try (Socket socket = new Socket("127.0.0.1", ADB_PORT)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Dadb tryConnectWithTimeout(final AdbKeyPair keyPair, long timeoutMs) throws Exception {
        final Dadb[] result = new Dadb[1];
        final Exception[] error = new Exception[1];

        Thread connectThread = new Thread(() -> {
            try {
                Dadb dadb = Dadb.create("127.0.0.1", ADB_PORT, keyPair);
                AdbShellResponse testResult = dadb.shell("echo ok");
                if (testResult.getExitCode() == 0) {
                    result[0] = dadb;
                } else {
                    dadb.close();
                }
            } catch (Exception e) {
                error[0] = e;
            }
        }, "adb-connect-probe");
        
        connectThread.setDaemon(true);
        connectThread.start();
        connectThread.join(timeoutMs);

        if (connectThread.isAlive()) {
            Log.d(TAG, "Connection timed out - auth likely pending");
            connectThread.interrupt();
            return null;
        }

        if (error[0] != null) {
            throw error[0];
        }
        return result[0];
    }

    private void startAuthPollingInternal(final AdbKeyPair keyPair) {
        pollingExecutor.execute(() -> {
            Log.i(TAG, "=== AUTH POLLING STARTED ===");
            int attempts = 0;
            int maxAttempts = 60;
            
            while (isAuthPending.get() && attempts < maxAttempts) {
                attempts++;
                try {
                    long backoffMs = attempts <= 3 ? 3000L : Math.min(3000L * (1L << Math.min(attempts - 3, 4)), 30000L);
                    Thread.sleep(backoffMs);
                } catch (InterruptedException e) {
                    Log.d(TAG, "Polling interrupted");
                    break;
                }
                
                if (!isAuthPending.get()) {
                    Log.d(TAG, "Auth no longer pending, stopping poll");
                    break;
                }
                
                Log.i(TAG, "Auth poll attempt " + attempts + "/" + maxAttempts + "...");
                
                if (!isAdbPortOpen()) {
                    Log.d(TAG, "ADB port not open, skipping attempt");
                    continue;
                }
                
                try {
                    Dadb testDadb = tryConnectWithTimeout(keyPair, 2000);
                    if (testDadb != null) {
                        synchronized (sharedDadbLock) {
                            if (sharedDadb != null) {
                                try { sharedDadb.close(); } catch (Exception ignored) {}
                            }
                            sharedDadb = testDadb;
                        }
                        isAuthPending.set(false);
                        wasAuthGranted.set(true);
                        pollingStarted.set(false);
                        Log.i(TAG, "=== AUTH GRANTED VIA POLLING ===");
                        if (authCallback != null) {
                            new Handler(Looper.getMainLooper()).post(() -> authCallback.onAuthGranted());
                        }
                        break;
                    }
                } catch (Exception ignored) {}
            }
            
            if (isAuthPending.get() && attempts >= maxAttempts) {
                Log.w(TAG, "Auth polling timed out");
                isAuthPending.set(false);
                pollingStarted.set(false);
                if (authCallback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> authCallback.onAuthFailed("Auth timeout - please grant ADB permission"));
                }
            }
        });
    }

    public void closeConnection() {
        synchronized (sharedDadbLock) {
            try {
                if (sharedDadb != null) {
                    sharedDadb.close();
                    Log.i(TAG, "Closed ADB connection");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error closing ADB connection", e);
            }
            sharedDadb = null;
        }
    }

    public void shutdown() {
        try {
            executor.shutdownNow();
        } catch (Exception e) {
            Log.w(TAG, "executor shutdown failed: " + e.getMessage());
        }
    }

    private AdbKeyPair getOrCreateAdbKeyPair() throws Exception {
        if (cachedKeyPair != null) return cachedKeyPair;
        
        synchronized (keyPairLock) {
            if (cachedKeyPair != null) return cachedKeyPair;
            
            File keyDir = context.getFilesDir();
            File privateKeyFile = new File(keyDir, ADB_KEY_FILE);
            File publicKeyFile = new File(keyDir, ADB_PUB_KEY_FILE);
            
            AdbKeyPair keyPair;
            if (privateKeyFile.exists() && publicKeyFile.exists()) {
                try {
                    keyPair = AdbKeyPair.Companion.read(privateKeyFile, publicKeyFile);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to read existing keys: " + e.getMessage());
                    keyPair = generateAndSaveKeyPair(privateKeyFile, publicKeyFile);
                }
            } else {
                Log.i(TAG, "Generating new ADB key pair");
                keyPair = generateAndSaveKeyPair(privateKeyFile, publicKeyFile);
            }
            
            cachedKeyPair = keyPair;
            return keyPair;
        }
    }

    private AdbKeyPair generateAndSaveKeyPair(File privateKeyFile, File publicKeyFile) throws Exception {
        AdbKeyPair.Companion.generate(privateKeyFile, publicKeyFile);
        return AdbKeyPair.Companion.read(privateKeyFile, publicKeyFile);
    }
}
