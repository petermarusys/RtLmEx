plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.peyo.rtlmex"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.peyo.rtlmex"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.srcDirs("src")
            res.srcDirs("res")
            assets.srcDirs("assets")
            jniLibs.srcDirs("jniLibs")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    aaptOptions {
        noCompress("litertlm")
        noCompress("tflite")
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.13.1")
    implementation("com.google.ai.edge.localagents:localagents-rag:0.3.0")
    implementation("com.google.protobuf:protobuf-javalite:3.25.3")
    debugImplementation(libs.androidx.compose.ui.tooling)
}