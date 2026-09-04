> **언어**: [English](controllable_tools.md) | **한국어**
> 
> 이 문서는 BYD Sub AI의 차량 제어 및 Gemini Live 도구(Tool) 한국어 명세서입니다.

# Gemini Live 차량 제어 및 기능 도구 (Tools) 명세서

본 문서는 **BYD Sub AI**의 Gemini Live 음성 AI 에이전트('루미', '키트', '아스라다')가 운전자의 음성 명령을 수신하여 차량 하드웨어, 시스템 설정, 내비게이션 길안내, 장소 검색, 날씨 및 주행거리 조회, 앱 실행, 미디어 재생 등을 제어하기 위해 사용하는 **Tool Calling API 명세서**입니다. (현재 활성화 도구: 17개 / 비활성화: 1개)

---

## 1. 전체 도구 요약 표 (Tools Summary)

| 카테고리 | 도구명 (Tool Name) | 기능 설명 | 주요 매개변수 (Parameters) |
| :--- | :--- | :--- | :--- |
| **공조기** | **`control_air_conditioner`** | 에어컨/공조기 전원, 풍량, 온도, 순환, 성에제거, 모드, 바람방향 제어 | `power`, `wind_level`, `temperature`, `cycle_mode`, `mode`, `front_defrost`, `rear_defrost`, `wind_direction_face`, `wind_direction_foot`, `wind_direction_screen` |
| **시트/핸들** | **`control_seat`** | 운전석 및 조수석 시트 열선/통풍 단계 조절 | `position` (필수), `heating_level`, `ventilation_level` |
| **시트/핸들** | **`control_steering_wheel_heating`** | 스티어링 휠(핸들) 열선 켜기/끄기 | `power` (필수) |
| **바디/트렁크**| **`control_trunk`** | 트렁크 테일게이트 열기/닫기/정지 | `action` (필수) |
| **바디/창문** | **`control_window`** | 개별/구역/전체 창문 개방률 프리셋 및 0~100% 미세 제어 | `area` (필수), `action` (필수), `custom_percent` |
| **바디/창문** | **`control_sunroof`** | 선루프 개방/폐쇄/환기/정지 및 0~100% 미세 제어 | `action` (필수), `custom_percent` |
| **바디/창문** | **`control_sunshade`** | 썬쉐이드(햇빛 가리개/블라인드) 개방/폐쇄/절반/정지 및 0~100% 미세 제어 | `action` (필수), `custom_percent` |
| **조명/오디오**| ~~`control_ambient_light`~~ *(비활성화)* | 엠비언트 라이트(무드등) 전원, 밝기, 색상 제어 *(주석 처리됨)* | `action`, `brightness`, `color_name` |
| **조명/오디오**| **`control_volume`** | 시스템 오디오 볼륨(0~39) 조절 및 음소거 설정/해제 | `action` (필수), `value` |
| **내비게이션**| **`launch_naver_map`** / **`launch_tmap`** | 네이버 지도 또는 티맵 오토 실행 및 좌표 기반 길안내 | `destination` (필수), `latitude`, `longitude` |
| **위치/날씨/주행거리** | **`get_current_location`** | 차량의 실시간 GPS 위도(Latitude) / 경도(Longitude) 좌표 조회 | *(매개변수 없음)* |
| **위치/날씨/주행거리** | **`get_driving_range`** | 배터리 잔량으로 주행 가능한 현재 전기차(EV) 잔여 주행가능거리(km) 및 배터리 SOC(%) 조회 | *(매개변수 없음)* |
| **외부 Tool 앱(선택)** | **`ext_*_search_places`** | 사용자가 승인한 Tool 앱이 제공하는 장소/상호 검색. 실제 함수명은 제공자 ID에 따라 정해짐 | `query` (필수), `latitude`, `longitude`, `radius` |
| **위치/날씨/주행거리** | **`get_current_weather`** | GPS 좌표 기반 Open-Meteo 실시간 날씨/기온/강수확률 및 차량 외기온도 조회 | *(매개변수 없음)* |
| **검색** | **`search_news`** | Google News RSS를 통한 실시간 주요 뉴스 및 키워드 뉴스 검색 | `query` (필수) |
| **앱/미디어** | **`get_installed_apps`** | 단말에 설치된 실행 가능한 모든 앱 목록 및 패키지명 조회 | *(매개변수 없음)* |
| **앱/미디어** | **`launch_app`** | 지정 안드로이드 패키지명 앱 실행 및 인앱 검색어 입력 | `package_name` (필수), `query` |
| **앱/미디어** | **`control_media`** | 원격 미디어 재생, 일시정지, 다음곡/이전곡, 검색 재생 제어 | `action` (필수), `query`, `app_name` |

---

## 2. 카테고리별 상세 도구 명세

### 가. 차량 하드웨어 및 데이터 조회 (Vehicle Hardware & Status Tools)

#### 1) 에어컨/공조기 제어 (`control_air_conditioner`)
* **목적**: 차량 내부 공조(에어컨/히터) 전원, 바람 세기, 온도, 순환 모드, 성에 제거 및 바람 방향 통합 제어
* **주요 파라미터**:
  - `power` (boolean): 전원 ON/OFF
  - `wind_level` (integer, 1~7): 바람 세기 단계
  - `temperature` (number, 17.0~33.0): 희망 실내 온도 (0.5도 단위)
  - `cycle_mode` (string): 순환 모드 (`inner`: 내기순환, `outer`: 외기순환)
  - `mode` (string): 동작 모드 (`auto`: 자동, `cool`: 냉방, `heat`: 난방)
  - `front_defrost` / `rear_defrost` (boolean): 앞/뒷유리 성에 제거
  - `wind_direction_face` / `wind_direction_foot` / `wind_direction_screen` (boolean): 송풍 방향

#### 2) 시트 열선 및 통풍 제어 (`control_seat`)
* **목적**: 운전석 및 조수석 시트의 열선 및 통풍 강도 단계 조절
* **주요 파라미터**:
  - `position` (string, 필수): 제어할 좌석 위치 (`driver`: 운전석, `copilot`: 조수석)
  - `heating_level` (integer, 0~3): 시트 열선 단계 (0=OFF, 1=1단, 2=2단, 3=3단)
  - `ventilation_level` (integer, 0~3): 시트 통풍 단계 (0=OFF, 1=1단, 2=2단, 3=3단)

#### 3) 스티어링 휠 핸들 열선 제어 (`control_steering_wheel_heating`)
* **목적**: 스티어링 휠(핸들) 열선 기능 켜기/끄기
* **주요 파라미터**:
  - `power` (boolean, 필수): 열선 전원 (`true`: 켜기, `false`: 끄기)

#### 4) 트렁크 테일게이트 제어 (`control_trunk`)
* **목적**: 차량 뒷문(트렁크 테일게이트) 열기, 닫기 및 동작 정지
* **주요 파라미터**:
  - `action` (string, 필수): 동작 지정 (`open`: 열기, `close`: 닫기, `stop`: 정지)

#### 5) 측면 창문 제어 (`control_window`)
* **목적**: 개별/구역/전체 측면 창문의 개방 상태 및 0~100% 미세 개방률 제어
* **주요 파라미터**:
  - `area` (string, 필수): 제어 영역 (`all`: 전체, `driver`: 운전석, `copilot`: 조수석, `rear_left`: 뒷좌석 좌측, `rear_right`: 뒷좌석 우측, `front`: 앞좌석 전체, `rear`: 뒷좌석 전체)
  - `action` (string, 필수): 개방 동작 (`open`: 전체 열기, `close`: 전체 닫기, `half`: 절반(50%), `vent`: 환기(약 10%), `stop`: 정지, `custom`: 미세 수치 지정)
  - `custom_percent` (integer, 0~100): `action=custom`일 때의 개방 백분율

#### 6) 선루프 제어 (`control_sunroof`)
* **목적**: 차량 선루프의 개방/폐쇄/환기/정지 및 0~100% 미세 개방률 제어
* **주요 파라미터**:
  - `action` (string, 필수): 동작 (`open`: 열기, `close`: 닫기, `vent`: 틸트 환기, `stop`: 정지, `custom`: 미세 수치 지정)
  - `custom_percent` (integer, 0~100): 개방 백분율

#### 7) 썬쉐이드(햇빛 가리개) 제어 (`control_sunshade`)
* **목적**: 차량 썬쉐이드(햇빛 가리개/블라인드)의 개방/폐쇄/절반/정지 및 0~100% 미세 개방률 제어
* **주요 파라미터**:
  - `action` (string, 필수): 동작 (`open`: 열기, `close`: 닫기, `half`: 절반, `stop`: 정지, `custom`: 미세 수치 지정)
  - `custom_percent` (integer, 0~100): 개방 백분율

#### 8) EV 잔여 주행 가능 거리 조회 (`get_driving_range`)
* **목적**: 차량 통계 디바이스의 배터리 잔량(SOC %)과 잔여 주행가능거리(km) 반환

#### 9) 시스템 사운드 볼륨 제어 (`control_volume`)
* **목적**: 차량 오디오 시스템 볼륨 단계(0~39) 조절 및 음소거 설정/해제
* **주요 파라미터**:
  - `action` (string, 필수): 동작 (`up`: 증가, `down`: 감소, `set`: 수치 지정, `mute`: 음소거, `unmute`: 음소거 해제)
  - `value` (integer, 0~39): 설정할 볼륨 값

---

### 나. 내비게이션 및 길안내 도구 (Navigation Tools)

#### 1) 네이버 지도 실행 및 길안내 (`launch_naver_map`)
* **목적**: 네이버 지도 앱(`nmap://navigation`)을 호출하여 실시간 내비게이션 주행 시작
* **주요 파라미터**:
  - `destination` (string, 필수): 목적지 상호 또는 명칭
  - `latitude` (number, 필수): WGS84 위도
  - `longitude` (number, 필수): WGS84 경도

#### 2) 티맵 오토 실행 및 길안내 (`launch_tmap`)
* **목적**: 차량용 TMAP Auto 앱(`tmap://route`)을 호출하여 실시간 경로 안내 시작
* **주요 파라미터**:
  - `destination` (string, 필수): 목적지 상호 또는 명칭
  - `latitude` (number, 필수): WGS84 위도
  - `longitude` (number, 필수): WGS84 경도

---

### 다. 외부 도구 앱 연동 (External Tool Applications)

#### 1) 장소 및 상호 검색 (`ext_*_search_places`)
* **목적**: 독립적으로 설치된 외부 도구 앱([`BydSubAI-KorTools`](https://github.com/PoorGrammerA/BydSubAI-KorTools))을 통해 최신 상호/POI/장소를 검색하고 위도·경도 좌표 반환
* **주요 파라미터**:
  - `query` (string, 필수): 검색어 (예: "스타벅스", "서울역")
  - `latitude` / `longitude` (number): 기준 중심 좌표
  - `radius` (integer): 검색 반경(미터)

---

### 라. 위치 및 날씨 정보 (Location & Weather Tools)

#### 1) 실시간 GPS 위치 조회 (`get_current_location`)
* **목적**: 차량의 현재 GPS 위도, 경도 좌표 반환

#### 2) 실시간 날씨 조회 (`get_current_weather`)
* **목적**: 현재 위치 기반 실시간 기온, 체감온도, 날씨 코드, 강수확률 및 외기온도 조회

---

### 마. 앱 실행 및 미디어 제어 (App & Media Tools)

#### 1) 최신 뉴스 검색 (`search_news`)
* **목적**: Google News RSS를 기반으로 특정 키워드의 실시간 뉴스 헤드라인 및 요약 검색
* **주요 파라미터**:
  - `query` (string, 필수): 검색 키워드

#### 2) 설치된 앱 목록 조회 (`get_installed_apps`)
* **목적**: 차량 안드로이드 시스템에 설치된 실행 가능한 런처 앱 및 패키지명 목록 조회

#### 3) 특정 앱 실행 및 검색어 전달 (`launch_app`)
* **목적**: 지정된 패키지명의 앱을 실행하고 인앱 검색어를 전달
* **주요 파라미터**:
  - `package_name` (string, 필수): 실행 대상 앱 패키지명
  - `query` (string): 전달할 검색어

#### 4) 미디어 원격 제어 (`control_media`)
* **목적**: 오디오/비디오 미디어 세션 재생, 일시정지, 이전곡, 다음곡 및 검색어 재생 제어
* **주요 파라미터**:
  - `action` (string, 필수): 동작 (`play`, `pause`, `stop`, `next`, `previous`, `search_play`)
  - `query` (string): 검색 재생 시의 곡명 또는 검색어
  - `app_name` (string): 타겟 미디어 앱 (예: "melon", "flo", "spotify", "youtube")
