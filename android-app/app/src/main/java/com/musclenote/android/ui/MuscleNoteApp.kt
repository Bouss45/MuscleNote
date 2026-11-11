package com.musclenote.android.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.musclenote.android.ui.home.HomeScreen
import com.musclenote.android.ui.navigation.TopLevelDestination
import com.musclenote.android.ui.nutrition.NutritionScreen
import com.musclenote.android.ui.progress.ProgressScreen
import com.musclenote.android.ui.settings.SettingsScreen
import com.musclenote.android.ui.theme.MuscleNoteTheme

/**
 * Racine de l'interface (SPEC § 2.1) : barre d'onglets + hôte de navigation, sans en-tête
 * système. Chaque écran dessine son propre en-tête sous la barre de statut (edge-to-edge).
 */
@Composable
fun MuscleNoteApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        containerColor = MuscleNoteTheme.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            MuscleNoteBottomBar(
                currentDestination = currentDestination,
                onSelect = { navController.navigateToTab(it) },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
        ) {
            composable(TopLevelDestination.Home.route) { HomeScreen() }
            composable(TopLevelDestination.Progress.route) { ProgressScreen() }
            composable(TopLevelDestination.Nutrition.route) { NutritionScreen() }
            composable(TopLevelDestination.Settings.route) { SettingsScreen() }
        }
    }
}

/** Onglet : une seule copie de chaque onglet dans la pile, état conservé entre deux visites. */
private fun NavHostController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Barre d'onglets (§ 2.1) : fond `background`, séparateur haut 1 dp `border`, onglet actif
 * `primary` avec icône remplie, inactif `mutedForeground` avec icône contour.
 */
@Composable
private fun MuscleNoteBottomBar(
    currentDestination: NavDestination?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val colors = MuscleNoteTheme.colors
    Column {
        HorizontalDivider(thickness = 1.dp, color = colors.border)
        NavigationBar(containerColor = colors.background, tonalElevation = 0.dp) {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(destination) },
                    icon = {
                        Icon(
                            painter = painterResource(
                                if (selected) destination.selectedIcon else destination.unselectedIcon,
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.primary,
                        selectedTextColor = colors.primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = colors.mutedForeground,
                        unselectedTextColor = colors.mutedForeground,
                    ),
                )
            }
        }
    }
}
