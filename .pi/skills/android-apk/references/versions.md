# Toolchain matrix (build-verified 2026-10-31)

This file is the source of truth for the Open IPTV build. Re-verify before
changing a version.

## Core

| Component | Version | Reason |
|---|---|---|
| Gradle | 8.14.6 | latest 8.x; AGP 8.13.2 compatible |
| AGP | 8.13.2 | latest 8.x; max compileSdk 36 |
| Kotlin | 2.2.21 | Compose compiler plugin matches |
| Compose BOM | 2026.06.01 | Compose UI 1.11.4; minCompileSdk 35 |
| Media3 | 1.11.1 | minCompileSdk 36 |
| compileSdk | 36 | installed |
| targetSdk | 36 | |
| minSdk | 24 | Android 7.0 |
| JDK | 17 | AGP 8.13 minimum |
| Build tools | 36.0.0 | installed |

## Libraries

| Library | Version | minCompileSdk / AGP |
|---|---|---|
| androidx.core:core-ktx | 1.18.0 | 36 / 8.9.1 |
| androidx.activity:activity-compose | 1.13.0 | 36 / 8.9.1 |
| androidx.lifecycle:* | 2.10.0 | 35 / 8.6.0 |
| androidx.navigation:navigation-compose | 2.9.8 | 35 / 8.6.0 |
| androidx.datastore:datastore-preferences | 1.3.0 | 34 / 8.1.1 |
| androidx.media3:* | 1.11.1 | 36 / 1.0.0 |
| com.squareup.okhttp3:okhttp | 4.12.0 | JVM; 5.x `okhttp-android` requires compileSdk 37 |
| org.jetbrains.kotlinx:kotlinx-coroutines-android | 1.11.0 | JVM |
| io.coil-kt.coil3:coil-compose | 3.6.3 | JVM/AndroidX |

## Do not use with this baseline

These require compileSdk 37 and/or AGP 9.1+:

- androidx.compose.ui 1.12.x (Compose BOM 2026.09.00)
- androidx.core:core-ktx 1.19.x
- androidx.lifecycle 2.11.x
- androidx.navigation 2.10.x
- AGP 9.x without an explicit migration decision

## Re-verification commands

```bash
curl -s https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml
curl -s https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml
curl -s https://dl.google.com/dl/android/maven2/androidx/media3/media3-exoplayer/maven-metadata.xml
curl -s https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml
curl -s https://services.gradle.org/versions/current

# AAR minimum compileSdk
unzip -p some.aar META-INF/com/android/build/gradle/aar-metadata.properties
```

AGP 8.13 compatibility: min Gradle 8.13, JDK 17, max API 36.
AGP 9.5 compatibility: min Gradle 9.6.0, JDK 17, max API 37, KGP 2.2.10.
