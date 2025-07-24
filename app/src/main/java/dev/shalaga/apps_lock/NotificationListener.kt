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

        if (LockManager.shouldLock(this, pkg)) {
            cancelNotification(sbn.key)
        }else{
            Log.d(TAG, "$pkg is not in locked set, ignoring")

        }

    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d(TAG, "Notification removed for package: ${sbn.packageName}; key=${sbn.key}")
    }
}