# Vokie

**Voice when networks fail.** Offline-first Android emergency communication prototype.

## Current prototype
- Kotlin + Jetpack Compose + Material 3
- Dark emergency-grade design system with manually selectable light tokens
- Home/status dashboard, Communicate walkie-talkie pipeline, SOS confirmation sheet
- Offline map, alert feed, contacts/resources/settings hub
- Transport, STT, TTS interfaces isolated from UI
- Room-ready local entities for messages, contacts, alerts, resources, regions, devices, settings
- Clearly identified DEMO MODE with simulated Tamil STT → Bluetooth → received text/TTS path

## Build
Open in Android Studio (JDK 17, Android SDK 34) and run `assembleDebug`.

Real Bluetooth, local STT/TTS, Room DAOs, and Wi-Fi Direct adapters are intentionally isolated as the next implementation phases. Delivery is only marked as peer-received in the demo; the UI never claims server delivery.
