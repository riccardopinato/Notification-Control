package com.riccardopinato.notificationcontrol.account

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.riccardopinato.notificationcontrol.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GoogleAccountProfile(
    val uniqueId: String,
    val email: String,
    val displayName: String?,
    val profilePictureUri: String?
)

data class AccountUiState(
    val profile: GoogleAccountProfile? = null,
    val loading: Boolean = false,
    val configured: Boolean = false,
    val errorMessage: String? = null
)

class GoogleAccountManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        AccountUiState(
            profile = readCachedProfile(),
            configured = serverClientId().isNotBlank()
        )
    )
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    suspend fun signIn(activity: Activity) {
        val clientId = serverClientId()
        if (clientId.isBlank()) {
            _state.value = _state.value.copy(
                loading = false,
                configured = false,
                errorMessage = appContext.getString(R.string.google_sign_in_not_configured)
            )
            return
        }

        _state.value = _state.value.copy(
            loading = true,
            configured = true,
            errorMessage = null
        )

        try {
            val credentialManager = CredentialManager.create(activity)
            val option = GetSignInWithGoogleOption.Builder(clientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )
            val credential = result.credential
            check(
                credential is CustomCredential &&
                    credential.type ==
                    GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            )
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            val profile = GoogleAccountProfile(
                uniqueId = google.id,
                email = google.id,
                displayName = google.displayName,
                profilePictureUri = google.profilePictureUri?.toString()
            )
            cacheProfile(profile)
            _state.value = AccountUiState(
                profile = profile,
                loading = false,
                configured = true
            )
        } catch (_: NoCredentialException) {
            _state.value = _state.value.copy(
                loading = false,
                errorMessage = appContext.getString(R.string.google_no_credential)
            )
        } catch (error: GetCredentialException) {
            _state.value = _state.value.copy(
                loading = false,
                errorMessage = error.message
                    ?: appContext.getString(R.string.google_sign_in_failed)
            )
        } catch (error: Throwable) {
            _state.value = _state.value.copy(
                loading = false,
                errorMessage = error.message
                    ?: appContext.getString(R.string.google_sign_in_failed)
            )
        }
    }

    suspend fun signOut(activity: Activity) {
        runCatching {
            CredentialManager.create(activity)
                .clearCredentialState(ClearCredentialStateRequest())
        }
        prefs.edit { clear() }
        _state.value = AccountUiState(
            profile = null,
            loading = false,
            configured = serverClientId().isNotBlank()
        )
    }

    private fun serverClientId(): String =
        appContext.getString(R.string.google_web_client_id).trim()

    private fun cacheProfile(profile: GoogleAccountProfile) {
        prefs.edit {
            putString(KEY_UNIQUE_ID, profile.uniqueId)
            putString(KEY_EMAIL, profile.email)
            putString(KEY_DISPLAY_NAME, profile.displayName)
            putString(KEY_PICTURE, profile.profilePictureUri)
        }
    }

    private fun readCachedProfile(): GoogleAccountProfile? {
        val uniqueId = prefs.getString(KEY_UNIQUE_ID, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        return GoogleAccountProfile(
            uniqueId = uniqueId,
            email = email,
            displayName = prefs.getString(KEY_DISPLAY_NAME, null),
            profilePictureUri = prefs.getString(KEY_PICTURE, null)
        )
    }

    companion object {
        private const val PREFS = "notification_control_google_account"
        private const val KEY_UNIQUE_ID = "unique_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_PICTURE = "picture"

        @Volatile
        private var instance: GoogleAccountManager? = null

        fun get(context: Context): GoogleAccountManager =
            instance ?: synchronized(this) {
                instance ?: GoogleAccountManager(context).also { instance = it }
            }
    }
}
