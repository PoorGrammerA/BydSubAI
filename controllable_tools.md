> **Language**: **English** | [한국어 (Korean)](controllable_tools.ko.md)
> 
> This document is the technical specification of Gemini Live Function Calling tools in BYD Sub AI.

# Gemini Live Vehicle Controls & Function Tools Specification

This document details the **Function Calling (Tool) APIs** used by **BYD Sub AI** voice assistants (Lumi, KITT, Asurada) to control in-vehicle hardware, system settings, navigation, place search, weather, battery telemetry, and multimedia apps via natural language commands. (Currently active: 17 tools / Disabled: 1 tool)

---

## 1. Tools Summary Table

| Category | Tool Name | Description | Key Parameters |
| :--- | :--- | :--- | :--- |
| **Climate / HVAC** | **`control_air_conditioner`** | Controls HVAC power, fan speed, target temperature, circulation mode, defrosters, and vent directions. | `power`, `wind_level`, `temperature`, `cycle_mode`, `mode`, `front_defrost`, `rear_defrost`, `wind_direction_face`, `wind_direction_foot`, `wind_direction_screen` |
| **Seats / Wheel** | **`control_seat`** | Controls heating and ventilation levels for driver and front passenger seats. | `position` (required), `heating_level`, `ventilation_level` |
| **Seats / Wheel** | **`control_steering_wheel_heating`** | Toggles heated steering wheel ON/OFF. | `power` (required) |
| **Body / Tailgate**| **`control_trunk`** | Opens, closes, or halts the rear motorized tailgate. | `action` (required) |
| **Body / Windows** | **`control_window`** | Controls side window positions with presets or fine 0–100% percentages. | `area` (required), `action` (required), `custom_percent` |
| **Body / Windows** | **`control_sunroof`** | Opens, closes, tilts (vents), or sets sunroof percentage. | `action` (required), `custom_percent` |
| **Body / Windows** | **`control_sunshade`** | Opens, closes, sets half-open, or sets sunshade blind percentage. | `action` (required), `custom_percent` |
| **Lights / Audio** | ~~`control_ambient_light`~~ *(Disabled)* | Controls ambient light power, brightness, and color. *(Commented out)* | `action`, `brightness`, `color_name` |
| **Lights / Audio** | **`control_volume`** | Adjusts master system audio volume (0–39) and mute state. | `action` (required), `value` |
| **Navigation** | **`launch_naver_map`** / **`launch_tmap`** | Launches Naver Map or TMAP Auto for real-time turn-by-turn guidance with destination coordinates. | `destination` (required), `latitude`, `longitude` |
| **Status / Telemetry** | **`get_current_location`** | Retrieves real-time GPS latitude and longitude coordinates. | *(None)* |
| **Status / Telemetry** | **`get_driving_range`** | Retrieves current high-voltage battery State of Charge (SOC %) and remaining EV range (km). | *(None)* |
| **External Tools** | **`ext_*_search_places`** | Searches POI / business locations using approved external Tool apps (e.g. Kakao Places). | `query` (required), `latitude`, `longitude`, `radius` |
| **Status / Telemetry** | **`get_current_weather`** | Fetches real-time weather, temperature, rain probability, and vehicle exterior ambient temperature via Open-Meteo. | *(None)* |
| **News / Search** | **`search_news`** | Fetches live news headlines and summaries via Google News RSS. | `query` (required) |
| **Apps / Media** | **`get_installed_apps`** | Lists all launchable user apps and package names installed on the vehicle Android system. | *(None)* |
| **Apps / Media** | **`launch_app`** | Launches a specific application package with optional in-app query strings. | `package_name` (required), `query` |
| **Apps / Media** | **`control_media`** | Controls playback (play, pause, next, previous) and search playback across media players. | `action` (required), `query`, `app_name` |

---

## 2. Detailed Tool Specifications by Category

### A. Vehicle Hardware & Status Tools

#### 1) Climate & A/C (`control_air_conditioner`)
* **Purpose**: Comprehensive climate control (power, fan speed, temperature, air flow directions, defrosters).
* **Parameters**:
  - `power` (boolean): Master A/C power.
  - `wind_level` (integer, 1–7): Blower fan speed.
  - `temperature` (number, 17.0–33.0): Target cabin temperature in Celsius (0.5 increments).
  - `cycle_mode` (string): Air circulation (`inner` for recirculation, `outer` for fresh air).
  - `mode` (string): Operation mode (`auto`, `cool`, `heat`).
  - `front_defrost` / `rear_defrost` (boolean): Window defrosters.
  - `wind_direction_face` / `wind_direction_foot` / `wind_direction_screen` (boolean): Airflow distribution flags.

#### 2) Seat Heating & Ventilation (`control_seat`)
* **Purpose**: Adjust seat warmer and cooling intensity levels.
* **Parameters**:
  - `position` (string, required): Target seat (`driver` or `copilot`).
  - `heating_level` (integer, 0–3): Heater level (0=OFF, 1=Low, 2=Medium, 3=High).
  - `ventilation_level` (integer, 0–3): Cooling level (0=OFF, 1=Low, 2=Medium, 3=High).

#### 3) Heated Steering Wheel (`control_steering_wheel_heating`)
* **Purpose**: Toggles steering wheel heating element.
* **Parameters**:
  - `power` (boolean, required): Heating power (`true` for ON, `false` for OFF).

#### 4) Tailgate / Trunk (`control_trunk`)
* **Purpose**: Operates the rear motorized tailgate door.
* **Parameters**:
  - `action` (string, required): Action (`open`, `close`, `stop`).

#### 5) Side Windows (`control_window`)
* **Purpose**: Controls side window open positions with presets or percentage values.
* **Parameters**:
  - `area` (string, required): Window zone (`all`, `driver`, `copilot`, `rear_left`, `rear_right`, `front`, `rear`).
  - `action` (string, required): Action (`open`, `close`, `half`, `vent`, `stop`, `custom`).
  - `custom_percent` (integer, 0–100): Percentage opening when `action=custom`.
* **Note**: Multi-window requests are executed sequentially with a 150 ms delay per window to prevent BCM current overload lockouts.

#### 6) Sunroof (`control_sunroof`)
* **Purpose**: Controls the panoramic sunroof glass.
* **Parameters**:
  - `action` (string, required): Action (`open`, `close`, `vent`, `stop`, `custom`).
  - `custom_percent` (integer, 0–100): Percentage opening when `action=custom`.

#### 7) Sunshade Blind (`control_sunshade`)
* **Purpose**: Controls the interior sunshade curtain.
* **Parameters**:
  - `action` (string, required): Action (`open`, `close`, `half`, `stop`, `custom`).
  - `custom_percent` (integer, 0–100): Percentage opening when `action=custom`.

#### 8) EV Driving Range Telemetry (`get_driving_range`)
* **Purpose**: Reads battery State of Charge (SOC %) and remaining electric driving range (km) from `BYDAutoStatisticDevice`.

#### 9) System Volume (`control_volume`)
* **Purpose**: Controls master audio volume (0–39) and mute toggle.
* **Parameters**:
  - `action` (string, required): Action (`up`, `down`, `set`, `mute`, `unmute`).
  - `value` (integer, 0–39): Volume level when `action=set`.

---

### B. Navigation Tools

#### 1) Launch Naver Map (`launch_naver_map`)
* **Purpose**: Launches Naver Map (`nmap://navigation`) to start live GPS navigation.
* **Parameters**:
  - `destination` (string, required): Destination name.
  - `latitude` (number, required): WGS84 latitude.
  - `longitude` (number, required): WGS84 longitude.

#### 2) Launch TMAP Auto (`launch_tmap`)
* **Purpose**: Launches TMAP Auto (`tmap://route`) to start live in-vehicle navigation.
* **Parameters**:
  - `destination` (string, required): Destination name.
  - `latitude` (number, required): WGS84 latitude.
  - `longitude` (number, required): WGS84 longitude.

---

### C. External Tool Applications

#### 1) Place / POI Search (`ext_*_search_places`)
* **Purpose**: Queries places via independently signed companion apps ([`BydSubAI-KorTools`](https://github.com/PoorGrammerA/BydSubAI-KorTools)) using Kakao Local API and returns WGS84 coordinates.
* **Parameters**:
  - `query` (string, required): Search keyword (e.g., "Starbucks", "Charging station").
  - `latitude` / `longitude` (number): Reference center coordinates.
  - `radius` (integer): Search radius in meters.

---

### D. Location & Weather Tools

#### 1) Current GPS Location (`get_current_location`)
* **Purpose**: Retrieves live vehicle latitude and longitude coordinates.

#### 2) Current Weather (`get_current_weather`)
* **Purpose**: Retrieves temperature, apparent temperature, precipitation chance, and outdoor ambient temperature via Open-Meteo.

---

### E. App & Media Tools

#### 1) Search Live News (`search_news`)
* **Purpose**: Queries real-time news headlines and summaries via Google News RSS.
* **Parameters**:
  - `query` (string, required): Search keyword.

#### 2) List Installed Apps (`get_installed_apps`)
* **Purpose**: Returns the list of launchable user applications and package names installed on the head unit.

#### 3) Launch App (`launch_app`)
* **Purpose**: Launches an application by package name with an optional search query string.
* **Parameters**:
  - `package_name` (string, required): Target Android package name.
  - `query` (string): Query string to forward to the target app.

#### 4) Media Remote Control (`control_media`)
* **Purpose**: Controls media player sessions (play, pause, next, previous, search playback).
* **Parameters**:
  - `action` (string, required): Action (`play`, `pause`, `stop`, `next`, `previous`, `search_play`).
  - `query` (string): Search query or song title.
  - `app_name` (string): Target media app (e.g., "melon", "flo", "spotify", "youtube").
