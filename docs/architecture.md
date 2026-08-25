# Vokie architecture

Vokie is an Android-first, offline-first emergency communication application. AWS exists only to distribute signed APKs and static documentation; it is not part of runtime communication.

## Runtime layers

`Compose UI → ViewModel/use cases → repositories → Transport / ML / Room implementations`

The UI never accesses Android Bluetooth APIs directly. `Transport` is the boundary for Bluetooth, Wi-Fi Direct, and future ultrasonic implementations. The current real transport is classic Bluetooth RFCOMM with the Vokie service UUID, protocol-versioned envelopes, length framing, receiver ACKs, timeout, and disconnect state.

The local pipeline is:

`microphone → local STT → text → peer transport → text → local TTS → speaker`

Cloud STT, cloud TTS, accounts, servers, and internet delivery are not used.

## Status

### IMPLEMENTED

- Compose Material 3 UI and Vokie design tokens
- Android 7.0+ baseline (`minSdk 24`), target SDK 34
- Contextual Nearby Devices permission request
- Real Bluetooth RFCOMM peer service identity, discoverability and discovery filtering
- Connector/listener roles, compact binary message protocol, persistence-before-ACK and failure states
- Room-backed message, peer and transport-event persistence with duplicate protection
- Process-independent outbound queue with bounded retries and interrupted-send recovery
- Native AudioRecord capture, bounded energy VAD, replaceable local model store, and whisper.cpp JNI inference
- Persistent selection for ten Whisper/message languages and real STT timing/RTF metrics
- Gradle wrapper and reproducible debug build
- CI and tag-triggered release workflow definitions
- Private S3 + CloudFront OAC infrastructure definition and static download site

### IN PROGRESS

- Physical two-device interoperability, disconnect and reconnect testing
- Physical multilingual STT accuracy, VAD threshold, latency, and memory testing
- Diagnostics presentation for persisted transport metrics

### PLANNED

- Language-managed local TTS models
- Wi-Fi Direct transport and fallback selection
- Multi-hop relay and ultrasonic transport

## Performance and safety principles

Only process audio on demand. Keep frames bounded, do not load all language models at once, avoid heavy UI effects, and never show peer receipt or delivery without a real protocol acknowledgement. Every permission, connection, transport, STT, and TTS failure must remain visible to the user.
