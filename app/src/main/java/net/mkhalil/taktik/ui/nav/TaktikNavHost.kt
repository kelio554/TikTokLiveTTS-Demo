package net.mkhalil.taktik.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.mkhalil.taktik.ui.connect.ConnectScreen
import net.mkhalil.taktik.ui.history.HistoryScreen
import net.mkhalil.taktik.ui.live.LiveScreen
import net.mkhalil.taktik.ui.session.SessionDetailScreen
import net.mkhalil.taktik.ui.settings.SettingsScreen
import net.mkhalil.taktik.ui.viewer.ViewerDetailScreen

private data class BottomDest(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val bottomDests = listOf(
    BottomDest(Routes.CONNECT, "Connect", Icons.Default.Bolt),
    BottomDest(Routes.LIVE, "Live", Icons.Default.Visibility),
    BottomDest(Routes.HISTORY, "History", Icons.Default.History),
    BottomDest(Routes.SETTINGS, "Settings", Icons.Default.Settings),
)

@Composable
fun TaktikNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val showBottom = currentRoute in bottomDests.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottom) {
                NavigationBar {
                    bottomDests.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                if (currentRoute != dest.route) {
                                    nav.navigate(dest.route) {
                                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(dest.icon, null) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = Routes.CONNECT,
            modifier = Modifier.padding(pad),
        ) {
            composable(Routes.CONNECT) {
                ConnectScreen(onSessionStarted = {
                    nav.navigate(Routes.LIVE) {
                        popUpTo(Routes.CONNECT) { saveState = true }
                        launchSingleTop = true
                    }
                })
            }
            composable(Routes.LIVE) {
                LiveScreen(
                    onViewerClick = { id -> nav.navigate(Routes.viewerDetail(id)) },
                    onStopped = {
                        nav.navigate(Routes.HISTORY) {
                            popUpTo(Routes.CONNECT) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onSessionClick = { id -> nav.navigate(Routes.sessionDetail(id)) },
                    onViewerClick = { id -> nav.navigate(Routes.viewerDetail(id)) },
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(
                Routes.SESSION_DETAIL,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) {
                SessionDetailScreen(
                    onBack = { nav.popBackStack() },
                    onViewerClick = { id -> nav.navigate(Routes.viewerDetail(id)) },
                )
            }
            composable(
                Routes.VIEWER_DETAIL,
                arguments = listOf(navArgument("userId") { type = NavType.StringType }),
            ) {
                ViewerDetailScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
