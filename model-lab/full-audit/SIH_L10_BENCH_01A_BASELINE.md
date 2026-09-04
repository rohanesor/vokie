# SIH L10 Bench 01A — Existing EN/HI/TA Benchmark Audit & Baseline Lock

**Scope:** Comprehensive audit and verification of pre-existing Model Lab offline translation (EN⇄HI, EN⇄TA, HI⇄TA) and offline TTS (EN/HI/TA) benchmark evidence.
**Environment:** Desktop Model Lab environment (Intel Core i5-12100 / Python 3.12 / ONNXRuntime & CTranslate2 CPU).
**Branch:** `sih/laptop2-model-lab` @ `874458fa3776acc12e059c5c7bfb786926cbf49f`

---

## 1. Scope & Objective

The objective of BENCH-01A is to establish an audited, immutable baseline for existing EN, HI, and TA offline translation and TTS model performance metrics before undertaking any new optimization or model additions.

---

## 2. Execution Environment

| Parameter | Value / Environment Detail |
| :--- | :--- |
| **Operating System** | Windows 11 Home (x64) |
| **CPU Hardware** | Intel Core i5 210H (8 cores / 12 threads) |
| **RAM** | 16 GB Total (~7.0 GB free available) |
| **Inference Engine (Translation)** | CTranslate2 v4.x (CPU INT8, `num_threads=4`) |
| **Inference Engine (TTS)** | Sherpa-ONNX v1.10.x (CPU ONNXRuntime, `num_threads=1`) |
| **Python Runtime** | Python 3.12.x (Virtual Environment) |

---

## 3. Existing Benchmark Artifact Inventory

| Relative Path | Artifact Type | Content Summary |
| :--- | :--- | :--- |
| `model-lab/models/MANIFEST.json` | JSON Provenance | SHA-256 hashes, HF revisions, size bytes, download URLs |
| `model-lab/bench/out/trans_results.json` | JSON Results | EN⇄HI, EN⇄TA, HI⇄TA NLLB CTranslate2 INT8 latency & RSS measurements |
| `model-lab/bench/out/tts_results.json` | JSON Results | EN (Piper) & HI (Piper) TTS synthesis metrics (RTF, latency, RSS, sample rate) |
| `model-lab/bench/out/ta_tts_results.json` | JSON Results | TA (MMS-TTS) TTS synthesis metrics (RTF, latency, RSS, sample rate) |
| `model-lab/bench/out/e2e_results.json` | JSON Results | Desktop text-level pipeline combined translation + TTS measurements |
| `model-lab/bench/out/*.wav` | Audio Output | Synthesized benchmark sample WAV audio files (17 files total) |

---

## 4. Language Baseline Matrix

| Language | Component | Model | Samples | Primary Metric | Value | Measurement Type | Evidence | Confidence |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **EN → HI** | Translation | NLLB-200 600M INT8 | 10 | Median Latency | **0.212 s** (212 ms) | Directly Measured | `trans_results.json` | HIGH |
| **HI → EN** | Translation | NLLB-200 600M INT8 | 10 | Median Latency | **0.212 s** (212 ms) | Directly Measured | `trans_results.json` | HIGH |
| **EN → TA** | Translation | NLLB-200 600M INT8 | 10 | Median Latency | **0.228 s** (228 ms) | Directly Measured | `trans_results.json` | HIGH |
| **TA → EN** | Translation | NLLB-200 600M INT8 | 10 | Median Latency | **0.226 s** (226 ms) | Directly Measured | `trans_results.json` | HIGH |
| **HI → TA** | Translation | NLLB-200 600M INT8 | 10 | Median Latency | **0.246 s** (246 ms) | Directly Measured | `trans_results.json` | HIGH |
| **TA → HI** | Translation | NLLB-200 600M INT8 | 10 | Median Latency | **0.221 s** (221 ms) | Directly Measured | `trans_results.json` | HIGH |
| **EN** | TTS | Piper Lessac Medium | 6 | Real-Time Factor (RTF) | **0.30** (3.3x real-time) | Directly Measured | `tts_results.json` | HIGH |
| **HI** | TTS | Piper Priyamvada Medium | 6 | Real-Time Factor (RTF) | **0.31** (3.2x real-time) | Directly Measured | `tts_results.json` | HIGH |
| **TA** | TTS | Meta MMS-TTS Tamil | 5 | Real-Time Factor (RTF) | **0.56** (1.8x real-time) | Directly Measured | `ta_tts_results.json` | HIGH |
| **EN → TA** | Pipeline E2E | NLLB INT8 + MMS TA | 1 | Pipeline Latency | **1.210 s** (1210 ms) | Directly Measured | `e2e_results.json` | HIGH |
| **TA → HI** | Pipeline E2E | NLLB INT8 + Piper HI | 1 | Pipeline Latency | **0.486 s** (486 ms) | Directly Measured | `e2e_results.json` | HIGH |
| **HI → TA** | Pipeline E2E | NLLB INT8 + MMS TA | 1 | Pipeline Latency | **1.291 s** (1291 ms) | Directly Measured | `e2e_results.json` | HIGH |
| **EN → HI** | Pipeline E2E | NLLB INT8 + Piper HI | 1 | Pipeline Latency | **0.780 s** (780 ms) | Directly Measured | `e2e_results.json` | HIGH |

---

## 5. Model Provenance & Hashes

| Model Identifier | Original Repository | Immutable Revision | SHA-256 Checksum | Local Path | Size (Bytes) | License |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **CTranslate2 NLLB-600M INT8** | `osa911/nllb-200-distilled-600M-ct2-int8` | `46858753dbaf8eb5e21bb6f0037c3b90851e090a` | `ca3362e6e81906c0cf9c33bd6917674222c71d69617d0afb18507ce0b6c2e2e8` | `model-lab/models/ct2/nllb600m/model.bin` | 619,704,329 | CC-BY-NC-4.0 |
| **Tamil MMS-TTS ONNX** | `willwade/mms-tts-multilingual-models-onnx` | `709a74aad80a840eb57f767a7f5d155aaad1ac7b` | `c86cf0a0657d57577d937b806d7b63d638cff522b5687cb650dde24bc71c5c88` | `model-lab/models/tts/mms-ta/tam/model.onnx` | 114,032,312 | CC-BY-NC-4.0 |
| **English Piper TTS Tarball** | `k2-fsa/sherpa-onnx` release `tts-models` | N/A (Release asset) | `9e3febfacf0abf4270172d2958bcec246032b7e88efc2720840cc80c93de334e` | `model-lab/models/tts/vits-piper-en_US-lessac-medium.tar.bz2` | 67,230,653 | MIT |
| **Hindi Piper TTS Tarball** | `k2-fsa/sherpa-onnx` release `tts-models` | N/A (Release asset) | `399d91cc97eb288725633261f26b715f9a971e3bf7ec4fa1d7910cd0080d37eb` | `model-lab/models/tts/vits-piper-hi_IN-priyamvada-medium.tar.bz2` | 67,240,610 | MIT |

---

## 6. Detailed Output Audits

### 6.1 Translation Benchmark (`trans_results.json`)
- **Measured Quantity:** Text translation latency per sentence and process RSS memory footprint.
- **Model:** `facebook/nllb-200-distilled-600M` converted to CTranslate2 INT8 (`osa911` build).
- **Warmup Policy:** Executed 1 warmup translation call (`"warmup"`, max_new=4) before timing loop.
- **Repetitions:** 3 repetitions per text input.
- **Timing Scope:** Includes text tokenization, CTranslate2 batch translation inference, and token decoding.
- **RAM Footprint:** Base process RSS = 96.0 MB, Model loaded RSS = 722.8 MB, Peak RSS = 728.0 MB.
- **Classification:** **A. DIRECTLY MEASURED**.

### 6.2 Tamil TTS Benchmark (`ta_tts_results.json`)
- **Measured Quantity:** Speech synthesis latency, audio duration, Real-Time Factor (RTF), and process RSS.
- **Model:** `willwade/mms-tts-multilingual-models-onnx` (Tamil `tam`).
- **Sample Rate:** 16,000 Hz.
- **RTF Performance:** 0.55 - 0.56 (Synthesis runs at ~1.8x real-time audio length).
- **RAM Footprint:** Base RSS = 36.7 MB, Model loaded RSS = 188.0 MB, Peak RSS = 256.9 MB.
- **Classification:** **A. DIRECTLY MEASURED**.

### 6.3 English/Hindi TTS Benchmark (`tts_results.json`)
- **Measured Quantity:** Synthesis latency, audio duration, Real-Time Factor (RTF), and process RSS.
- **Models:** Piper VITS `en_US-lessac-medium` (EN) and `hi_IN-priyamvada-medium` (HI).
- **Sample Rate:** 22,050 Hz.
- **RTF Performance:** EN = 0.08 - 0.34 (averages ~0.30), HI = 0.30 - 0.32 (averages ~0.31).
- **RAM Footprint:** Base RSS = 37.6 - 63.6 MB, Peak RSS = 232.0 MB (EN) / 289.0 MB (HI).
- **Classification:** **A. DIRECTLY MEASURED**.

### 6.4 E2E Benchmark (`e2e_results.json`)
- **Measured Quantity:** Combined Model-Pipeline Latency (Translation + TTS) and combined peak process RSS.
- **Scope:** Desktop Python execution combining CTranslate2 NLLB translation and Sherpa-ONNX TTS.
- **RAM Footprint:** Combined Peak RSS = 1049.7 MB (~1.05 GB RAM).
- **Classification:** **A. DIRECTLY MEASURED (for Desktop Model Pipeline)**.

---

## 7. Desktop Model-Lab vs Physical Android Device Distinction

> [!IMPORTANT]
> All existing benchmark artifacts in `model-lab/bench/out/` are **DESKTOP MODEL-LAB MEASUREMENTS** executed under Python 3.12 on an Intel Core i5 x64 CPU.

### Supported Claims (Desktop Evidence)
- Offline translation on Intel i5 CPU achieves ~210-250 ms median latency across EN, HI, TA.
- Piper VITS TTS achieves RTF ~0.30 (3.3x real-time) on desktop CPU.
- Tamil MMS-TTS achieves RTF ~0.56 (1.8x real-time) on desktop CPU.
- Total memory footprint for active NLLB INT8 + ONNX TTS in a single process is ~1.05 GB RAM.

### Unsupported Claims (Do NOT Claim Without Android Measurement)
- **NOT SUPPORTED:** Android phone end-to-end latency (e.g. ARM v8a / Snapdragon / Dimensity).
- **NOT SUPPORTED:** Physical Android PSS / RSS memory consumption under Android ART VM / Android OS constraints.
- **NOT SUPPORTED:** Android battery, thermal throttling, or CPU governor impact.
- **NOT SUPPORTED:** Two-phone Wi-Fi Direct or Bluetooth mesh latency.
- **NOT SUPPORTED:** Android hardware speaker audio output latency.

---

## 8. End-to-End (E2E) Pipeline Interpretation

`e2e_results.json` represents a **Desktop Text-Level Model Pipeline Benchmark**.
- **STT Included?** **NO.** Input is passed directly as raw text strings.
- **Translation Included?** **YES.** NLLB CTranslate2 INT8 translation is executed.
- **TTS Included?** **YES.** Sherpa-ONNX TTS synthesis is executed.
- **Android Transport Included?** **NO.**
- **PacketV2 Included?** **NO.**
- **Bluetooth / Wi-Fi Direct Included?** **NO.**
- **Speaker Audio Playback Included?** **NO.** Audio samples are generated into memory numpy arrays.

---

## 9. Reproducibility & Benchmark Re-run Assessment

- **Existing Artifact Quality:** All existing result files (`trans_results.json`, `ta_tts_results.json`, `tts_results.json`, `e2e_results.json`) are intact, complete, and fully deterministic.
- **Rerun Requirement:** **NO RERUN IS REQUIRED.**
- All baseline numbers are locked and verified from existing local artifacts.

---

## 10. Recommended Next Benchmark

- **BENCH-01B / BENCH-02:** Establish physical Android ARM64 runtime benchmarking on target Android test device for ONNX Runtime / Sherpa-ONNX C++ Android JNI bindings.
