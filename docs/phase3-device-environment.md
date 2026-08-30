# Phase 3 real-device environment

## Capture

Captured from the connected Windows ADB server through the SDK at `C:\Users\kille\AppData\Local\Android\Sdk\platform-tools\adb.exe`. No personal files, contacts, messages, photos, accounts, or tokens were collected.

## Device A

| Field | Result |
|---|---|
| Type | Physical Android device |
| Manufacturer | realme |
| Model | RMX3782 (narzo 60x 5G) |
| Android | 15 |
| API | 35 |
| ABI | arm64-v8a |
| ADB serial | MJPVXCSG9HYL65YL |
| ADB state | `device` / authorized |
| Display | 1080x2400 |
| Density | 480 dpi |
| Existing package | `com.vokie`, versionName 1.0.0, versionCode 1 |
| Microphone permission | granted=true on installed package |

## Device B

| Field | Result |
|---|---|
| Type | Not detected |
| ADB serial | None |
| Status | **NOT AVAILABLE** |

The current `adb devices -l` result contained only Device A. No emulator was detected through this ADB server.

## Environment checks

| Check | Result | Evidence |
|---|---|---|
| adb installed | PASS | Windows SDK `adb.exe`, version 1.0.41 / 37.0.0-14910828 |
| physical device detected | PASS | `MJPVXCSG9HYL65YL device` |
| device authorized | PASS | ADB status is `device`, not `unauthorized` |
| debug APK build | FAIL in WSL | repository `local.properties` points to Windows SDK path; WSL Android build tools are Windows `.exe` files and Linux Gradle cannot execute expected extensionless tools |
| APK installation | NOT RUN for current source | build did not produce a current APK in this invocation |
| app launch | PASS for already-installed `com.vokie` | `monkey -p com.vokie 1` returned activity launch and logcat showed `MainActivity` |
| logcat | PASS | filtered `com.vokie`/AndroidRuntime output available |
| microphone access | PASS / permission state only | `dumpsys package` reports RECORD_AUDIO granted; actual PTT test not run by agent |
| second device | FAIL / not detected | `adb devices -l` listed only Device A |

## SDK build blocker

The Android SDK exists at the Windows path referenced by Gradle, but this Linux/WSL shell cannot use that Windows SDK as a normal Linux SDK. Running Gradle with `/mnt/c/.../Android/Sdk` still reports Build Tools 34.0.0 corrupted/missing because it expects `aapt`, while the installation contains `aapt.exe`.

No `PATH`, `local.properties`, application source, or SDK installation was modified. Recommended fix is to run Gradle from Windows/Android Studio using the Windows SDK, or configure a valid Linux SDK separately. Do not mix the Windows SDK path into a Linux Gradle execution.

## Transport capability

| Capability | Status | Basis |
|---|---|---|
| Wi-Fi Direct hardware/API on Device A | UNKNOWN | no two-device test and no transport test performed |
| Bluetooth Classic hardware/API on Device A | UNKNOWN | no two-device test performed |
| Wi-Fi Direct pair testing | UNAVAILABLE | no Device B |
| Bluetooth RFCOMM pair testing | UNAVAILABLE | no Device B |
| Internet-off local transport | NOT MEASURED | not tested |

Hardware presence alone is not treated as proof of usable Wi-Fi Direct or RFCOMM behavior.

## Result

```text
ADB STATUS = PASS
REAL DEVICE TESTING = PARTIALLY READY
DEVICE A = AVAILABLE and authorized
DEVICE B = NOT AVAILABLE
DEBUG APK INSTALL = BLOCKED by WSL/Windows SDK tool mismatch
APP LAUNCH = PASS for existing installed package only
LOGCAT = PASS
MICROPHONE PERMISSION = PASS (permission state only)
TWO_DEVICE_TEST = BLOCKED
```

No source code or product configuration was changed during this environment setup. No release build, model download, dataset acquisition, AWS action, or training was performed.
