package com.pace.app.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(kotlin.time.ExperimentalTime::class)
class AndroidEncryptedSessionManagerTest {
    @Test
    fun sessionSurvivesReloadAndSignOutDeletesIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = AndroidEncryptedSessionManager(context)
        manager.deleteSession()
        val session = UserSession(
            accessToken = "test-access-token",
            refreshToken = "test-refresh-token",
            expiresIn = 300,
            tokenType = "bearer",
        )

        manager.saveSession(session)
        val encryptedValue = context.getSharedPreferences("pace_auth_session", 0)
            .getString("encrypted_session", "")
        assertFalse(encryptedValue.orEmpty().contains("test-access-token"))
        assertEquals(session, manager.loadSession())
        manager.deleteSession()
        assertNull(manager.loadSession())
    }
}
