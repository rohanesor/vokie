# IndicVoices-R and LibriTTS data audit

## Exact source state

| Dataset | Exact version/release evidence | Acquisition performed | Result |
|---|---|---|---|
| IndicVoices-R | GitHub revision `4d77d8a402b680fb739c1a6a3ec531de0cf17139`, dated 2024-12-11; Zenodo record `11636050` | official repository tree, README, license, `data_links.txt`, and HTTP metadata request for each required official slice | Source index acquired; all nine official archive URLs returned HTTP 403; no per-record manifest/checksum available separately |
| LibriTTS | OpenSLR SLR60 | official landing page/archive index only; no corpus archive downloaded | Source index acquired; detailed manifests/checksums unavailable without archive acquisition |

## Required-language statistics

| Language | ISO | Dataset | Hours | Utterances | Speakers | Gender | Sample rate | Domains | License | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| Hindi | hi | IndicVoices-R | UNKNOWN | UNKNOWN | UNKNOWN | available in manifest, not acquired | UNKNOWN | UNKNOWN | CC-BY-4.0 declaration | Blocked metadata |
| Gujarati | gu | IndicVoices-R | UNKNOWN | UNKNOWN | UNKNOWN | available in manifest, not acquired | UNKNOWN | UNKNOWN | CC-BY-4.0 declaration | Blocked metadata |
| Marathi | mr | IndicVoices-R | UNKNOWN | UNKNOWN | UNKNOWN | available in manifest, not acquired | UNKNOWN | UNKNOWN | CC-BY-4.0 declaration | Blocked metadata |
| Kannada | kn | IndicVoices-R | UNKNOWN | UNKNOWN | UNKNOWN | available in manifest, not acquired | UNKNOWN | UNKNOWN | CC-BY-4.0 declaration | Blocked metadata |
| Malayalam | ml | IndicVoices-R | UNKNOWN | UNKNOWN | UNKNOWN | available in manifest, not acquired | UNKNOWN | UNKNOWN | CC-BY-4.0 declaration | Blocked metadata |
| Tamil | ta | IndicVoices-R | UNKNOWN | UNKNOWN | UNKNOWN | available in manifest, not acquired | UNKNOWN | UNKNOWN | CC-BY-4.0 declaration | Blocked metadata |
| Telugu | te | IndicVoices-R | UNKNOWN | UNKNOWN | UNKNOWN | available in manifest, not acquired | UNKNOWN | UNKNOWN | CC-BY-4.0 declaration | Blocked metadata |
| Odia | or | IndicVoices-R | UNKNOWN | UNKNOWN | UNKNOWN | available in manifest, not acquired | UNKNOWN | UNKNOWN | CC-BY-4.0 declaration | Blocked metadata |
| Bengali | bn | IndicVoices-R | UNKNOWN | UNKNOWN | UNKNOWN | available in manifest, not acquired | UNKNOWN | UNKNOWN | CC-BY-4.0 declaration | Blocked metadata |
| English | en | LibriTTS | approximately 585 total corpus hours | UNKNOWN | UNKNOWN | UNKNOWN | 24,000 Hz | read English | CC-BY-4.0 | Blocked detailed metadata |

IndicVoices-R public aggregate facts are 1,704 hours, 10,496 speakers, and 22 Indian languages. They must not be divided across languages without manifests.

## Audio and transcript audit result

No source audio or official per-record manifest was downloaded, as required by the initial metadata-only acquisition rule. Consequently, sample rate distribution, bit depth, channels, clipping, silence, noise, volume, corruption, transcript/audio mismatch, Unicode normalization, punctuation, numeric forms, code-switching, transliteration, and spelling consistency are **UNKNOWN**. The required automated audit tools are committed under `scripts/tts-data/`; they have not been run against data.

## Dataset training gate

**BLOCKED.** The required language slices are named in the official IndicVoices-R index, but reproducible archive integrity and per-language corpus statistics are unavailable. Training must not start until official access succeeds and private manifest/audio audits produce the required evidence.
