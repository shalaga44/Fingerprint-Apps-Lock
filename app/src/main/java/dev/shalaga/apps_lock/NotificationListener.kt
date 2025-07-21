package dev.shalaga.apps_lock

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationListener : NotificationListenerService() {
    private val TAG = "NotificationListener"
    private val prefs by lazy {
        getSharedPreferences("apps_lock_prefs", MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        Log.d(TAG, "Notification posted for package: $pkg; key=${sbn.key}")

        val locked = prefs.getStringSet("locked_set", emptySet()) ?: emptySet()
        if (pkg in locked) {
            Log.d(TAG, "→ $pkg is locked. Cancelling notification and launching unlock screen.")
            cancelNotification(sbn.key)

            Intent(this, UnlockActivity::class.java).apply {
                putExtra("packageName", pkg)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }.also { intent ->
                Log.d(TAG, "Starting UnlockActivity for $pkg")
                startActivity(intent)
            }
        } else {
            Log.d(TAG, "$pkg is not in locked set, ignoring")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d(TAG, "Notification removed for package: ${sbn.packageName}; key=${sbn.key}")
    }
}