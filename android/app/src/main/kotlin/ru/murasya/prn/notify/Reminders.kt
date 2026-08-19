package ru.murasya.prn.notify

import android.content.Context
import java.time.ZoneId
import ru.murasya.prn.data.Intake
import ru.murasya.prn.data.PrnDatabase
import ru.murasya.prn.domain.medStates
import ru.murasya.prn.domain.nextWakeAt
import ru.murasya.prn.domain.notifiableAlerts

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
    syncNotifications(app, notifiableAlerts(states))
    scheduleNextTick(app, nextWakeAt(states))
}

suspend fun takeDose(context: Context, medId: Long) {
    val dao = PrnDatabase.get(context.applicationContext).dao()
    val med = dao.med(medId) ?: return
    dao.insertIntake(
        Intake(medId = med.id, takenAt = System.currentTimeMillis(), doseMg = med.doseMg),
    )
    dao.spendStock(med.id, med.doseMg)
}
