package com.pace.app.auth

import android.content.Context
import android.content.Intent
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PaceAccountState {
    data object LocalOnly : PaceAccountState
    data object Loading : PaceAccountState
    data object SignedOut : PaceAccountState
    data class SignedIn(val userId: String, val email: String?) : PaceAccountState
    data object NeedsReauthentication : PaceAccountState
}

enum class AuthMessage {
    CheckEmail,
    ResetEmailSent,
    PasswordUpdated,
    SignedOut,
    SignedIn,
    InvalidInput,
    AccountUnavailable,
    NetworkOrServiceError,
}

data class AuthActionResult(val message: AuthMessage, val isSuccess: Boolean)

interface PaceAuthRepository {
    val isConfigured: Boolean
    val accountState: StateFlow<PaceAccountState>

    suspend fun signIn(email: String, password: String): AuthActionResult
    suspend fun register(email: String, password: String): AuthActionResult
    suspend fun sendPasswordReset(email: String): AuthActionResult
    suspend fun updatePassword(password: String): AuthActionResult
    suspend fun signOut(): AuthActionResult
    fun handleAuthCallback(intent: Intent)
}

class LocalOnlyAuthRepository : PaceAuthRepository {
    override val isConfigured: Boolean = false
    override val accountState: StateFlow<PaceAccountState> = MutableStateFlow(PaceAccountState.LocalOnly).asStateFlow()

    override suspend fun signIn(email: String, password: String) = unavailable()
    override suspend fun register(email: String, password: String) = unavailable()
    override suspend fun sendPasswordReset(email: String) = unavailable()
    override suspend fun updatePassword(password: String) = unavailable()
    override suspend fun signOut() = unavailable()
    override fun handleAuthCallback(intent: Intent) = Unit

    private fun unavailable() = AuthActionResult(AuthMessage.AccountUnavailable, isSuccess = false)
}

class SupabaseAuthRepository(
    context: Context,
    private val supabaseUrl: String,
    publishableKey: String,
) : PaceAuthRepository {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = createSupabaseClient(supabaseUrl, publishableKey) {
        install(Auth) {
            flowType = FlowType.PKCE
            scheme = AUTH_SCHEME
            host = AUTH_HOST
            sessionManager = AndroidEncryptedSessionManager(appContext)
        }
    }
    private val mutableAccountState = MutableStateFlow<PaceAccountState>(PaceAccountState.Loading)

    override val isConfigured: Boolean = true
    override val accountState: StateFlow<PaceAccountState> = mutableAccountState.asStateFlow()

    init {
        scope.launch {
            client.auth.sessionStatus.collect { status ->
                mutableAccountState.value = when (status) {
                    SessionStatus.Initializing -> PaceAccountState.Loading
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        if (user?.id.isNullOrBlank()) PaceAccountState.NeedsReauthentication
                        else PaceAccountState.SignedIn(user!!.id, user.email)
                    }
                    is SessionStatus.NotAuthenticated -> PaceAccountState.SignedOut
                    is SessionStatus.RefreshFailure -> PaceAccountState.NeedsReauthentication
                }
            }
        }
    }

    override suspend fun signIn(email: String, password: String): AuthActionResult = runAuthAction(AuthMessage.SignedIn) {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    override suspend fun register(email: String, password: String): AuthActionResult = runAuthAction(AuthMessage.CheckEmail) {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    override suspend fun sendPasswordReset(email: String): AuthActionResult = runAuthAction(AuthMessage.ResetEmailSent) {
        client.auth.resetPasswordForEmail(email.trim())
    }

    override suspend fun updatePassword(password: String): AuthActionResult = runAuthAction(AuthMessage.PasswordUpdated) {
        client.auth.updateUser { this.password = password }
    }

    override suspend fun signOut(): AuthActionResult = runAuthAction(AuthMessage.SignedOut) {
        client.auth.signOut()
    }

    override fun handleAuthCallback(intent: Intent) {
        if (intent.data?.scheme == AUTH_SCHEME && intent.data?.host == AUTH_HOST) {
            scope.launch { client.handleDeeplinks(intent) }
        }
    }

    private suspend fun runAuthAction(successMessage: AuthMessage, action: suspend () -> Unit): AuthActionResult =
        try {
            action()
            AuthActionResult(successMessage, isSuccess = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AuthActionResult(AuthMessage.NetworkOrServiceError, isSuccess = false)
        }

    private companion object {
        const val AUTH_SCHEME = "pace"
        const val AUTH_HOST = "auth-callback"
    }
}
