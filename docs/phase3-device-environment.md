# Phase 3 real-device environment

## Verification date

2026-08-30. Commands were executed from Windows PowerShell through the Windows Android SDK/ADB, while the repository is located at `D:\vibe\vokie`.

## Device A

| Field | Result |
|---|---|
| Type | Physical Android device |
| Manufacturer | realme |
| Model | RMX3782 / narzo 60x 5G |
| Android | 15 |
| API | 35 |
| ABI | arm64-v8a |
| ADB serial | MJPVXCSG9HYL65YL |
| ADB state | `device` / authorized |
| Display | 1080x2400 |
| Density | 480 dpi |
| Application ID | `com.vokie` |
| Installed version | versionName 1.0.0, versionCode 1 |
| Microphone permission | granted=true |

## Device B

```text
NOT AVAILABLE
```

`adb devices -l` lists only Device A. No Android Studio emulator or second physical device is connected to the Windows ADB server.

## Windows build/deployment verification

| Check | Result | Evidence |
|---|---|---|
| Windows Java | PASS | Microsoft OpenJDK 17.0.18 at `C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot` |
| Windows SDK | PASS | `C:\Users\kille\AppData\Local\Android\Sdk` exists |
| Windows ADB | PASS | ADB 1.0.41 / 37.0.0-14910828 |
| Build Tools 34.0.0 | PASS | `aapt.exe`, `zipalign.exe`, and `apksigner.bat` present |
| Windows Gradle tests | PASS | wrapper main executed with Windows Java; `BUILD SUCCESSFUL` |
| Windows debug build | PASS | `assembleDebug --no-daemon`; `BUILD SUCCESSFUL` |
| Debug APK | PASS | `D:\vibe\vokie\app\build\outputs\apk\debug\app-debug.apk`, 198,787,226 bytes |
| APK installation | PASS | `adb install -r`; `Success` |
| APK launch | PASS | `monkey -p com.vokie 1`; `MainActivity` focused/displayed |
| Logcat | PASS | filtered package logs captured; no `AndroidRuntime` crash observed |
| Microphone permission | PASS | installed package reports `RECORD_AUDIO: granted=true`; actual PTT benchmark remains pending manual interaction |

The correct Windows test commands use the Gradle wrapper JAR because this checkout contains `gradlew` but not `gradlew.bat`:

```powershell
Set-Location D:\vibe\vokie
java -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon
java -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain assembleDebug --no-daemon
```

This avoids the WSL/Linux SDK mismatch. No project SDK path, PATH, or application source was changed.

## Launch/log evidence

The installed current debug APK launched `com.vokie/.MainActivity`. Relevant filtered logs included the local Whisper model load event and Android activity display events. No crash was observed in the captured log window.

## Transport capability

| Capability | Status |
|---|---|
| Wi-Fi Direct peer discovery | UNKNOWN — no second device |
| Wi-Fi Direct connection/TCP | UNKNOWN — no second device |
| Bluetooth Classic RFCOMM | UNKNOWN — no second device |
| Two-device transport validation | BLOCKED |
| Internet-off local transfer | NOT MEASURED |

The presence of a radio or Android API is not treated as proof of usable peer-to-peer transport.

## Final environment status

```text
ADB STATUS = PASS
WINDOWS JAVA = PASS
WINDOWS ANDROID SDK = PASS
WINDOWS DEBUG BUILD = PASS
CURRENT DEBUG APK INSTALL = PASS
CURRENT DEBUG APK LAUNCH = PASS
LOGCAT = PASS
MICROPHONE PERMISSION = PASS
DEVICE A = READY
DEVICE B = NOT AVAILABLE
REAL DEVICE BUILD/DEPLOYMENT = READY FOR SINGLE-DEVICE TESTING
TWO_DEVICE TESTING = BLOCKED
```

No release build, dataset/model download, AWS action, training, or product source-code change was performed for this environment verification.
