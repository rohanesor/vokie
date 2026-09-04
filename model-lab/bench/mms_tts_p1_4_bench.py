"""Acquire and benchmark one official MMS-TTS candidate at a time."""

import argparse
import hashlib
import json
import os
import platform
import statistics
import sys
import time
from pathlib import Path

if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")


# Torch on this host needs the locally staged MSVC runtime DLLs.
CRT_DIR = os.environ.get("MMS_CRT_DIR", r"C:\tts\model-lab\msvc_crt")
if os.name == "nt":
    os.environ["PATH"] = CRT_DIR + os.pathsep + os.environ.get("PATH", "")
    if hasattr(os, "add_dll_directory"):
        os.add_dll_directory(CRT_DIR)

import psutil
import torch
from huggingface_hub import hf_hub_download
from transformers import AutoTokenizer, VitsModel


ROOT = Path(os.environ.get("MMS_TTS_ROOT", r"C:\tts\model-lab"))
MODEL_ROOT = ROOT / "models" / "tts" / "mms-official"
RESULT_PATH = Path(os.environ.get(
    "MMS_TTS_RESULT_PATH",
    str(ROOT / "bench" / "out" / "sih_l10_p1_4_tts_results.json"),
))
WAV_ROOT = Path(os.environ.get(
    "MMS_TTS_WAV_ROOT",
    str(ROOT / "bench" / "p1_4_wav"),
))
THREADS = 4
WARM_SAMPLES = 5

CANDIDATES = {
    "te": {
        "language": "Telugu",
        "repo": "facebook/mms-tts-tel",
        "revision": "dea6807154acc01918581982dcd40a116882a14d",
        "artifact": "model.safetensors",
        "size_bytes": 145248248,
        "sha256": "067ac7ad1632d214dec61bf78cd3c2921358284614f5a4063378cc1434a389cf",
        "phrases": ["నాకు సహాయం కావాలి.", "నేను ఇక్కడ ఉన్నాను.", "ఆసుపత్రి ఎక్కడ ఉంది?", "నాకు నీరు కావాలి.", "పోలీసులకు కాల్ చేయండి."],
    },
    "bn": {
        "language": "Bengali",
        "repo": "facebook/mms-tts-ben",
        "revision": "0da99de6074c8829121cdabfbdba423af18e8e56",
        "artifact": "model.safetensors",
        "size_bytes": 145255160,
        "sha256": "6a0e055ec13ecd0a07ead04dec7974a071846e64a9fe0c0b188f61b32a9bd5ba",
        "phrases": ["আমার সাহায্য দরকার।", "আমি এখানে আছি।", "হাসপাতাল কোথায়?", "আমার পানি দরকার।", "পুলিশকে ফোন করুন।"],
    },
    "mr": {
        "language": "Marathi",
        "repo": "facebook/mms-tts-mar",
        "revision": "7af4a6db1df2eb20042d24cc7c180a492df1cc13",
        "artifact": "model.safetensors",
        "size_bytes": 145254392,
        "sha256": "fb53c1d8cd642b1df939162c71f91fb75d40b9c919a860de2f171e46295312b9",
        "phrases": ["मला मदत हवी आहे.", "मी इथे आहे.", "रुग्णालय कुठे आहे?", "मला पाणी हवे आहे.", "पोलिसांना फोन करा."],
    },
    "gu": {
        "language": "Gujarati",
        "repo": "facebook/mms-tts-guj",
        "revision": "b72e80a7eeca90b72e0af2e2d00b77a336ce242d",
        "artifact": "model.safetensors",
        "size_bytes": 145244408,
        "sha256": "f1f4e01188507d3cc8526d1326a6f1c8a9b51e5fd9abe7a92b500326808a0c6a",
        "phrases": ["મને મદદની જરૂર છે.", "હું અહીં છું.", "હોસ્પિટલ ક્યાં છે?", "મને પાણી જોઈએ છે.", "પોલીસને ફોન કરો."],
    },
    "kn": {
        "language": "Kannada",
        "repo": "facebook/mms-tts-kan",
        "revision": "30e3c5d533e8c559c10bf0d25637fea51b95bd7c",
        "artifact": "model.safetensors",
        "size_bytes": 145255928,
        "sha256": "12a68748b7aeab553c8b145ab2de198617644eb89e5f0b7008a2f3a7cf91a9bd",
        "phrases": ["ನನಗೆ ಸಹಾಯ ಬೇಕು.", "ನಾನು ಇಲ್ಲಿದ್ದೇನೆ.", "ಆಸ್ಪತ್ರೆ ಎಲ್ಲಿದೆ?", "ನನಗೆ ನೀರು ಬೇಕು.", "ಪೊಲೀಸರಿಗೆ ಕರೆ ಮಾಡಿ."],
    },
    "ml": {
        "language": "Malayalam",
        "repo": "facebook/mms-tts-mal",
        "revision": "893b8c6442d6a630896d1d3ac0f429094ddfae82",
        "artifact": "model.safetensors",
        "size_bytes": 145262840,
        "sha256": "a97a1e677ec67e05124b799dadd66630181fe9c29beb4e590454689ff8f698c5",
        "phrases": ["എനിക്ക് സഹായം വേണം.", "ഞാൻ ഇവിടെയുണ്ട്.", "ആശുപത്രി എവിടെയാണ്?", "എനിക്ക് വെള്ളം വേണം.", "പോലീസിനെ വിളിക്കൂ."],
    },
    "pa": {
        "language": "Punjabi",
        "repo": "facebook/mms-tts-pan",
        "revision": "45d7962e8daba724f9ff251ee3198bdb47a5f498",
        "artifact": "model.safetensors",
        "size_bytes": 145243640,
        "sha256": "071db9963578edff7be6b660e9fb69bb1f2aa3596d77d632b76a7f3353373977",
        "phrases": ["ਮੈਨੂੰ ਮਦਦ ਦੀ ਲੋੜ ਹੈ।", "ਮੈਂ ਇੱਥੇ ਹਾਂ।", "ਹਸਪਤਾਲ ਕਿੱਥੇ ਹੈ?", "ਮੈਨੂੰ ਪਾਣੀ ਚਾਹੀਦਾ ਹੈ।", "ਪੁਲਿਸ ਨੂੰ ਬੁਲਾਓ।"],
    },
}


def rss_mb():
    return psutil.Process().memory_info().rss / (1024 * 1024)


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def percentile(values, p):
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    rank = (len(ordered) - 1) * p
    low = int(rank)
    high = min(low + 1, len(ordered) - 1)
    return ordered[low] + (ordered[high] - ordered[low]) * (rank - low)


def acquire(candidate, target_dir):
    target_dir.mkdir(parents=True, exist_ok=True)
    files = [
        "config.json",
        "model.safetensors",
        "special_tokens_map.json",
        "tokenizer_config.json",
        "vocab.json",
    ]
    paths = {}
    for filename in files:
        paths[filename] = Path(hf_hub_download(
            repo_id=candidate["repo"],
            filename=filename,
            revision=candidate["revision"],
            local_dir=str(target_dir),
        ))
    return paths


def load_previous():
    if RESULT_PATH.exists():
        return json.loads(RESULT_PATH.read_text(encoding="utf-8"))
    return {
        "phase": "SIH-L10-P1.4",
        "device": "cpu",
        "candidates": {},
    }


def main(language):
    candidate = CANDIDATES[language]
    target_dir = MODEL_ROOT / language
    record = {
        "language": candidate["language"],
        "language_code": language,
        "repository": candidate["repo"],
        "revision": candidate["revision"],
        "artifact": candidate["artifact"],
        "license": "CC-BY-NC-4.0",
        "runtime": "Transformers %s / PyTorch %s" % (transformers_version(), torch.__version__),
        "architecture": "VITS",
        "tokenizer": "AutoTokenizer vocabulary.json/configured local tokenizer",
        "frontend": "MMS character/tokenizer frontend; no espeak-ng phonemizer",
        "cpu": platform.processor(),
        "thread_count": THREADS,
        "quality_status": "NOT_HUMAN_EVALUATED",
        "decision": "RESEARCH-ONLY",
    }

    try:
        paths = acquire(candidate, target_dir)
        artifact_path = paths["model.safetensors"]
        record["local_path"] = str(artifact_path)
        record["artifact_size_bytes"] = artifact_path.stat().st_size
        record["artifact_sha256"] = sha256(artifact_path)
        record["expected_size_bytes"] = candidate["size_bytes"]
        record["expected_sha256"] = candidate["sha256"]
        record["provenance_status"] = "PASS" if (
            record["artifact_size_bytes"] == candidate["size_bytes"]
            and record["artifact_sha256"] == candidate["sha256"]
        ) else "FAIL"
        record["source"] = "https://huggingface.co/%s/tree/%s" % (candidate["repo"], candidate["revision"])

        baseline_rss = rss_mb()
        load_started = time.perf_counter()
        tokenizer = AutoTokenizer.from_pretrained(str(target_dir), local_files_only=True)
        model = VitsModel.from_pretrained(str(target_dir), local_files_only=True, use_safetensors=True)
        model.to("cpu").eval()
        load_time_s = time.perf_counter() - load_started
        loaded_rss = rss_mb()
        record["model_load_s"] = load_time_s
        record["baseline_rss_mb"] = baseline_rss
        record["loaded_rss_mb"] = loaded_rss
        record["sample_rate"] = int(model.config.sampling_rate)

        def synthesize(text):
            inputs = tokenizer(text, return_tensors="pt")
            with torch.inference_mode():
                output = model(**inputs).waveform
            waveform = output.detach().cpu().reshape(-1)
            return waveform, inputs

        torch.manual_seed(0)
        first_started = time.perf_counter()
        first_waveform, _ = synthesize(candidate["phrases"][0])
        first_time_s = time.perf_counter() - first_started
        peak_rss = max(loaded_rss, rss_mb())

        # One warm-up generation per phrase, followed by five measured generations.
        for phrase in candidate["phrases"]:
            synthesize(phrase)
        latencies = []
        samples = []
        for phrase in candidate["phrases"]:
            started = time.perf_counter()
            waveform, _ = synthesize(phrase)
            elapsed = time.perf_counter() - started
            duration_s = len(waveform) / record["sample_rate"]
            latencies.append(elapsed)
            peak_rss = max(peak_rss, rss_mb())
            samples.append({
                "text": phrase,
                "audio_duration_s": duration_s,
                "synthesis_latency_s": elapsed,
                "rtf": elapsed / duration_s if duration_s else None,
                "finite": bool(torch.isfinite(waveform).all()),
                "peak_abs": float(waveform.abs().max()),
                "sample_count": len(waveform),
            })

        wav_path = WAV_ROOT / (language + "_mms_official.wav")
        WAV_ROOT.mkdir(parents=True, exist_ok=True)
        import soundfile
        soundfile.write(str(wav_path), first_waveform.numpy(), record["sample_rate"])
        record.update({
            "load_status": "PASS",
            "synthesis_status": "PASS",
            "first_synthesis_s": first_time_s,
            "warm_sample_count": len(latencies),
            "warm_median_s": statistics.median(latencies),
            "warm_p95_s": percentile(latencies, 0.95),
            "peak_rss_mb": peak_rss,
            "samples": samples,
            "temporary_wav": str(wav_path),
            "wav_sha256": sha256(wav_path),
            "decision": "PROMOTED" if record["provenance_status"] == "PASS" else "RESEARCH-ONLY",
        })
    except Exception as exc:
        record.update({
            "load_status": "FAIL",
            "synthesis_status": "NOT_RUN",
            "error_type": type(exc).__name__,
            "error": str(exc),
            "decision": "BLOCKED",
        })

    result = load_previous()
    result.update({
        "environment": {
            "python": platform.python_version(),
            "torch": torch.__version__,
            "transformers": transformers_version(),
            "numpy": numpy_version(),
            "onnxruntime": onnxruntime_version(),
            "sherpa_onnx": sherpa_version(),
            "os": platform.platform(),
            "cpu": platform.processor(),
            "thread_count": THREADS,
        },
    })
    result.setdefault("candidates", {})[language] = record
    RESULT_PATH.parent.mkdir(parents=True, exist_ok=True)
    RESULT_PATH.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"language": language, "decision": record["decision"], "record": record}, ensure_ascii=False, indent=2))


def transformers_version():
    import transformers
    return transformers.__version__


def numpy_version():
    import numpy
    return numpy.__version__


def onnxruntime_version():
    import onnxruntime
    return onnxruntime.__version__


def sherpa_version():
    import sherpa_onnx
    return sherpa_onnx.__version__


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--language", choices=sorted(CANDIDATES), required=True)
    args = parser.parse_args()
    torch.set_num_threads(THREADS)
    torch.set_num_interop_threads(1)
    main(args.language)
