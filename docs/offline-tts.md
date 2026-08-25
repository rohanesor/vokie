# Offline MMS-TTS with sherpa-onnx

Vokie uses the official **sherpa-onnx 1.13.6 Android AAR** and its offline VITS API. The AAR is resolved at build time from the pinned official GitHub release (`sherpa-onnx-1.13.6.aar`, 49,097,942 bytes, SHA-256 `0012d9a28f15bd6fb966b62b70a75da3990512fdccce28b83098248ce4be1698`). The installed app has no Internet permission and contains no model downloader, cloud TTS, Android system-TTS fallback, or server integration.

Runtime architecture:

`Room-persisted received text → TextToSpeechUseCase → priority TtsPlaybackQueue → SherpaOnnxTtsEngine → MMS/VITS ONNX → float PCM → VokieAudioPlayer → AudioTrack`

The Bluetooth ACK remains immediately after successful Room persistence. TTS enqueueing happens afterwards and cannot delay or redefine peer receipt.

## Verified official MMS catalogue

The official sherpa-onnx `tts-models` GitHub release was inspected directly. It currently lists eight `vits-mms` archives in total (`nan`, `eng`, `spa`, `fra`, `tha`, `ukr`, `rus`, and `deu`). Only English overlaps Vokie's ten target languages. The following table deliberately does not invent package names for absent conversions.

| Vokie language | MMS ISO 639-3 | Official sherpa `vits-mms` package | Status |
|---|---:|---|---|
| English | `eng` | `vits-mms-eng.tar.bz2` | Implemented |
| Hindi | `hin` | Not listed | Official pre-converted package unavailable |
| Gujarati | `guj` | Not listed | Official pre-converted package unavailable |
| Marathi | `mar` | Not listed | Official pre-converted package unavailable |
| Kannada | `kan` | Not listed | Official pre-converted package unavailable |
| Malayalam | `mal` | Not listed | Official pre-converted package unavailable |
| Tamil | `tam` | Not listed | Official pre-converted package unavailable |
| Telugu | `tel` | Not listed | Official pre-converted package unavailable |
| Odia | `ory` | Not listed | Official pre-converted package unavailable |
| Bengali | `ben` | Not listed | Official pre-converted package unavailable |

Meta's original MMS catalogue contains those languages, but an original checkpoint is not the same as an official sherpa-onnx conversion. Vokie reports model unavailability for the nine absent conversions instead of silently substituting a model. Future verified packages can be added through `TtsLanguage`/`TtsModelPackage` without changing capture, queue, UI, inference, or playback layers.

## English model details

| Property | Verified value |
|---|---|
| Official archive | `vits-mms-eng.tar.bz2` |
| Archive size | 107,737,708 bytes |
| Archive SHA-256 | `8712cb52f71ee00bde27b8c18058d97a794fccf873c4629fbea0de87d31366b4` |
| ONNX file | `model.onnx` |
| ONNX size | 114,016,948 bytes |
| ONNX SHA-256 | `e3a198f6a4473429bab138be040e7cd40d2cab7a31b6410ff0a94d5a7fbbc254` |
| Tokens file | `tokens.txt` |
| Tokens size | 303 bytes |
| Tokens SHA-256 | `dff08580748be688d9112d62d6352422c56d372dfe34b24ea3f66fa1b75cfaa9` |
| Lexicon | Not required |
| Data directory | Not required |
| Sample rate | 16,000 Hz |
| Quantization | Unquantized FP32 ONNX; no official quantized `vits-mms-eng` variant was listed |
| Model license | Meta MMS weights: CC-BY-NC-4.0 |
| sherpa-onnx code | Apache-2.0 |

The CC-BY-NC-4.0 non-commercial restriction requires legal/product review before commercial distribution.

## Packaging decision

Vokie uses **Option C: APK plus separately distributed model packs**.

Bundling one 114 MB model would substantially increase every APK; bundling ten models would be unsuitable for low-end phones and normal Git. Runtime download is intentionally prohibited. Deployment therefore downloads the official archive outside Vokie, verifies its archive checksum, and creates a ZIP containing the unchanged `model.onnx` and `tokens.txt`. The user selects that ZIP through Android's system document picker. `TtsModelManager` performs bounded extraction and verifies each exact file size and SHA-256 before atomically installing it under:

`files/tts-models/<iso6393>/`

No model, model archive, generated PCM, or WAV file is committed. Installed models remain fully offline. Only one sherpa native context is loaded at a time; changing language releases the previous context before loading another.

## Playback and emergency behavior

Normal speech uses transient-may-duck audio focus and accessibility/speech audio attributes. SOS speech is placed at the front of the pending queue, can preempt normal playback, uses alarm usage with transient-exclusive focus, requests maximum application gain, and repeats twice. Vokie does **not** override system volume or claim non-interruptible playback. Explicit **Play message**, **Stop speech**, and **Acknowledged — stop alert** controls remain available while text is always readable.

PCM is played directly from memory with `AudioTrack` (`ENCODING_PCM_FLOAT`, mono). Audio is bounded to 120 seconds and no temporary WAV is created.

## Real host smoke measurement

The official sherpa-onnx 1.13.6 Linux binary and exact English package synthesized `Help me` successfully:

| Metric | Measured value |
|---|---:|
| Text length | 7 characters |
| sherpa synthesis time | 315 ms |
| Generated audio | 564 ms, 9,019 samples, mono 16 kHz |
| Synthesis RTF | 0.559 |
| Whole-process wall time including model startup | 2.57 s |
| Maximum host RSS | 171,072 KiB |

These are real development-host results, not Android performance claims. Android model load, first/subsequent synthesis, speaker playback, memory, and thermal behavior remain pending physical-device testing.
