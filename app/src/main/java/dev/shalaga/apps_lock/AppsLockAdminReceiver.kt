package dev.shalaga.apps_lock

import android.app.admin.DeviceAdminReceiver

/**
 * Minimal DeviceAdminReceiver — we're only using `forceLock()` so no
 * callbacks are required beyond the base implementation.
 */
class AppsLockAdminReceiver : DeviceAdminReceiver()