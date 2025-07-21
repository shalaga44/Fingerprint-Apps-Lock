package dev.shalaga.apps_lock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.ON_RESUME
import androidx.lifecycle.LifecycleEventObserver

class DeviceAdminSetupActivity : ComponentActivity() {
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var compName: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Grab the DevicePolicyManager and our AdminReceiver component
        devicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, AppsLockAdminReceiver::class.java)

        setContent {
            val lifecycleOwner = LocalLifecycleOwner.current

            // Compose state tracking whether we're active
            var isActive by remember {
                mutableStateOf(devicePolicyManager.isAdminActive(compName))
            }

            // Re-check on every resume
            DisposableEffect(lifecycleOwner) {
                val obs = LifecycleEventObserver { _, event ->
                    if (event == ON_RESUME) {
                        isActive = devicePolicyManager.isAdminActive(compName)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(obs)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(obs)
                }
            }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DeviceAdminSetupScreen(
                        isActive = isActive,
                        onEnable = { requestAdmin() },
                        onLockNow = { devicePolicyManager.lockNow() },
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    /** Launches the system dialog to activate our DeviceAdminReceiver */
    private fun requestAdmin() {
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "AppsLock needs device-admin to be able to lock your screen."
            )
        }.also(::startActivity)
    }
}

@Composable
private fun DeviceAdminSetupScreen(
    isActive: Boolean,
    onEnable: () -> Unit,
    onLockNow: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isActive) "Device Admin is ENABLED" else "Device Admin is DISABLED",
            style = MaterialTheme.typography.titleMedium
        )

        if (isActive) {
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(onClick = { onLockNow() }) {
                Text(text = "Test Lock Now")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBack) {
                Text("Back")
            }
        } else {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { onEnable() }) {
                Text(text = "Enable Device Admin")
            }
        }
    }
}