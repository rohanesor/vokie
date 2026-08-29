# AI4Bharat `vits_rasa_13` evaluation

## Status: blocked pending approved, pinned source artifact

`vits_rasa_13` is the primary multilingual-TTS research candidate for iTantra. It is **not** a production dependency, is **not** bundled in the APK, and has **not** been downloaded, converted, benchmarked, or declared Android-compatible by this repository.

This status is intentional. No approved model-source workflow in the repository identifies an official AI4Bharat artifact URL, immutable revision, SHA-256, license text, tokenizer/frontend assets, language/speaker mapping, or redistribution approval for this candidate. The existing private-model workflow is specific to the current Whisper/MMS archive and must not be repurposed to fetch an unspecified model.

Do not infer facts about `vits_rasa_13` from its name or from third-party conversions.

## Evidence required before acquisition

An approved model-source change must record all of the following before any artifact is retrieved:

| Required evidence | Required record |
|---|---|
| Official source | AI4Bharat-controlled release/model card/repository URL |
| Immutable identity | commit, release tag, or content-addressed revision |
| Artifact inventory | exact filenames, sizes, SHA-256, and required non-weight assets |
| License | full license text and a product/legal determination for redistribution and intended use |
| Language coverage | official evidence for English, Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali |
| Voice routing | language IDs, speaker IDs, and default voice policy |
| Text frontend | normalization, script handling, tokenizer/phonemizer, language-specific dependencies, licenses |
| Runtime constraints | PyTorch, GPU, CPU, Python, custom operators, sample rate, vocoder details |
| Distribution approval | whether original and derived/ONNX/quantized weights may be bundled in the APK |

The source artifact must be downloaded in a controlled build/research environment, verified against its upstream checksum when available, and recorded in a new manifest separate from the existing `vokie-models-v1.0.0.tar.zst` supply chain. Never commit model binaries to Git.

## Questions not yet proven

The following must remain **unknown** until official artifacts are inspected:

- model topology and parameter count;
- whether the model is one multilingual acoustic/vocoder runtime or a collection of language-specific components;
- exact supported language and language-ID coverage;
- speaker-ID behavior and voice availability;
- sample rate and PCM output format;
- tokenizer/text-normalization/phonemizer dependencies;
- CPU-only latency and memory on Android;
- ONNX exportability and operator compatibility;
- ONNX Runtime Mobile compatibility;
- sherpa-onnx VITS configuration compatibility;
- quality impact of FP16/INT8 quantization;
- license and redistribution compatibility.

## Required artifact audit

Once approved assets are available, produce the following evidence in this document and a machine-readable research manifest:

1. Enumerate every source file; calculate SHA-256 and sizes.
2. Inspect checkpoint/config/tokenizer/speaker/language files without executing untrusted code.
3. Record architecture, parameter count, language IDs, speaker IDs, sample rate, phonemizer/tokenizer, and vocoder requirements from source/configuration.
4. Record license text, source revision, and redistribution determination.
5. Determine whether an existing official sherpa-onnx package exists. If not, document why it is incompatible or absent rather than claiming compatibility.

## Android conversion decision tree

Conversion is attempted only after the audit and legal gates pass.

1. **Official sherpa-onnx-compatible package:** validate complete assets, exact frontend behavior, Android CPU inference, and all target languages.
2. **ONNX Runtime Mobile:** export only through a documented reproducible toolchain; validate ONNX graphs with `onnx.checker`, record opset/custom operators, and run CPU inference on Android.
3. **Custom native runtime:** consider only if the first two options fail and only with a maintainable tokenizer/frontend and license-compatible dependencies.

Python/PyTorch is never an Android runtime dependency. An ONNX file alone is insufficient: tokenizer/frontend, language/speaker routing, and generated PCM must all be validated.

## Benchmark protocol

Compare the candidate with the existing MMS-TTS baseline using identical versioned sentences and target devices. At minimum, every target language requires:

- a source sentence in the target script, normalized input, generated WAV/PCM hash, and listening evaluation record;
- model/tokenizer/runtime payload size;
- cold model initialization, warm synthesis latency, generated duration, RTF, peak and steady-state PSS/RSS, CPU, and repeated synthesis behavior;
- language-switch behavior with one active inference session;
- emergency-message intelligibility/pronunciation/naturalness review;
- airplane-mode test after a clean install.

FP32, FP16 where supported, and INT8 where supported must each pass graph validation, runtime inference, audio sanity, and quality gates before size/performance results are compared. INT8 is not presumed acceptable.

## Selection gate

`vits_rasa_13` can become the production TTS engine only when it has:

1. verified official provenance and license/redistribution approval;
2. complete coverage of all ten target languages or a documented safe bundled fallback that still meets the one-APK/no-download requirement;
3. reproducible Android inference with no Python, cloud, or runtime download;
4. measured quality and low-end-device performance that beats or justifies replacing MMS-TTS;
5. a reproducible build/staging/checksum process and APK content verification.

Until then, MMS-TTS remains the current baseline, not a final model-selection decision.
