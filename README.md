# iTantra

## Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low-Bitrate Links

iTantra is an offline-first Android application for multilingual emergency communication when conventional networks are unavailable. It converts speech to local text, transfers compact text over nearby peer-to-peer transports, and plays received text locally.

## Current status

- **IMPLEMENTED:** Kotlin, Jetpack Compose, Material 3, dark-first emergency UI, Gradle wrapper, real Bluetooth RFCOMM transport, iTantra service UUID filtering, discovery/discoverability, connector/server roles, compact binary protocol, persistence-before-ACK, Room message/peer/event storage, bounded persistent outbound queue, real text composer, local AudioRecord capture, energy VAD, whisper.cpp JNI inference, sherpa-onnx MMS/VITS synthesis, direct PCM playback, multilingual language persistence, CI/CD definitions, private S3 + CloudFront infrastructure, and static download website.
- **IN PROGRESS:** Physical two-device interoperability, on-device STT/TTS performance validation, and verified MMS conversions for nine target languages absent from the official sherpa catalogue.
- **PLANNED:** Wi-Fi Direct fallback, relay, and ultrasonic transport.

The application does not use DemoTransport, fake Bluetooth, fake STT, fake TTS, a backend, cloud communication, or simulated delivery states.

## Offline communication architecture

```text
Microphone → local STT → compact text → Bluetooth/Wi-Fi Direct → text → local TTS → speaker
```

AWS is only used for signed APK distribution and the static download site. It is never used by the installed app communication pipeline.

## Supported transports

1. **Bluetooth RFCOMM — implemented foundation**
2. **Wi-Fi Direct — planned**
3. **Ultrasonic modem — planned**

The Bluetooth protocol service UUID is documented in `app/src/main/java/com/vokie/communication/VokieProtocol.kt`.

## Supported languages

Offline whisper.cpp STT is configured for Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, and English. The replaceable STT strategy is documented in [`docs/offline-stt.md`](docs/offline-stt.md). MMS-TTS model availability, exact files, licensing, and sherpa-onnx playback are documented in [`docs/offline-tts.md`](docs/offline-tts.md).

## Android requirements

- Android 7.0+ (`minSdk 24`)
- Target SDK 34
- JDK 17 for development/builds
- Android SDK 34, NDK 27.0.12077973, and CMake 3.22.1
- Nearby Devices permission is requested contextually when discovery or connection starts
- No account or internet connection is required for local communication after installation

## Local development

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Open the project in Android Studio with JDK 17, or use the included Gradle wrapper. Do not use globally installed Gradle.

## CI/CD

`.github/workflows/ci.yml` runs tests, lint, debug assembly, and uploads the APK for pushes and pull requests. `.github/workflows/release.yml` runs on semantic version tags (`vMAJOR.MINOR.PATCH`), requires release-signing secrets, builds a signed APK, generates SHA-256, deploys to AWS through GitHub OIDC, and creates a GitHub Release.

See [`docs/release-and-deployment.md`](docs/release-and-deployment.md) for AWS, OIDC, signing, and release setup.

## Download website

The lightweight static site is under `website/`. It uses the Android palette, responsive semantic HTML, keyboard focus states, reduced-motion support, and honest release metadata. A release workflow generates `release.json`; before the first release the site clearly says that no production release is published.

## Security

Never commit signing keystores, private keys, passwords, AWS access keys, or `local.properties`. Production AWS deployment uses GitHub Actions OIDC and a narrowly scoped IAM role. Verify downloaded APKs against the published SHA-256 checksum.

## Roadmap

The immediate product priority is physical Android validation of the complete voice → text → Bluetooth → text → speech path. Wi-Fi Direct follows only after that core loop is stable.
