package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.repository.HydrationRepository
import com.example.updater.AppUpdateCoordinator

class HydrateViewModelFactory(
    private val repository: HydrationRepository,
    private val appContext: Context,
    private val updateCoordinator: AppUpdateCoordinator? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val coordinator = updateCoordinator ?: AppUpdateCoordinator(appContext, repository)
        return when {
            modelClass.isAssignableFrom(AppViewModel::class.java) ->
                AppViewModel(repository, appContext, coordinator) as T
            modelClass.isAssignableFrom(OnboardingViewModel::class.java) ->
                OnboardingViewModel(repository, appContext) as T
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(repository) as T
            modelClass.isAssignableFrom(HistoryViewModel::class.java) ->
                HistoryViewModel(repository) as T
            modelClass.isAssignableFrom(AchievementsViewModel::class.java) ->
                AchievementsViewModel(repository) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(repository, appContext, coordinator) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
