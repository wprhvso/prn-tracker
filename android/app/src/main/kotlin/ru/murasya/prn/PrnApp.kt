package ru.murasya.prn

import android.app.Application
import ru.murasya.prn.notify.ensureChannels

class PrnApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
    }
}
