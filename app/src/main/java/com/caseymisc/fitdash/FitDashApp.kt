package com.caseymisc.fitdash

import android.app.Application
import com.caseymisc.fitdash.data.garmin.EncryptedTokenStorage
import com.caseymisc.fitdash.data.garmin.GarminClient
import com.caseymisc.fitdash.data.garmin.GarminRepository
import com.caseymisc.fitdash.data.health.HealthConnectRepository
import com.caseymisc.fitdash.data.hevy.HevyClient
import com.caseymisc.fitdash.data.settings.SettingsRepository

class AppContainer(app: Application) {
    val settings = SettingsRepository(app)
    val hevy = HevyClient()
    val health = HealthConnectRepository(app)
    val garminClient = GarminClient(EncryptedTokenStorage(app))
    val garmin = GarminRepository(garminClient)
}

class FitDashApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
