> **Language**: **English** | [한국어 (Korean)](subai_tool_protocol_v1.ko.md)
> 
> This document specifies the SubAI external tool integration protocol.

# SubAI Tool Protocol v1

SubAI Tool Protocol lets independently signed Android applications add Gemini function tools to a SubAI host without rebuilding the host APK.

## Trust model

- Installing an APK and approving it once in SubAI are the trust boundary.
- Tool applications do not approve individual SubAI hosts, so open-source forks remain compatible.
- SubAI cannot verify a provider's billing behavior, disclosures, or internal implementation.
- API keys and provider credentials stay inside the Tool application.
- Approval identity is the service component plus the APK signing-certificate SHA-256 digest.
- A signing-certificate change revokes approval. An update signed with the same certificate retains it.

## Discovery

Tool applications export a bound service with this intent action:

```xml
<service
    android:name=".MyToolService"
    android:exported="true">
    <intent-filter>
        <action android:name="com.poorgrammera.subai.action.TOOL_SERVICE" />
    </intent-filter>
</service>
```

Hosts query the action, resolve each service, then bind using an explicit component. `QUERY_ALL_PACKAGES` is not required.

## Binder API

The `tool-sdk` module publishes `ISubAiToolService` and `ISubAiToolCallback`.

- `getManifestJson()` returns a small, static manifest and must not perform network I/O.
- `getStatusJson()` returns `ready`, `needs_configuration`, or `unavailable`.
- `getSettingsIntent()` optionally opens provider-owned configuration UI.
- `execute()` starts asynchronous work and returns exactly one callback.
- `cancel()` requests best-effort cancellation.

Binder calls use JSON payloads so protocol fields can be extended without changing the AIDL surface.

## Provider manifest

```json
{
  "protocolVersion": 1,
  "provider": {
    "id": "com.example.kortools",
    "name": "Korea Tools",
    "version": "1.0.0",
    "sourceUrl": "https://github.com/example/kortools",
    "disclosures": ["Search queries and coordinates may be sent to Kakao."]
  },
  "tools": [
    {
      "name": "search_places",
      "displayName": "Korean place search",
      "description": "Searches for places in South Korea.",
      "parameters": {
        "type": "object",
        "properties": {
          "query": {"type": "string"},
          "latitude": {"type": "number"},
          "longitude": {"type": "number"}
        },
        "required": ["query"]
      }
    }
  ]
}
```

Provider IDs use reverse-domain notation. Local tool names contain only ASCII letters, digits, and underscores. SubAI exposes an external function to Gemini as `ext_<provider alias>_<local tool name>` and keeps a routing map to the original provider and name. External tools cannot replace built-in functions.

Parameters use the Gemini-supported subset of OpenAPI 3.0 Schema: `object`, `array`, `string`, `integer`, `number`, `boolean`, `properties`, `required`, `items`, `enum`, `minimum`, `maximum`, and descriptions. The host rejects invalid or excessively nested schemas.

## Invocation

```json
{
  "protocolVersion": 1,
  "requestId": "uuid",
  "toolName": "search_places",
  "arguments": {"query": "nearby restaurant"},
  "locale": "ko-KR"
}
```

Successful response:

```json
{"status":"success","result":{"places":[]}}
```

Error response:

```json
{
  "status":"error",
  "error":{"code":"NOT_CONFIGURED","message":"An API key is required."}
}
```

Standard error codes are `INVALID_ARGUMENTS`, `NOT_CONFIGURED`, `PERMISSION_REQUIRED`, `RATE_LIMITED`, `UNAVAILABLE`, `TIMEOUT`, and `INTERNAL_ERROR`.

The default timeout is 15 seconds and the host-enforced maximum is 60 seconds. Protocol v1 transports JSON only and limits manifests, requests, and responses to 256 KiB each. Binary and streaming results are reserved for a later protocol.

## Session behavior

Approved manifests are cached by SubAI. Ready tools are added to the Gemini Live connection configuration when a new session starts. Installing, removing, enabling, or updating a Tool application affects the next Gemini Live session rather than mutating an active session.

## Reference Implementation

A fully working, independently installable companion Tool application implementing this protocol is published at [`BydSubAI-KorTools`](https://github.com/PoorGrammerA/BydSubAI-KorTools). Developers can reference its AIDL service implementation, manifest setup, and JSON request dispatching as a practical template to create new SubAI tools.
