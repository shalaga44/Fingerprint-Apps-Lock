package dev.shalaga.apps_lock

import android.content.Context
import android.util.Log
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import androidx.core.content.edit

object LockManager {
    private const val TAG = "LockManager"
    private const val PREFS_NAME = "apps_lock_prefs"
    private const val KEY_LOCKED_SET = "locked_set"
    private const val KEY_UNLOCKED_SET = "unlocked_set"
    private const val PREFIX_UNLOCKED_TIME = "unlocked_"
    private val RECENT_THRESHOLD: Duration = 30.seconds

    private var lastUnlockedForeground: String? = null

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAppLocked(ctx: Context, pkg: String): Boolean {
        val locked = prefs(ctx).getStringSet(KEY_LOCKED_SET, emptySet()) ?: emptySet()
        Log.d(TAG, "isAppLocked? $pkg -> ${locked.contains(pkg)}")
        return locked.contains(pkg)
    }

    fun setList(ctx: Context, pkgs: MutableSet<String>) {
        prefs(ctx).edit { putStringSet(KEY_LOCKED_SET, pkgs) }
        Log.d(TAG, "setList ${pkgs}")
        return
    }

    fun getList(ctx: Context): Set<String> {
        val set = prefs(ctx).getStringSet(KEY_LOCKED_SET, emptySet()) ?: emptySet<String>()
        Log.d(TAG, "getList ${set}")
        return set
    }

    fun recordUnlock(ctx: Context, pkg: String) {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        val unlocked = p.getStringSet(KEY_UNLOCKED_SET, emptySet())!!.toMutableSet()
        unlocked += pkg
        p.edit()
            .putLong(PREFIX_UNLOCKED_TIME + pkg, now)
            .putStringSet(KEY_UNLOCKED_SET, unlocked)
            .apply()
        Log.d(TAG, "recordUnlock: $pkg at $now")
    }

    private fun getLastUnlockTime(ctx: Context, pkg: String): Long {
        val t = prefs(ctx).getLong(PREFIX_UNLOCKED_TIME + pkg, 0L)
        Log.d(TAG, "getLastUnlockTime: $pkg -> $t")
        return t
    }


    fun shouldLock(ctx: Context, pkg: String): Boolean {
        Log.d(TAG, "shouldLock called for $pkg")
        if (!isAppLocked(ctx, pkg)) {
            Log.d(TAG, "shouldLock: $pkg is not locked, skip")
            return false
        }
        if (pkg == lastUnlockedForeground) {
            Log.d(TAG, "shouldLock: $pkg == lastUnlockedForeground, skip")
            return false
        }
        val now = System.currentTimeMillis()
        val last = getLastUnlockTime(ctx, pkg)
        if (now - last < RECENT_THRESHOLD.inWholeMilliseconds) {
            Log.d(
                TAG,
                "shouldLock: $pkg was unlocked ${now - last}ms ago (< $RECENT_THRESHOLD), skip"
            )
            lastUnlockedForeground = pkg
            return false
        }
        Log.d(TAG, "shouldLock: $pkg should be locked now")
        return true
    }
}
