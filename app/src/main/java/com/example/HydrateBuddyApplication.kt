package com.example

import android.app.Application
import com.example.data.UserPreferencesDataStore
import com.example.database.HydrateDatabase
import com.example.notifications.NotificationHelper
import com.example.notifications.ReminderScheduler
import com.example.repository.HydrationRepository
import com.example.updater.AppUpdateCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HydrateBuddyApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: HydrateDatabase by lazy { HydrateDatabase.getInstance(this) }
    val preferencesDataStore: UserPreferencesDataStore by lazy { UserPreferencesDataStore(this) }
    val repository: HydrationRepository by lazy {
        HydrationRepository(database = database, preferencesDataStore = preferencesDataStore)
    }
    val updateCoordinator: AppUpdateCoordinator by lazy {
        AppUpdateCoordinator(appContext = this, repository = repository)
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        appScope.launch {
            repository.ensureInitialized()
            val prefs = repository.getCurrentPreferences()
            if (prefs.onboardingCompleted && prefs.remindersEnabled) {
                ReminderScheduler.syncReminders(
                    context = this@HydrateBuddyApplication,
                    prefs = prefs,
                    forceReschedule = false
                )
            }
        }
    }
}
