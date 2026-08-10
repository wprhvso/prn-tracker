package ru.murasya.prn.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import ru.murasya.prn.R
import ru.murasya.prn.domain.Alert
import ru.murasya.prn.domain.AlertKind
import ru.murasya.prn.domain.formatNumber
import ru.murasya.prn.text.shortDuration
import ru.murasya.prn.ui.MainActivity

/**
 * Channel ids carry a version because a channel's importance, sound and vibration are frozen the
 * moment it is created — changing them later means shipping a new id.
 */
private const val CHANNEL_DOSE = "dose_v1"
private const val CHANNEL_STOCK = "stock_v1"
private const val REQUEST_OPEN = 100
private val VIBRATION = longArrayOf(0, 400, 200, 400, 200, 600)

fun ensureChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    manager.createNotificationChannels(listOf(doseChannel(context), stockChannel(context)))
}

/**
 * Makes the shade match [alerts] exactly: everything that still applies is re-posted so it alerts
 * again, everything that stopped applying is pulled. Re-posting is what makes the reminder nag.
 */
fun syncNotifications(context: Context, alerts: List<Alert>) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val pushed = alerts.filter { it.kind.notifies }.associateBy(::notificationId)
    manager.activeNotifications
        .map { it.id }
        .filterNot { pushed.containsKey(it) }
        .forEach(manager::cancel)
    pushed.forEach { (id, alert) -> manager.notify(id, build(context, alert)) }
}

/** Notification ids are derived from the medication id, so a med never fights with itself. */
private fun notificationId(alert: Alert): Int =
    (alert.state.med.id * AlertKind.entries.size + alert.kind.ordinal).toInt()

private fun doseChannel(context: Context): NotificationChannel {
    val alarmLike =
        AudioAttributes
            .Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()
    return NotificationChannel(
        CHANNEL_DOSE,
        context.getString(R.string.channel_dose),
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = context.getString(R.string.channel_dose_desc)
        setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarmLike)
        enableVibration(true)
        vibrationPattern = VIBRATION
        enableLights(true)
        setShowBadge(true)
        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
    }
}

private fun stockChannel(context: Context): NotificationChannel =
    NotificationChannel(
        CHANNEL_STOCK,
        context.getString(R.string.channel_stock),
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = context.getString(R.string.channel_stock_desc)
        setShowBadge(true)
        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
    }

private fun build(context: Context, alert: Alert): Notification {
    val builder =
        Notification
            .Builder(context, channelOf(alert.kind))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(alert.state.med.colorArgb)
            .setContentTitle(title(context, alert))
            .setContentText(text(context, alert))
            .setContentIntent(openApp(context))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(false)
            .setAutoCancel(false)
    return if (alert.kind == AlertKind.DUE) dueNotification(context, alert, builder) else stock(builder)
}

/**
 * A due dose is treated as an alarm: an alarm-stream sound that survives a silenced ringer, a
 * heads-up that shows even on a locked screen, and a body that keeps counting how late it is. The
 * full-screen intent is deliberately used *without* declaring `USE_FULL_SCREEN_INTENT` — that
 * permission is reserved for calling and alarm-clock apps, and without it the heads-up still
 * appears over a locked or dark screen for a minute.
 */
private fun dueNotification(context: Context, alert: Alert, builder: Notification.Builder): Notification {
    val dueAt = alert.state.dueAt
    builder
        .setCategory(Notification.CATEGORY_ALARM)
        .setOngoing(true)
        .setFullScreenIntent(openApp(context), true)
        .addAction(takeAction(context, alert))
    if (dueAt != null) {
        builder
            .setWhen(dueAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
    }
    return builder.build()
}

private fun stock(builder: Notification.Builder): Notification =
    builder.setCategory(Notification.CATEGORY_REMINDER).build()

private fun channelOf(kind: AlertKind): String = if (kind == AlertKind.DUE) CHANNEL_DOSE else CHANNEL_STOCK

private fun title(context: Context, alert: Alert): String {
    val name = alert.state.med.name
    return when (alert.kind) {
        AlertKind.DUE -> context.getString(R.string.notify_due_title, name)
        AlertKind.LOW_STOCK -> context.getString(R.string.notify_low_title, name)
        AlertKind.OUT_OF_STOCK -> context.getString(R.string.notify_empty_title, name)
        AlertKind.TOLERANCE -> name
    }
}

private fun text(context: Context, alert: Alert): String {
    val med = alert.state.med
    return when (alert.kind) {
        AlertKind.DUE -> dueText(context, alert)
        AlertKind.LOW_STOCK -> context.resources.getQuantityString(R.plurals.doses_left, med.dosesLeft, med.dosesLeft)
        AlertKind.OUT_OF_STOCK -> context.getString(R.string.notify_empty_text)
        AlertKind.TOLERANCE -> ""
    }
}

private fun dueText(context: Context, alert: Alert): String {
    val dose = formatNumber(alert.state.med.doseMg)
    val last = alert.state.lastTakenAt ?: return context.getString(R.string.notify_due_text_plain, dose)
    val ago = shortDuration(context, System.currentTimeMillis() - last)
    return context.getString(R.string.notify_due_text, dose, ago)
}

private fun takeAction(context: Context, alert: Alert): Notification.Action {
    val medId = alert.state.med.id
    val intent =
        Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_TAKE)
            .putExtra(EXTRA_MED_ID, medId)
    val pending =
        PendingIntent.getBroadcast(
            context,
            medId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    return Notification.Action
        .Builder(
            Icon.createWithResource(context, R.drawable.ic_notification),
            context.getString(R.string.action_take),
            pending,
        ).build()
}

private fun openApp(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
