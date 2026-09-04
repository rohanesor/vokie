# SIH-L10-P1.4 MMS-TTS Candidate Matrix

Laptop-2 desktop Model-Lab evidence only. The seven candidates are official
Meta Hugging Face MMS-TTS repositories. All were downloaded one language at a
time under `C:\tts\model-lab\models\tts\mms-official`, loaded locally, and
benchmarked with CPU-only PyTorch. No candidate is Android-integrated.

| Language | Repository / revision | Artifact / size / SHA-256 | License | Tokenizer / frontend | Runtime / sample rate | Load / synthesis | Warm latency / RTF | RSS baseline / loaded / peak | Quality | Decision | Reason |
|---|---|---|---|---|---|---|---|---|---|---|---|
| TE | `facebook/mms-tts-tel` @ `dea6807154acc01918581982dcd40a116882a14d` | `model.safetensors` / 145,248,248 B / `067ac7ad1632d214dec61bf78cd3c2921358284614f5a4063378cc1434a389cf` | CC-BY-NC-4.0; dataset/voice terms UNKNOWN | AutoTokenizer; MMS character frontend; no espeak-ng | Transformers 5.16.1 / PyTorch 2.14.0+cpu; VITS; 16 kHz | load 943.3 ms; synthesis PASS | median 669.8 ms; p95 733.8 ms; RTF median 0.330 | 312.8 / 381.5 / 549.1 MB | NOT_HUMAN_EVALUATED | PROMOTED | Provenance, checksum, offline load, synthesis, and five samples PASS |
| BN | `facebook/mms-tts-ben` @ `0da99de6074c8829121cdabfbdba423af18e8e56` | `model.safetensors` / 145,255,160 B / `6a0e055ec13ecd0a07ead04dec7974a071846e64a9fe0c0b188f61b32a9bd5ba` | CC-BY-NC-4.0; dataset/voice terms UNKNOWN | AutoTokenizer; MMS character frontend; no espeak-ng | Transformers 5.16.1 / PyTorch 2.14.0+cpu; VITS; 16 kHz | load 1,653.9 ms; synthesis PASS | median 4,703.6 ms; p95 4,942.7 ms; RTF median 2.587 | 337.4 / 403.0 / 545.6 MB | NOT_HUMAN_EVALUATED | PROMOTED | Provenance, checksum, offline load, synthesis, and five samples PASS; slower than real time |
| MR | `facebook/mms-tts-mar` @ `7af4a6db1df2eb20042d24cc7c180a492df1cc13` | `model.safetensors` / 145,254,392 B / `fb53c1d8cd642b1df939162c71f91fb75d40b9c919a860de2f171e46295312b9` | CC-BY-NC-4.0; dataset/voice terms UNKNOWN | AutoTokenizer; MMS character frontend; no espeak-ng | Transformers 5.16.1 / PyTorch 2.14.0+cpu; VITS; 16 kHz | load 2,030.2 ms; synthesis PASS | median 4,170.8 ms; p95 5,188.7 ms; RTF median 2.544 | 338.4 / 403.0 / 547.6 MB | NOT_HUMAN_EVALUATED | PROMOTED | Provenance, checksum, offline load, synthesis, and five samples PASS; slower than real time |
| GU | `facebook/mms-tts-guj` @ `b72e80a7eeca90b72e0af2e2d00b77a336ce242d` | `model.safetensors` / 145,244,408 B / `f1f4e01188507d3cc8526d1326a6f1c8a9b51e5fd9abe7a92b500326808a0c6a` | CC-BY-NC-4.0; dataset/voice terms UNKNOWN | AutoTokenizer; MMS character frontend; no espeak-ng | Transformers 5.16.1 / PyTorch 2.14.0+cpu; VITS; 16 kHz | load 822.9 ms; synthesis PASS | median 430.1 ms; p95 448.9 ms; RTF median 0.364 | 337.7 / 403.3 / 560.0 MB | NOT_HUMAN_EVALUATED | PROMOTED | Provenance, checksum, offline load, synthesis, and five samples PASS |
| KN | `facebook/mms-tts-kan` @ `30e3c5d533e8c559c10bf0d25637fea51b95bd7c` | `model.safetensors` / 145,255,928 B / `12a68748b7aeab553c8b145ab2de198617644eb89e5f0b7008a2f3a7cf91a9bd` | CC-BY-NC-4.0; dataset/voice terms UNKNOWN | AutoTokenizer; MMS character frontend; no espeak-ng | Transformers 5.16.1 / PyTorch 2.14.0+cpu; VITS; 16 kHz | load 857.3 ms; synthesis PASS | median 666.7 ms; p95 743.4 ms; RTF median 0.328 | 338.4 / 404.0 / 565.9 MB | NOT_HUMAN_EVALUATED | PROMOTED | Provenance, checksum, offline load, synthesis, and five samples PASS |
| ML | `facebook/mms-tts-mal` @ `893b8c6442d6a630896d1d3ac0f429094ddfae82` | `model.safetensors` / 145,262,840 B / `a97a1e677ec67e05124b799dadd66630181fe9c29beb4e590454689ff8f698c5` | CC-BY-NC-4.0; dataset/voice terms UNKNOWN | AutoTokenizer; MMS character frontend; no espeak-ng | Transformers 5.16.1 / PyTorch 2.14.0+cpu; VITS; 16 kHz | load 827.0 ms; synthesis PASS | median 507.6 ms; p95 571.2 ms; RTF median 0.341 | 336.0 / 401.6 / 560.9 MB | NOT_HUMAN_EVALUATED | PROMOTED | Provenance, checksum, offline load, synthesis, and five samples PASS |
| PA | `facebook/mms-tts-pan` @ `45d7962e8daba724f9ff251ee3198bdb47a5f498` | `model.safetensors` / 145,243,640 B / `071db9963578edff7be6b660e9fb69bb1f2aa3596d77d632b76a7f3353373977` | CC-BY-NC-4.0; dataset/voice terms UNKNOWN | AutoTokenizer; MMS character frontend; no espeak-ng | Transformers 5.16.1 / PyTorch 2.14.0+cpu; VITS; 16 kHz | load 840.1 ms; synthesis PASS | median 570.8 ms; p95 597.2 ms; RTF median 0.328 | 336.2 / 401.8 / 560.8 MB | NOT_HUMAN_EVALUATED | PROMOTED | Provenance, checksum, offline load, synthesis, and five samples PASS |

## Evidence Sources

- TE: https://huggingface.co/facebook/mms-tts-tel/tree/dea6807154acc01918581982dcd40a116882a14d
- BN: https://huggingface.co/facebook/mms-tts-ben/tree/0da99de6074c8829121cdabfbdba423af18e8e56
- MR: https://huggingface.co/facebook/mms-tts-mar/tree/7af4a6db1df2eb20042d24cc7c180a492df1cc13
- GU: https://huggingface.co/facebook/mms-tts-guj/tree/b72e80a7eeca90b72e0af2e2d00b77a336ce242d
- KN: https://huggingface.co/facebook/mms-tts-kan/tree/30e3c5d533e8c559c10bf0d25637fea51b95bd7c
- ML: https://huggingface.co/facebook/mms-tts-mal/tree/893b8c6442d6a630896d1d3ac0f429094ddfae82
- PA: https://huggingface.co/facebook/mms-tts-pan/tree/45d7962e8daba724f9ff251ee3198bdb47a5f498

Each model card identifies the corresponding language and states that the
model is licensed CC-BY-NC 4.0. The cards do not document a separate dataset
or voice license, so those fields remain UNKNOWN rather than inferred.
