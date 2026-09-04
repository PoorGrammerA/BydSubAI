package com.poorgrammera.bydsubai.tool;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.poorgrammera.subai.tool.ISubAiToolService;
import com.poorgrammera.subai.tool.SubAiToolContract;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Discovers and snapshots Tool services without keeping their processes alive. */
public final class ExternalToolDiscovery {
    public interface Listener {
        void onProvider(ExternalToolDescriptor descriptor, PendingIntent settingsIntent);
        void onProviderError(ComponentName component, String message);
        void onComplete();
    }

    private final Context context;
    private final ExternalToolApprovalStore approvalStore;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public ExternalToolDiscovery(Context context) {
        this.context = context.getApplicationContext();
        this.approvalStore = new ExternalToolApprovalStore(this.context);
    }

    public void discover(Listener listener) {
        Intent query = new Intent(SubAiToolContract.ACTION_TOOL_SERVICE);
        List<ResolveInfo> services = context.getPackageManager().queryIntentServices(query, 0);
        if (services == null || services.isEmpty()) {
            listener.onComplete();
            return;
        }
        List<ComponentName> components = new ArrayList<>();
        for (ResolveInfo resolveInfo : services) {
            if (resolveInfo.serviceInfo == null || !resolveInfo.serviceInfo.exported) continue;
            components.add(new ComponentName(resolveInfo.serviceInfo.packageName,
                    resolveInfo.serviceInfo.name));
        }
        discoverNext(components, 0, listener);
    }

    private void discoverNext(List<ComponentName> components, int index, Listener listener) {
        if (index >= components.size()) {
            listener.onComplete();
            return;
        }
        ComponentName component = components.get(index);
        AtomicBoolean finished = new AtomicBoolean(false);
        final ServiceConnection[] holder = new ServiceConnection[1];
        Runnable timeout = () -> {
            if (!finished.compareAndSet(false, true)) return;
            safeUnbind(holder[0]);
            listener.onProviderError(component, "Tool service connection timed out");
            discoverNext(components, index + 1, listener);
        };

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                handler.removeCallbacks(timeout);
                if (!finished.compareAndSet(false, true)) return;
                try {
                    ISubAiToolService service = ISubAiToolService.Stub.asInterface(binder);
                    String manifest = service.getManifestJson();
                    String status = service.getStatusJson();
                    if (manifest == null || manifest.getBytes(StandardCharsets.UTF_8).length
                            > SubAiToolContract.MAX_MANIFEST_BYTES) {
                        throw new IllegalArgumentException("Tool manifest is missing or too large");
                    }
                    String certificate = approvalStore.signingCertificateSha256(component.getPackageName());
                    CharSequence label = context.getPackageManager()
                            .getApplicationLabel(context.getPackageManager()
                                    .getApplicationInfo(component.getPackageName(), 0));
                    ExternalToolDescriptor descriptor = ExternalToolDescriptor.create(
                            component, certificate, label.toString(), manifest, status);
                    approvalStore.updateSnapshotIfApproved(descriptor);
                    listener.onProvider(descriptor, service.getSettingsIntent());
                } catch (Exception e) {
                    listener.onProviderError(component, e.getMessage() == null ? e.toString() : e.getMessage());
                } finally {
                    safeUnbind(this);
                    discoverNext(components, index + 1, listener);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                if (!finished.compareAndSet(false, true)) return;
                handler.removeCallbacks(timeout);
                listener.onProviderError(component, "Tool service disconnected");
                discoverNext(components, index + 1, listener);
            }

            @Override
            public void onNullBinding(ComponentName name) {
                if (!finished.compareAndSet(false, true)) return;
                handler.removeCallbacks(timeout);
                safeUnbind(this);
                listener.onProviderError(component, "Tool service returned no Binder");
                discoverNext(components, index + 1, listener);
            }
        };
        holder[0] = connection;
        try {
            Intent explicit = new Intent(SubAiToolContract.ACTION_TOOL_SERVICE).setComponent(component);
            if (!context.bindService(explicit, connection, Context.BIND_AUTO_CREATE)) {
                finished.set(true);
                listener.onProviderError(component, "Unable to bind Tool service");
                discoverNext(components, index + 1, listener);
                return;
            }
            handler.postDelayed(timeout, 3_000L);
        } catch (Exception e) {
            finished.set(true);
            listener.onProviderError(component, e.getMessage() == null ? e.toString() : e.getMessage());
            discoverNext(components, index + 1, listener);
        }
    }

    private void safeUnbind(ServiceConnection connection) {
        if (connection == null) return;
        try { context.unbindService(connection); } catch (Exception ignored) { }
    }
}
