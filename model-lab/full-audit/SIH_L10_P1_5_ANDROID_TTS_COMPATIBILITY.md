# SIH-L10-P1.5 Android TTS Compatibility Audit

Date: 2026-09-04

This is a compatibility and integration audit only. It does not add Android
source, model binaries, APK assets, or release-manifest entries. P1.4 desktop
promotion is not an Android approval.

## Executive Decision

The seven P1.4 candidates are official Meta MMS-TTS VITS checkpoints in
`model.safetensors` form. The current Android implementation accepts a
sherpa-onnx offline VITS graph plus a character-token map:

```text
tts/<iso6393>/model.onnx
tts/<iso6393>/tokens.txt
```

Therefore none of the seven is `DIRECT-COMPATIBLE` in its current artifact
form. Six are `ADAPTATION-REQUIRED`: they need deterministic conversion to
ONNX, token-map generation, and validation with the pinned Android runtime.
Punjabi is `BLOCKED` for current Vokie routing because `VokieLanguage` and
`TtsLanguage` have no Punjabi entry, even though its model can follow the same
technical conversion path.

| Language | App code | P1.4 source | Current classification | Reason |
|---|---|---|---|---|
| Telugu | `tel` | `facebook/mms-tts-tel` | `ADAPTATION-REQUIRED` | Safetensors VITS checkpoint; requires ONNX and character token-map conversion |
| Bengali | `ben` | `facebook/mms-tts-ben` | `ADAPTATION-REQUIRED` | Same format gap; P1.4 desktop RTF is 2.587 and requires an Android performance gate |
| Marathi | `mar` | `facebook/mms-tts-mar` | `ADAPTATION-REQUIRED` | Same format gap; P1.4 desktop RTF is 2.544 and requires an Android performance gate |
| Gujarati | `guj` | `facebook/mms-tts-guj` | `ADAPTATION-REQUIRED` | Safetensors VITS checkpoint; requires ONNX and character token-map conversion |
| Kannada | `kan` | `facebook/mms-tts-kan` | `ADAPTATION-REQUIRED` | Safetensors VITS checkpoint; requires ONNX and character token-map conversion |
| Malayalam | `mal` | `facebook/mms-tts-mal` | `ADAPTATION-REQUIRED` | Safetensors VITS checkpoint; requires ONNX and character token-map conversion |
| Punjabi | `pan` | `facebook/mms-tts-pan` | `BLOCKED` | No `PAN`/Punjabi value exists in the current message or TTS language enums |

`RESEARCH-ONLY` is appropriate for the unconverted source files themselves.
It is not a release classification for any of the seven. No candidate has
been declared Android-ready.

## Android Runtime Contract

Evidence inspected in the existing app:

| Contract | Current value |
|---|---|
| sherpa dependency | Official `com.k2fsa:sherpa-onnx:1.13.6@aar` |
| Android target | `compileSdk 34`, `minSdk 24`, `arm64-v8a` only |
| model API | `OfflineTts` with `OfflineTtsConfig` and `OfflineTtsModelConfig` |
| model type | `OfflineTtsVitsModelConfig` |
| required files | `model.onnx` and `tokens.txt` |
| frontend | Character/token map; `lexicon`, `dataDir`, and `dictDir` are empty |
| provider | CPU |
| inference threads | 2 by default, bounded to 1..4 |
| sentence batching | `maxNumSentences = 1` |
| speaker | `sid = 0` |
| audio | Generated float PCM, model-reported sample rate, mono playback through `AudioTrack` |
| loading | One active language context; filesystem paths after APK extraction and checksum verification |

The Kotlin engine does not load Transformers, PyTorch, safetensors, Hugging
Face tokenizer files, or a phonemizer on device. A converted package must
already provide the integer token IDs expected by the ONNX graph through
`tokens.txt`; no `dataDir` should be added for the MMS character frontend.

## Candidate Evidence

All rows below are desktop-only evidence from
`SIH_L10_P1_4_TTS_CANDIDATE_MATRIX.md` and
`sih_l10_p1_4_tts_results.json`. Every source is CC-BY-NC-4.0. Dataset and
voice-specific terms remain unknown, so commercial distribution is not
approved.

| Code | Immutable revision | Source bytes | Source SHA-256 | Sample rate | Warm median / RTF | Desktop peak RSS |
|---|---|---:|---|---:|---:|---:|
| `tel` | `dea6807154acc01918581982dcd40a116882a14d` | 145,248,248 | `067ac7ad1632d214dec61bf78cd3c2921358284614f5a4063378cc1434a389cf` | 16,000 | 669.8 ms / 0.330 | 549.1 MB |
| `ben` | `0da99de6074c8829121cdabfbdba423af18e8e56` | 145,255,160 | `6a0e055ec13ecd0a07ead04dec7974a071846e64a9fe0c0b188f61b32a9bd5ba` | 16,000 | 4,703.6 ms / 2.587 | 545.6 MB |
| `mar` | `7af4a6db1df2eb20042d24cc7c180a492df1cc13` | 145,254,392 | `fb53c1d8cd642b1df939162c71f91fb75d40b9c919a860de2f171e46295312b9` | 16,000 | 4,170.8 ms / 2.544 | 547.6 MB |
| `guj` | `b72e80a7eeca90b72e0af2e2d00b77a336ce242d` | 145,244,408 | `f1f4e01188507d3cc8526d1326a6f1c8a9b51e5fd9abe7a92b500326808a0c6a` | 16,000 | 430.1 ms / 0.364 | 560.0 MB |
| `kan` | `30e3c5d533e8c559c10bf0d25637fea51b95bd7c` | 145,255,928 | `12a68748b7aeab553c8b145ab2de198617644eb89e5f0b7008a2f3a7cf91a9bd` | 16,000 | 666.7 ms / 0.328 | 565.9 MB |
| `mal` | `893b8c6442d6a630896d1d3ac0f429094ddfae82` | 145,262,840 | `a97a1e677ec67e05124b799dadd66630181fe9c29beb4e590454689ff8f698c5` | 16,000 | 507.6 ms / 0.341 | 560.9 MB |
| `pan` | `45d7962e8daba724f9ff251ee3198bdb47a5f498` | 145,243,640 | `071db9963578edff7be6b660e9fb69bb1f2aa3596d77d632b76a7f3353373977` | 16,000 | 570.8 ms / 0.328 | 560.8 MB |

The P1.4 benchmark used Transformers 5.16.1, PyTorch 2.14.0+cpu, and
`sherpa_onnx` 1.13.7 only as an installed host package. It did not execute
these safetensors models through sherpa-onnx and did not measure Android.

## Compatibility Findings

### Model graph

- MMS VITS architecture is conceptually aligned with the app's offline VITS
  API, but source architecture alone does not establish graph compatibility.
- The conversion must produce the same practical contract as the existing
  approved graphs: opset 13, integer `x` and `x_length` inputs, float noise and
  length controls, and float `y` output.
- The output graph must pass `onnx.checker`, deterministic host inference, and
  `OfflineTtsConfig.validate()`/construction using the exact pinned runtime.
- P1.4 source weights must never be staged into `app/src/main/assets`.

### Tokenizer and frontend

- P1.4 used `AutoTokenizer` with local `vocab.json` and tokenizer config.
- The Android app has no Transformers tokenizer. Conversion must reproduce the
  MMS character token IDs in `tokens.txt`, including unknown/punctuation
  behavior, and verify representative native-script sentences.
- The existing Tamil precedent uses no espeak-ng data. The same no-phonemizer
  assumption is the target for these MMS conversions, but it must be proven
  per generated package rather than inferred from the source checkpoint.

### Audio

- P1.4 reports 16 kHz output for all seven candidates.
- The device test must confirm positive sample rate, non-empty audio, sensible
  duration, finite samples, no clipping, and playback through the existing
  float PCM path.
- Generated audio duration and synthesis time must be recorded separately;
  desktop latency cannot be presented as Android latency.

### Packaging

- `TtsModelManager` accepts only `model.onnx` and `tokens.txt` and verifies
  exact byte sizes against the protected manifest.
- `stage-bundled-models.py` requires the full ten-language manifest but stages
  only the base English package into the APK. The six currently routable new
  language packages therefore belong in the protected release archive and an
  explicitly approved packaging flow, not in Git. The current app has no
  downloader/importer; adding one is outside this audit.
- `model-lab/models/MANIFEST.json` and the production manifest must not be
  changed during this documentation phase.

## Required Gates Before Any Android Promotion

1. Convert each source checkpoint in an isolated, reproducible environment.
2. Record source revision, source hash, conversion tool versions, output file
   sizes, output hashes, and the exact token-map procedure.
3. Run graph validation and host sherpa-onnx synthesis for all five standard
   phrases per language.
4. Run the same package with sherpa-onnx 1.13.6, not only the host 1.13.7
   package used by the P1.4 environment.
5. Build a debug ARM64 APK with staged private assets and test initialization,
   switching, synthesis, stop, release, extraction, and checksum rejection on
   a physical ARM64 device.
6. Measure first load, warm synthesis, RTF, native/Java memory, audio output,
   thermal behavior, and interrupted extraction on the target device.
7. Human-review the five native-script samples and obtain legal confirmation
   for CC-BY-NC-4.0 plus unknown dataset/voice terms.
8. Resolve Punjabi product routing before assigning it a Vokie language code.

## Sources

- `app/build.gradle.kts`
- `app/src/main/java/com/vokie/tts/TtsModels.kt`
- `app/src/main/java/com/vokie/tts/TtsModelManager.kt`
- `app/src/main/java/com/vokie/tts/SherpaOnnxTtsEngine.kt`
- `app/src/main/java/com/vokie/domain/model/Models.kt`
- `scripts/stage-bundled-models.py`
- `docs/offline-tts.md`
- `docs/production-benchmark.md`
- `model-lab/full-audit/SIH_L10_P1_4_TTS_CANDIDATE_MATRIX.md`
- `model-lab/bench/out/sih_l10_p1_4_tts_results.json`
