import java.util.Properties
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing credentials live only in the git-ignored
// signing/keystore.properties. Never hardcode passwords here and never fall
// back to the debug signing key for a release build.
val signingPropertiesFile = rootProject.file("signing/keystore.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.exists()) {
        signingPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "dev.lluissunol.iptv"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.lluissunol.iptv"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
    }

    signingConfigs {
        create("release") {
            if (signingPropertiesFile.exists()) {
                storeFile = file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Fail fast with an actionable message if a release build is requested while
// signing/keystore.properties is missing. Applies only to release APK/AAB
// tasks, so debug and lint builds keep working without the keystore.
gradle.taskGraph.whenReady {
    val needsReleaseSigning = allTasks.any { task ->
        val name = task.name.lowercase()
        (name.startsWith("assemble") || name.startsWith("package") || name.startsWith("bundle")) &&
            name.endsWith("release")
    }
    if (needsReleaseSigning && !signingPropertiesFile.exists()) {
        throw GradleException(
            "Cannot build a release APK: signing/keystore.properties is missing.\n" +
                "Create it (git-ignored) next to signing/opentv-release.jks with:\n" +
                "  storeFile=../signing/opentv-release.jks\n" +
                "  storePassword=<store password>\n" +
                "  keyAlias=opentv\n" +
                "  keyPassword=<key password>\n" +
                "Release builds never fall back to the debug signing key."
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
