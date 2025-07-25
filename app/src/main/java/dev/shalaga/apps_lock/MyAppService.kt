package dev.shalaga.apps_lock

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.*
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
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
    private var lastUnlockedForeground: String? = null
    private var lastPoll = System.currentTimeMillis()
    private val prefs by lazy {
        getSharedPreferences("apps_lock_prefs", Context.MODE_PRIVATE)
    }

    private val poller = object : Runnable {
        override fun run() {
            Log.d(TAG, ">>> Poller: scheduling checkForeground()")
            try {
                checkForeground()
            } catch (t: Throwable) {
                Log.e(TAG, "❌ Error during polling", t)
            } finally {
                lastPoll = System.currentTimeMillis()
                handler.postDelayed(this, POLL_MS)
                Log.d(TAG, "⏱ Next poll in ${POLL_MS / 1000 / 60}m")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()


        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CH_POLL,
                    "AppsLock Polling",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Monitoring foreground apps" }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CH_UNLOCK,
                    "AppsLock Unlock",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Popup to unlock a locked app"
                    setSound(null, null)
                }
            )
        }


        val pollNotif = NotificationCompat.Builder(this, CH_POLL)
            .setContentTitle("AppsLock is running")
            .setContentText("Monitoring your apps…")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        startForeground(ID_POLL, pollNotif)


        handler.post(poller)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(poller)
        handler.post(poller)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(poller)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null


    private fun checkForeground() {
        val now = System.currentTimeMillis()
        val usm = getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(lastPoll, now)
        val ev = UsageEvents.Event()
        var candidate: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                candidate = ev.packageName
            }
        }

        Log.d(TAG, "Candidate: $candidate")
        if (candidate == null || candidate == packageName) return

        if (LockManager.shouldLock(this, candidate)) {
            showUnlock(candidate)
        } else {
            Log.d(TAG, "No lock needed for $candidate")
        }
    }

    private val appsLockPrefs by lazy {
        getSharedPreferences("apps_lock_prefs", MODE_PRIVATE)
    }

    private fun getUnlockedPackages(): Set<String> =
        appsLockPrefs.getStringSet("unlocked_set", emptySet()) ?: emptySet()

    private fun getLastUnlockTime(pkg: String): Long =
        appsLockPrefs.getLong("unlocked_$pkg", 0L)

    private fun showUnlock(pkg: String) {
        Log.d(TAG, "Unlock: $pkg")

        val unlockIntent = Intent(this, UnlockActivity::class.java).apply {
            putExtra("packageName", pkg)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        startActivity(unlockIntent)

    }
}