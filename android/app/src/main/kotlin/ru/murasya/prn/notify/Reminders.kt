package ru.murasya.prn.notify

import android.content.Context
import java.time.ZoneId
import ru.murasya.prn.data.Intake
import ru.murasya.prn.data.PrnDatabase
import ru.murasya.prn.domain.alerts
import ru.murasya.prn.domain.medStates
import ru.murasya.prn.domain.nextWakeAt

/**
 * Recomputes the whole reminder state from the database and applies it: the shade is brought in
 * line with the current alerts and the next alarm is armed. Every entry point — the alarm, boot,
 * and each edit made in the app — funnels through here, so there is only one source of truth.
 */
suspend fun refreshReminders(context: Context) {
    val app = context.applicationContext
    val dao = PrnDatabase.get(app).dao()
    val states =
        medStates(
            meds = dao.meds(),
            intakes = dao.intakes(),
            now = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
        )
    ensureChannels(app)
    syncNotifications(app, alerts(states))
    scheduleNextTick(app, nextWakeAt(states))
}

/** Logs a dose straight from a notification action, spending one from stock. */
suspend fun takeDose(context: Context, medId: Long) {
    val dao = PrnDatabase.get(context.applicationContext).dao()
    val med = dao.med(medId) ?: return
    dao.insertIntake(
        Intake(medId = med.id, takenAt = System.currentTimeMillis(), doseMg = med.doseMg),
    )
    dao.spendDose(med.id)
}
