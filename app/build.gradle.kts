import java.util.Properties
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val mapsApiKey = providers.gradleProperty("MAPS_API_KEY").orNull
    ?: providers.environmentVariable("MAPS_API_KEY").orNull
    ?: Properties().run {
        val secretsFile = rootProject.file("secrets.properties")
        if (secretsFile.exists()) {
            secretsFile.inputStream().use { load(it) }
        }
        getProperty("MAPS_API_KEY", "")
    }

android {
    namespace = "com.example.axognition"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.axognition"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "AXOGNITION_SERVER_URL", "\"http://192.168.178.23:8080/\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("main").assets.directories += rootProject.file("offline-tts/assets").path
    androidResources {
        noCompress += "onnx"
    }
}

// Keep large, checksum-verified model downloads out of Git. They are bundled in
// the APK, so the installed app needs no connection or external TTS service.
tasks.named("preBuild") {
    val offlineTtsDirectory = rootProject.file("offline-tts").absolutePath
    doFirst {
        val directory = File(offlineTtsDirectory)
        check(listOf("sherpa-onnx-1.13.8.aar", "assets/tts/lecture/en/model.onnx", "assets/tts/lecture/sq/model.onnx",
            "assets/tts/assistant/en/model.onnx", "assets/tts/assistant/sq/model.onnx", "assets/tts/espeak-ng-data/phontab")
            .all { directory.resolve(it).isFile }) {
            "Offline narration files are missing. Run: powershell -ExecutionPolicy Bypass -File scripts/setup-offline-tts.ps1"
        }
    }
}

dependencies {
    implementation(files(rootProject.file("offline-tts/sherpa-onnx-1.13.8.aar")))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.reorderable)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.play.services.location)
    implementation(libs.maps.compose)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

}
