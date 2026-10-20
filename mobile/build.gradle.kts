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
        versionCode = 10004
        versionName = "1.0.3-beta"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
