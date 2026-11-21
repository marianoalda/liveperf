import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildTimeStamp: String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

fun gitCommand(vararg args: String): String? = try {
    val process = ProcessBuilder("git", *args)
        .directory(rootProject.projectDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    if (process.waitFor() == 0 && output.isNotEmpty()) output else null
} catch (_: Exception) {
    null
}

/** Display form: vX.Y.Z, or vX.Y.Z-snapshot if HEAD is past the latest tag. */
fun resolveAppVersionLabel(): String {
    fun withV(tag: String) = if (tag.startsWith("v")) tag else "v$tag"
    val exact = gitCommand("describe", "--tags", "--exact-match", "HEAD")
    if (exact != null) return withV(exact)
    val latest = gitCommand("describe", "--tags", "--abbrev=0") ?: return "v0.0.0-snapshot"
    return "${withV(latest)}-snapshot"
}

val appVersionLabel: String = resolveAppVersionLabel()
val appVersionName: String = appVersionLabel.removePrefix("v")

android {
    namespace = "com.marianoalda.liveperf"
    compileSdk = 35
    buildToolsVersion = "35.0.1"

    defaultConfig {
        applicationId = "com.marianoalda.liveperf"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = appVersionName
        buildConfigField("String", "BUILD_TIME", "\"$buildTimeStamp\"")
        buildConfigField("String", "VERSION_LABEL", "\"$appVersionLabel\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
}
