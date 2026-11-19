import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.handson.tablet"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.handson.tablet"
        minSdk = 28
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // Assinatura fixa: assim cada versão nova instala por cima da anterior no tablet.
    signingConfigs {
        create("handson") {
            storeFile = file("handson.keystore")
            storePassword = "handson123"
            keyAlias = "handson"
            keyPassword = "handson123"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("handson") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("handson")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    androidResources { noCompress += "task" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")

    implementation("com.google.mediapipe:tasks-vision:0.10.20")
}

// Baixa o modelo de leitura da mão (Google MediaPipe) na primeira compilação.
val baixarModelo by tasks.registering {
    val destino = file("src/main/assets/hand_landmarker.task")
    outputs.file(destino)
    doLast {
        if (!destino.exists() || destino.length() < 1_000_000) {
            destino.parentFile.mkdirs()
            val url = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task"
            println("Baixando o modelo da mão: $url")
            URI(url).toURL().openStream().use { entrada -> destino.outputStream().use { entrada.copyTo(it) } }
        }
    }
}
tasks.named("preBuild") { dependsOn(baixarModelo) }
