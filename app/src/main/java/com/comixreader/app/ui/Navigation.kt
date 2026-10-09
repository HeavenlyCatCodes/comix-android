package com.comixreader.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.comixreader.app.data.Catalog
import com.comixreader.app.data.MangaSummary
import com.comixreader.app.ui.browse.BrowseScreen
import com.comixreader.app.ui.details.DetailsScreen
import com.comixreader.app.ui.home.HomeScreen
import com.comixreader.app.ui.library.HistoryScreen
import com.comixreader.app.ui.library.LibraryScreen
import com.comixreader.app.ui.reader.ReaderScreen
import com.comixreader.app.ui.settings.SettingsScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Outlined.Home),
    Browse("browse", "Browse", Icons.Outlined.Explore),
    Library("library", "Library", Icons.AutoMirrored.Outlined.LibraryBooks),
    History("history", "History", Icons.Outlined.History),
    Settings("settings", "Settings", Icons.Outlined.Settings),
}

private fun NavHostController.openDetails(slug: String) = navigate("details/${Uri.encode(slug)}")
private fun NavHostController.openReader(slug: String, chapterId: Long) =
    navigate("reader/${Uri.encode(slug)}/$chapterId")

@Composable
fun ComixNavHost(deepLinkSlug: String?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = Tab.entries.any { it.route == route }

    LaunchedEffect(deepLinkSlug) {
        if (deepLinkSlug != null) {
            nav.openDetails(deepLinkSlug)
            onDeepLinkHandled()
        }
    }

    val open: (MangaSummary) -> Unit = { nav.openDetails(it.slug) }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.Home.route,
            // Screens draw their own top bars, so only the bottom bar is reserved here.
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(padding),
        ) {
            composable(Tab.Home.route) { HomeScreen(onOpen = open, onSearch = { nav.navigate("search") }) }
            composable(Tab.Browse.route) { BrowseScreen(onOpen = open, onBack = null) }
            composable("search") { BrowseScreen(onOpen = open, onBack = { nav.popBackStack() }) }
            composable(
                "genre/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { e ->
                BrowseScreen(onOpen = open, onBack = { nav.popBackStack() }, initialGenre = e.arguments?.getString("id"))
            }
            composable(Tab.Library.route) { LibraryScreen(onOpen = open) }
            composable(Tab.History.route) {
                HistoryScreen(
                    onResume = { nav.openReader(it.manga.slug, it.chapterId) },
                    onOpen = open,
                )
            }
            composable(Tab.Settings.route) { SettingsScreen() }
            composable(
                "details/{slug}",
                arguments = listOf(navArgument("slug") { type = NavType.StringType }),
            ) { e ->
                val slug = e.arguments?.getString("slug").orEmpty()
                DetailsScreen(
                    slug = slug,
                    onBack = { nav.popBackStack() },
                    onRead = { s, id -> nav.openReader(s, id) },
                    onGenre = { name ->
                        Catalog.genres.firstOrNull { it.label.equals(name, ignoreCase = true) }
                            ?.let { nav.navigate("genre/${it.value}") }
                    },
                )
            }
            composable(
                "reader/{slug}/{chapter}",
                arguments = listOf(
                    navArgument("slug") { type = NavType.StringType },
                    navArgument("chapter") { type = NavType.LongType },
                ),
            ) { e ->
                ReaderScreen(
                    slug = e.arguments?.getString("slug").orEmpty(),
                    chapterId = e.arguments?.getLong("chapter") ?: 0L,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
