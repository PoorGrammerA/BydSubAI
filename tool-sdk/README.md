> **Language**: **English** | [한국어 (Korean)](README.ko.md)
> 
> SDK for developing companion Tool APKs for BYD Sub AI.

# SubAI Tool SDK

This Android library is the compatibility surface shared by SubAI hosts and independently signed Tool APKs.

## Provider Setup

1. Add the built `tool-sdk` AAR to an Android application.
2. Export a bound `Service` with the action `com.poorgrammera.subai.action.TOOL_SERVICE`.
3. Implement `ISubAiToolService.Stub`.
4. Return a protocol-v1 manifest and current configuration status.
5. Execute requests asynchronously and return exactly one callback.

```xml
<service
    android:name=".MyToolService"
    android:exported="true">
    <intent-filter>
        <action android:name="com.poorgrammera.subai.action.TOOL_SERVICE" />
    </intent-filter>
</service>
```

The service is intentionally discoverable by open-source SubAI forks. Users establish trust by installing the APK and approving its signing identity in SubAI. Do not expose provider credentials in manifests, status messages, results, or logs.

## Reference Implementation

See [`BydSubAI-KorTools`](https://github.com/PoorGrammerA/BydSubAI-KorTools) for a complete, production-ready reference provider application implementing Kakao place search. You can use it as a boilerplate template to build your own custom Sub AI tools (e.g., alternative map searches, smart home IoT integrations, custom vehicle macros).

For protocol specifications, error codes, and trust boundaries, see [`../docs/subai_tool_protocol_v1.md`](../docs/subai_tool_protocol_v1.md).
