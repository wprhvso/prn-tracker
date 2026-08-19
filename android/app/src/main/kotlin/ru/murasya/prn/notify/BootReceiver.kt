package ru.murasya.prn.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                refreshReminders(app)
            } catch (e: RuntimeException) {
                Log.e(TAG, "failed to restore reminders after ${intent.action}", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "PrnBoot"
    }
}
