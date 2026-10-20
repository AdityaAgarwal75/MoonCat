plugins {
    alias(libs.plugins.android.application)
}
android {
    namespace = "dev.mooncat.mobile"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.mooncat.mobile"
        minSdk = 24
        targetSdk = 35
        versionCode = 10003
        versionName = "1.0.2-beta"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
