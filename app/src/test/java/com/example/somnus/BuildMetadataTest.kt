package com.example.somnus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BuildMetadataTest {

    @Test
    fun versionMetadata_matchesExpectedReleaseConfiguration() {
        assertEquals("0.1.0", BuildConfig.VERSION_NAME)
        assertEquals(1, BuildConfig.VERSION_CODE)
    }

    @Test
    fun isDebugSignedRelease_defaultsToFalseInStandardBuild() {
        assertFalse(BuildConfig.IS_DEBUG_SIGNED_RELEASE)
    }
}
