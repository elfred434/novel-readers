package com.novelreader.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.novelreader.ui.screens.browse.BrowseScreen
import com.novelreader.ui.screens.detail.DetailScreen
import com.novelreader.ui.screens.downloads.DownloadsScreen
import com.novelreader.ui.screens.extensions.ExtensionsScreen
import com.novelreader.ui.screens.history.HistoryScreen
import com.novelreader.ui.screens.library.LibraryScreen
import com.novelreader.ui.screens.reader.ReaderScreen
import com.novelreader.ui.screens.settings.SettingsScreen
import com.novelreader.ui.screens.updates.UpdatesScreen

@Composable
fun NovelReaderNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val bottomBarRoutes = Screen.bottomNavItems.map { it.route }
    val showBottomBar = currentDestination?.route in bottomBarRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    Screen.bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = { screen.icon?.let { Icon(it, contentDescription = screen.title) } },
                            label = { Text(screen.title, style = MaterialTheme.typography.labelSmall) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(250)) },
            exitTransition = { fadeOut(animationSpec = tween(250)) }
        ) {
            composable(Screen.Library.route) {
                LibraryScreen(
                    onNovelClick = { slug -> navController.navigate(Screen.Detail.createRoute(slug)) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Browse.route) { BrowseScreen(onNovelClick = { slug -> navController.navigate(Screen.Detail.createRoute(slug)) }) }
            composable(Screen.Updates.route) { UpdatesScreen(onNovelClick = { slug -> navController.navigate(Screen.Detail.createRoute(slug)) }) }
            composable(Screen.History.route) { HistoryScreen(onNovelClick = { slug -> navController.navigate(Screen.Detail.createRoute(slug)) }) }
            composable(Screen.Detail.route, arguments = listOf(navArgument("slug") { type = NavType.StringType })) {
                DetailScreen(onBack = { navController.popBackStack() }, onChapterClick = { chapterUrl -> navController.navigate(Screen.Reader.createRoute(chapterUrl)) })
            }
            composable(Screen.Reader.route, arguments = listOf(navArgument("chapterUrlEncoded") { type = NavType.StringType })) {
                ReaderScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() }, onExtensionsClick = { navController.navigate(Screen.Extensions.route) }, onDownloadsClick = { navController.navigate(Screen.Downloads.route) })
            }
            composable(Screen.Extensions.route) { ExtensionsScreen(onBack = { navController.popBackStack() }) }
            composable(Screen.Downloads.route) { DownloadsScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
