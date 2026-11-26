package com.swordfish.lemuroid.ext.feature.savesync

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.api.services.drive.DriveScopes
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Google Drive access through Google's Authorization API (Identity.getAuthorizationClient), which
 * replaces the deprecated Google Sign-In. Only the app's private Drive folder (drive.appdata) is
 * requested. The chosen account's email is remembered so background syncs ask for the same account
 * without showing anything.
 */
object DriveAuth {
    private const val PREF_ACCOUNT_EMAIL = "gdrive_account_email"
    private const val ACCOUNT_TYPE = "com.google"
    private const val EMAIL_SCOPE = "email"
    private const val TOKEN_TIMEOUT_SECONDS = 30L

    private val driveScope = Scope(DriveScopes.DRIVE_APPDATA)

    private fun preferences(context: Context) = SharedPreferencesHelper.getSharedPreferences(context)

    fun accountEmail(context: Context): String? = preferences(context).getString(PREF_ACCOUNT_EMAIL, null)

    fun isConnected(context: Context): Boolean = accountEmail(context) != null

    /** Asks for Drive access. The result may need the user's consent ([AuthorizationResult.hasResolution]). */
    fun authorize(
        context: Context,
        email: String? = accountEmail(context),
    ): Task<AuthorizationResult> {
        val request =
            AuthorizationRequest.Builder()
                .setRequestedScopes(listOf(driveScope, Scope(EMAIL_SCOPE)))
                .apply { if (email != null) setAccount(Account(email, ACCOUNT_TYPE)) }
                .build()
        return Identity.getAuthorizationClient(context).authorize(request)
    }

    /** Remembers the account of a granted [result]. Returns its email, if known. */
    fun onAuthorized(
        context: Context,
        result: AuthorizationResult,
    ): String? {
        val email = result.toGoogleSignInAccount()?.email
        preferences(context).edit().putString(PREF_ACCOUNT_EMAIL, email ?: "").apply()
        return email
    }

    /**
     * Returns an access token for Drive without any UI, or null if access was never granted or was
     * withdrawn. Blocks: call it off the main thread.
     */
    fun accessToken(context: Context): String? {
        if (!isConnected(context)) return null
        val email = accountEmail(context)?.takeIf { it.isNotEmpty() }
        return runCatching {
            val result = Tasks.await(authorize(context, email), TOKEN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (result.hasResolution()) null else result.accessToken
        }.onFailure { Timber.e(it, "Could not get a Google Drive access token") }.getOrNull()
    }

    /** Tells Play services to drop a cached [token] (e.g. after Drive rejected it). */
    fun clearToken(
        context: Context,
        token: String,
    ) {
        Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(token).build())
    }

    /** Withdraws Drive access for the remembered account and forgets it. */
    fun disconnect(context: Context): Task<Void> {
        val email = accountEmail(context)?.takeIf { it.isNotEmpty() }
        preferences(context).edit().remove(PREF_ACCOUNT_EMAIL).apply()
        if (email == null) return Tasks.forResult(null)
        val request =
            RevokeAccessRequest.builder()
                .setAccount(Account(email, ACCOUNT_TYPE))
                .setScopes(listOf(driveScope))
                .build()
        return Identity.getAuthorizationClient(context).revokeAccess(request)
    }
}
