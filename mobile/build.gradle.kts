plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val clientRoot = if (project == rootProject) rootDir.parentFile else rootDir
val sharedTorrentSource = layout.buildDirectory.dir("generated/sharedTorrentSource")
val prepareTorrentSource by tasks.registering(Sync::class) {
    from(clientRoot.resolve("app/src/main/java/com/nuvio/tv/core/torrent/TorrentMagnet.kt"))
    into(sharedTorrentSource)
}
val preparedAssets = layout.buildDirectory.dir("generated/clientAssets")
val prepareClientAssets by tasks.registering(Exec::class) {
    workingDir(clientRoot)
    inputs.files(
        clientRoot.resolve("clients/web"),
        clientRoot.resolve("branding/mooncat-logo.png"),
        clientRoot.resolve("app/src/main/res/drawable-nodpi/mooncat_mark_v102.webp"),
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
        ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        targetSdk = 35
        versionCode = 30005
        versionName = "3.0.0-alpha.5"
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
    sourceSets["main"].res.srcDir(layout.buildDirectory.dir("generated/tvBranding"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    sourceSets.named("main") { kotlin.srcDir(prepareTorrentSource) }
}

val copyPhoneLauncher by tasks.registering(Copy::class) {
    from(clientRoot.resolve("app/src/main/res")) {
        include("mipmap-*/ic_launcher.png")
        rename("ic_launcher.png", "mooncat_launcher.png")
    }
    into(layout.buildDirectory.dir("generated/tvBranding"))
}
tasks.named("preBuild") { dependsOn(prepareClientAssets, copyPhoneLauncher) }

dependencies {
    implementation(files(clientRoot.resolve("app/libs/lib-nuvio-engine-android-0.1.5.aar")))
    implementation(libs.coroutines.android)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
}
