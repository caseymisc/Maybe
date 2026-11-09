package com.caseymisc.fitdash

import android.app.Application
import com.caseymisc.fitdash.data.health.HealthConnectRepository
import com.caseymisc.fitdash.data.hevy.HevyClient
import com.caseymisc.fitdash.data.settings.SettingsRepository

class AppContainer(app: Application) {
    val settings = SettingsRepository(app)
    val hevy = HevyClient()
    val health = HealthConnectRepository(app)
}

class FitDashApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
