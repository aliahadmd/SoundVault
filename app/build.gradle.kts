import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

class ReleaseSigning(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)

fun signingProperty(name: String): String? = providers.gradleProperty(name)
    .orElse(providers.environmentVariable(name))
    .orNull
    ?.takeIf { it.isNotBlank() }

// Release signing, in order of precedence:
// 1. The SOUNDVAULT_RELEASE_* Gradle properties or environment variables (all four, never mixed with 2.).
// 2. The git-ignored local keystore: keystore/soundvault-release.jks with alias "soundvault" and
//    keystore/soundvault-release.pass holding the password (the same for store and key).
// Without either, assembleRelease produces app-release-unsigned.apk.
val releaseSigning: ReleaseSigning? = run {
    val storeFile = signingProperty("SOUNDVAULT_RELEASE_STORE_FILE")
    val storePassword = signingProperty("SOUNDVAULT_RELEASE_STORE_PASSWORD")
    val keyAlias = signingProperty("SOUNDVAULT_RELEASE_KEY_ALIAS")
    val keyPassword = signingProperty("SOUNDVAULT_RELEASE_KEY_PASSWORD")
    if (listOf(storeFile, storePassword, keyAlias, keyPassword).any { it != null }) {
        if (storeFile != null && storePassword != null && keyAlias != null && keyPassword != null) {
            ReleaseSigning(file(storeFile), storePassword, keyAlias, keyPassword)
        } else {
            null
        }
    } else {
        val keystoreDir = rootProject.layout.projectDirectory.dir("keystore")
        val localKeystore = keystoreDir.file("soundvault-release.jks").asFile
        val localPassword = providers.fileContents(keystoreDir.file("soundvault-release.pass"))
            .asText.orNull?.trim()?.takeIf { it.isNotEmpty() }
        if (localKeystore.isFile && localPassword != null) {
            ReleaseSigning(localKeystore, localPassword, "soundvault", localPassword)
        } else {
            null
        }
    }
}

android {
    namespace = "me.aliahad.audioplayer"
    compileSdk {
        version = release(36)
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = releaseSigning.storeFile
                storePassword = releaseSigning.storePassword
                keyAlias = releaseSigning.keyAlias
                keyPassword = releaseSigning.keyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "me.aliahad.audioplayer"
        minSdk = 31
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8 strips unused code (notably the extended Material icon set) and resources: ~49 MB -> a few MB.
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigning != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Release configuration (R8, resource shrinking) signed with the debug key, installable
        // side by side with release, for smoke-testing minified builds on a device.
        create("qa") {
            initWith(getByName("release"))
            applicationIdSuffix = ".qa"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media.compat)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.assertions.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
