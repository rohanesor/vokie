# IndicVoices-R and LibriTTS data audit

## Exact source state

| Dataset | Exact version/release evidence | Acquisition performed | Result |
|---|---|---|---|
| IndicVoices-R | GitHub revision `4d77d8a402b680fb739c1a6a3ec531de0cf17139`, dated 2024-12-11; Zenodo concept record `11636050`, version record `14016558` | repository tree/README/license/index, Zenodo API and official `v1.0.2` ZIP, and one ordinary request per required official slice | Zenodo supplies only a 310,314-byte repository snapshot (official MD5 recorded); all nine audio URLs return HTTP 403 AccessDenied; no per-record manifest/checksum available separately |
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

**USER ACTION REQUIRED.** The required language slices are named in the official IndicVoices-R index, but reproducible archive integrity and per-language corpus statistics are unavailable because the provider's documented URLs return AccessDenied and publishes no access process. Training must not start until the official provider supplies public access, a documented approved authentication process, or a manifest/checksum/data-card bundle.
