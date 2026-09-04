# SIH-L10-P1.5 Android TTS Artifact Plan

Date: 2026-09-04

This plan defines the future conversion and packaging work for the seven P1.4
MMS-TTS candidates. It is intentionally documentation-only: no model binaries
are copied, no Android source is changed, and no production manifest is edited
in P1.5.

## Target Artifact Contract

For each language accepted by the app, produce exactly:

```text
models/tts/<iso6393>/model.onnx
models/tts/<iso6393>/tokens.txt
```

The target package must load with the existing Kotlin configuration:

```text
OfflineTtsVitsModelConfig(
    model = ".../model.onnx",
    tokens = ".../tokens.txt",
    lexicon = "",
    dataDir = "",
    dictDir = "",
    lengthScale = 1.0
)
```

No `model.safetensors`, PyTorch checkpoint, Hugging Face tokenizer files,
espeak-ng data, temporary WAV, or Python runtime belongs in the Android pack.

## Source-to-Target Inventory

| Target code | Source repository | Source revision | Source artifact | Target output | Integration status |
|---|---|---|---|---|---|
| `tel` | `facebook/mms-tts-tel` | `dea6807154acc01918581982dcd40a116882a14d` | `model.safetensors` | `tts/tel/model.onnx`, `tts/tel/tokens.txt` | Adaptation required |
| `ben` | `facebook/mms-tts-ben` | `0da99de6074c8829121cdabfbdba423af18e8e56` | `model.safetensors` | `tts/ben/model.onnx`, `tts/ben/tokens.txt` | Adaptation required; slow desktop gate |
| `mar` | `facebook/mms-tts-mar` | `7af4a6db1df2eb20042d24cc7c180a492df1cc13` | `model.safetensors` | `tts/mar/model.onnx`, `tts/mar/tokens.txt` | Adaptation required; slow desktop gate |
| `guj` | `facebook/mms-tts-guj` | `b72e80a7eeca90b72e0af2e2d00b77a336ce242d` | `model.safetensors` | `tts/guj/model.onnx`, `tts/guj/tokens.txt` | Adaptation required |
| `kan` | `facebook/mms-tts-kan` | `30e3c5d533e8c559c10bf0d25637fea51b95bd7c` | `model.safetensors` | `tts/kan/model.onnx`, `tts/kan/tokens.txt` | Adaptation required |
| `mal` | `facebook/mms-tts-mal` | `893b8c6442d6a630896d1d3ac0f429094ddfae82` | `model.safetensors` | `tts/mal/model.onnx`, `tts/mal/tokens.txt` | Adaptation required |
| `pan` | `facebook/mms-tts-pan` | `45d7962e8daba724f9ff251ee3198bdb47a5f498` | `model.safetensors` | `tts/pan/model.onnx`, `tts/pan/tokens.txt` | Technically adaptable; app routing blocked |

The source artifact sizes and hashes are recorded in the compatibility audit.
Target sizes and hashes are intentionally `TBD` until conversion and output
verification are complete.

## Conversion Procedure

Run one language at a time in a clean, pinned environment. The converter must:

1. Fetch only the immutable source revision and verify the P1.4 source
   `model.safetensors` hash before loading it.
2. Load the local MMS tokenizer and VITS checkpoint with no network access
   after acquisition.
3. Export the VITS inference graph to ONNX with fixed, documented export
   settings. Preserve the model's character-token input semantics and output
   sample rate.
4. Generate `tokens.txt` from the exact tokenizer vocabulary in the integer ID
   order consumed by the exported graph. Record handling for blank, unknown,
   whitespace, native-script characters, and punctuation.
5. Verify that the graph uses the sherpa-compatible input/output names and
   types. Reject exports needing an unplanned custom frontend or runtime.
6. Run five native-script phrases through the converted package with host
   sherpa-onnx 1.13.6 and save temporary listening WAVs outside Git.
7. Compare text-to-token behavior and basic audio properties against the
   Transformers reference. A matching waveform is not required, but token
   coverage, duration sanity, finite samples, and intelligibility are required.
8. Remove all intermediate checkpoint, tokenizer, cache, and WAV files from
   the release staging directory before packaging.

The existing Tamil conversion is a procedural precedent, not proof that an
export of these seven checkpoints will succeed. The conversion toolchain must
be pinned in the artifact record, including Python, PyTorch, Transformers,
ONNX exporter, ONNX opset, and sherpa-onnx versions.

## Per-Artifact Verification

Each target language pack must have a machine-readable record containing:

| Field | Requirement |
|---|---|
| source repository | Exact Hugging Face repository |
| source revision | Full immutable commit SHA |
| source artifact | Filename, byte size, SHA-256 |
| conversion | Tool, version, command/config, opset |
| output graph | Filename, byte size, SHA-256 |
| token map | Filename, byte size, SHA-256, vocabulary procedure |
| graph audit | `onnx.checker` result, inputs, outputs, opset, initializer types |
| host runtime | sherpa-onnx version, provider, thread count, sample rate |
| synthesis | Five phrases, status, durations, latency, RTF, clipping/finite checks |
| Android status | Build, install, extraction, load, synthesize, playback, stop/release |
| legal status | CC-BY-NC-4.0 and separate dataset/voice review status |

The target graph should match the established production audit shape:

```text
inputs:  x INT64[N,L]
         x_length INT64[N]
         noise_scale FLOAT[1]
         length_scale FLOAT[1]
         noise_scale_w FLOAT[1]
output:  y FLOAT[N,1,L]
```

That shape is a verification target, not a permission to waive sherpa
construction or physical-device tests if the graph differs.

## Release Archive Layout

The protected archive should contain the existing exact ten-language contract:

```text
models/manifest.json
models/stt/ggml-tiny-q5_1.bin
models/tts/eng/model.onnx
models/tts/eng/tokens.txt
models/tts/hin/model.onnx
models/tts/hin/tokens.txt
models/tts/guj/model.onnx
models/tts/guj/tokens.txt
models/tts/kan/model.onnx
models/tts/kan/tokens.txt
models/tts/mal/model.onnx
models/tts/mal/tokens.txt
models/tts/mar/model.onnx
models/tts/mar/tokens.txt
models/tts/tam/model.onnx
models/tts/tam/tokens.txt
models/tts/tel/model.onnx
models/tts/tel/tokens.txt
models/tts/ory/model.onnx
models/tts/ory/tokens.txt
models/tts/ben/model.onnx
models/tts/ben/tokens.txt
```

`manifest.json` must list every required file with only `sha256` and
`sizeBytes`, as enforced by `scripts/stage-bundled-models.py`. The P1.5 work
must not edit the manifest by hand. A protected release step may add a new
language only after its converted outputs pass all gates and the archive has
been independently rehashed.

The current staging script embeds the base English TTS package. The remaining
verified language packs require an explicitly approved protected packaging
flow; the current app has no downloader/importer. Do not put the seven source
checkpoints or converted binaries in Git.

## Storage and Performance Budget

- Reserve approximately 115 MiB per converted FP32 ONNX graph until actual
  output sizes are measured; the existing production graphs are about 114 MB.
- `tokens.txt` is expected to be small, but its exact size and hash remain
  release metadata and must be recorded.
- Load only one TTS language context at a time, matching the current engine.
- Treat P1.4 desktop peak RSS of roughly 546-566 MB as a risk signal, not an
  Android budget or claim.
- Bengali and Marathi exceeded real time on the P1.4 desktop benchmark. They
  require explicit device latency and UX review before release inclusion.
- Do not approve INT8 or other quantized variants from the prior investigation;
  those candidates changed waveform lengths or became slower and were not
  validated for sherpa Android.

## Acceptance Checklist

- [ ] Source checkpoint hash matches P1.4 evidence.
- [ ] Export completes offline from the pinned source files.
- [ ] ONNX graph checker passes.
- [ ] Graph loads with sherpa-onnx 1.13.6 on host.
- [ ] `tokens.txt` reproduces required MMS character IDs.
- [ ] Five native-script phrases synthesize with finite, non-clipped audio.
- [ ] Android ARM64 debug APK loads and synthesizes the pack.
- [ ] APK asset extraction and manifest checksum verification pass.
- [ ] Language switching, stop, release, and interrupted extraction are tested.
- [ ] Android latency, RTF, memory, thermal, and playback evidence is recorded.
- [ ] Human listening review passes.
- [ ] CC-BY-NC-4.0, dataset, and voice terms are legally cleared.
- [ ] Punjabi domain/routing support is explicitly resolved before packaging.

## Explicit Non-Goals

- No changes to `app/src/` in P1.5.
- No changes to `PacketV2`, transport, turn timing, or TTS queue behavior.
- No edits to `model-lab/models/MANIFEST.json`.
- No claim that desktop PyTorch success proves Android compatibility.
- No commitment to commercial distribution under the current license evidence.
