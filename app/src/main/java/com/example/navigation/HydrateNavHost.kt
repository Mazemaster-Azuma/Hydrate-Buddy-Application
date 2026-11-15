package com.example.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.screens.AchievementsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.viewmodel.AchievementsViewModel
import com.example.viewmodel.HistoryViewModel
import com.example.viewmodel.HomeViewModel
import com.example.viewmodel.HydrateViewModelFactory
import com.example.viewmodel.OnboardingViewModel
import com.example.viewmodel.SettingsViewModel

private data class BottomNavItem(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun HydrateNavHost(
    onboardingCompleted: Boolean,
    vibrationEnabled: Boolean,
    viewModelFactory: HydrateViewModelFactory
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val navItems = remember {
        listOf(
            BottomNavItem(
                route = HomeRoute,
                labelRes = R.string.nav_home,
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                testTag = "nav_home"
            ),
            BottomNavItem(
                route = HistoryRoute,
                labelRes = R.string.nav_history,
                selectedIcon = Icons.Filled.BarChart,
                unselectedIcon = Icons.Outlined.BarChart,
                testTag = "nav_history"
            ),
            BottomNavItem(
                route = AchievementsRoute,
                labelRes = R.string.nav_achievements,
                selectedIcon = Icons.Filled.EmojiEvents,
                unselectedIcon = Icons.Outlined.EmojiEvents,
                testTag = "nav_achievements"
            ),
            BottomNavItem(
                route = SettingsRoute,
                labelRes = R.string.nav_settings,
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                testTag = "nav_settings"
            )
        )
    }

    val navigateToTab: (Any) -> Unit = remember(navController) {
        { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val showChrome = currentDestination != null && !currentDestination.hasRoute(OnboardingRoute::class)
    val startDestination: Any = remember { if (onboardingCompleted) HomeRoute else OnboardingRoute }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showChrome && !useNavRail) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(
                            windowInsets = WindowInsets.navigationBars,
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            val navBarColors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            navItems.forEach { item ->
                                val selected = currentDestination?.hasRoute(item.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(item.route) },
                                    colors = navBarColors,
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = stringResource(item.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(item.labelRes),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showChrome && useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        val railColors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        navItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                colors = railColors,
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(item.labelRes),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                val onOnboardingCompleted = remember(navController) {
                    {
                        navController.navigate(HomeRoute) {
                            popUpTo(0) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                }
                val onNavigateToAchievements = remember(navigateToTab) {
                    { navigateToTab(AchievementsRoute) }
                }
                val onDataCleared = remember(navController) {
                    {
                        navController.navigate(OnboardingRoute) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                val onRevisitOnboarding = remember(navController) {
                    {
                        navController.navigate(OnboardingRoute) {
                            launchSingleTop = true
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    enterTransition = {
                        fadeIn(animationSpec = tween(150)) +
                            scaleIn(initialScale = 0.985f, animationSpec = tween(150))
                    },
                    exitTransition = { fadeOut(animationSpec = tween(110)) },
                    popEnterTransition = {
                        fadeIn(animationSpec = tween(150)) +
                            scaleIn(initialScale = 0.985f, animationSpec = tween(150))
                    },
                    popExitTransition = { fadeOut(animationSpec = tween(110)) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    composable<OnboardingRoute> {
                        val onboardingViewModel: OnboardingViewModel = viewModel(factory = viewModelFactory)
                        OnboardingScreen(
                            viewModel = onboardingViewModel,
                            onCompleted = onOnboardingCompleted
                        )
                    }

                    composable<HomeRoute> {
                        val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
                        HomeScreen(
                            viewModel = homeViewModel,
                            vibrationEnabled = vibrationEnabled,
                            snackbarHostState = snackbarHostState,
                            onNavigateToAchievements = onNavigateToAchievements
                        )
                    }

                    composable<HistoryRoute> {
                        val historyViewModel: HistoryViewModel = viewModel(factory = viewModelFactory)
                        HistoryScreen(
                            viewModel = historyViewModel,
                            snackbarHostState = snackbarHostState
                        )
                    }

                    composable<AchievementsRoute> {
                        val achievementsViewModel: AchievementsViewModel = viewModel(factory = viewModelFactory)
                        AchievementsScreen(viewModel = achievementsViewModel)
                    }

                    composable<SettingsRoute> {
                        val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            snackbarHostState = snackbarHostState,
                            onDataCleared = onDataCleared,
                            onRevisitOnboarding = onRevisitOnboarding
                        )
                    }
                }
            }
        }
    }
}
