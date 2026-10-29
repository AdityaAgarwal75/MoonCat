package com.nuvio.tv.updater

/** Pure policy shared by the installer and JVM regression tests. */
internal data class ApkUpdateIdentity(val packageName: String, val versionCode: Long, val certificateDigests: Set<String>)

internal fun validateApkUpdate(installed: ApkUpdateIdentity, downloaded: ApkUpdateIdentity) {
    require(downloaded.packageName == installed.packageName) { "This APK belongs to a different app. Download the matching MoonCat build." }
    require(downloaded.versionCode > installed.versionCode) { "This APK is not a newer MoonCat update." }
    // Some devices omit signing details; Android's installer still checks the APK.
    require(installed.certificateDigests.isEmpty() || downloaded.certificateDigests.isEmpty() || downloaded.certificateDigests == installed.certificateDigests) {
        "This APK uses a different signing key and cannot update this installation. Keep your existing app installed."
    }
}
