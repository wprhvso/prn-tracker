package ru.murasya.prn.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

const val ACTION_TICK = "ru.murasya.prn.TICK"
const val ACTION_TAKE = "ru.murasya.prn.TAKE"
const val EXTRA_MED_ID = "medId"

private const val TAG = "PrnAlarms"
private const val REQUEST_TICK = 1

fun scheduleNextTick(context: Context, at: Long?) {
    val manager = context.getSystemService(AlarmManager::class.java) ?: return
    val pending = tickIntent(context)
    if (at == null) {
        manager.cancel(pending)
        return
    }
    try {
        if (manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    } catch (e: SecurityException) {

        Log.w(TAG, "exact alarm refused, falling back to inexact", e)
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }
}

fun canScheduleExact(context: Context): Boolean =
    context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false

fun requestExactAlarms(context: Context): Boolean {
    val intent =
        Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "no exact alarm settings screen", e)
        false
    }
}

private fun tickIntent(context: Context): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        REQUEST_TICK,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
