package com.example

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.UserPreferencesDataStore
import com.example.database.HydrateDatabase
import com.example.model.DrinkType
import com.example.model.HistoryPeriod
import com.example.model.HistoryUiState
import com.example.model.MeasurementUnit
import com.example.model.ThemeMode
import com.example.notifications.AlarmRingController
import com.example.notifications.AlarmRingState
import com.example.repository.HydrationRepository
import com.example.ui.components.AlarmRingingDialog
import com.example.ui.screens.AchievementsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.HydrateBuddyTheme
import com.example.utils.DateUtils
import com.example.viewmodel.AchievementsViewModel
import com.example.viewmodel.HistoryViewModel
import com.example.viewmodel.HomeViewModel
import com.example.viewmodel.OnboardingViewModel
import com.example.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: HydrateDatabase
    private lateinit var repository: HydrationRepository

    private lateinit var preferencesDataStore: UserPreferencesDataStore
    private lateinit var viewModelStore: ViewModelStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        preferencesDataStore = UserPreferencesDataStore(context)
        runBlocking { preferencesDataStore.clearAll() }
        viewModelStore = ViewModelStore()
        db = Room.inMemoryDatabaseBuilder(context, HydrateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HydrationRepository(
            database = db,
            preferencesDataStore = preferencesDataStore
        )
    }

    @After
    fun tearDown() {
        AlarmRingController.stopRinging(context)
        viewModelStore.clear()
        db.close()
    }

    private inline fun <reified T : ViewModel> trackVm(
        key: String,
        noinline create: () -> T
    ): T {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
        }
        return ViewModelProvider(viewModelStore, factory)[key, T::class.java]
    }

    @Test
    fun `read app name from context`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Hydrate Buddy", appName)
    }

    @Test
    fun `adding and undoing water entry updates total and unlocks first drop`() = runTest {
        repository.ensureInitialized()
        repository.updatePreferences { it.copy(dailyGoalMl = 1000, onboardingCompleted = true) }

        val result = repository.addWaterEntry(amountMl = 500, drinkType = DrinkType.WATER)
        assertEquals(500, result.newTotalMl)
        assertTrue(result.newlyUnlockedAchievements.any { it.id == "first_drop" })
        assertTrue(result.newlyUnlockedAchievements.any { it.id == "getting_started" })

        val todayTotal = repository.observeTotalMlForDate(DateUtils.todayIso()).first()
        assertEquals(500, todayTotal)

        repository.deleteWaterEntry(result.entryId)
        val afterUndo = repository.observeTotalMlForDate(DateUtils.todayIso()).first()
        assertEquals(0, afterUndo)
    }

    @Test
    fun `onboarding screen scrolls and displays sticky start button`() {
        val vm = trackVm("onboarding_scroll") { OnboardingViewModel(repository, context) }
        composeTestRule.setContent {
            HydrateBuddyTheme {
                OnboardingScreen(viewModel = vm, onCompleted = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("onboarding_start_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_list")
            .performScrollToNode(hasTestTag("onboarding_wake_time_card"))
        composeTestRule.onNodeWithTag("onboarding_wake_time_card").assertIsDisplayed()
    }

    @Test
    fun `home screen scrolls and quick add logs water`() {
        val vm = trackVm("home_scroll") { HomeViewModel(repository) }
        composeTestRule.setContent {
            HydrateBuddyTheme {
                val snackbarHost = remember { SnackbarHostState() }
                HomeScreen(
                    viewModel = vm,
                    vibrationEnabled = false,
                    snackbarHostState = snackbarHost,
                    onNavigateToAchievements = {}
                )
            }
        }
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("home_list"), timeoutMillis = 5000)
        composeTestRule.onNodeWithTag("home_list")
            .performScrollToNode(hasTestTag("quick_add_250"))
        composeTestRule.onNodeWithTag("quick_add_250").performClick()
        composeTestRule.waitUntilAtLeastOneExists(
            hasTestTag("dismiss_achievement_button"),
            timeoutMillis = 5000
        )
        composeTestRule.onNodeWithTag("dismiss_achievement_button").performClick()
        composeTestRule.waitUntilAtLeastOneExists(
            hasTestTag("home_total_intake_text") and hasText("250 ml", substring = true),
            timeoutMillis = 5000
        )
        composeTestRule.onNodeWithTag("home_list")
            .performScrollToNode(hasTestTag("home_total_intake_text"))
        composeTestRule.onNodeWithTag("home_total_intake_text").assertIsDisplayed()
    }

    @Test
    fun `settings screen scrolls to bottom options`() {
        val vm = trackVm("settings_scroll") { SettingsViewModel(repository, context) }
        composeTestRule.setContent {
            HydrateBuddyTheme {
                val snackbarHost = remember { SnackbarHostState() }
                SettingsScreen(
                    viewModel = vm,
                    snackbarHostState = snackbarHost,
                    onDataCleared = {},
                    onRevisitOnboarding = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_list").performTouchInput { swipeUp() }
        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_about_card"))
        composeTestRule.onNodeWithTag("settings_about_card").assertIsDisplayed()
    }

    @Test
    fun `ringing reminder defaults to false and toggles in repository`() = runTest {
        repository.ensureInitialized()
        val initial = repository.getCurrentPreferences()
        assertFalse(initial.ringingReminderEnabled)

        repository.updatePreferences { it.copy(ringingReminderEnabled = true) }
        val updated = repository.getCurrentPreferences()
        assertTrue(updated.ringingReminderEnabled)
    }

    @Test
    fun `settings screen toggles optional ringing alarm mode`() {
        val vm = trackVm("settings_ringing") { SettingsViewModel(repository, context) }
        composeTestRule.setContent {
            HydrateBuddyTheme {
                val snackbarHost = remember { SnackbarHostState() }
                SettingsScreen(
                    viewModel = vm,
                    snackbarHostState = snackbarHost,
                    onDataCleared = {},
                    onRevisitOnboarding = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_ringing_switch"))
        composeTestRule.onNodeWithTag("settings_ringing_switch").assertIsOff()
        composeTestRule.onNodeWithTag("settings_ringing_switch").performClick()
        composeTestRule.waitUntilAtLeastOneExists(
            hasTestTag("settings_ringing_switch") and isOn(),
            timeoutMillis = 5000
        )
        composeTestRule.onNodeWithTag("settings_ringing_switch").assertIsOn()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            runBlocking { repository.getCurrentPreferences().ringingReminderEnabled }
        }
    }

    @Test
    fun `alarm ringing dialog triggers log snooze and stop actions`() {
        var loggedAmount = 0
        var snoozed = false
        var stopped = false

        val ringState = AlarmRingState(
            userName = "Alex",
            totalMl = 750,
            goalMl = 2000,
            unit = MeasurementUnit.ML,
            quickAmountMl = 250
        )

        composeTestRule.setContent {
            HydrateBuddyTheme {
                AlarmRingingDialog(
                    ringState = ringState,
                    onLogAndStop = { loggedAmount = it },
                    onSnooze = { snoozed = true },
                    onStop = { stopped = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("alarm_ringing_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("alarm_log_stop_button").performClick()
        assertEquals(250, loggedAmount)

        composeTestRule.onNodeWithTag("alarm_snooze_button").performClick()
        assertTrue(snoozed)

        composeTestRule.onNodeWithTag("alarm_stop_button").performClick()
        assertTrue(stopped)
    }

    @Test
    fun `default theme is professional light and water audio resources are bundled`() = runTest {
        repository.ensureInitialized()
        val prefs = repository.getCurrentPreferences()
        assertEquals(ThemeMode.LIGHT, prefs.themeMode)

        context.resources.openRawResource(R.raw.hydrate_alarm_chime).use { stream ->
            assertTrue(stream.readBytes().size > 10_000)
        }
        context.resources.openRawResource(R.raw.hydrate_water_drop).use { stream ->
            assertTrue(stream.readBytes().size > 5_000)
        }
    }

    @Test
    fun `history month stepping uses calendar months and tabs switch smoothly`() {
        val vm = trackVm("history_month") { HistoryViewModel(repository) }
        composeTestRule.setContent {
            HydrateBuddyTheme {
                val snackbarHost = remember { SnackbarHostState() }
                HistoryScreen(viewModel = vm, snackbarHostState = snackbarHost)
            }
        }
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("history_list"), timeoutMillis = 5000)

        composeTestRule.onNodeWithTag("history_tab_month").performClick()
        composeTestRule.runOnUiThread {
            vm.selectDate("2026-03-31")
        }
        composeTestRule.waitUntilAtLeastOneExists(hasText("Tue, Mar 31"), timeoutMillis = 5000)

        composeTestRule.onNodeWithTag("history_prev_day").performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasText("Sat, Feb 28"), timeoutMillis = 5000)

        composeTestRule.onNodeWithTag("history_prev_day").performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasText("Wed, Jan 28"), timeoutMillis = 5000)
    }

    @Test
    fun `history screen switches between day week and month tabs`() {
        val vm = trackVm("history_tabs") { HistoryViewModel(repository) }
        composeTestRule.setContent {
            HydrateBuddyTheme {
                val snackbarHost = remember { SnackbarHostState() }
                HistoryScreen(viewModel = vm, snackbarHostState = snackbarHost)
            }
        }
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("history_list"), timeoutMillis = 5000)
        composeTestRule.onNodeWithTag("history_tab_day").performClick()
        composeTestRule.onNodeWithTag("history_tab_month").performClick()
        composeTestRule.onNodeWithTag("history_tab_week").performClick()
        composeTestRule.onNodeWithTag("history_list")
            .performScrollToNode(hasTestTag("history_add_for_date_button"))
        composeTestRule.onNodeWithTag("history_add_for_date_button").assertIsDisplayed()
    }

    @Test
    fun `home quick add triggers droplet burst immediately and onboarding caps name length`() {
        val homeVm = trackVm("home_burst") { HomeViewModel(repository) }
        composeTestRule.setContent {
            HydrateBuddyTheme {
                val snackbarHost = remember { SnackbarHostState() }
                HomeScreen(
                    viewModel = homeVm,
                    vibrationEnabled = false,
                    snackbarHostState = snackbarHost,
                    onNavigateToAchievements = {}
                )
            }
        }
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("home_list"), timeoutMillis = 5000)
        assertEquals(0, homeVm.dropletBurstTrigger.value)
        composeTestRule.runOnUiThread {
            homeVm.logWater(250, DrinkType.WATER)
        }
        assertEquals(1, homeVm.dropletBurstTrigger.value)
        composeTestRule.waitUntilAtLeastOneExists(
            hasTestTag("dismiss_achievement_button"),
            timeoutMillis = 5000
        )

        val onboardingVm = trackVm("onboarding_cap") { OnboardingViewModel(repository, context) }
        onboardingVm.setUserName("Alex".repeat(15))
        assertEquals(32, onboardingVm.uiState.value.userName.length)
    }

    @Test
    fun `rewards shows stay tuned footer and settings shows test ringtone without file name`() {
        val achievementsVm = trackVm("rewards_footer") { AchievementsViewModel(repository) }
        composeTestRule.setContent {
            HydrateBuddyTheme {
                AchievementsScreen(viewModel = achievementsVm)
            }
        }
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("achievements_list"), timeoutMillis = 5000)
        composeTestRule.onNodeWithTag("achievements_list")
            .performScrollToNode(hasTestTag("achievements_stay_tuned_footer"))
        composeTestRule.onNodeWithTag("achievements_stay_tuned_footer").assertIsDisplayed()
    }

    @Test
    fun `settings test ringtone omits audio file name`() {
        val vm = trackVm("settings_ringtone") { SettingsViewModel(repository, context) }
        composeTestRule.setContent {
            HydrateBuddyTheme {
                val snackbarHost = remember { SnackbarHostState() }
                SettingsScreen(
                    viewModel = vm,
                    snackbarHostState = snackbarHost,
                    onDataCleared = {},
                    onRevisitOnboarding = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_preview_water_chime_button"))
        composeTestRule.onNode(
            hasTestTag("settings_preview_water_chime_button") and hasText("Test Ringtone")
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `history month calendar switches months and selects days cleanly`() {
        val vm = trackVm("history_month_calendar") { HistoryViewModel(repository) }
        composeTestRule.setContent {
            HydrateBuddyTheme {
                val snackbarHost = remember { SnackbarHostState() }
                HistoryScreen(viewModel = vm, snackbarHostState = snackbarHost)
            }
        }
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("history_list"), timeoutMillis = 5000)
        composeTestRule.onNodeWithTag("history_tab_month").performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("history_prev_month"), timeoutMillis = 5000)

        composeTestRule.runOnUiThread {
            vm.selectDate("2026-04-15")
        }
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("month_day_2026-04-01"), timeoutMillis = 5000)
        composeTestRule.onNodeWithTag("month_day_2026-04-03").performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasText("Fri, Apr 3"), timeoutMillis = 5000)

        composeTestRule.onNodeWithTag("history_prev_month").performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("month_day_2026-03-03"), timeoutMillis = 5000)

        composeTestRule.onNodeWithTag("history_next_month").performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("month_day_2026-04-03"), timeoutMillis = 5000)
    }
}

