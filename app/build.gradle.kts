import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.sqldelight")
}

android {
    namespace = "one.calypsocloud.ezamp"
    compileSdk = 36

    defaultConfig {
        applicationId = "one.calypsocloud.ezamp"
        minSdk = 26
        targetSdk = 36
        // VERSION at the repo root is the single source of truth: major*10000 + minor*100 + patch.
        val version = rootProject.file("VERSION").readText().trim()
        val parts = version.split("-", "+")[0].split(".").map { it.toIntOrNull() ?: 0 }
        versionName = version
        versionCode = (parts.getOrElse(0) { 0 } * 10_000) +
            (parts.getOrElse(1) { 0 } * 100) +
            parts.getOrElse(2) { 0 }
    }

    // The upload key lives outside the tree. keystore.properties (git-ignored) names it:
    //   storeFile=C:/ezamp/keys/ezamp-upload.jks  storePassword=…  keyAlias=upload  keyPassword=…
    // Without that file, release falls back to the debug key so local sideload builds keep working.
    val keyProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }
    }
    signingConfigs {
        if (keyProps != null) {
            create("upload") {
                storeFile = file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ""
            isMinifyEnabled = false
        }
        release {
            // Perf numbers from a debug build are meaningless: R8 + no JIT warmup is the real app.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // VERSION_NAME goes into Jellyfin's client header.
        buildConfig = true
    }

    testOptions {
        // Android stubs return defaults instead of throwing, so JVM tests can touch classes that
        // merely reference android.* (Log, Context) without an emulator.
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST", "/META-INF/DEPENDENCIES")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    implementation("androidx.media3:media3-exoplayer:1.9.4")
    implementation("androidx.media3:media3-session:1.9.4")

    implementation("io.ktor:ktor-client-core:3.5.2")
    implementation("io.ktor:ktor-client-okhttp:3.5.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
}

sqldelight {
    databases {
        create("EzampDb") {
            packageName.set("one.calypsocloud.ezamp.db")
            // databases/N.db are snapshots of shipped schemas; the verify task replays N.sqm onward
            // over each and fails the gate if the result drifts from Library.sq.
            schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

dependencies {
    implementation("app.cash.sqldelight:android-driver:2.4.1")
    implementation("app.cash.sqldelight:coroutines-extensions:2.4.1")

    // Test-only: never packaged. The JDBC driver runs the real generated queries on in-memory SQLite.
    testImplementation("junit:junit:4.13.2")
    testImplementation("app.cash.sqldelight:sqlite-driver:2.4.1")
}
