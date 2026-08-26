#!/usr/bin/env python3
"""Fails unless a production APK embeds every staged model byte-for-byte."""
import hashlib, json, sys, zipfile
LANGUAGES = ('eng','hin','guj','mar','kan','mal','tam','tel','ory','ben')
REQUIRED = {'stt/ggml-tiny-q5_1.bin'} | {f'tts/{l}/{n}' for l in LANGUAGES for n in ('model.onnx','tokens.txt')}
def fail(m): raise SystemExit(f'APK model validation failed: {m}')
def digest(data): return hashlib.sha256(data).hexdigest()
if len(sys.argv) != 2: fail('usage: verify-bundled-model-apk.py APK')
with zipfile.ZipFile(sys.argv[1]) as apk:
    try: manifest = json.loads(apk.read('assets/models/manifest.json'))
    except KeyError: fail('assets/models/manifest.json is missing')
    files = manifest.get('files', {})
    if set(files) != REQUIRED: fail('manifest does not cover every required model')
    for relative, expected in files.items():
        name = 'assets/models/' + relative
        try: data = apk.read(name)
        except KeyError: fail(f'{name} is missing')
        if len(data) != expected['sizeBytes'] or digest(data) != expected['sha256'].lower(): fail(f'{name} does not match manifest')
        if apk.getinfo(name).compress_type != zipfile.ZIP_STORED: fail(f'{name} must be stored uncompressed')
print(f'Validated {len(REQUIRED)} bundled offline model files in {sys.argv[1]}.')
