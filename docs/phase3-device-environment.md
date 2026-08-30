# Phase 3 real-device environment

## Verification date

2026-08-30. Windows PowerShell/Windows Java/Windows Android SDK were used for Gradle, and Windows ADB controlled both endpoints. No personal files, contacts, messages, photos, accounts, or credentials were collected.

## Device A — physical

| Field | Result |
|---|---|
| Type | Physical Android device |
| Manufacturer | realme |
| Model | RMX3782 / narzo 60x 5G |
| Android/API | 15 / 35 |
| ABI | arm64-v8a |
| ADB serial | MJPVXCSG9HYL65YL |
| ADB state | `device` / authorized |
| Display/density | 1080x2400 / 480 dpi |
| Application ID | `com.vokie` |
| Version | 1.0.0 / versionCode 1 |
| Microphone | RECORD_AUDIO granted=true |

## Device B — virtual

| Field | Result |
|---|---|
| Type | Android Studio emulator |
| AVD name | Pixel_10_Pro_XL |
| Reported manufacturer | Google |
| Reported model | sdk_gphone16k_x86_64 |
| Android/API | 17 / 37 |
| ABI | x86_64 |
| ADB serial | emulator-5554 |
| ADB state | `device` |
| Display/density | 1344x2992 / 480 dpi |
| Application ID | `com.vokie` |
| Version | 1.0.0 / versionCode 1 |
| Microphone | RECORD_AUDIO granted=false; manual permission acceptance remains required |

The AVD is named Pixel 10 Pro XL, but the runtime property reports the generic emulator model `sdk_gphone16k_x86_64`; both values are recorded rather than conflated.

## ADB and build/deployment checks

| Capability | Result | Evidence |
|---|---|---|
| Windows Java | PASS | Microsoft OpenJDK 17.0.18 |
| Windows SDK | PASS | `C:\Users\kille\AppData\Local\Android\Sdk` |
| Build Tools 34.0.0 | PASS | `aapt.exe`, `zipalign.exe`, `apksigner.bat` present |
| Device A detected | PASS | `MJPVXCSG9HYL65YL device` |
| Device B detected | PASS | `emulator-5554 device` |
| Both ADB-authorized | PASS | both status values are `device` |
| Windows Gradle tests | PASS | wrapper JAR + Windows Java, `BUILD SUCCESSFUL` |
| Windows debug build | PASS | `assembleDebug --no-daemon`, `BUILD SUCCESSFUL` |
| APK | PASS | `D:\vibe\vokie\app\build\outputs\apk\debug\app-debug.apk`, 198,787,226 bytes |
| APK install A | PASS | `adb -s MJPVXCSG9HYL65YL install -r`, `Success` |
| APK install B | PASS | `adb -s emulator-5554 install -r`, `Success` |
| App launch A | PASS | MainActivity focused/displayed |
| App launch B | PASS | MainActivity started/displayed; no crash observed |
| Logcat A | PASS | package-filtered logs captured |
| Logcat B | PASS | package-filtered logs captured |
| Microphone A | PASS | permission already granted |
| Microphone B | PARTIAL | permission is false; manual Android permission acceptance is required |

## ADB result

```text
List of devices attached
MJPVXCSG9HYL65YL       device product:RMX3782 model:RMX3782
emulator-5554          device product:sdk_gphone16k_x86_64 model:sdk_gphone16k_x86_64
```

## Transport classification

| Capability | Result |
|---|---|
| Two-endpoint ADB | PASS |
| Two-endpoint software testing | READY |
| Physical phone-to-phone testing | BLOCKED — Device B is virtual |
| Wi-Fi Direct physical validation | BLOCKED |
| Bluetooth Classic RFCOMM physical validation | BLOCKED |
| Emulator/phone Wi-Fi Direct equivalence | NOT CLAIMED |
| Emulator/phone Bluetooth Classic equivalence | NOT CLAIMED |
| Offline physical transfer | NOT MEASURED |

The emulator is suitable for APK, protocol, Room, lifecycle, and software-level testing only. Its presence does not prove genuine Wi-Fi Direct or Bluetooth Classic behavior with the physical handset.

## Final classification

```text
DEVICE A = READY
DEVICE B = READY FOR SOFTWARE TESTING
TWO-ENDPOINT_ADB = PASS
TWO-ENDPOINT SOFTWARE TESTING = READY
PHYSICAL TWO-DEVICE TESTING = BLOCKED
ADB STATUS = PASS
WINDOWS BUILD = PASS
DEBUG APK INSTALL A = PASS
DEBUG APK INSTALL B = PASS
APP LAUNCH A = PASS
APP LAUNCH B = PASS
LOGCAT A = PASS
LOGCAT B = PASS
MICROPHONE A = PASS
MICROPHONE B = PARTIAL — manual permission acceptance required
```

No source code or product architecture was modified for this setup. No release build, model/dataset download, AWS action, or training was performed.
