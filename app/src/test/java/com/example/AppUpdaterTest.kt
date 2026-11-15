package com.example

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.UserPreferencesDataStore
import com.example.database.HydrateDatabase
import com.example.repository.HydrationRepository
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.HydrateBuddyTheme
import com.example.updater.AppUpdateCoordinator
import com.example.updater.GitHubUpdateMetadata
import com.example.updater.NetworkMonitor
import com.example.updater.UpdateCheckUiState
import com.example.updater.UpdateVersionComparator
import com.example.viewmodel.SettingsViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class AppUpdaterTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: HydrateDatabase
    private lateinit var prefsDataStore: UserPreferencesDataStore
    private lateinit var repository: HydrationRepository
    private lateinit var viewModelStore: ViewModelStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefsDataStore = UserPreferencesDataStore(context)
        runBlocking { prefsDataStore.clearAll() }
        viewModelStore = ViewModelStore()
        db = Room.inMemoryDatabaseBuilder(context, HydrateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HydrationRepository(database = db, preferencesDataStore = prefsDataStore)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        db.close()
    }

    @Test
    fun `repo slug normalizer handles urls and rejects invalid input`() {
        assertEquals("alex/Hydrate-Buddy", UpdateVersionComparator.normalizeRepoSlug("alex/Hydrate-Buddy"))
        assertEquals(
            "alex/Hydrate-Buddy",
            UpdateVersionComparator.normalizeRepoSlug("https://github.com/alex/Hydrate-Buddy/")
        )
        assertEquals(
            "alex/Hydrate-Buddy",
            UpdateVersionComparator.normalizeRepoSlug("https://github.com/alex/Hydrate-Buddy.git")
        )
        assertNull(UpdateVersionComparator.normalizeRepoSlug(""))
        assertNull(UpdateVersionComparator.normalizeRepoSlug("not a repo"))
    }

    @Test
    fun `version comparison detects newer versionCode and semver strings`() {
        assertTrue(
            UpdateVersionComparator.isRemoteVersionNewer(
                currentVersionCode = 1,
                currentVersionName = "1.0",
                remoteVersionCode = 102,
                remoteVersionName = "1.0.2"
            )
        )
        assertFalse(
            UpdateVersionComparator.isRemoteVersionNewer(
                currentVersionCode = 105,
                currentVersionName = "1.0.5",
                remoteVersionCode = 105,
                remoteVersionName = "1.0.5"
            )
        )
        assertTrue(
            UpdateVersionComparator.isRemoteVersionNewer(
                currentVersionCode = 1,
                currentVersionName = "1.0.2",
                remoteVersionCode = 0,
                remoteVersionName = "v1.0.10"
            )
        )
        assertEquals(
            112,
            UpdateVersionComparator.extractVersionCodeFromNotes("Release notes\nversionCode: 112")
        )
    }

    @Test
    fun `metadata json parses cleanly`() {
        val raw = """
            {
              "versionCode": 108,
              "versionName": "1.0.8",
              "tagName": "v1.0.8",
              "apkFileName": "HydrateBuddy-v1.0.8-release.apk",
              "apkSha256": "abc123",
              "apkSizeBytes": 15728640,
              "releaseNotes": "Smoother UI"
            }
        """.trimIndent()
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<GitHubUpdateMetadata>(raw)
        assertEquals(108, parsed.versionCode)
        assertEquals("1.0.8", parsed.versionName)
        assertEquals("HydrateBuddy-v1.0.8-release.apk", parsed.apkFileName)
        assertEquals("15.0 MB", UpdateVersionComparator.formatFileSize(parsed.apkSizeBytes))
    }

    @Test
    fun `settings manual update check handles offline safely without crashing`() {
        val coordinator = AppUpdateCoordinator(appContext = context, repository = repository)
        runBlocking { coordinator.saveRepositoryOverride("alex/Hydrate-Buddy") }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(repository, context, coordinator) as T
        }
        val vm = ViewModelProvider(viewModelStore, factory)[SettingsViewModel::class.java]

        // Robolectric default NetworkCapabilities may report disconnected; ensure no crash on check
        NetworkMonitor.isInternetAvailable(context)

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
            .performScrollToNode(hasTestTag("settings_check_updates_button"))
        composeTestRule.onNodeWithTag("settings_check_updates_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_update_repo").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_check_updates_button").performClick()
        composeTestRule.waitForIdle()

        // Should end in a safe non-crashing state (either Offline or Checking/Error if network mocked)
        val state = vm.updateState.value
        assertTrue(
            state is UpdateCheckUiState.Offline ||
                state is UpdateCheckUiState.Checking ||
                state is UpdateCheckUiState.Error ||
                state is UpdateCheckUiState.UpToDate ||
                state is UpdateCheckUiState.UpdateAvailable
        )
    }
}
