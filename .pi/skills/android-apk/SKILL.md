---
name: android-apk
description: Build, verify, sign, and install the Open IPTV Android APK. Use for Gradle setup, wrapper builds, debug and release APKs, keystore signing, apksigner/aapt2 verification, adb install, logcat, and Windows-specific Android SDK paths. Includes the verified toolchain baseline and failure troubleshooting.
---

# Android APK delivery

The build must be reproducible with the Gradle wrapper and JDK 17. Do not
require a global Gradle, Android Studio, or a specific IDE.

## Baseline (build-verified 2026-10-31)

| Tool | Version |
|---|---|
| Gradle wrapper | 8.14.6 |
| AGP | 8.13.2 |
| Kotlin | 2.2.21 |
| Compose BOM | 2026.06.01 (UI 1.11.4, Material3 1.4.0) |
| Media3 | 1.11.1 |
| compileSdk / targetSdk | 36 |
| minSdk | 24 |
| JDK | 17 |
| Build tools | 36.0.0 |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk` |

Why not AGP 9 / Compose 1.12: Compose 1.12 requires compileSdk 37 and AGP 9.1+.
AGP 9 uses built-in Kotlin and pins KGP 2.2.10, complicating Compose compiler
alignment. Only migrate with explicit approval and after reading the
`agp-9-upgrade` skill. Platform 37 is available as `platforms;android-37.0` if
that migration is approved.

Dependency versions that require compileSdk 37 / AGP 9.1+ and must not be used
with this baseline: core-ktx 1.19.x, lifecycle 2.11.x, navigation 2.10.x,
Compose UI 1.12.x. Use core-ktx 1.18.0, lifecycle 2.10.0, navigation 2.9.8,
Compose BOM 2026.06.01.

## First-time setup

```bash
# from the repository root
java -version                      # must be 17.x
echo "$JAVA_HOME"                  # Temurin 17 path
adb devices -l                     # phone connected + authorized?

# SDK path for Gradle (git-ignored)
cat > local.properties <<'EOF'
sdk.dir=C:/Android/Sdk
EOF

# wrapper (one-time; commit gradle/wrapper/* and gradlew*)
gradle wrapper --gradle-version 8.14.6
```

If Gradle is not installed, generate the wrapper with Android Studio or copy the
wrapper files from a known project, then `./gradlew --version`.

On PowerShell, write the path as:

```properties
sdk.dir=C:/Android/Sdk
```

Do not commit `local.properties`.

## Build

```bash
./gradlew :app:assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Useful focused tasks:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug --stacktrace
```

Do not run `clean` as a reflex. It hides incremental-build bugs and wastes time.
Use `--rerun-tasks` only when a task is genuinely stale.

## Verify the artifact

```bash
APK=app/build/outputs/apk/debug/app-debug.apk
ls -lh "$APK"
sha256sum "$APK"

SDK="$LOCALAPPDATA/Android/Sdk"
"$SDK/build-tools/36.0.0/apksigner.bat" verify --print-certs "$APK"
"$SDK/build-tools/36.0.0/aapt2.exe" dump badging "$APK" | head -20
```

`aapt2 dump badging` must show:

- `package: name='app.opentv'`
- `sdkVersion:'24'`
- `targetSdkVersion:'36'`

If the APK is unsigned (debug builds are signed with the debug key),
`apksigner verify` still works and prints the debug certificate.

## Install and launch

```bash
adb devices -l
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.opentv/.MainActivity
adb logcat --pid=$(adb shell pidof -s app.opentv)
```

If `adb devices` is empty:

1. Enable Developer options → USB debugging on the phone.
2. Accept the RSA authorization prompt.
3. Try another USB cable/port (data cable, not charge-only).
4. `adb kill-server && adb start-server`.
5. For wireless debugging, use `adb pair` + `adb connect`.

If no phone is available, create an emulator:

```bash
SDK="$LOCALAPPDATA/Android/Sdk"
"$SDK/cmdline-tools/latest/bin/sdkmanager.bat" "system-images;android-36;google_apis;x86_64"
"$SDK/cmdline-tools/latest/bin/avdmanager.bat" create avd -n opentv36 -k "system-images;android-36;google_apis;x86_64" -d pixel_6
"$SDK/emulator/emulator.exe" -avd opentv36
```

## Release signing (only with user approval)

Generate a keystore **outside** the repository or in `signing/` (git-ignored):

```bash
keytool -genkeypair -v \
  -keystore signing/opentv-release.jks \
  -alias opentv \
  -keyalg RSA -keysize 4096 -validity 10000
```

`keystore.properties` (git-ignored):

```properties
storeFile=../signing/opentv-release.jks
storePassword=CHANGE_ME
keyAlias=opentv
keyPassword=CHANGE_ME
```

`app/build.gradle.kts` reads it only if the file exists. A release build without
it must fail with a clear message; never fall back to the debug key for a
release.

```bash
./gradlew :app:assembleRelease
"$SDK/build-tools/36.0.0/apksigner.bat" verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Never commit `*.jks`, `*.keystore`, `keystore.properties`, or passwords. Do not
publish a personal build signed with a debug key.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `SDK location not found` | missing/incorrect `local.properties` | write `sdk.dir` with escaped `\` and `:` |
| `Unsupported class file major version` | wrong JDK | set `JAVA_HOME` to JDK 17 and `org.gradle.java.home` if needed |
| `Android Gradle plugin requires Java 17` | JDK 11/8 | switch to JDK 17 |
| `Dependency ... requires compileSdk 37` | Compose 1.12 / core 1.19 / lifecycle 2.11 / nav 2.10 | use the baseline versions |
| `Failed to apply plugin 'org.jetbrains.kotlin.android'` | AGP 9 built-in Kotlin | do not upgrade; or follow `agp-9-upgrade` |
| `Duplicate class ...` | overlapping dependencies | align versions; exclude the duplicate |
| `R8 missing class` | missing keep rule | add a targeted `-keep`; do not disable R8 |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | different signing key | `adb uninstall app.opentv` then install |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | lower versionCode | bump versionCode or use `-d` for debug |
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` | phone full | free space |
| `CLEARTEXT communication not permitted` | manifest flag missing | `android:usesCleartextTraffic="true"` |
| `Resources$NotFoundException` | missing/renamed resource | check `strings.xml`/drawables |
| Gradle daemon OOM | default heap | `org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8` |
| Slow dependency downloads | network/proxy | check proxy, use `--offline` only after a full sync |
| `No connected devices` | USB debugging/auth | see install section |
| App opens then closes | runtime exception | `adb logcat` around `AndroidRuntime` |
| Black video, audio OK | codec/surface | try non-HEVC variant; check `MediaCodec` logs |
| `PlayerView` crash on exit | player released while attached | release in `onCleared`, detach surface first |

## Build-file expectations

- Version catalog `gradle/libs.versions.toml` is preferred over hard-coded
  versions in `build.gradle.kts`.
- `gradle.properties`: `org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8`,
  `android.useAndroidX=true`, `kotlin.code.style=official`.
- `app/build.gradle.kts`: `compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`,
  `buildFeatures { compose = true }`, Compose compiler plugin 2.2.21.
- No `kotlin-android` plugin if AGP 9 is ever used; with AGP 8.13.2 it is
  required.
- `android:usesCleartextTraffic="true"` in the manifest.
- Keep `INTERNET` as the only dangerous-ish permission; no storage/location.

## Definition of done

- [ ] `./gradlew :app:assembleDebug` succeeds from a clean checkout.
- [ ] APK path, size, SHA-256, package, minSdk, targetSdk verified.
- [ ] `adb install -r` succeeds on a real phone or emulator.
- [ ] App launches and reaches the playlist screen.
- [ ] `adb logcat` shows no `FATAL EXCEPTION` on startup.
- [ ] APK is available to the user with the exact install command.
- [ ] No secret is committed or printed.
