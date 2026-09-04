package com.poorgrammera.bydsubai.audio;
import com.poorgrammera.bydsubai.BuildConfig;
import com.poorgrammera.bydsubai.R;


import android.content.ComponentName;
import android.content.Context;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import java.util.HashMap;
import java.util.Map;

public class MediaBrowserManager {
    private static final String TAG = "MediaBrowserManager";
    private static MediaBrowserManager instance;
    private final Context context;

    public static final String APP_FLO = "flo";

    private static final String PKG_FLO = "skplanet.musicmate";
    private static final String SVC_FLO = "com.skplanet.musicmate.mediaplayer.MediaBrowserService";

    private final Map<String, BrowserConnection> connections = new HashMap<>();

    private MediaBrowserManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new MediaBrowserManager(context);
            instance.connectAll();
        }
    }

    public static synchronized MediaBrowserManager getInstance() {
        return instance;
    }

    public static synchronized MediaBrowserManager getInstance(Context context) {
        if (instance == null && context != null) {
            instance = new MediaBrowserManager(context);
            instance.connectAll();
        }
        return instance;
    }

    public void connectAll() {
        connect(APP_FLO, PKG_FLO, SVC_FLO);
    }

    public void disconnectAll() {
        for (BrowserConnection conn : connections.values()) {
            conn.disconnect();
        }
        connections.clear();
    }

    private void connect(String name, String pkg, String svc) {
        if (connections.containsKey(name)) {
            return;
        }
        BrowserConnection conn = new BrowserConnection(name, pkg, svc);
        connections.put(name, conn);
        conn.connect();
    }

    public void reconnect(String name) {
        BrowserConnection conn = connections.get(name);
        if (conn != null) {
            conn.connect();
        } else {
            if (APP_FLO.equals(name)) {
                connect(APP_FLO, PKG_FLO, SVC_FLO);
            }
        }
    }

    public MediaController getController(String name) {
        BrowserConnection conn = connections.get(name);
        if (conn != null) {
            return conn.getController();
        }
        return null;
    }

    public Map<String, BrowserConnection> getConnections() {
        return connections;
    }

    public class BrowserConnection {
        private final String name;
        private final String pkg;
        private final String svc;
        private MediaBrowser mediaBrowser;
        private MediaController mediaController;

        public BrowserConnection(String name, String pkg, String svc) {
            this.name = name;
            this.pkg = pkg;
            this.svc = svc;
        }

        public void connect() {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    if (mediaBrowser != null) {
                        try {
                            mediaBrowser.disconnect();
                        } catch (Exception ignored) {}
                        mediaBrowser = null;
                    }
                    Log.d(TAG, "Connecting to " + name + " (" + pkg + ") on Main Looper");
                    mediaBrowser = new MediaBrowser(context,
                            new ComponentName(pkg, svc),
                            new MediaBrowser.ConnectionCallback() {
                                @Override
                                public void onConnected() {
                                    Log.i(TAG, name + " MediaBrowser connected");
                                    try {
                                        mediaController = new MediaController(context, mediaBrowser.getSessionToken());
                                    } catch (Exception e) {
                                        Log.e(TAG, "Failed to create MediaController for " + name, e);
                                    }
                                }

                                @Override
                                public void onConnectionSuspended() {
                                    Log.w(TAG, name + " MediaBrowser connection suspended");
                                    mediaController = null;
                                }

                                @Override
                                public void onConnectionFailed() {
                                    Log.e(TAG, name + " MediaBrowser connection failed");
                                    mediaController = null;
                                }
                            },
                            null);
                    mediaBrowser.connect();
                } catch (Exception e) {
                    Log.e(TAG, "Error connecting to " + name, e);
                }
            });
        }

        public void disconnect() {
            if (mediaBrowser != null && mediaBrowser.isConnected()) {
                mediaBrowser.disconnect();
            }
            mediaController = null;
            mediaBrowser = null;
        }

        public MediaController getController() {
            return mediaController;
        }

        public boolean isConnected() {
            return mediaBrowser != null && mediaBrowser.isConnected();
        }
    }
}
