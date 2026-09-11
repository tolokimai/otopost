package com.example

import android.app.Application
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.preferences.SettingsManager
import com.example.data.repository.AutoPostRepository

class AutoPostApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var settingsManager: SettingsManager
        private set

    lateinit var repository: AutoPostRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            database = AppDatabase.getInstance(this)
            settingsManager = SettingsManager(this)
            repository = AutoPostRepository(this, database, settingsManager)

            // Start background scheduling loop
            repository.initPeriodicPostingWorker()
        } catch (e: Exception) {
            Log.e("AutoPostApplication", "Initialization failed", e)
        }
    }

    companion object {
        lateinit var instance: AutoPostApplication
            private set
    }
}
