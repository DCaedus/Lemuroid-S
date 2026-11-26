package com.swordfish.lemuroid.ext.feature.savesync

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.swordfish.lemuroid.ext.R
import timber.log.Timber

/** Connects Google Drive save sync, or disconnects it if it's already connected. */
class ActivateGoogleDriveActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return

        val apiAvailability = GoogleApiAvailability.getInstance()
        val errorCode = apiAvailability.isGooglePlayServicesAvailable(this)
        when {
            errorCode != ConnectionResult.SUCCESS -> {
                Toast.makeText(this, R.string.gdrive_missing_play_services, Toast.LENGTH_LONG).show()
                finish()
            }
            DriveAuth.isConnected(this) -> disconnect()
            else -> connect()
        }
    }

    private fun connect() {
        DriveAuth.authorize(this, email = null)
            .addOnSuccessListener { result ->
                val pendingIntent = result.pendingIntent
                if (result.hasResolution() && pendingIntent != null) {
                    // The user picks an account and allows access.
                    runCatching {
                        startIntentSenderForResult(pendingIntent.intentSender, REQUEST_AUTHORIZE, null, 0, 0, 0)
                    }.onFailure { onFailed(it) }
                } else {
                    onConnected(result)
                }
            }
            .addOnFailureListener { onFailed(it) }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_AUTHORIZE) return
        runCatching { Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data) }
            .onSuccess { onConnected(it) }
            .onFailure { onFailed(it) }
    }

    private fun onConnected(result: AuthorizationResult) {
        val email = DriveAuth.onAuthorized(this, result) ?: "Google Drive"
        Toast.makeText(this, getString(R.string.gdrive_sign_in_success, email), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun onFailed(error: Throwable) {
        val statusCode = (error as? ApiException)?.statusCode?.toString() ?: "-"
        val message = getString(R.string.gdrive_sign_in_failed, error.message, statusCode)
        Timber.e(error, message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun disconnect() {
        DriveAuth.disconnect(this).addOnCompleteListener {
            if (!it.isSuccessful) Timber.e(it.exception, "Could not revoke Google Drive access")
            Toast.makeText(this, R.string.gdrive_sign_out_success, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        const val REQUEST_AUTHORIZE = 45
    }
}
