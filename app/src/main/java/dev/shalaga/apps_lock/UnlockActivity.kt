package dev.shalaga.apps_lock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor
import androidx.core.content.edit

class UnlockActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "UnlockActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkg = intent.getStringExtra("packageName")
            ?: run {
                Log.w(TAG, "No packageName extra—finishing")
                finish(); return
            }

        Log.d(TAG, "Unlock requested for $pkg")

        setContent {
            MaterialTheme {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UnlockScreen(pkg) {
                        recordUnlock(pkg)
                        launchApp(pkg)
                        finish()
                    }
                }
            }
        }
    }

    private val appsLockPrefs by lazy {
        getSharedPreferences("apps_lock_prefs", MODE_PRIVATE)
    }

    private fun recordUnlock(pkg: String) {
        val now = System.currentTimeMillis()
        appsLockPrefs.edit {
            putLong("unlocked_$pkg", now)
                .putStringSet(
                    "unlocked_set",
                    (appsLockPrefs.getStringSet("unlocked_set", emptySet()) ?: emptySet()) + pkg
                )
        }
        Log.d(TAG, "Recorded unlock of $pkg at $now")
    }
}

@Composable
fun UnlockScreen(packageName: String, onAuthenticated: () -> Unit) {
    val activity = LocalActivity.current as FragmentActivity
    val executor = ContextCompat.getMainExecutor(activity)
    var err by remember { mutableStateOf<String?>(null) }

    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onAuthenticated()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            err = errString.toString()
        }
    }

    val prompt: PromptInfo = PromptInfo.Builder()
        .setTitle("Unlock $packageName")
        .setNegativeButtonText("Cancel")
        .build()

    LaunchedEffect(Unit) {
        val bm = BiometricManager.from(activity)
        if (bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS) {
            BiometricPrompt(activity, executor, callback)
                .authenticate(prompt)
        } else {
            err = "Biometric unavailable"
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Unlocking $packageName", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

fun Context.launchApp(pkg: String) {
    val pm = packageManager
    val launchIntent = pm.getLaunchIntentForPackage(pkg)
    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    } else {
        Toast.makeText(this, "App not found: $pkg", Toast.LENGTH_SHORT).show()
    }
}