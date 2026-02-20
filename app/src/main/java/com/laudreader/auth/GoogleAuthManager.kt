package com.laudreader.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GoogleAuthManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform"
        private const val OAUTH_SCOPE_PREFIX = "oauth2:"
    }

    private val signInClient: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CLOUD_PLATFORM_SCOPE))
            .build()
        GoogleSignIn.getClient(context, options)
    }

    fun getSignInIntent(): Intent = signInClient.signInIntent

    fun isSignedIn(): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null
    }

    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)?.account ?: return@withContext null
        try {
            GoogleAuthUtil.getToken(context, account, "$OAUTH_SCOPE_PREFIX$CLOUD_PLATFORM_SCOPE")
        } catch (e: Exception) {
            // Token might be expired; clear it and retry once
            try {
                GoogleAuthUtil.clearToken(context, GoogleAuthUtil.getToken(context, account, "$OAUTH_SCOPE_PREFIX$CLOUD_PLATFORM_SCOPE"))
                GoogleAuthUtil.getToken(context, account, "$OAUTH_SCOPE_PREFIX$CLOUD_PLATFORM_SCOPE")
            } catch (retryException: Exception) {
                null
            }
        }
    }

    suspend fun signOut() {
        signInClient.signOut()
    }
}
