package com.getguruhat.hatchat

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * New-message alerts without push: Instagram and Facebook put the unread count in the
 * page title ("(2) Instagram"). While HatChat runs in the background, a rising count
 * posts a notification. It stops when Android closes the app.
 *
 * Notifications never include names or message text, so nothing private shows on the
 * lock screen.
 */
object MessageAlerts {

    enum class Service(val label: String, val tab: Int, val notificationId: Int) {
        Instagram("Instagram", 0, 1001),
        Messenger("Messenger", 1, 1002),
    }

    const val EXTRA_TAB = "com.getguruhat.hatchat.TAB"
    private const val CHANNEL_ID = "messages"
    private const val PREFS = "settings"
    const val PREF_ENABLED = "message_alerts"
    private const val TAG = "HatChat"

    /** Set by MainActivity while it is on screen; alerts are only posted when it isn't. */
    @Volatile var appVisible = false

    private val lastCount = mutableMapOf<Service, Int>()

    fun isEnabled(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, true)

    /** Called with the unread count parsed from a page title. */
    fun onUnreadCount(context: Context, service: Service, count: Int) {
        val previous = lastCount[service] ?: 0
        lastCount[service] = count
        Log.d(TAG, "unread ${service.label}: $count (was $previous, visible=$appVisible)")
        if (count == 0) {
            NotificationManagerCompat.from(context).cancel(service.notificationId)
            return
        }
        if (count > previous && !appVisible && isEnabled(context)) notify(context, service, count)
    }

    /** Back on screen: what's visible now is "seen", so clear alerts. */
    fun onAppVisible(context: Context) {
        appVisible = true
        NotificationManagerCompat.from(context).cancelAll()
    }

    fun canNotify(context: Context) =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun notify(context: Context, service: Service, count: Int) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_TAB, service.tab)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context, service.notificationId, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (count == 1) "New message" else "$count unread chats"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(service.label)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(service.notificationId, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "notification permission missing")
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "New messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when Instagram or Messenger has new messages while HatChat is in the background."
            },
        )
    }
}
