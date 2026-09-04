package com.poorgrammera.bydsubai.tool;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.google.genai.types.FunctionDeclaration;
import com.poorgrammera.subai.tool.ISubAiToolCallback;
import com.poorgrammera.subai.tool.ISubAiToolService;
import com.poorgrammera.subai.tool.SubAiToolContract;
import com.poorgrammera.subai.tool.SubAiToolNames;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Session-scoped routing table from Gemini function names to approved Tool services. */
public final class ExternalToolRegistry {
    private static final String TAG = "ExternalToolRegistry";

    public interface ResultCallback {
        void onComplete(Map<String, Object> response);
    }

    private static final class Route {
        final ExternalToolDescriptor descriptor;
        final String localName;

        Route(ExternalToolDescriptor descriptor, String localName) {
            this.descriptor = descriptor;
            this.localName = localName;
        }
    }

    private final Context context;
    private final ExternalToolApprovalStore approvalStore;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Route> routes = new LinkedHashMap<>();

    public ExternalToolRegistry(Context context) {
        this.context = context.getApplicationContext();
        this.approvalStore = new ExternalToolApprovalStore(this.context);
    }

    public List<FunctionDeclaration> buildDeclarations(Set<String> reservedNames) {
        routes.clear();
        Set<String> usedNames = new HashSet<>(reservedNames);
        List<FunctionDeclaration> declarations = new ArrayList<>();
        for (ExternalToolDescriptor descriptor : approvalStore.loadInstalledApproved()) {
            if (!descriptor.isReady()) continue;
            for (ExternalToolDescriptor.Function function : descriptor.functions()) {
                try {
                    String geminiName = uniqueGeminiName(descriptor, function.name, usedNames);
                    if (!approvalStore.isFunctionEnabled(descriptor.identity(), function.name)) continue;
                    FunctionDeclaration declaration = FunctionDeclaration.builder()
                            .name(geminiName)
                            .description(function.description + " Provided by " + descriptor.providerName() + ".")
                            .parameters(GeminiSchemaAdapter.fromJson(function.parameters))
                            .build();
                    declarations.add(declaration);
                    routes.put(geminiName, new Route(descriptor, function.name));
                    usedNames.add(geminiName);
                } catch (Exception e) {
                    Log.w(TAG, "Ignoring invalid external tool " + function.name, e);
                }
            }
        }
        return declarations;
    }

    public boolean handles(String geminiName) {
        return routes.containsKey(geminiName);
    }

    public String findGeminiNameForLocalTool(String localName) {
        for (Map.Entry<String, Route> entry : routes.entrySet()) {
            if (entry.getValue().localName.equals(localName)) return entry.getKey();
        }
        return null;
    }

    /** Executes an approved provider function outside a Gemini session, for host UI workflows. */
    public void executeProviderTool(String providerId, String localName,
                                    Map<String, Object> arguments, ResultCallback callback) {
        executeMatchingProviderTool(providerId, localName, arguments, callback);
    }

    public void executeFirstProviderTool(String localName, Map<String, Object> arguments,
                                         ResultCallback callback) {
        executeMatchingProviderTool(null, localName, arguments, callback);
    }

    private void executeMatchingProviderTool(String providerId, String localName,
                                             Map<String, Object> arguments, ResultCallback callback) {
        for (ExternalToolDescriptor descriptor : approvalStore.loadInstalledApproved()) {
            if (!descriptor.isReady()
                    || (providerId != null && !descriptor.providerId().equals(providerId))) continue;
            for (ExternalToolDescriptor.Function function : descriptor.functions()) {
                if (function.name.equals(localName)
                        && approvalStore.isFunctionEnabled(descriptor.identity(), localName)) {
                    String requestId = UUID.randomUUID().toString();
                    try {
                        JSONObject request = new JSONObject()
                                .put("protocolVersion", SubAiToolContract.PROTOCOL_VERSION)
                                .put("requestId", requestId)
                                .put("toolName", localName)
                                .put("arguments", new JSONObject(arguments))
                                .put("locale", context.getResources().getConfiguration()
                                        .getLocales().get(0).toLanguageTag());
                        if (request.toString().getBytes(StandardCharsets.UTF_8).length
                                > SubAiToolContract.MAX_REQUEST_BYTES) {
                            callback.onComplete(error(SubAiToolContract.ERROR_INVALID_ARGUMENTS,
                                    "External tool request is too large"));
                            return;
                        }
                        bindAndExecute(descriptor.component, requestId, request.toString(), callback);
                    } catch (Exception e) {
                        callback.onComplete(error(SubAiToolContract.ERROR_INVALID_ARGUMENTS,
                                e.getMessage()));
                    }
                    return;
                }
            }
        }
        callback.onComplete(error(SubAiToolContract.ERROR_UNAVAILABLE,
                "Required external Tool app is not approved, configured, or enabled"));
    }

    public void execute(String geminiName, Map<String, Object> arguments, ResultCallback callback) {
        Route route = routes.get(geminiName);
        if (route == null) {
            callback.onComplete(error("UNAVAILABLE", "External tool is not registered"));
            return;
        }
        String requestId = UUID.randomUUID().toString();
        JSONObject request = new JSONObject();
        try {
            request.put("protocolVersion", SubAiToolContract.PROTOCOL_VERSION);
            request.put("requestId", requestId);
            request.put("toolName", route.localName);
            request.put("arguments", new JSONObject(arguments));
            request.put("locale", context.getResources().getConfiguration()
                    .getLocales().get(0).toLanguageTag());
        } catch (Exception e) {
            callback.onComplete(error("INVALID_ARGUMENTS", e.getMessage()));
            return;
        }
        byte[] requestBytes = request.toString().getBytes(StandardCharsets.UTF_8);
        if (requestBytes.length > SubAiToolContract.MAX_REQUEST_BYTES) {
            callback.onComplete(error("INVALID_ARGUMENTS", "External tool request is too large"));
            return;
        }
        bindAndExecute(route.descriptor.component, requestId, request.toString(), callback);
    }

    private void bindAndExecute(ComponentName component, String requestId, String requestJson,
                                ResultCallback callback) {
        AtomicBoolean finished = new AtomicBoolean(false);
        final ISubAiToolService[] serviceHolder = new ISubAiToolService[1];
        final ServiceConnection[] connectionHolder = new ServiceConnection[1];

        Runnable timeout = () -> finish(finished, connectionHolder[0], callback,
                error(SubAiToolContract.ERROR_TIMEOUT, "External tool timed out"),
                serviceHolder[0], requestId);

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                ISubAiToolService service = ISubAiToolService.Stub.asInterface(binder);
                serviceHolder[0] = service;
                try {
                    service.execute(requestJson, new ISubAiToolCallback.Stub() {
                        @Override
                        public void onResult(String callbackRequestId, String responseJson) {
                            if (!requestId.equals(callbackRequestId)) return;
                            handler.post(() -> {
                                Map<String, Object> response;
                                try {
                                    if (responseJson == null || responseJson.getBytes(StandardCharsets.UTF_8).length
                                            > SubAiToolContract.MAX_RESPONSE_BYTES) {
                                        throw new IllegalArgumentException("External tool response is missing or too large");
                                    }
                                    response = jsonObjectToMap(new JSONObject(responseJson));
                                } catch (Exception e) {
                                    response = error(SubAiToolContract.ERROR_INTERNAL, e.getMessage());
                                }
                                finish(finished, connectionHolder[0], callback, response, null, requestId);
                            });
                        }

                        @Override
                        public void onError(String callbackRequestId, String errorCode, String message) {
                            if (!requestId.equals(callbackRequestId)) return;
                            handler.post(() -> finish(finished, connectionHolder[0], callback,
                                    error(errorCode, message), null, requestId));
                        }
                    });
                } catch (Exception e) {
                    finish(finished, connectionHolder[0], callback,
                            error(SubAiToolContract.ERROR_UNAVAILABLE, e.getMessage()), null, requestId);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                finish(finished, this, callback,
                        error(SubAiToolContract.ERROR_UNAVAILABLE, "External tool disconnected"),
                        null, requestId);
            }

            @Override
            public void onNullBinding(ComponentName name) {
                finish(finished, this, callback,
                        error(SubAiToolContract.ERROR_UNAVAILABLE, "External tool returned no Binder"),
                        null, requestId);
            }
        };
        connectionHolder[0] = connection;
        try {
            Intent intent = new Intent(SubAiToolContract.ACTION_TOOL_SERVICE).setComponent(component);
            if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                callback.onComplete(error(SubAiToolContract.ERROR_UNAVAILABLE,
                        "Unable to bind external tool"));
                return;
            }
            handler.postDelayed(timeout, SubAiToolContract.DEFAULT_TIMEOUT_MS);
        } catch (Exception e) {
            callback.onComplete(error(SubAiToolContract.ERROR_UNAVAILABLE, e.getMessage()));
        }
    }

    private void finish(AtomicBoolean finished, ServiceConnection connection, ResultCallback callback,
                        Map<String, Object> response, ISubAiToolService cancelService,
                        String requestId) {
        if (!finished.compareAndSet(false, true)) return;
        if (cancelService != null) {
            try { cancelService.cancel(requestId); } catch (Exception ignored) { }
        }
        try { context.unbindService(connection); } catch (Exception ignored) { }
        callback.onComplete(response);
    }

    private static String uniqueGeminiName(ExternalToolDescriptor descriptor, String localName,
                                           Set<String> usedNames) {
        String base = SubAiToolNames.externalFunctionBase(descriptor.providerId(), localName);
        if (base.length() > 115) base = base.substring(0, 115);
        String candidate = base;
        if (usedNames.contains(candidate)) {
            String identitySuffix = Integer.toUnsignedString(descriptor.identity().hashCode(), 16);
            candidate = base + "_" + identitySuffix;
        }
        int counter = 2;
        while (usedNames.contains(candidate)) {
            String suffix = "_" + counter++;
            candidate = base.substring(0, Math.min(base.length(), 128 - suffix.length())) + suffix;
        }
        if (candidate.length() > 128) candidate = candidate.substring(0, 128);
        return candidate;
    }

    private static Map<String, Object> error(String code, String message) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("code", code == null || code.isEmpty() ? "INTERNAL_ERROR" : code);
        detail.put("message", message == null ? "External tool failed" : message);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("error", detail);
        return response;
    }

    private static Map<String, Object> jsonObjectToMap(JSONObject object) throws Exception {
        Map<String, Object> result = new HashMap<>();
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            result.put(key, jsonValue(object.get(key)));
        }
        return result;
    }

    private static Object jsonValue(Object value) throws Exception {
        if (value == JSONObject.NULL) return null;
        if (value instanceof JSONObject) return jsonObjectToMap((JSONObject) value);
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) result.add(jsonValue(array.get(i)));
            return result;
        }
        return value;
    }
}
