> **Language**: **English** | [한국어 (Korean)](user_guide.ko.md)
> 
> This document is the user guide for BYD Sub AI.

# BYD Sub AI User Guide

This guide explains the key features, operation, and practical in-vehicle usage of **Lumi**, the unofficial voice assistant agent and alert sound system built for BYD vehicles.

---

## 🎙️ 1. Activating the Voice Assistant (Gemini Live)

Sub AI supports multiple activation triggers for safe and convenient access while driving.

### 1) Steering Wheel Button Shortcut
- **How it works**: Quickly toggle the left steering wheel **Menu button 6 times within 1 second** (a fast double-click toggle sequence) to connect or disconnect the assistant.
- **Audio prompt**: When connected, a character signature prompt chime indicates readiness for voice input.

### 2) Screen Transition Trigger (Launcher Integration)
- Listens for screen state transition broadcasts (`ENTER_POWER_SCREEN` ➔ `CLOSE_SCREEN_SAVER`) to automatically awaken the voice assistant right after vehicle startup or when switching away from the screensaver.

### 3) Home Screen Shortcuts (One-Tap Access)
- Android launcher shortcut icons for each character persona (**Lumi, KITT, Asurada**) allow starting a conversation directly with your chosen persona in a single tap.

---

## 🧭 2. Navigation & Place Search Pipeline

Sub AI connects with independent external Tool applications to ensure privacy while providing powerful local place searches.

### 1) Two-Stage Navigation Pipeline
1. **Place / POI Search**: When you request a destination (e.g., *"Find a nearby charging station"* or *"Navigate to Seoul Station"*), an approved external Tool APK ([**`BydSubAI-KorTools`**](https://github.com/PoorGrammerA/BydSubAI-KorTools)) queries local map APIs to quickly fetch accurate WGS84 geographic coordinates.
2. **Turn-by-Turn Navigation**: Once coordinates are resolved, Sub AI automatically launches **Naver Map** (`nmap://navigation`) or **TMAP Auto** (`com.tmap.auto.*`) in live navigation mode with the target destination preset.

### 2) First-Time External Tool Approval
- Korean place search requires installing the companion [`BydSubAI-KorTools`](https://github.com/PoorGrammerA/BydSubAI-KorTools) APK.
- When Sub AI detects the newly installed service, a **one-time approval prompt** appears. Once approved, external tools become available in all subsequent Gemini Live sessions.

---

## 🚗 3. Voice-Controlled Vehicle Hardware

With Gemini Live Function Calling, drivers can adjust car settings hands-free without looking away from the road.

### 1) Climate & A/C Control
- *"Turn on the A/C to 22 degrees"*, *"Set fan speed to 3"*, *"Switch to fresh air mode"*, *"Turn on front windshield defroster"*
- Adjusts power, temperature, fan speed, air circulation (recirculation/fresh air), defrosters, and air vent directions (face/foot/screen).

### 2) Seat & Steering Wheel Heating / Ventilation
- *"Turn driver seat warmer to level 2"*, *"Set passenger seat ventilation to max"*, *"Turn on steering wheel heater"*
- Controls driver and passenger seat heating/cooling levels (0–3) and steering wheel heating.

### 3) Sequential Window & Sunroof Control
- *"Close all windows"*, *"Open front windows halfway"*, *"Vent the sunroof"*
- **Safety Delay**: To prevent current overload lockouts in the vehicle Body Control Module (BCM), group window commands are executed **sequentially with a 150 ms delay** per window, ensuring reliable execution.

### 4) Battery State of Charge (SOC) & Driving Range
- *"What is our remaining battery percentage?"*, *"How many kilometers can we drive with the current charge?"*
- Reads real-time data from `BYDAutoStatisticDevice` to report high-voltage battery percentage and remaining EV range in kilometers.

---

## 🎵 4. Multimedia & App Launching

- *"Play the latest hits on Melon"* ➔ Opens Melon and searches songs automatically.
- *"Search driving music on YouTube"* ➔ Launches YouTube (official, ReVanced, or NewPipe) with search queries.
- *"Start Karaoke"* ➔ Launches in-car Stingray Karaoke via custom URL scheme (`stingraykaraoke://`).

---

## 💡 5. Ambient Light Voice Synchronization

- **Reactive Lighting**: While the assistant speaks, interior ambient lighting dynamically pulses across 6 brightness levels in real time based on voice volume.
- **Smooth Recovery**: When the assistant stops speaking, ambient lights smoothly return to their previous brightness and color setting.
- **Toggle**: Enable via `Sync Ambient Light with Gemini Live speech` in the settings screen.

---

## 🔊 6. Character Themes & Low-Latency Driving Alerts

Vehicle telemetry and sensor events trigger responsive voice alerts from your selected persona.

### 1) Three Character Personas
Select your preferred theme in the settings menu:
- **Lumi**: Friendly and conversational Korean assistant voice.
- **KITT**: Iconic Knight Rider AI persona.
- **Asurada**: Cyber Formula navigation computer persona.

### 2) Low-Latency (`SoundPool`) Telemetry Audio
- **Gear Shifts**: Immediate voice confirmation when shifting between P, R, N, and D.
- **Cruise Control**: Audible notifications when Adaptive Cruise Control (ACC) or Intelligent Cruise Control (ICC) engages or disengages.
- **Blind Spot Detection (BSD)**: Real-time warnings for side blind spots.
- **Regenerative Braking & Drive Modes**: Feedback when cycling energy recycling levels (standard/high), snow surface detection, and sports mode activation.
- **Reverse Radar**: Proximity alerts when reversing.

### 3) ⚠️ Slope Parking Warning (Slope Warning)
- When shifting to **P (Park) on an incline or slope**, the vehicle pitch sensor triggers an immediate safety advisory to prevent rollaways:
  - *"Slope parking risk detected. Please make sure the parking brake is engaged."*

---

## 📲 7. Acoustic API Key Setup (Web Key Sender)

Avoid tedious on-screen keyboard typing on the in-vehicle display:

1. Open the `key-sender` web app on your smartphone browser (via GitHub Pages or a local server).
2. Enter your Gemini API key and select your preferred model.
3. Verify that Sub AI on the vehicle head unit is in key-listening mode, then tap **Send**.
4. The smartphone speaker emits a **short GGwave acoustic signal** (3–5 seconds) that transmits credentials over audio waves. The key is securely received and encrypted inside Android Keystore.

---

## 🛠️ 8. Diagnostics & Power User Utilities

- **Local ADB Self-Granting**: Connects locally to `127.0.0.1:5555` on startup to automatically grant the `READ_LOGS` permission without needing an external PC.
- **Real-Time Logcat Console**: High-performance in-app viewer to filter, record, and export Sub AI logs and BYD vehicle framework logs.
