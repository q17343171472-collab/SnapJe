package com.rapii.snapje.util

import android.util.Log
import com.rapii.snapje.BuildConfig
import timber.log.Timber

/**
 * Logging utility providing consistent logging across the app.
 * Uses Timber for tree-based logging with automatic debug/release handling.
 *
 * Usage:
 * ```
 * import com.rapii.snapje.util.L
 *
 * L.d("Tag", "Debug message")
 * L.e("Tag", "Error message", exception)
 * L.i("Tag", "Info message")
 * ```
 */
object L {

    /**
     * Initialize logging. Call from Application.onCreate().
     * In debug builds: logs everything to Logcat.
     * In release builds: logs errors only (or nothing if disabled).
     */
    fun init() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // In release, only log errors
            Timber.plant(ReleaseTree())
        }
    }

    // Delegated logging methods
    fun d(tag: String, message: String, throwable: Throwable? = null) =
        Timber.tag(tag).d(throwable, message)

    fun i(tag: String, message: String, throwable: Throwable? = null) =
        Timber.tag(tag).i(throwable, message)

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        Timber.tag(tag).w(throwable, message)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        Timber.tag(tag).e(throwable, message)

    fun wtf(tag: String, message: String, throwable: Throwable? = null) =
        Timber.tag(tag).wtf(throwable, message)

    // Tagless variants (uses class name as tag)
    fun d(message: String, throwable: Throwable? = null) =
        Timber.d(throwable, message)

    fun i(message: String, throwable: Throwable? = null) =
        Timber.i(throwable, message)

    fun w(message: String, throwable: Throwable? = null) =
        Timber.w(throwable, message)

    fun e(message: String, throwable: Throwable? = null) =
        Timber.e(throwable, message)

    /**
     * Release logging tree - only logs errors and above.
     * Prevents sensitive information leakage in production.
     */
    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // Only log errors in release builds
            if (priority >= Log.ERROR) {
                Log.e(tag, message, t)
            }
        }
    }
}
