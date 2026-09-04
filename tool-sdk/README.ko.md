> **언어**: [English](README.md) | **한국어**
> 
> BYD Sub AI용 서드파티 Tool APK 개발을 위한 소프트웨어 개발 키트(SDK)입니다.

# SubAI 도구 개발 SDK (SubAI Tool SDK)

이 안드로이드 라이브러리는 SubAI 호스트 앱과 독립적으로 서명된 외부 Tool APK 간의 상호 호환성을 제공하는 공통 인터페이스 계층입니다.

## 제공자 설정 (Provider Setup)

1. 빌드된 `tool-sdk` AAR 라이브러리를 안드로이드 애플리케이션 프로젝트에 추가합니다.
2. `com.poorgrammera.subai.action.TOOL_SERVICE` 인텐트 액션을 갖는 바인드 `Service`를 매니페스트에 선언하고 내보냅니다(`exported="true"`).
3. `ISubAiToolService.Stub` AIDL 인터페이스를 구현합니다.
4. 프로토콜 v1 규격의 JSON 매니페스트와 현재 설정 상태(`ready` 등)를 반환합니다.
5. 요청(`execute`)을 비동기로 처리하고 완료 시 정확히 하나의 콜백을 반환합니다.

```xml
<service
    android:name=".MyToolService"
    android:exported="true">
    <intent-filter>
        <action android:name="com.poorgrammera.subai.action.TOOL_SERVICE" />
    </intent-filter>
</service>
```

이 서비스는 SubAI 오픈소스 포크 프로젝트에서도 자동으로 검색될 수 있도록 설계되었습니다. 사용자는 APK를 설치하고 SubAI에서 서명 인증서를 승인함으로써 신뢰 관계를 형성합니다. 매니페스트, 상태 메시지, 실행 결과 또는 시스템 로그에 제공자 API 키나 개인 자격 증명을 노출하지 마십시오.

## 공식 참조 예제 (Reference Implementation)

실제 카카오 장소 검색 기능을 구현한 완성형 참조 구현체는 [`BydSubAI-KorTools`](https://github.com/PoorGrammerA/BydSubAI-KorTools) 저장소를 참고하십시오. 이 예제 코드를 템플릿 삼아 다른 지도 서비스 검색, 스마트홈 IoT 연동, 커스텀 매크로 등 독자적인 SubAI 호환 툴을 손쉽게 제작할 수 있습니다.

전체 프로토콜 규격은 [`../docs/subai_tool_protocol_v1.ko.md`](../docs/subai_tool_protocol_v1.ko.md) 문서를 참고하십시오.
