import java.util.Properties

plugins { id("com.android.application") }
val releaseKeys = Properties().apply {
    rootProject.file(".signing/release.properties").inputStream().use { load(it) }
}

android {
    namespace = "com.qianyu.lux"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.qianyu.lux"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
    }
signingConfigs {
    create("release") {
        storeFile = rootProject.file(".signing/lucent-singularity.jks")
        storePassword = releaseKeys.getProperty("storePassword")
        keyAlias = "lucent-singularity"
        keyPassword = releaseKeys.getProperty("keyPassword")
    }
}
buildTypes {
    getByName("release") {
        signingConfig = signingConfigs.getByName("release")
    }
}
compileOptions {

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.activity:activity:1.11.0")
    implementation("androidx.camera:camera-camera2:1.5.1")
    implementation("androidx.camera:camera-lifecycle:1.5.1")
    implementation("androidx.camera:camera-view:1.5.1")
}
