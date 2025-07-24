package dev.shalaga.apps_lock

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

data class PermissionItem(val key: String, val label: String)

class PermissionActivity : ComponentActivity() {
    private val TAG = "PermissionActivity"
    private lateinit var dpm: DevicePolicyManager
    private lateinit var compName: ComponentName

    internal companion object {
        const val KEY_DEVICE_ADMIN = "DEVICE_ADMIN"
        const val KEY_USAGE_ACCESS = "USAGE_ACCESS"
        const val KEY_NOTIFICATION_LISTENER = "NOTIFICATION_LISTENER"
        const val KEY_OVERLAY = "OVERLAY"
    }

    private val allPermissions = listOfNotNull(
        PermissionItem(KEY_DEVICE_ADMIN, "Enable Device Admin"),
        PermissionItem(KEY_USAGE_ACCESS, "Enable Stats Usage"),
        PermissionItem(KEY_OVERLAY, "Display over other apps"),
        PermissionItem(KEY_NOTIFICATION_LISTENER, "Notification listener"),
        PermissionItem(Manifest.permission.USE_BIOMETRIC, "Biometric authentication"),
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            PermissionItem(
                Manifest.permission.USE_FINGERPRINT,
                "Fingerprint authentication"
            ) else null,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            PermissionItem(
                Manifest.permission.QUERY_ALL_PACKAGES,
                "Query all installed apps"
            ) else null,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            PermissionItem(Manifest.permission.POST_NOTIFICATIONS, "Show notifications") else null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, AppsLockAdminReceiver::class.java)

        setContent {
            PermissionScreen(
                items = allPermissions,
                onAllGranted = {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(
    items: List<PermissionItem>,
    onAllGranted: () -> Unit
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as ComponentActivity

    val states = remember { items.associate { it.key to mutableStateOf(false) } }

    fun refreshAll() {
        if(context.permissionsAllGranted())
            onAllGranted()
        states[PermissionActivity.KEY_DEVICE_ADMIN]?.value =
            (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
                .isAdminActive(ComponentName(context, AppsLockAdminReceiver::class.java))
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

        states[PermissionActivity.KEY_USAGE_ACCESS]?.value =
            (ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED)



        states[PermissionActivity.KEY_NOTIFICATION_LISTENER]?.value =
            (context.run {
                val flat = Settings.Secure.getString(
                    contentResolver,
                    "enabled_notification_listeners"
                ) ?: ""
                val me = ComponentName(this, NotificationListener::class.java)
                flat.split(":")
                    .mapNotNull { ComponentName.unflattenFromString(it) }
                    .any { it == me }
            }
                    )

        states[PermissionActivity.KEY_OVERLAY]?.value =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                    Settings.canDrawOverlays(context)
        items.forEach { item ->
            when (item.key) {
                Manifest.permission.USE_BIOMETRIC,
                Manifest.permission.USE_FINGERPRINT,
                Manifest.permission.QUERY_ALL_PACKAGES,
                Manifest.permission.POST_NOTIFICATIONS -> {
                    states[item.key]?.value =
                        ContextCompat.checkSelfPermission(context, item.key) ==
                                PackageManager.PERMISSION_GRANTED
                }
            }
        }
    }

    LaunchedEffect(Unit) { refreshAll() }
    DisposableEffect(activity) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) refreshAll()
        }
        activity.lifecycle.addObserver(obs)
        onDispose { activity.lifecycle.removeObserver(obs) }
    }

    val launchers = items.filter {
        it.key.startsWith("android.permission")
    }.associate { item ->
        item.key to rememberLauncherForActivityResult(RequestPermission()) { granted ->
            states[item.key]?.value = granted
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Grant Permissions") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("AppLock needs these to function:", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            LazyColumn {
                items(items) { item ->
                    val granted = states[item.key]?.value == true
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.label, Modifier.weight(1f))
                        if (granted) {
                            Icon(
                                Icons.Filled.Check, contentDescription = "Granted",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Button(onClick = {
                                when (item.key) {
                                    PermissionActivity.KEY_DEVICE_ADMIN -> {
                                        context.startActivity(
                                            Intent(context, DeviceAdminSetupActivity::class.java)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }

                                    PermissionActivity.KEY_USAGE_ACCESS -> {
                                        context.startActivity(
                                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )


                                    }

                                    PermissionActivity.KEY_NOTIFICATION_LISTENER -> {
                                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

                                    }

                                    PermissionActivity.KEY_OVERLAY -> {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }

                                    else -> {
                                        launchers[item.key]?.launch(item.key)
                                    }
                                }
                            }) {
                                Text("Grant")
                            }
                        }
                    }
                    Divider()
                }
            }
        }
    }
}

fun Context.permissionsAllGranted(): Boolean {
    val TAG = "PermissionCheck"

    val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val comp = ComponentName(this, AppsLockAdminReceiver::class.java)
    if (!dpm.isAdminActive(comp)) {
        Log.w(TAG, "Device admin not active")
        return false
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
        !Settings.canDrawOverlays(this)
    ) {
        Log.w(TAG, "Overlay permission not granted")
        return false
    }

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.USE_BIOMETRIC)
        != PackageManager.PERMISSION_GRANTED
    ) {
        Log.w(TAG, "USE_BIOMETRIC permission not granted")
        return false
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.USE_FINGERPRINT)
        != PackageManager.PERMISSION_GRANTED
    ) {
        Log.w(TAG, "USE_FINGERPRINT permission not granted")
        return false
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.QUERY_ALL_PACKAGES)
        != PackageManager.PERMISSION_GRANTED
    ) {
        Log.w(TAG, "QUERY_ALL_PACKAGES permission not granted")
        return false
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) {
        Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
        return false
    }

    val appOps = getSystemService(AppOpsManager::class.java)!!
    if (appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), packageName
        ) != AppOpsManager.MODE_ALLOWED
    ) {
        Log.w(TAG, "Usage-Stats access not granted")
        return false
    }

    val flat = Settings.Secure.getString(
        contentResolver,
        "enabled_notification_listeners"
    ) ?: ""
    val me = ComponentName(this, NotificationListener::class.java)
    if (flat.split(":")
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .none { it == me }
    ) {
        Log.w(TAG, "Notification-Listener not enabled")
        return false
    }

    Log.d(TAG, "All permissions & special access granted!")
    return true
}