package com.vachan.app.core.safety

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vachan.app.MainActivity
import com.vachan.app.R

/**
 * "Meet safely" check-in. The deadline is persisted in settings (so the in-app countdown survives
 * navigation, process death and restarts) and an alarm raises a notification when it passes, even
 * if the app is closed. The notification never contains the other person's name or the place —
 * only a neutral prompt — because lock screens are often visible to others.
 *
 * Uses an inexact, Doze-tolerant alarm (no exact-alarm permission needed); it may fire a few
 * minutes late on an idle phone, which the copy accounts for.
 */
object CheckInScheduler {
    private const val REQUEST_CODE = 4107
    const val CHANNEL_ID = "safety_checkin"
    const val NOTIFICATION_ID = 4107

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, CheckInReceiver::class.java).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(context: Context, atMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.checkin_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.checkin_channel_description)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        nm.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

class CheckInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!CheckInScheduler.canNotify(context)) return
        CheckInScheduler.ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CheckInScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Time to check in")
            .setContentText("Tap to tell us you're safe, or call your trusted contact.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(CheckInScheduler.NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; the in-app banner still shows.
        }
    }
}

/** Phone number helpers shared by sign-in, masking and validation. */
object PhoneNumbers {
    data class Country(val name: String, val dialCode: String, val flag: String, val minDigits: Int, val maxDigits: Int, val example: String)

    /** India first, then the countries most Indian families abroad live in. */
    val countries = listOf(
        Country("India", "+91", "🇮🇳", 10, 10, "98765 43210"),
        Country("United Arab Emirates", "+971", "🇦🇪", 9, 9, "50 123 4567"),
        Country("United States", "+1", "🇺🇸", 10, 10, "201 555 0123"),
        Country("United Kingdom", "+44", "🇬🇧", 10, 10, "7400 123456"),
        Country("Canada", "+1", "🇨🇦", 10, 10, "416 555 0123"),
        Country("Australia", "+61", "🇦🇺", 9, 9, "412 345 678"),
        Country("Singapore", "+65", "🇸🇬", 8, 8, "8123 4567"),
        Country("Saudi Arabia", "+966", "🇸🇦", 9, 9, "51 234 5678"),
        Country("Qatar", "+974", "🇶🇦", 8, 8, "3312 3456"),
        Country("Kuwait", "+965", "🇰🇼", 8, 8, "500 12345"),
        Country("Oman", "+968", "🇴🇲", 8, 8, "9212 3456"),
        Country("New Zealand", "+64", "🇳🇿", 8, 10, "21 123 4567"),
        Country("Germany", "+49", "🇩🇪", 10, 11, "1512 3456789"),
    )

    private val indianMobile = Regex("[6-9]\\d{9}")

    /** Validates the national part for [country]; returns an error message or null. */
    fun validate(country: Country, national: String): String? = when {
        national.length !in country.minDigits..country.maxDigits -> "Enter a ${country.minDigits}-digit mobile number"
        country.dialCode == "+91" && !indianMobile.matches(national) -> "Enter a valid Indian mobile number"
        else -> null
    }

    val e164 = Regex("\\+[1-9]\\d{7,14}")

    /** "+91 ••••• ••210": enough to recognise your own number, useless to anyone else. */
    fun mask(e164Number: String?): String {
        if (e164Number.isNullOrBlank()) return ""
        val cc = countries.map { it.dialCode }.sortedByDescending { it.length }.firstOrNull { e164Number.startsWith(it) } ?: e164Number.take(3)
        return "$cc ••••• ••" + e164Number.takeLast(3)
    }
}
