# Multilingual TTS training-data plan

## Training gate: BLOCKED

IndicVoices-R and LibriTTS are promising official CC-BY-4.0 sources, but this is not a training pass. Per-language manifests, archive checksums, source accessibility, corpus statistics, audio/transcript quality, vocabulary coverage, and complete derivative-model legal review remain unresolved.

## Provisional common sample-rate decision

**22,050 Hz is the provisional deployment target, subject to manifest audit and prototype listening tests.** It is a compromise between 16 kHz emergency intelligibility/CPU cost and LibriTTS's native 24 kHz quality. It is not an instruction to resample data now. The decision must be revisited after IndicVoices-R source sample-rate distribution, Android RTF, and native-listener scores are measured.

| Rate | Benefit | Cost / concern |
|---:|---|---|
| 16 kHz | smallest waveform/vocoder CPU and memory cost | may reduce fricative/high-frequency quality and naturalness |
| 22.05 kHz | provisional quality/CPU compromise; common neural-TTS target | requires validated resampling from source rates |
| 24 kHz | matches published LibriTTS corpus rate | higher output/vocoder cost; IndicVoices-R compatibility unknown |

## Feasibility status

| Item | Status |
|---|---|
| Total training hours | Indic total 1,704 plus English corpus 585 published; selected language totals UNKNOWN |
| Storage requirement | UNKNOWN until official archive metadata/manifests are acquired |
| Preprocessing duration | UNKNOWN / REQUIRES PROTOTYPE |
| GPU VRAM / GPU count | UNKNOWN / REQUIRES PROTOTYPE |
| Training duration | UNKNOWN / REQUIRES PROTOTYPE |
| Student size, Android RAM, RTF, quality | UNKNOWN / REQUIRES PROTOTYPE |

## Required pre-training sequence

1. Resolve official IndicVoices-R object-store access and obtain archive checksums or owner-provided immutable artifact metadata.
2. Acquire private manifests first; run committed manifest/transcript checks before bulk audio retrieval.
3. Review data-card, license, attribution, consent, and trained-model distribution terms with product/legal owners.
4. Compute exact per-language/speaker/audio/transcript/vocabulary reports and freeze speaker-disjoint splits.
5. Create and review the separate emergency evaluation set.
6. Only then estimate hardware/storage from actual manifests and authorize a small supervised student feasibility run.

No training, corpus transformation, APK packaging, or production MMS modification is authorized by this document.
