package dev.shalaga.apps_lock

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import dev.shalaga.apps_lock.ui.theme.AppsLockTheme
import java.util.concurrent.Executor

class UnlockActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "UnlockActivity"
    }

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "🔓 onCreate invoked with intent=$intent")
        super.onCreate(savedInstanceState)

        // 1) Wake screen & show over lockscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            Log.d(TAG, "Configuring showWhenLocked / turnScreenOn")
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        } else {
            Log.d(TAG, "Applying window flags for older Android")
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // 2) Extract which package we’re unlocking
        val packageToUnlock = intent.getStringExtra("packageName")
        if (packageToUnlock == null) {
            Log.w(TAG, "No packageName extra found—finishing")
            finish()
            return
        }
        Log.d(TAG, "Unlock requested for package: $packageToUnlock")

        // 3) Compose UI
        setContent {
            AppsLockTheme {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UnlockScreen(
                        packageName = packageToUnlock,
                        onAuthenticated = {
                            Log.d(TAG, "Authentication succeeded, finishing activity")
                            finish()
                        }
                    )
                }
            }
        }
    }
}
@Composable
fun UnlockScreen(packageName: String, onAuthenticated: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    val executor = ContextCompat.getMainExecutor(activity)
    var err by remember { mutableStateOf<String?>(null) }

    val callback = object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(res: androidx.biometric.BiometricPrompt.AuthenticationResult) {
            onAuthenticated()
        }

        override fun onAuthenticationError(code: Int, str: CharSequence) {
            err = str.toString()
        }
    }

    val prompt = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock $packageName")
        .setNegativeButtonText("Cancel")
        .build()

    androidx.biometric.BiometricPrompt(activity, executor, callback)
        .authenticate(prompt)

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

