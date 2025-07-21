package dev.shalaga.apps_lock
import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MyAppService : Service() {
    companion object {
        private const val TAG = "MyAppService"
        private val POLL_MS = (5.seconds).inWholeMilliseconds
        private const val CH_POLL = "apps_lock_poll"
        private const val CH_UNLOCK = "apps_lock_unlock"
        private const val ID_POLL = 1
        private const val ID_UNLOCK = 2
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastPoll = System.currentTimeMillis()
    private var lastFg: String? = null
    private val prefs by lazy {
        getSharedPreferences("apps_lock_prefs", Context.MODE_PRIVATE)
    }

    private val task = object : Runnable {
        override fun run() {
            Log.d(TAG, "Polling from $lastPoll")
            ensureUsageAccess()
            checkForeground()
            lastPoll = System.currentTimeMillis()
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CH_POLL, "Polling", NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_UNLOCK, "Unlock", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                }
            )
        }
        val notif = NotificationCompat.Builder(this, CH_POLL)
            .setContentTitle("AppsLock running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        startForeground(ID_POLL, notif)

        handler.post(task)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(task)
        handler.post(task)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(task)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun ensureUsageAccess() {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        if (ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), packageName
            ) != AppOpsManager.MODE_ALLOWED
        ) {
            startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun checkForeground() {
        val now = System.currentTimeMillis()
        val usm = getSystemService(UsageStatsManager::class.java)
        val evts = usm.queryEvents(lastPoll, now)
        val ev = UsageEvents.Event()
        var candidate: String? = null
        while (evts.hasNextEvent()) {
            evts.getNextEvent(ev)
            if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                candidate = ev.packageName
            }
        }
        Log.d(TAG, "Candidate: $candidate")
        val locked = prefs.getStringSet("locked_set", emptySet()) ?: emptySet()
        if (candidate != null &&
            candidate != packageName &&
            candidate in locked &&
            candidate != lastFg
        ) {
            lastFg = candidate
            showUnlock(candidate)
        }
    }

    private fun showUnlock(pkg: String) {
        Log.d(TAG, "Unlock: $pkg")

        val unlockIntent = Intent(this, UnlockActivity::class.java).apply {
            putExtra("packageName", pkg)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
            return
        }

        startActivity(unlockIntent)

        val pi = PendingIntent.getActivity(
            this, 0, unlockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CH_UNLOCK)
            .setContentTitle("Unlock $pkg")
            .setContentText("Authenticate")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pi, true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(ID_UNLOCK, notif)
    }
}