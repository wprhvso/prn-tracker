package ru.murasya.prn.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val take = intent.action == ACTION_TAKE
        val medId = intent.getLongExtra(EXTRA_MED_ID, 0L)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (take) takeDose(app, medId)
                refreshReminders(app)
            } catch (e: RuntimeException) {
                Log.e(TAG, "reminder tick failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "PrnReminder"
    }
}
