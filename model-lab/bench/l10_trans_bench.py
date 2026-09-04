import ctranslate2, time, os, json, psutil, sys
from tokenizers import Tokenizer

if sys.stdout.encoding.lower() != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8')

CT_DIR = r"C:\tts\model-lab\models\ct2\nllb600m"
TOK = r"C:\tts\model-lab\models\nllb\tokenizer.json"
OUT = r"C:\tts\model-lab\bench\out"

LANGS = {
    "en": "eng_Latn",
    "hi": "hin_Deva",
    "ta": "tam_Taml",
    "te": "tel_Telu",
    "bn": "ben_Beng",
    "mr": "mar_Deva",
    "gu": "guj_Gujr",
    "kn": "kan_Knda",
    "ml": "mal_Mlym",
    "pa": "pan_Guru"
}

TEST_PHRASES = {
    "en": [
        "Help me.",
        "I need help.",
        "Please come to my location.",
        "Where are you?",
        "I need a doctor.",
        "Where is the hospital?",
        "I am in danger.",
        "I need water.",
        "I am at the meeting point.",
        "Call the police."
    ]
}

def rss(): return round(psutil.Process().memory_info().rss / (1024 * 1024), 1)

base_rss = rss()
t0 = time.time()
tok = Tokenizer.from_file(TOK)
tok.post_processor = None
tr = ctranslate2.Translator(CT_DIR, device="cpu", inter_threads=1, intra_threads=4)
# Warmup
warmup_src = [LANGS["en"]] + [tok.id_to_token(i) for i in tok.encode("Help me.").ids] + ["</s>"]
_ = tr.translate_batch([warmup_src], target_prefix=[[LANGS["hi"]]], max_decoding_length=30)
load_time = round(time.time() - t0, 3)
loaded_rss = rss()

results = {
    "load_time_s": load_time,
    "base_rss_mb": base_rss,
    "loaded_rss_mb": loaded_rss,
    "languages": list(LANGS.keys()),
    "directions": {}
}

print(f"Loaded CTranslate2 NLLB-600M in {load_time}s | RSS: {loaded_rss} MB", flush=True)

# Benchmark EN -> Target for all 9 other languages
for code, tag in LANGS.items():
    if code == "en": continue
    dir_key = f"en->{code}"
    latencies = []
    outputs = []
    
    for text in TEST_PHRASES["en"]:
        src_tokens = [LANGS["en"]] + [tok.id_to_token(i) for i in tok.encode(text).ids] + ["</s>"]
        t_start = time.time()
        res = tr.translate_batch([src_tokens], target_prefix=[[tag]], beam_size=4, max_decoding_length=128, repetition_penalty=1.1, no_repeat_ngram_size=3)
        dt = time.time() - t_start
        latencies.append(round(dt, 3))
        
        hyp_toks = res[0].hypotheses[0]
        if hyp_toks and hyp_toks[0] == tag:
            hyp_toks = hyp_toks[1:]
        ids = [tok.token_to_id(t) for t in hyp_toks if tok.token_to_id(t) is not None]
        out_text = tok.decode(ids, skip_special_tokens=True)
        outputs.append({"src": text, "out": out_text})
    
    sorted_lat = sorted(latencies)
    med = sorted_lat[len(sorted_lat) // 2]
    p95 = sorted_lat[int(len(sorted_lat) * 0.95)]
    results["directions"][dir_key] = {
        "median_s": med,
        "p95_s": p95,
        "latencies_s": latencies,
        "outputs": outputs
    }
    print(f"[{dir_key}] Median: {med}s | P95: {p95}s | Output sample 0: {outputs[0]['out']}", flush=True)

results["peak_rss_mb"] = rss()
os.makedirs(OUT, exist_ok=True)
out_file1 = os.path.join(OUT, "l10_translation_results.json")
out_file2 = r"C:\Users\ZAYN\AppData\Local\Temp\opencode\vokie-repo\model-lab\bench\out\l10_translation_results.json"
with open(out_file1, "w", encoding="utf-8") as f:
    json.dump(results, f, ensure_ascii=False, indent=2)
with open(out_file2, "w", encoding="utf-8") as f:
    json.dump(results, f, ensure_ascii=False, indent=2)

print("Saved 10-language translation results to l10_translation_results.json", flush=True)
