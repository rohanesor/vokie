# SIH-L10-P1.3 Language Candidates

Scope: the seven target languages remaining after the locked EN/HI/TA baseline:
Telugu (TE), Bengali (BN), Marathi (MR), Gujarati (GU), Kannada (KN), Malayalam
(ML), and Punjabi (PA). This is Laptop-2 Model-Lab evidence only. It is not an
Android measurement and it does not change Android production code.

The target-language specification found in the existing Model-Lab material is:
`EN, HI, TA, TE, BN, MR, GU, KN, ML, PA`.

## Decision Summary

| Language | Translation | TTS | STT |
|---|---|---|---|
| TE | PROMOTED | RESEARCH-ONLY | RESEARCH-ONLY |
| BN | PROMOTED | RESEARCH-ONLY | RESEARCH-ONLY |
| MR | PROMOTED | RESEARCH-ONLY | RESEARCH-ONLY |
| GU | PROMOTED | RESEARCH-ONLY | RESEARCH-ONLY |
| KN | PROMOTED | RESEARCH-ONLY | RESEARCH-ONLY |
| ML | PROMOTED | RESEARCH-ONLY | RESEARCH-ONLY |
| PA | PROMOTED | RESEARCH-ONLY | RESEARCH-ONLY |

PROMOTED means first-integration candidate for the specific Laptop-2 text
translation path. It does not mean Android production-ready. No TTS or STT
candidate is promoted because the artifact was not downloaded, loaded, and
benchmarked in this run.

## Translation Candidate

The same verified CTranslate2 artifact covers all seven languages. The official
Meta NLLB model card verifies the language codes, scripts, and single-sentence
multilingual coverage. The CTranslate2 repository is an immutable community
conversion of that model and was loaded and executed locally.

| Field | Verified value |
|---|---|
| Candidate model | `facebook/nllb-200-distilled-600M` via `osa911/nllb-200-distilled-600M-ct2-int8` |
| Task | Offline translation, direct EN -> TE/BN/MR/GU/KN/ML/PA |
| Immutable revision | `46858753dbaf8eb5e21bb6f0037c3b90851e090a` |
| Artifact | `model.bin`, 619,704,329 bytes, SHA-256 `ca3362e6e81906c0cf9c33bd6917674222c71d69617d0afb18507ce0b6c2e2e8` |
| Other required artifacts | `config.json` 223 bytes, SHA-256 `8f6496adfc930cbfecbe8281112197705c488fab47d34b4829b06d7f478909af`; `shared_vocabulary.json` 5,921,176 bytes, SHA-256 `af53bfd0e6f726209e7325e45b87ab3b14e5856f7d42d7b9be91de3287c45267`; `sentencepiece.bpe.model` 4,852,054 bytes, SHA-256 `14bb8dfb35c0ffdea7bc01e56cea38b9e3d5efcdcb9c251d6b40538e1aab555a` |
| Source | https://huggingface.co/osa911/nllb-200-distilled-600M-ct2-int8/tree/46858753dbaf8eb5e21bb6f0037c3b90851e090a |
| License | CC-BY-NC-4.0; non-commercial use only |
| Model-card restrictions | Research model; not released for production deployment; general-domain use; not medical/legal-domain or document translation; input lengths over 512 tokens may degrade; output is not certified translation |
| Supported language/script | `tel_Telu` Telugu, `ben_Beng` Bengali, `mar_Deva` Marathi, `guj_Gujr` Gujarati, `kan_Knda` Kannada, `mal_Mlym` Malayalam, `pan_Guru` Punjabi |
| Runtime | CTranslate2 4.8.2, CPU, INT8 weights automatically using `int8_float32` on this host |
| Offline status | PASS: local model and tokenizer loaded without network access during benchmark |
| Gate status | PROMOTED |

The official base model card and the conversion card are the evidence for the
license and usage terms:

- https://huggingface.co/facebook/nllb-200-distilled-600M/blob/f8d333a098d19b4fd9a8b18f94170487ad3f821d/README.md
- https://huggingface.co/osa911/nllb-200-distilled-600M-ct2-int8/blob/46858753dbaf8eb5e21bb6f0037c3b90851e090a/README.md

### Translation Benchmark

Benchmark file: `bench/out/sih_l10_p1_3_new_language_translation.json`.
The locked EN/HI/TA files were not overwritten or rerun. Each direction used
10 warm samples, beam size 4, maximum decode length 128, one inter-thread, and
four intra-threads. The cold value is model load plus one real decode. Peak RSS
is process RSS observed during this run.

| Direction | Cold load + warmup | Warm median | Warm p95 | Samples | Peak RSS | Status |
|---|---:|---:|---:|---:|---:|---|
| EN -> TE | 2.304 s | 0.258 s | 0.336 s | 10 | 728.9 MB | PROMOTED |
| EN -> BN | 2.304 s | 0.228 s | 0.336 s | 10 | 728.9 MB | PROMOTED |
| EN -> MR | 2.304 s | 0.246 s | 0.290 s | 10 | 728.9 MB | PROMOTED |
| EN -> GU | 2.304 s | 0.248 s | 0.331 s | 10 | 728.9 MB | PROMOTED |
| EN -> KN | 2.304 s | 0.243 s | 0.337 s | 10 | 728.9 MB | PROMOTED |
| EN -> ML | 2.304 s | 0.233 s | 0.383 s | 10 | 728.9 MB | PROMOTED |
| EN -> PA | 2.304 s | 0.256 s | 0.359 s | 10 | 728.9 MB | PROMOTED |

All seven output samples were non-degenerate and used the expected target
script. This is a smoke/quality gate, not a human translation-quality
certification.

Environment: Intel Core 5 210H host, 15,984.5 MB installed RAM, Windows 11
build 26200, Python 3.12.10, CTranslate2 4.8.2, CPU, one inter-thread and
four intra-threads. These are desktop Model-Lab measurements, not Android
measurements.

## TTS Candidates

Meta's official MMS-TTS repositories provide one VITS checkpoint per language.
The model cards verify language, script family, Transformers offline-capable
inference, and `CC-BY-NC-4.0`. The exact Hub revisions and model artifact
metadata below came from the immutable Hub tree API. LFS OIDs are the SHA-256
values for the downloadable `model.safetensors` blobs.

All seven are RESEARCH-ONLY. None was downloaded, loaded, converted to the
existing sherpa-onnx path, or benchmarked here. Therefore latency, RTF, peak
RSS, sample rate in the local runtime, and CPU suitability are UNKNOWN. No
large TTS binaries were added to the workspace or manifest.

| Language | Candidate / task | Revision | Artifact, size, SHA-256 | License | Runtime / offline | Status |
|---|---|---|---|---|---|---|
| TE | `facebook/mms-tts-tel`, TTS | `dea6807154acc01918581982dcd40a116882a14d` | `model.safetensors`, 145,248,248 bytes, `067ac7ad1632d214dec61bf78cd3c2921358284614f5a4063378cc1434a389cf` | CC-BY-NC-4.0 | Transformers 4.33+ / PyTorch; offline after acquisition, not locally verified | RESEARCH-ONLY |
| BN | `facebook/mms-tts-ben`, TTS | `0da99de6074c8829121cdabfbdba423af18e8e56` | `model.safetensors`, 145,255,160 bytes, `6a0e055ec13ecd0a07ead04dec7974a071846e64a9fe0c0b188f61b32a9bd5ba` | CC-BY-NC-4.0 | Transformers 4.33+ / PyTorch; offline after acquisition, not locally verified | RESEARCH-ONLY |
| MR | `facebook/mms-tts-mar`, TTS | `7af4a6db1df2eb20042d24cc7c180a492df1cc13` | `model.safetensors`, 145,254,392 bytes, `fb53c1d8cd642b1df939162c71f91fb75d40b9c919a860de2f171e46295312b9` | CC-BY-NC-4.0 | Transformers 4.33+ / PyTorch; offline after acquisition, not locally verified | RESEARCH-ONLY |
| GU | `facebook/mms-tts-guj`, TTS | `b72e80a7eeca90b72e0af2e2d00b77a336ce242d` | `model.safetensors`, 145,244,408 bytes, `f1f4e01188507d3cc8526d1326a6f1c8a9b51e5fd9abe7a92b500326808a0c6a` | CC-BY-NC-4.0 | Transformers 4.33+ / PyTorch; offline after acquisition, not locally verified | RESEARCH-ONLY |
| KN | `facebook/mms-tts-kan`, TTS | `30e3c5d533e8c559c10bf0d25637fea51b95bd7c` | `model.safetensors`, 145,255,928 bytes, `12a68748b7aeab553c8b145ab2de198617644eb89e5f0b7008a2f3a7cf91a9bd` | CC-BY-NC-4.0 | Transformers 4.33+ / PyTorch; offline after acquisition, not locally verified | RESEARCH-ONLY |
| ML | `facebook/mms-tts-mal`, TTS | `893b8c6442d6a630896d1d3ac0f429094ddfae82` | `model.safetensors`, 145,262,840 bytes, `a97a1e677ec67e05124b799dadd66630181fe9c29beb4e590454689ff8f698c5` | CC-BY-NC-4.0 | Transformers 4.33+ / PyTorch; offline after acquisition, not locally verified | RESEARCH-ONLY |
| PA | `facebook/mms-tts-pan`, TTS | `45d7962e8daba724f9ff251ee3198bdb47a5f498` | `model.safetensors`, 145,243,640 bytes, `071db9963578edff7be6b660e9fb69bb1f2aa3596d77d632b76a7f3353373977` | CC-BY-NC-4.0 | Transformers 4.33+ / PyTorch; offline after acquisition, not locally verified | RESEARCH-ONLY |

Evidence links:

- TE: https://huggingface.co/facebook/mms-tts-tel/tree/dea6807154acc01918581982dcd40a116882a14d
- BN: https://huggingface.co/facebook/mms-tts-ben/tree/0da99de6074c8829121cdabfbdba423af18e8e56
- MR: https://huggingface.co/facebook/mms-tts-mar/tree/7af4a6db1df2eb20042d24cc7c180a492df1cc13
- GU: https://huggingface.co/facebook/mms-tts-guj/tree/b72e80a7eeca90b72e0af2e2d00b77a336ce242d
- KN: https://huggingface.co/facebook/mms-tts-kan/tree/30e3c5d533e8c559c10bf0d25637fea51b95bd7c
- ML: https://huggingface.co/facebook/mms-tts-mal/tree/893b8c6442d6a630896d1d3ac0f429094ddfae82
- PA: https://huggingface.co/facebook/mms-tts-pan/tree/45d7962e8daba724f9ff251ee3198bdb47a5f498

## STT Candidate

| Field | Verified value |
|---|---|
| Candidate model | `facebook/mms-1b-all` with language adapters `tel`, `ben`, `mar`, `guj`, `kan`, `mal`, `pan` |
| Task | Offline ASR |
| Immutable revision | `3d33597edbdaaba14a8e858e2c8caa76e3cec0cd` |
| Artifact | Adapter files `adapter.<lang>.safetensors`; exact per-file size and SHA-256: UNKNOWN |
| Source | https://huggingface.co/facebook/mms-1b-all/tree/3d33597edbdaaba14a8e858e2c8caa76e3cec0cd |
| License | CC-BY-NC-4.0 |
| Supported languages | `tel`, `ben`, `mar`, `guj`, `kan`, `mal`, `pan` listed by the model card; scripts UNKNOWN |
| Runtime | Transformers / PyTorch, CPU/offline path documented; local execution not verified |
| Size and CPU suitability | UNKNOWN locally; Hub page reports a 29.2 GB repository and the 1B-parameter base is not suitable for promotion without a memory test |
| Benchmark status | NOT RUN; latency, repetitions, peak RSS, and sample rate UNKNOWN |
| Decision | RESEARCH-ONLY |

Evidence: https://huggingface.co/facebook/mms-1b-all/blob/3d33597edbdaaba14a8e858e2c8caa76e3cec0cd/README.md

## Gate Accounting

| Candidate family | Coverage | Offline load | Source/revision | SHA-256 | License | Benchmark | Decision |
|---|---|---|---|---|---|---|---|
| CT2 NLLB translation | PASS | PASS | PASS | PASS | PASS for non-commercial use | PASS | PROMOTED |
| Meta MMS TTS x7 | PASS | UNKNOWN locally | PASS | PASS from Hub metadata | PASS for non-commercial use | NOT RUN | RESEARCH-ONLY |
| Meta MMS STT adapters | PASS for language IDs | UNKNOWN locally | PASS | UNKNOWN per adapter | PASS for non-commercial use | NOT RUN | RESEARCH-ONLY |

The previously tested Xenova NLLB ONNX export remains a negative-result
artifact and was not rerun: it produced degenerate output and is not an
integration candidate (`REJECTED`, carried forward from the locked baseline).

## Large-Artifact and Manifest Policy

No new large model binary, ONNX file, BIN file, or WAV file was downloaded by
this pass. The seven MMS TTS artifacts and the MMS STT adapters are recorded
as remote candidates only. `models/MANIFEST.json` was not changed because no
new artifact passed the gate and no new artifact was acquired.

## Files and Provenance

Created or updated:

- `model-lab/bench/new_language_translation_bench.py`
- `model-lab/bench/out/sih_l10_p1_3_new_language_translation.json`
- `model-lab/full-audit/SIH_L10_P1_3_LANGUAGE_CANDIDATES.md`

The JSON result contains the full ten-sample latency arrays and generated
outputs for every new translation direction, plus CPU, RAM, OS, Python,
runtime, revision, thread configuration, cold load, and peak RSS. It is a
desktop Model-Lab result and must not be represented as an Android result.
