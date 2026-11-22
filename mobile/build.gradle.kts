plugins {
    alias(libs.plugins.android.application)
}

val clientRoot = if (project == rootProject) rootDir.parentFile else rootDir
val preparedAssets = layout.buildDirectory.dir("generated/clientAssets")
val prepareClientAssets by tasks.registering(Exec::class) {
    workingDir(clientRoot)
    inputs.files(
        clientRoot.resolve("clients/web"),
        clientRoot.resolve("branding/mooncat-logo.png"),
        clientRoot.resolve("app/src/main/assets/studios"),
        clientRoot.resolve("app/src/main/res/drawable"),
        clientRoot.resolve("app/src/main/java/com/nuvio/tv/ui/screens/news/MooncatNewsScreen.kt"),
        clientRoot.resolve("app/src/main/java/com/nuvio/tv/ui/screens/collection/MooncatStudiosScreen.kt"),
        clientRoot.resolve("scripts/prepare_client_assets.py")
    )
    outputs.dir(preparedAssets)
    commandLine("python3", "scripts/prepare_client_assets.py", preparedAssets.get().asFile.path)
}

android {
    namespace = "dev.mooncat.mobile"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "dev.mooncat.mobile.v3"
        minSdk = 24
        targetSdk = 35
        versionCode = 30004
        versionName = "3.0.0-alpha.4.1"
    }

    val signingPath = providers.environmentVariable("MOONCAT_CLIENT_KEYSTORE").orNull
    if (signingPath != null) {
        signingConfigs.create("clientRelease") {
            storeFile = file(signingPath)
            storePassword = providers.environmentVariable("MOONCAT_CLIENT_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("MOONCAT_CLIENT_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("MOONCAT_CLIENT_KEY_PASSWORD").get()
        }
    }

    buildTypes {
        debug { applicationIdSuffix = ".preview" }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("clientRelease")
        }
    }

    sourceSets["main"].assets.setSrcDirs(listOf(preparedAssets.get().asFile))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.named("preBuild") { dependsOn(prepareClientAssets) }

dependencies {
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
}
