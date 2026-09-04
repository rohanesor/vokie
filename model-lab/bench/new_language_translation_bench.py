"""Benchmark only the SIH-L10 languages added after the EN/HI/TA baseline."""

import json
import os
import platform
import statistics
import sys
import time
from pathlib import Path

import ctranslate2
import psutil
from tokenizers import Tokenizer


MODEL_LAB = Path(os.environ.get("MODEL_LAB_ROOT", Path(__file__).resolve().parents[1]))
CT_DIR = Path(os.environ.get("CT2_MODEL_DIR", MODEL_LAB / "models" / "ct2" / "nllb600m"))
TOK_PATH = Path(os.environ.get("NLLB_TOKENIZER_PATH", MODEL_LAB / "models" / "nllb" / "tokenizer.json"))
OUT_PATH = Path(os.environ.get("TRANSLATION_RESULT_PATH", MODEL_LAB / "bench" / "out" / "sih_l10_p1_3_new_language_translation.json"))
THREADS = 4
REPETITIONS = 10

LANGS = {
    "te": "tel_Telu",
    "bn": "ben_Beng",
    "mr": "mar_Deva",
    "gu": "guj_Gujr",
    "kn": "kan_Knda",
    "ml": "mal_Mlym",
    "pa": "pan_Guru",
}

PHRASES = [
    "Help me.",
    "I need help.",
    "Please come to my location.",
    "Where are you?",
    "I need a doctor.",
    "Where is the hospital?",
    "I am in danger.",
    "I need water.",
    "I am at the meeting point.",
    "Call the police.",
]
SRC_LANG = "eng_Latn"


def rss_mb():
    return psutil.Process().memory_info().rss / (1024 * 1024)


def percentile(values, p):
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    rank = (len(ordered) - 1) * p
    low = int(rank)
    high = min(low + 1, len(ordered) - 1)
    return ordered[low] + (ordered[high] - ordered[low]) * (rank - low)


def translate(translator, tokenizer, text, target):
    source = [SRC_LANG]
    source.extend(tokenizer.id_to_token(i) for i in tokenizer.encode(text).ids)
    source.append("</s>")
    result = translator.translate_batch(
        [source],
        target_prefix=[[target]],
        beam_size=4,
        max_decoding_length=128,
        repetition_penalty=1.1,
        no_repeat_ngram_size=3,
    )[0]
    hypothesis = result.hypotheses[0]
    if hypothesis and hypothesis[0] == target:
        hypothesis = hypothesis[1:]
    ids = [tokenizer.token_to_id(token) for token in hypothesis]
    return tokenizer.decode([token_id for token_id in ids if token_id is not None])


if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")

base_rss = rss_mb()
load_started = time.perf_counter()
tokenizer = Tokenizer.from_file(str(TOK_PATH))
tokenizer.post_processor = None
translator = ctranslate2.Translator(
    str(CT_DIR),
    device="cpu",
    inter_threads=1,
    intra_threads=THREADS,
)
# Include one actual decode in the cold-start measurement.
translate(translator, tokenizer, PHRASES[0], LANGS["te"])
cold_load_s = time.perf_counter() - load_started
loaded_rss = rss_mb()
peak_rss = loaded_rss

directions = {}
for language, target in LANGS.items():
    latencies = []
    outputs = []
    for phrase in PHRASES:
        started = time.perf_counter()
        output = translate(translator, tokenizer, phrase, target)
        elapsed = time.perf_counter() - started
        latencies.append(elapsed)
        outputs.append({"src": phrase, "out": output})
        peak_rss = max(peak_rss, rss_mb())

    directions[f"en->{language}"] = {
        "warm_samples": len(latencies),
        "median_s": statistics.median(latencies),
        "p95_s": percentile(latencies, 0.95),
        "latencies_s": latencies,
        "outputs": outputs,
    }

result = {
    "status": "PROMOTED",
    "scope": "new SIH-L10 languages only; EN/HI/TA baseline not rerun",
    "task": "translation",
    "model": "osa911/nllb-200-distilled-600M-ct2-int8",
    "revision": "46858753dbaf8eb5e21bb6f0037c3b90851e090a",
    "artifact": "model.bin",
    "runtime": f"CTranslate2 {ctranslate2.__version__}",
    "device": "cpu",
    "thread_config": {"inter_threads": 1, "intra_threads": THREADS},
    "environment": {
        "cpu": platform.processor(),
        "ram_total_mb": round(psutil.virtual_memory().total / (1024 * 1024), 1),
        "os": platform.platform(),
        "python": platform.python_version(),
    },
    "cold_load_s": cold_load_s,
    "base_rss_mb": base_rss,
    "loaded_rss_mb": loaded_rss,
    "peak_rss_mb": peak_rss,
    "directions": directions,
}

OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
with OUT_PATH.open("w", encoding="utf-8") as output_file:
    json.dump(result, output_file, ensure_ascii=False, indent=2)

print(json.dumps({
    "output": str(OUT_PATH),
    "cold_load_s": round(cold_load_s, 6),
    "peak_rss_mb": round(peak_rss, 1),
    "directions": {
        key: {
            "median_s": round(value["median_s"], 6),
            "p95_s": round(value["p95_s"], 6),
        }
        for key, value in directions.items()
    },
}, ensure_ascii=False, indent=2))
