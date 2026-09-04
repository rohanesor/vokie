# SIH-L10-P1.4 MMS-TTS Runtime Repair and Benchmark

Status: `PASS` for the seven Laptop-2 desktop candidate benchmarks.
This phase did not modify Android production code and does not constitute
Android runtime validation or integration.

## Environment Repair

The existing isolated environment was retained at `C:\tts\model-lab\.venv`.
Initial inventory:

| Component | Version / result |
|---|---|
| Python | 3.12.10 |
| pip | 26.2.1 |
| PyTorch before repair | 2.13.0, import failed with WinError 126 |
| PyTorch after repair | 2.14.0+cpu |
| torchvision | Not installed |
| torchaudio | Not installed |
| Transformers | 5.16.1 |
| onnxruntime | 1.29.0 |
| numpy | 2.5.2 |
| sherpa-onnx | 1.13.7 |

PE import inspection identified `VCRUNTIME140_THREADS.dll` as the missing
dependency of the Torch CPU wheel's `torch_cpu.dll`. Torch was replaced only
inside the venv from the official PyTorch CPU wheel index using
`--force-reinstall --no-deps`. The missing DLL was obtained from pinned local
package `NtvLibs.MSVCP.vcruntime140_threads.runtime.win-x64` version
`14.42.34430` and staged at `C:\tts\model-lab\msvc_crt`.

Local DLL evidence:

- `vcruntime140_threads.dll`: 38,432 bytes
- SHA-256: `bf05e339e29c8ad35d9a1f02513d740b4e5fefa729150cff9327af791c423636`

Verification passed:

```text
2.14.0+cpu
torch.Size([100, 100])
```

`torch.__config__.show()` reported `USE_CUDA=0`, MKL, MKLDNN, OpenMP, and
AVX2 CPU capability. No system Python or Android project was modified.

## Benchmark Protocol

Each candidate was downloaded at its immutable revision to
`C:\tts\model-lab\models\tts\mms-official\<language>` and checked against
the authoritative Hub artifact size and SHA-256. Each model was loaded with
`VitsModel.from_pretrained(..., local_files_only=True)` after acquisition.
Each run performed tokenizer loading, one first synthesis, one warm-up per
phrase, and five measured warm syntheses. CPU thread count was four. RTF is
synthesis latency divided by generated audio duration and excludes model-load
time. RSS is desktop process RSS, not Android PSS.

## Results

| Language | Artifact size | Model load | First synthesis | Warm median / p95 | RTF median | Sample rate | Peak RSS | Status |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| TE | 145,248,248 B | 943.3 ms | 837.8 ms | 669.8 / 733.8 ms | 0.330 | 16,000 Hz | 549.1 MB | PROMOTED |
| BN | 145,255,160 B | 1,653.9 ms | 5,283.4 ms | 4,703.6 / 4,942.7 ms | 2.587 | 16,000 Hz | 545.6 MB | PROMOTED |
| MR | 145,254,392 B | 2,030.2 ms | 4,440.1 ms | 4,170.8 / 5,188.7 ms | 2.544 | 16,000 Hz | 547.6 MB | PROMOTED |
| GU | 145,244,408 B | 822.9 ms | 626.3 ms | 430.1 / 448.9 ms | 0.364 | 16,000 Hz | 560.0 MB | PROMOTED |
| KN | 145,255,928 B | 857.3 ms | 854.8 ms | 666.7 / 743.4 ms | 0.328 | 16,000 Hz | 565.9 MB | PROMOTED |
| ML | 145,262,840 B | 827.0 ms | 722.2 ms | 507.6 / 571.2 ms | 0.341 | 16,000 Hz | 560.9 MB | PROMOTED |
| PA | 145,243,640 B | 840.1 ms | 785.3 ms | 570.8 / 597.2 ms | 0.328 | 16,000 Hz | 560.8 MB | PROMOTED |

All seven candidates passed correct-language acquisition, local model load,
tokenization, valid finite waveform generation, provenance/checksum matching,
and five-sample CPU benchmarks. Bengali and Marathi are slower than real time
on this host and require explicit performance review before any product use.

Quality was not human/listening evaluated:
`QUALITY_STATUS = NOT_HUMAN_EVALUATED`. Objective waveform checks passed for
all samples: finite values, nonzero duration, and no model-load or synthesis
exception. No MOS or intelligibility claim is made.

## Candidate Gate

All seven candidates are `PROMOTED` as evidence-backed Laptop-2 desktop
integration candidates. This means benchmarked candidate only. The progression
still requires Android runtime verification, integration, and physical
validation before deployment. No candidate is marked integrated.

All model cards specify `CC-BY-NC-4.0`. Dataset/voice-specific licensing was
not documented in those cards and remains `UNKNOWN`; non-commercial use and
attribution restrictions therefore remain applicable.

## Files and Artifacts

Created for Git:

- `model-lab/bench/mms_tts_p1_4_bench.py`
- `model-lab/bench/out/sih_l10_p1_4_tts_results.json`
- `model-lab/full-audit/SIH_L10_P1_4_MMS_TTS_BENCHMARK.md`
- `model-lab/full-audit/SIH_L10_P1_4_TTS_CANDIDATE_MATRIX.md`

Kept local and excluded from Git:

- Seven `model.safetensors` files under `C:\tts\model-lab\models\tts\mms-official`, each about 145 MB
- Generated WAV files under `C:\tts\model-lab\bench\p1_4_wav`
- `C:\tts\model-lab\.venv`
- Local MSVC runtime staging and temporary download/extraction files

`model-lab/models/MANIFEST.json` was not modified, as requested. Existing
historical result files were not overwritten.
