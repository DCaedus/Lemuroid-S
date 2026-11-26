package com.swordfish.lemuroid.ext.feature.savesync

import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
class ActivateGoogleDriveActivity : ComponentActivity() {
    // The account picker / consent screen, through the activity result API.
    private val authorizeLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            onAuthorizeResult(result.data)
        }

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
                        authorizeLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    }.onFailure { onFailed(it) }
                } else {
                    onConnected(result)
                }
            }
            .addOnFailureListener { onFailed(it) }
    }

    private fun onAuthorizeResult(data: Intent?) {
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
}
