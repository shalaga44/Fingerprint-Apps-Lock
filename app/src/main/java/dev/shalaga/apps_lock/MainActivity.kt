package dev.shalaga.apps_lock

import android.app.AppOpsManager
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.shalaga.apps_lock.ui.theme.AppsLockTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

data class AppInfo(val name: String, val packageName: String)

class AppsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application
        .getSharedPreferences("apps_lock_prefs", Context.MODE_PRIVATE)

    private val _installed = MutableStateFlow<List<AppInfo>>(emptyList())
    val installed = _installed.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    private val saved = prefs.getStringSet("locked_set", emptySet()) ?: emptySet()
    private val _locked = MutableStateFlow(saved)
    val locked = _locked.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = application.packageManager
            val apps = pm.getInstalledApplications(0)
                .mapNotNull { ai ->
                    pm.getLaunchIntentForPackage(ai.packageName)
                        ?.let { AppInfo(ai.loadLabel(pm).toString(), ai.packageName) }
                }.sortedBy { it.name }
            _installed.value = apps
            _loading.value = false
        }
    }

    fun toggle(pkg: String) {
        val cur = _locked.value.toMutableSet()
        if (!cur.remove(pkg)) cur += pkg
        _locked.value = cur
        prefs.edit().putStringSet("locked_set", cur).apply()
    }
}

class MainActivity : AppCompatActivity() {
    val TAG = "MainActivity"
    private lateinit var dpm: DevicePolicyManager
    private lateinit var comp: ComponentName

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        comp = ComponentName(this, AppsLockAdminReceiver::class.java)

        if (!dpm.isAdminActive(comp)) {
            startActivity(Intent(this, DeviceAdminSetupActivity::class.java))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)
        ) {
            Log.w(TAG, "Overlay permission missing; sending user to Settings")
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )

        }

        enableEdgeToEdge()
        requestPermissions()
        requireUsageAccess()
        requireNotifListener()

        setContent {
            AppsLockTheme {
                AppsLockApp()
            }
        }
    }

    private fun requestPermissions() {
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.USE_BIOMETRIC)
            != PackageManager.PERMISSION_GRANTED
        ) need += android.Manifest.permission.USE_BIOMETRIC
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.QUERY_ALL_PACKAGES)
            != PackageManager.PERMISSION_GRANTED
        ) need += android.Manifest.permission.QUERY_ALL_PACKAGES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) need += android.Manifest.permission.POST_NOTIFICATIONS
        if (need.isNotEmpty())
            ActivityCompat.requestPermissions(this, need.toTypedArray(), 0)
    }

    private fun requireUsageAccess() {
        val ops = getSystemService(AppOpsManager::class.java)!!
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), packageName
        )
        if (mode != AppOpsManager.MODE_ALLOWED) {
            Toast.makeText(this, "Grant Usage Access", Toast.LENGTH_LONG).show()
            startActivity(Intent(ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun requireNotifListener() {
        val flat = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: ""
        val me = ComponentName(this, NotificationListener::class.java)
        if (!flat.split(":")
                .mapNotNull { ComponentName.unflattenFromString(it) }
                .any { it == me }
        ) {
            Toast.makeText(this, "Grant Notification Access", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }
}

@Composable
fun AppsLockApp() {
    val TAG = "AppsLockApp"
    val vm: AppsViewModel = viewModel(
        factory =
            AndroidViewModelFactory.getInstance(LocalContext.current.applicationContext as Application)
    )
    val apps by vm.installed.collectAsState()
    val loading by vm.loading.collectAsState()
    val locked by vm.locked.collectAsState()
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            AppListScreen(
                apps, loading, locked,
                onToggle = { vm.toggle(it) },
                onLaunch = { pkg -> nav.navigate("unlock/$pkg") }
            )
        }
        composable(
            "unlock/{pkg}",
            arguments = listOf(navArgument("pkg") { type = NavType.StringType })
        ) { bc ->
            bc.arguments?.getString("pkg")?.let { pkg ->
                UnlockScreen(
                    packageName = pkg,
                    onAuthenticated = {
                        nav.popBackStack()
                        Log.d(TAG, "Authentication succeeded → finish()")
                    },

                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    apps: List<AppInfo>,
    loading: Boolean,
    locked: Set<String>,
    onToggle: (String) -> Unit,
    onLaunch: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.name.contains(query, true) }
    }
    Scaffold(topBar = { TopAppBar({ Text("AppLocker") }) }) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
            when {
                loading -> Box(
                    Modifier.fillMaxSize(),
                    Alignment.Center
                ) { CircularProgressIndicator() }

                filtered.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    Alignment.Center
                ) { Text("No apps") }

                else -> LazyColumn {
                    items(filtered) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val pm = LocalContext.current.packageManager
                            val icon = pm.getApplicationIcon(app.packageName)
                            val bmp = (icon as? BitmapDrawable)?.bitmap ?: run {
                                Bitmap.createBitmap(
                                    icon.intrinsicWidth.coerceAtLeast(48),
                                    icon.intrinsicHeight.coerceAtLeast(48),
                                    Bitmap.Config.ARGB_8888
                                ).also { b ->
                                    Canvas(b).let { c ->
                                        icon.setBounds(
                                            0,
                                            0,
                                            c.width,
                                            c.height
                                        );icon.draw(c)
                                    }
                                }
                            }
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(app.name, Modifier.weight(1f))
                            IconButton(onClick = { onToggle(app.packageName) }) {
                                Icon(
                                    if (app.packageName in locked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null
                                )
                            }
                            if (app.packageName in locked) {
                                Text(
                                    "Open",
                                    Modifier
                                        .clickable { onLaunch(app.packageName) }
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun PreviewList() = AppListScreen(emptyList(), false, emptySet(), {}, {})