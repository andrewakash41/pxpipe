package com.vachan.app.core.security

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import android.view.WindowManager
import java.io.File

/** Blocks screenshots, screen recording and Recents thumbnails for the whole app. */
object SecureWindow {
    fun apply(activity: Activity) {
        activity.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(false)
        }
        // Tapjacking defence: ignore touches when another app draws over us.
        activity.window.decorView.filterTouchesWhenObscured = true
    }
}

/**
 * Coarse device-integrity signals. These are *risk signals* sent with sign-in (and combined with
 * Play Integrity via App Check on the server); they never lock a user out on their own, because
 * rooted phones are common among enthusiasts and false positives exist.
 */
object DeviceIntegrity {

    enum class Signal(val description: String) {
        ROOT_BINARY("Root access tools found"),
        TEST_KEYS("Custom or test system build"),
        DEBUGGER("A debugger is attached"),
        EMULATOR("Running on an emulator"),
        DEBUGGABLE_BUILD("Debuggable app build"),
    }

    fun signals(context: Context): Set<Signal> = buildSet {
        val suPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/app/Superuser.apk", "/data/adb/magisk")
        if (suPaths.any { File(it).exists() }) add(Signal.ROOT_BINARY)
        if (Build.TAGS?.contains("test-keys") == true) add(Signal.TEST_KEYS)
        if (Debug.isDebuggerConnected()) add(Signal.DEBUGGER)
        if (looksLikeEmulator()) add(Signal.EMULATOR)
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) add(Signal.DEBUGGABLE_BUILD)
    }

    private fun looksLikeEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.PRODUCT.contains("sdk_gphone")
}
