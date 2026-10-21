package com.nuvio.tv.updater

import org.junit.Assert.*
import org.junit.Test

class ApkUpdateIdentityTest {
    private val installed = ApkUpdateIdentity("dev.mooncat.tv", 10005, setOf("original"))
    @Test fun `newer original-key release can update installed TV app`() {
        validateApkUpdate(installed, installed.copy(versionCode = 10007))
    }
    @Test fun `side by side previews cannot be offered as installed app updates`() {
        assertThrows(IllegalArgumentException::class.java) {
            validateApkUpdate(installed, ApkUpdateIdentity("dev.mooncat.tv.preview", 10007, setOf("debug")))
        }
    }
    @Test fun `different keys and absent certificates are rejected before installer opens`() {
        listOf(setOf("debug"), emptySet()).forEach { certificates ->
            assertThrows(IllegalArgumentException::class.java) {
                validateApkUpdate(installed, installed.copy(versionCode = 10007, certificateDigests = certificates))
            }
        }
    }
    @Test fun `downgrades and reinstall loops are rejected`() {
        listOf(10004L, 10005L).forEach { version ->
            assertThrows(IllegalArgumentException::class.java) {
                validateApkUpdate(installed, installed.copy(versionCode = version))
            }
        }
    }
}
