> **언어**: [English](subai_tool_protocol_v1.md) | **한국어**
> 
> 이 문서는 SubAI 외부 도구 연동 프로토콜 v1 공식 명세서입니다.

# SubAI 도구 프로토콜 v1 (SubAI Tool Protocol v1)

SubAI 도구 프로토콜은 독립적으로 서명된 서드파티 안드로이드 애플리케이션이 SubAI 호스트 APK를 다시 빌드하지 않고도 Gemini Function Calling 도구를 SubAI에 추가할 수 있도록 지원합니다.

## 신뢰 모델 (Trust Model)

- APK 설치와 SubAI 내에서의 최초 1회 사용자 승인이 신뢰 경계(Trust Boundary)를 형성합니다.
- 도구 애플리케이션은 개별 SubAI 호스트를 승인하지 않으므로 오픈소스 포크와의 호환성이 유지됩니다.
- SubAI는 제공자의 과금 동작, 공개 고지 사항, 내부 구현을 검증할 수 없습니다.
- API 키 및 제공자 인증 정보는 도구 애플리케이션 내부에만 안전하게 보관됩니다.
- 승인 식별자는 서비스 컴포넌트 이름과 APK 서명 인증서의 SHA-256 다이제스트입니다.
- 서명 인증서가 변경되면 승인이 취소됩니다. 동일한 인증서로 서명된 업데이트는 승인을 유지합니다.

## 서비스 검색 (Discovery)

도구 애플리케이션은 다음 인텐트 액션을 갖는 바인드 서비스를 외부에 내보냅니다:

```xml
<service
    android:name=".MyToolService"
    android:exported="true">
    <intent-filter>
        <action android:name="com.poorgrammera.subai.action.TOOL_SERVICE" />
    </intent-filter>
</service>
```

호스트는 해당 액션을 쿼리하고 서비스를 확인한 후 명시적 컴포넌트를 사용하여 바인딩합니다. `QUERY_ALL_PACKAGES` 권한은 필요하지 않습니다.

## 바인더 API (Binder API)

`tool-sdk` 모듈은 `ISubAiToolService`와 `ISubAiToolCallback` 인터페이스를 제공합니다.

- `getManifestJson()`: 작고 정적인 매니페스트 JSON을 반환하며 네트워크 I/O를 수행해서는 안 됩니다.
- `getStatusJson()`: `ready`, `needs_configuration`, 또는 `unavailable` 상태를 반환합니다.
- `getSettingsIntent()`: 선택적으로 제공자가 소유한 설정 UI 액티비티 인텐트를 반환합니다.
- `execute()`: 비동기 작업을 시작하고 정확히 하나의 콜백을 반환합니다.
- `cancel()`: 베스트 에포트(Best-effort) 취소를 요청합니다.

바인더 호출은 AIDL 인터페이스 표면을 변경하지 않고 프로토콜 필드를 확장할 수 있도록 JSON 페이로드를 사용합니다.

## 제공자 매니페스트 (Provider Manifest)

```json
{
  "protocolVersion": 1,
  "provider": {
    "id": "com.example.kortools",
    "name": "Korea Tools",
    "version": "1.0.0",
    "sourceUrl": "https://github.com/example/kortools",
    "disclosures": ["검색 쿼리와 좌표가 카카오 서버로 전송될 수 있습니다."]
  },
  "tools": [
    {
      "name": "search_places",
      "displayName": "한국 장소 검색",
      "description": "대한민국 내의 장소 및 상호를 검색합니다.",
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

제공자 ID는 역방향 도메인 표기법을 사용합니다. 로컬 도구 이름은 ASCII 영문자, 숫자, 밑줄만 포함해야 합니다. SubAI는 외부 함수를 Gemini에 `ext_<provider alias>_<local tool name>` 형식으로 노출하고 원래 제공자 및 도구 이름에 대한 라우팅 맵을 유지합니다. 외부 도구는 기본 내장 함수를 대체할 수 없습니다.

매개변수는 Gemini가 지원하는 OpenAPI 3.0 스키마 하위 집합을 사용합니다: `object`, `array`, `string`, `integer`, `number`, `boolean`, `properties`, `required`, `items`, `enum`, `minimum`, `maximum`, 그리고 `description`. 호스트는 잘못되었거나 과도하게 중첩된 스키마를 거부합니다.

## 함수 호출 (Invocation)

```json
{
  "protocolVersion": 1,
  "requestId": "uuid",
  "toolName": "search_places",
  "arguments": {"query": "근처 맛집"},
  "locale": "ko-KR"
}
```

성공 응답 예시:

```json
{"status":"success","result":{"places":[]}}
```

오류 응답 예시:

```json
{
  "status":"error",
  "error":{"code":"NOT_CONFIGURED","message":"API 키 설정이 필요합니다."}
}
```

표준 오류 코드는 `INVALID_ARGUMENTS`, `NOT_CONFIGURED`, `PERMISSION_REQUIRED`, `RATE_LIMITED`, `UNAVAILABLE`, `TIMEOUT`, `INTERNAL_ERROR`입니다.

기본 타임아웃은 15초이며 호스트가 강제하는 최대 시간은 60초입니다. 프로토콜 v1은 JSON만 전송하며 매니페스트, 요청, 응답 크기를 각각 256 KiB로 제한합니다. 바이너리 및 스트리밍 결과는 향후 프로토콜을 위해 예약되어 있습니다.

## 세션 동작 방식 (Session Behavior)

승인된 매니페스트는 SubAI에 의해 캐시됩니다. 준비 상태(`ready`)의 도구들은 새로운 Gemini Live 세션이 연결될 때 설정에 등록됩니다. 도구 앱의 설치, 제거, 활성화, 업데이트는 실행 중인 활성 세션을 즉시 변경하지 않고 다음 Gemini Live 세션부터 반영됩니다.

## 공식 참조 예제 (Reference Implementation)

본 프로토콜을 구현한 실제 동작하는 독립 설치형 컴패니언 Tool 애플리케이션은 [`BydSubAI-KorTools`](https://github.com/PoorGrammerA/BydSubAI-KorTools) 저장소에 공개되어 있습니다. 개발자는 이 저장소의 AIDL 바인더 서비스 구현, AndroidManifest 설정, JSON 요청/응답 디스패치 코드를 예제로 참고하여 새로운 커스텀 SubAI 툴을 직접 제작할 수 있습니다.
