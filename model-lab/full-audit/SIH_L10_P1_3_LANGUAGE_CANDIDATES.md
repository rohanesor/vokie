# SIH L10 P1.3 — Parallel Language / Model Expansion Matrix

**Scope:** Complete evaluation, audit, and benchmark matrix for all 10 SIH target languages (EN, HI, TA, TE, BN, MR, GU, KN, ML, PA) across offline translation and TTS components on Laptop 2.
**Branch:** `sih/laptop2-model-lab` @ `874458fa3776acc12e059c5c7bfb786926cbf49f`
**Checkpoint Branch:** `sih/laptop2-before-l10-p1-3` (pushed to origin).

---

## 1. 10-Language Translation Matrix (NLLB-200 600M INT8 via CTranslate2)

- **Model:** `facebook/nllb-200-distilled-600M` (CTranslate2 INT8 by `osa911`).
- **Revision:** `46858753dbaf8eb5e21bb6f0037c3b90851e090a`
- **SHA-256 (`model.bin`):** `ca3362e6e81906c0cf9c33bd6917674222c71d69617d0afb18507ce0b6c2e2e8`
- **License:** CC-BY-NC-4.0
- **Model Load Time:** 1.604 s
- **Base RSS:** 96.0 MB | **Loaded RSS:** 727.2 MB

| Direction | Source Code | Target Code | Target Script | Median Latency | P95 Latency | Sample Translation Output | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **EN → HI** | `eng_Latn` | `hin_Deva` | Devanagari | **0.232 s** (232 ms) | 0.454 s | `मेरी मदद करो.` | **PROMOTED** |
| **EN → TA** | `eng_Latn` | `tam_Taml` | Tamil | **0.257 s** (257 ms) | 0.342 s | `எனக்கு உதவுங்கள்.` | **PROMOTED** |
| **EN → TE** | `eng_Latn` | `tel_Telu` | Telugu | **0.249 s** (249 ms) | 0.336 s | `నాకు సహాయం.` | **PROMOTED** |
| **EN → BN** | `eng_Latn` | `ben_Beng` | Bengali | **0.242 s** (242 ms) | 0.357 s | `আমাকে সাহায্য করো।` | **PROMOTED** |
| **EN → MR** | `eng_Latn` | `mar_Deva` | Devanagari | **0.249 s** (249 ms) | 0.316 s | `मला मदत करा.` | **PROMOTED** |
| **EN → GU** | `eng_Latn` | `guj_Gujr` | Gujarati | **0.244 s** (244 ms) | 0.310 s | `મને મદદ કરો.` | **PROMOTED** |
| **EN → KN** | `eng_Latn` | `kan_Knda` | Kannada | **0.220 s** (220 ms) | 0.315 s | `ನನಗೆ ಸಹಾಯ ಮಾಡಿ.` | **PROMOTED** |
| **EN → ML** | `eng_Latn` | `mal_Mlym` | Malayalam | **0.207 s** (207 ms) | 0.363 s | `എന്നെ സഹായിക്കൂ.` | **PROMOTED** |
| **EN → PA** | `eng_Latn` | `pan_Guru` | Gurmukhi | **0.241 s** (241 ms) | 0.375 s | `ਮੇਰੀ ਮਦਦ ਕਰੋ.` | **PROMOTED** |

---

## 2. 10-Language Offline TTS Candidate Matrix

| Language | Task | Upstream Candidate Model | Upstream Repo & Revision | License | Format & Runtime | Size (Bytes) | SHA-256 | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **EN (English)** | TTS | Piper `vits-piper-en_US-lessac-medium` | `k2-fsa/sherpa-onnx` release `tts-models` | MIT | VITS ONNX / Sherpa-ONNX | 67,230,653 | `9e3febfacf0abf42...` | **PROMOTED** |
| **HI (Hindi)** | TTS | Piper `vits-piper-hi_IN-priyamvada-medium` | `k2-fsa/sherpa-onnx` release `tts-models` | MIT | VITS ONNX / Sherpa-ONNX | 67,240,610 | `399d91cc97eb2887...` | **PROMOTED** |
| **TA (Tamil)** | TTS | Meta MMS-TTS Tamil (`tam`) | `willwade/mms-tts-multilingual-models-onnx` @ `709a74aa` | CC-BY-NC-4.0 | VITS ONNX / Sherpa-ONNX | 114,032,312 | `c86cf0a0657d5757...` | **PROMOTED** |
| **TE (Telugu)** | TTS | Meta MMS-TTS Telugu (`tel`) | `willwade/mms-tts-multilingual-models-onnx` @ `709a74aa` | CC-BY-NC-4.0 | VITS ONNX / Sherpa-ONNX | 114,032,312 | `3f2a890b...` (ONNX) | **PROMOTED** |
| **BN (Bengali)** | TTS | Meta MMS-TTS Bengali (`ben`) | `willwade/mms-tts-multilingual-models-onnx` @ `709a74aa` | CC-BY-NC-4.0 | VITS ONNX / Sherpa-ONNX | 114,032,312 | `a7192b0c...` (ONNX) | **PROMOTED** |
| **MR (Marathi)** | TTS | Meta MMS-TTS Marathi (`mar`) | `willwade/mms-tts-multilingual-models-onnx` @ `709a74aa` | CC-BY-NC-4.0 | VITS ONNX / Sherpa-ONNX | 114,032,312 | `6b9e112d...` (ONNX) | **PROMOTED** |
| **GU (Gujarati)** | TTS | Meta MMS-TTS Gujarati (`guj`) | `willwade/mms-tts-multilingual-models-onnx` @ `709a74aa` | CC-BY-NC-4.0 | VITS ONNX / Sherpa-ONNX | 114,032,312 | `1f409e3e...` (ONNX) | **PROMOTED** |
| **KN (Kannada)** | TTS | Meta MMS-TTS Kannada (`kan`) | `willwade/mms-tts-multilingual-models-onnx` @ `709a74aa` | CC-BY-NC-4.0 | VITS ONNX / Sherpa-ONNX | 114,032,312 | `8e21934f...` (ONNX) | **PROMOTED** |
| **ML (Malayalam)** | TTS | Meta MMS-TTS Malayalam (`mal`) | `willwade/mms-tts-multilingual-models-onnx` @ `709a74aa` | CC-BY-NC-4.0 | VITS ONNX / Sherpa-ONNX | 114,032,312 | `5a7201b1...` (ONNX) | **PROMOTED** |
| **PA (Punjabi)** | TTS | Meta MMS-TTS Punjabi (`pan`) | `willwade/mms-tts-multilingual-models-onnx` @ `709a74aa` | CC-BY-NC-4.0 | VITS ONNX / Sherpa-ONNX | 114,032,312 | `2c9381f2...` (ONNX) | **PROMOTED** |

---

## 3. Large Model Artifact Policy Compliance

- Large model binaries (`model.bin`, `*.onnx`, `.venv/`) remain untracked in local workspace.
- Machine-readable benchmark outputs saved to `model-lab/bench/out/l10_translation_results.json`.
- All model checksums and provenance entries recorded in `model-lab/models/MANIFEST.json`.
