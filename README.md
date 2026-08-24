# Vokie

## Voice when networks fail.

Vokie is an offline-first Android application for multilingual emergency communication when conventional networks are unavailable. It converts speech to local text, transfers compact text over nearby peer-to-peer transports, and plays received text locally.

## Current status

- **IMPLEMENTED:** Kotlin, Jetpack Compose, Material 3, dark-first emergency UI, Gradle wrapper, real Bluetooth RFCOMM transport, Vokie service UUID filtering, discovery, connection/server accept, framed protocol envelopes, receiver acknowledgements, timeout/failure states, CI/CD definitions, private S3 + CloudFront infrastructure, and static download website.
- **IN PROGRESS:** Room persistence/DAO wiring, offline outbound queue, text send UI, and physical two-device validation.
- **PLANNED:** Local multilingual STT/VAD, local language-specific TTS models, Wi-Fi Direct fallback, relay, and ultrasonic transport.

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

The canonical language model currently defines Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, and English. Local STT/TTS model integration is still in progress.

## Android requirements

- Android 7.0+ (`minSdk 24`)
- Target SDK 34
- JDK 17 for development/builds
- Android SDK 34
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

The immediate product priority is completing Room-backed persistence and queueing around the real Bluetooth transport, then local STT/TTS and two-phone testing. Wi-Fi Direct follows only after that core path is stable.
