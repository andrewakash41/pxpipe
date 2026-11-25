package com.vachan.app.ui.common

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** A message safe to show a member: never a stack trace or SDK internals. */
fun Throwable.userMessage(fallback: String = "Something went wrong. Please try again."): String =
    message?.takeIf { it.isNotBlank() && it.length < 200 && !it.contains("Exception") } ?: fallback

/**
 * Launches work that must never crash the app: any failure is reported through [onError] as a
 * member-friendly message. Cancellation still propagates normally.
 */
fun ViewModel.launchSafely(onError: (String) -> Unit, block: suspend CoroutineScope.() -> Unit): Job =
    viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e.userMessage())
        }
    }

/**
 * Copies text without it appearing in Android 13+'s clipboard preview or keyboard suggestions
 * (used for phone numbers and invite codes).
 */
fun copySensitive(context: Context, label: String, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    cm.setPrimaryClip(clip)
}

/** "6:09 PM" today, "Yesterday", "Mon" this week, otherwise "24 Nov". */
fun relativeTime(t: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - t
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "now"
        diff < TimeUnit.DAYS.toMillis(1) -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(t))
        diff < TimeUnit.DAYS.toMillis(2) -> "Yesterday"
        diff < TimeUnit.DAYS.toMillis(7) -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(t))
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(t))
    }
}

/** Morning / afternoon / evening greeting for the Today screen. */
fun greeting(hour: Int): String = when (hour) {
    in 4..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
