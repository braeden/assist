package com.assist.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.assist.ui.sessions.RecipesScreen
import com.assist.ui.sessions.SessionDetailScreen
import com.assist.ui.sessions.SessionsScreen
import com.assist.ui.theme.AssistTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AssistTheme {
                AssistApp()
            }
        }
    }
}

/** Top-level bottom-nav destinations. */
private sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Tab("home", "Home", Icons.Filled.Home)
    data object Sessions : Tab("sessions", "Sessions", Icons.Filled.List)
    data object Memory : Tab("recipes", "Memory", Icons.Filled.Star)
}

private const val SESSION_DETAIL_ROUTE = "session/{sessionId}"

/**
 * App shell: a bottom navigation bar over a [NavHost]. Home is the onboarding /
 * control screen; Sessions lists past runs (tap → transcript); Memory browses
 * learned task recipes. The session transcript is a nested route under the
 * Sessions tab, which stays selected while it's shown.
 */
@Composable
private fun AssistApp() {
    val nav = rememberNavController()
    val tabs = listOf(Tab.Home, Tab.Sessions, Tab.Memory)

    Scaffold(
        bottomBar = {
            val entry by nav.currentBackStackEntryAsState()
            val route = entry?.destination?.route
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = route == tab.route ||
                        (tab == Tab.Sessions && route == SESSION_DETAIL_ROUTE)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.route) {
                                // One copy per tab, preserving each tab's own state.
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Tab.Home.route,
            modifier = Modifier.padding(inner),
        ) {
            composable(Tab.Home.route) { OnboardingScreen() }
            composable(Tab.Sessions.route) {
                SessionsScreen(
                    onOpenSession = { id -> nav.navigate("session/$id") },
                    onOpenRecipes = {
                        nav.navigate(Tab.Memory.route) { launchSingleTop = true }
                    },
                )
            }
            composable(
                route = SESSION_DETAIL_ROUTE,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
            ) {
                SessionDetailScreen(onBack = { nav.popBackStack() })
            }
            composable(Tab.Memory.route) {
                RecipesScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
