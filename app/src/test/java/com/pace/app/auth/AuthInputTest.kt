package com.pace.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AuthInputTest {
    @Test
    fun emailIsTrimmedAndMustHaveLocalAndDomainParts() {
        assertEquals("runner@example.com", normalizeEmail(" runner@example.com "))
        assertEquals(null, normalizeEmail("runner"))
        assertEquals(null, normalizeEmail("@example.com"))
        assertEquals(null, normalizeEmail("runner@"))
        assertEquals(null, normalizeEmail("run ner@example.com"))
    }

    @Test
    fun newPasswordUsesEightCharacterMinimum() {
        assertFalse(isValidNewPassword("1234567"))
        assertTrue(isValidNewPassword("12345678"))
    }

    @Test
    fun missingServiceKeepsTheLocalProfileSignedInLocally() = runBlocking {
        val repository = LocalOnlyAuthRepository()
        assertFalse(repository.isConfigured)
        assertEquals(PaceAccountState.LocalOnly, repository.accountState.value)
        assertFalse(repository.signIn("runner@example.com", "long-password").isSuccess)
        assertFalse(repository.register("runner@example.com", "long-password").isSuccess)
    }

    @Test
    @OptIn(kotlin.time.ExperimentalTime::class)
    fun supabaseSessionSerializationRoundTrips() {
        val session = UserSession(
            accessToken = "test-access-token",
            refreshToken = "test-refresh-token",
            expiresIn = 300,
            tokenType = "bearer",
        )
        val json = Json { encodeDefaults = true }
        val encoded = json.encodeToString(session)
        assertEquals(session, json.decodeFromString<UserSession>(encoded))
    }
}
