package com.pace.app.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ResumableUploadIdentityTest {
    @Test
    fun identityIsStableForExactArchiveAndScopedToObjectPath() {
        val archive = byteArrayOf(1, 2, 3)
        val first = resumableUploadIdentity("user/activity.pace.gz", archive)
        assertEquals(first, resumableUploadIdentity("user/activity.pace.gz", archive.copyOf()))
        assertNotEquals(first, resumableUploadIdentity("user/routes/activity.pace.gz", archive))
        assertNotEquals(first, resumableUploadIdentity("user/activity.pace.gz", byteArrayOf(1, 2, 4)))
    }
}
