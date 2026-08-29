# Training-data balance and split plan

## Current result

No per-record manifests were available in Phase 2F or 2G. Phase 2G verified that the official Zenodo artifact is a repository snapshot only and that documented per-language audio URLs return HTTP 403 AccessDenied. Therefore hours/language, utterances/language, speakers/language, gender distribution, hours/speaker, average/median duration, dominant speakers, and single-speaker risks are all **UNKNOWN**. The aggregate IndicVoices-R total cannot establish balance for any target language.

## Required calculation

After official manifests are acquired privately, run:

```text
scripts/tts-data/audit_manifests.py PRIVATE_MANIFEST.jsonl --output .research/tts-data/balance.json
scripts/tts-data/audit_transcripts.py PRIVATE_MANIFEST.jsonl --output .research/tts-data/transcripts.json
scripts/tts-data/audit_audio.py PRIVATE_AUDIO_ROOT --output .research/tts-data/audio.json
```

The private JSONL schema is documented in `scripts/tts-data/audit_manifests.py` and requires file, language, speaker, duration, sample rate, format, transcript, source, license, and checksum. The committed `data/manifests/` indexes contain only source metadata, never corpus audio or speaker records.

## Balance policy to evaluate, not yet apply

- Use language-balanced batches, with temperature sampling over per-language utterance counts; choose the temperature only after observed imbalance is reported.
- Cap or weight speakers by hours/utterances so a dominant speaker cannot define a language embedding.
- Preserve low-resource language examples through oversampling only in training; retain natural held-out distributions for evaluation.
- Keep language identity explicit in model inputs and prevent speaker IDs from crossing speaker-disjoint splits.
- Report gender categories exactly as published; do not infer gender.

## Train/validation/test split

Split **by speaker** where speaker IDs are available. Assign speakers deterministically using a recorded hash seed, stratified by language and available gender/category. No speaker may occur in more than one split. Target ratios are 90/5/5 only if every language has enough speakers; otherwise record a language-specific exception rather than leaking speakers. English LibriTTS must use its official split identity where available and still be checked for speaker overlap.

Raw data remains immutable. The reproducible pipeline is:

```text
raw archives → checksum/format validation → source manifest → NFC analysis
→ language normalizer (sidecar output) → audio/silence/duration reports
→ speaker filtering report → balance plan → speaker-disjoint manifests
```

No original transcript or audio is overwritten; every transformation is a versioned sidecar manifest with source record IDs.
