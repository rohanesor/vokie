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
- Real Bluetooth RFCOMM peer service identity and discovery filtering
- Bluetooth connection/server accept, framed message protocol, ACK handling and failure states
- Gradle wrapper and reproducible debug build
- CI and tag-triggered release workflow definitions
- Private S3 + CloudFront OAC infrastructure definition and static download site

### IN PROGRESS

- Room DAO/database wiring and persistent outbound queue
- Text composer/send action connected to Bluetooth repository
- Physical two-device interoperability testing

### PLANNED

- Local multilingual STT/VAD
- Language-managed local TTS models
- Wi-Fi Direct transport and fallback selection
- Multi-hop relay and ultrasonic transport

## Performance and safety principles

Only process audio on demand. Keep frames bounded, do not load all language models at once, avoid heavy UI effects, and never show peer receipt or delivery without a real protocol acknowledgement. Every permission, connection, transport, STT, and TTS failure must remain visible to the user.
