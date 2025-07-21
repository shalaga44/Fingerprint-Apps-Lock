package dev.shalaga.apps_lock

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
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
                        finish()
                    }
                }
            }
        }
    }

    private fun recordUnlock(pkg: String) {
        val prefs = getSharedPreferences("apps_lock_prefs", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        prefs.edit {
            putLong("unlocked_$pkg", now)
                .putStringSet(
                    "unlocked_set",
                    (prefs.getStringSet("unlocked_set", emptySet()) ?: emptySet()) + pkg
                )
        }
        Log.d(TAG, "Recorded unlock of $pkg at $now")
    }
}

@Composable
fun UnlockScreen(packageName: String, onAuthenticated: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
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