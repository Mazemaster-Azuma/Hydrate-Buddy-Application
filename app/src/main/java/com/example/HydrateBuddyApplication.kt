package com.example

import android.app.Application
import com.example.data.UserPreferencesDataStore
import com.example.database.HydrateDatabase
import com.example.notifications.NotificationHelper
import com.example.repository.HydrationRepository
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

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        appScope.launch {
            repository.ensureInitialized()
        }
    }
}
