package com.fitpro.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fitpro.ui.screens.ai.AiChatScreen
import com.fitpro.ui.screens.cardapio.CardapioScreen
import com.fitpro.ui.screens.dashboard.DashboardScreen
import com.fitpro.ui.screens.diary.DiaryScreen
import com.fitpro.ui.screens.mercado.MercadoScreen
import com.fitpro.ui.screens.profile.ProfileScreen
import com.fitpro.ui.screens.training.TrainingScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Inicio",    Icons.Outlined.Home)
    object Diary     : Screen("diary",     "Diario",   Icons.Outlined.MenuBook)
    object Cardapio  : Screen("cardapio",  "Cardapio", Icons.Outlined.RestaurantMenu)
    object Training  : Screen("training",  "Treinos",  Icons.Outlined.FitnessCenter)
    object Mercado   : Screen("mercado",   "Mercado",  Icons.Outlined.ShoppingCart)
    object Profile   : Screen("profile",   "Perfil",   Icons.Outlined.Person)
    object AiChat    : Screen("ai_chat",   "IA",       Icons.Outlined.AutoAwesome)
}

private val TOP_LEVEL = listOf(
    Screen.Dashboard,
    Screen.Diary,
    Screen.Cardapio,
    Screen.Training,
    Screen.Mercado,
    Screen.Profile,
)

fun NavController.navigateTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState    = true
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TOP_LEVEL.forEach { screen ->
                item(
                    icon     = { Icon(screen.icon, contentDescription = screen.label) },
                    label    = { Text(screen.label) },
                    selected = currentDest?.hierarchy?.any { it.route == screen.route } == true,
                    onClick  = { navController.navigateTo(screen.route) }
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            NavHost(navController = navController, startDestination = Screen.Dashboard.route) {
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        onNavigateToDiary    = { navController.navigateTo(Screen.Diary.route) },
                        onNavigateToTraining = { navController.navigateTo(Screen.Training.route) },
                        onNavigateToProfile  = { navController.navigateTo(Screen.Profile.route) }
                    )
                }
                composable(Screen.Diary.route) {
                    DiaryScreen(
                        onNavigateToCardapio = { navController.navigateTo(Screen.Cardapio.route) }
                    )
                }
                composable(Screen.Cardapio.route)  { CardapioScreen() }
                composable(Screen.Training.route)  { TrainingScreen() }
                composable(Screen.Mercado.route)   { MercadoScreen() }
                composable(Screen.Profile.route) {
                    ProfileScreen(
                        onNavigateToAi = { navController.navigateTo(Screen.AiChat.route) }
                    )
                }
                composable(Screen.AiChat.route)    { AiChatScreen() }
            }
        }
    }
}
