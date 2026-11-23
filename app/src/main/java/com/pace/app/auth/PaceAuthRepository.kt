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
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import com.pace.app.domain.sync.resumableUploadIdentity
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
    EmailUnconfirmed,
    InvalidCredentials,
    RateLimited,
    EmailUnavailable,
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
    fun accessTokenOrNull(): String?
    suspend fun uploadPrivateArchive(userId: String, objectPath: String, archive: ByteArray)

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
    override fun accessTokenOrNull(): String? = null
    override suspend fun uploadPrivateArchive(userId: String, objectPath: String, archive: ByteArray) =
        throw IllegalStateException("Cloud storage is unavailable in local-only mode.")

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
        install(Storage)
    }
    private val mutableAccountState = MutableStateFlow<PaceAccountState>(PaceAccountState.Loading)
    private val mutableAccessToken = MutableStateFlow<String?>(null)

    override val isConfigured: Boolean = true
    override val accountState: StateFlow<PaceAccountState> = mutableAccountState.asStateFlow()
    override fun accessTokenOrNull(): String? = mutableAccessToken.value

    override suspend fun uploadPrivateArchive(userId: String, objectPath: String, archive: ByteArray) {
        val signedIn = accountState.value as? PaceAccountState.SignedIn
            ?: throw IllegalStateException("A signed-in account is required for archive upload.")
        require(signedIn.userId == userId)
        require(userId.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")))
        require(objectPath.matches(Regex("${Regex.escape(userId)}/(?:[0-9a-fA-F-]{36}\\.pace\\.gz|routes/[0-9a-fA-F-]{36}\\.pace\\.gz)")))
        require(archive.isNotEmpty() && archive.size <= 100 * 1024 * 1024)
        val source = resumableUploadIdentity(objectPath, archive)
        client.storage.from("pace-private-tracks").resumable
            .createOrContinueUpload(
                data = archive,
                source = source,
                path = objectPath,
                options = { upsert = true },
            )
            .startOrResumeUploading()
    }

    init {
        scope.launch {
            client.auth.sessionStatus.collect { status ->
                mutableAccountState.value = when (status) {
                    SessionStatus.Initializing -> PaceAccountState.Loading
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        if (user?.id.isNullOrBlank()) {
                            mutableAccessToken.value = null
                            PaceAccountState.NeedsReauthentication
                        } else {
                            mutableAccessToken.value = status.session.accessToken
                            PaceAccountState.SignedIn(user.id, user.email)
                        }
                    }
                    is SessionStatus.NotAuthenticated -> { mutableAccessToken.value = null; PaceAccountState.SignedOut }
                    is SessionStatus.RefreshFailure -> { mutableAccessToken.value = null; PaceAccountState.NeedsReauthentication }
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
        client.auth.signUpWith(Email, redirectUrl = AUTH_REDIRECT_URL) {
            this.email = email.trim()
            this.password = password
        }
    }

    override suspend fun sendPasswordReset(email: String): AuthActionResult = runAuthAction(AuthMessage.ResetEmailSent) {
        client.auth.resetPasswordForEmail(email.trim(), redirectUrl = AUTH_REDIRECT_URL)
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
        } catch (error: io.github.jan.supabase.auth.exception.AuthRestException) {
            val message = when (error.errorCode?.value) {
                "email_not_confirmed" -> AuthMessage.EmailUnconfirmed
                "invalid_credentials" -> AuthMessage.InvalidCredentials
                "over_request_rate_limit", "over_email_send_rate_limit" -> AuthMessage.RateLimited
                "email_address_not_authorized", "email_provider_disabled", "signup_disabled" -> AuthMessage.EmailUnavailable
                else -> AuthMessage.NetworkOrServiceError
            }
            AuthActionResult(message, isSuccess = false)
        } catch (_: Exception) {
            AuthActionResult(AuthMessage.NetworkOrServiceError, isSuccess = false)
        }

    private companion object {
        const val AUTH_SCHEME = "pace"
        const val AUTH_HOST = "auth-callback"
        const val AUTH_REDIRECT_URL = "$AUTH_SCHEME://$AUTH_HOST"
    }
}
