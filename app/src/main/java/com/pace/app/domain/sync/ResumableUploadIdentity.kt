package com.pace.app.domain.sync

import java.security.MessageDigest

/** Stable SDK cache key for resuming only the same private object and exact archive bytes. */
fun resumableUploadIdentity(objectPath: String, archive: ByteArray): String {
    require(objectPath.isNotBlank())
    require(archive.isNotEmpty())
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(objectPath.toByteArray(Charsets.UTF_8))
    digest.update(archive)
    return digest.digest().joinToString("") { "%02x".format(it) }
}
