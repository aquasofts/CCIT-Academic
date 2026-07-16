package edu.ccit.webvpn

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import edu.ccit.webvpn.feature.tieba.TiebaRuntime

@HiltAndroidApp
class CcitAcademicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TiebaRuntime.get(this)
    }
}
