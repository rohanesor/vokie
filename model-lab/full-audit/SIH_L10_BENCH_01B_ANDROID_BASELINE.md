# SIH L10 Bench 01B — Physical Android ARM64 Baseline Audit & Status

**Scope:** Physical Android ARM64 runtime benchmarking for existing EN/HI/TA offline translation and TTS model stack.
**Target Environment:** Physical Android ARM64 Device (ADB).
**Branch:** `sih/laptop2-model-lab` @ `874458fa3776acc12e059c5c7bfb786926cbf49f`
**Safety Checkpoint Branch:** `sih/laptop2-before-bench-01b` (pushed to origin).

---

## 1. Executive Summary & Status

- **BENCH-01B STATUS:** **BLOCKED (Physical Android Device & SDK Environment Required on Laptop 1)**
- **Safety Checkpoint Created & Pushed:** `sih/laptop2-before-bench-01b` -> `origin/sih/laptop2-before-bench-01b` (`874458fa3776acc12e059c5c7bfb786926cbf49f`).

---

## 2. Blockage Diagnosis & Audit Findings

| Audit Check | Status | Detailed Finding |
| :--- | :--- | :--- |
| **Git Safety & Checkpoint** | **PASS** | Branch `sih/laptop2-before-bench-01b` established and pushed to `origin`. Working tree inspected. |
| **ADB Executable Availability** | **NOT INSTALLED** | `adb` CLI tool is not present in PATH or environment on Laptop 2 (Model Lab dedicated research host). |
| **Physical Android ARM64 Device** | **NOT CONNECTED** | No physical Android ARM64 device is attached via USB/ADB debugging to Laptop 2. |
| **Android App Gradle Build Environment** | **NOT PRESENT** | Gradle wrapper (`gradlew.bat`) and Android production project (`app/src/main/`) reside on Laptop 1 (`sih/laptop1-c1-c2-integration`) and are not present on `sih/laptop2-model-lab`. |

---

## 3. Physical Device Metric Matrix

| Parameter | Required Value | Actual Status |
| :--- | :--- | :--- |
| **Device Manufacturer** | Physical Device | **NOT CONNECTED** |
| **Device Model** | e.g. Pixel / Samsung / Xiaomi | **NOT CONNECTED** |
| **Android Version** | Android 10+ (Release) | **NOT CONNECTED** |
| **API Level / SDK** | API 29+ | **NOT CONNECTED** |
| **CPU ABI / Architecture** | `arm64-v8a` | **NOT MEASURED** |
| **RAM** | Physical Device RAM | **NOT MEASURED** |
| **Storage / Battery / Thermal** | Real-time sensor metrics | **NOT MEASURED** |

---

## 4. Benchmark Components Status (ARM64 Physical Device)

Pursuant to Section 14 Failure Policy: *If a component cannot be benchmarked, mark as NOT MEASURED.*

| Component | Metric | Status |
| :--- | :--- | :--- |
| **EN → HI Translation** | Cold/Warm Latency & PSS | **NOT MEASURED** |
| **HI → EN Translation** | Cold/Warm Latency & PSS | **NOT MEASURED** |
| **EN → TA Translation** | Cold/Warm Latency & PSS | **NOT MEASURED** |
| **TA → EN Translation** | Cold/Warm Latency & PSS | **NOT MEASURED** |
| **HI → TA Translation** | Cold/Warm Latency & PSS | **NOT MEASURED** |
| **TA → HI Translation** | Cold/Warm Latency & PSS | **NOT MEASURED** |
| **EN Piper TTS** | Synthesis Latency & RTF | **NOT MEASURED** |
| **HI Piper TTS** | Synthesis Latency & RTF | **NOT MEASURED** |
| **TA MMS-TTS** | Synthesis Latency & RTF | **NOT MEASURED** |
| **Android Process PSS** | `dumpsys meminfo` (Heap/Native) | **NOT MEASURED** |
| **CPU Utilization** | `adb shell top` | **NOT MEASURED** |
| **Thermal State** | Sustained load throttling | **NOT MEASURED** |
| **C2 T0–T8 Boundaries** | Pipeline handoffs | **NOT MEASURED** |

---

## 5. Machine-Readable Results Files Created

In accordance with Section 12, placeholder result manifests have been initialized in `model-lab/bench/out/`:
- `model-lab/bench/out/android_translation_results.json`
- `model-lab/bench/out/android_tts_results.json`
- `model-lab/bench/out/android_memory_results.json`
- `model-lab/bench/out/android_thermal_results.json`

---

## 6. Desktop (BENCH-01A) vs Android (BENCH-01B) Reference Comparison

| Metric Category | Desktop Baseline (BENCH-01A) | Physical Android Target (BENCH-01B) |
| :--- | :--- | :--- |
| **Host CPU** | Intel Core i5 210H (8 Cores) | Physical ARM64 Chipset (**NOT MEASURED**) |
| **Translation Median Latency** | 212 ms - 246 ms (CTranslate2 INT8 CPU) | Physical ARM64 Runtime (**NOT MEASURED**) |
| **EN / HI TTS RTF** | 0.30 - 0.31 (Sherpa-ONNX Piper) | Physical ARM64 Runtime (**NOT MEASURED**) |
| **TA TTS RTF** | 0.56 (Sherpa-ONNX MMS-TTS) | Physical ARM64 Runtime (**NOT MEASURED**) |
| **Peak RAM / Footprint** | 1049.7 MB (Process RSS) | Android PSS (`dumpsys meminfo`) (**NOT MEASURED**) |

> [!WARNING]
> Desktop benchmark numbers (212 ms translation, 0.30 RTF) must NEVER be presented as physical Android performance. Android performance can only be claimed once measured on physical ARM64 hardware via ADB.

---

## 7. Mandatory Prerequisites for Physical Execution (Next Steps)

To execute BENCH-01B on physical Android ARM64 hardware:
1. **Branch Integration:** Complete the selective sync (`git cherry-pick 874458f`) from `sih/laptop2-model-lab` onto `sih/laptop1-c1-c2-integration` on Laptop 1 (which houses the Android Gradle project and Android SDK/ADB setup).
2. **Device Connection:** Connect a physical Android ARM64 device via USB with ADB debugging enabled.
3. **APK Assembly & Measurement:** Build `./gradlew assembleDebug` on Laptop 1, deploy to device, and execute `dumpsys meminfo` / C2 timing benchmarks.
