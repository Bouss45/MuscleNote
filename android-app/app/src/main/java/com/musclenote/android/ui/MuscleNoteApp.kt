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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.musclenote.android.ui.history.ExerciseHistoryScreen
import com.musclenote.android.ui.home.HomeScreen
import com.musclenote.android.ui.navigation.Routes
import com.musclenote.android.ui.navigation.TopLevelDestination
import com.musclenote.android.ui.nutrition.NutritionScreen
import com.musclenote.android.ui.progress.ProgressScreen
import com.musclenote.android.ui.settings.BackupViewModel
import com.musclenote.android.ui.settings.SettingsScreen
import com.musclenote.android.ui.theme.MuscleNoteTheme
import com.musclenote.android.ui.workout.EditWorkoutScreen
import com.musclenote.android.ui.workout.ExercisePickerScreen
import com.musclenote.android.ui.workout.StartWorkoutScreen
import com.musclenote.android.ui.workout.WorkoutScreen
import kotlinx.coroutines.flow.Flow

/** Écran demandé de l'extérieur de l'app (toucher d'une notification). */
enum class ExternalDestination { Workout, Nutrition }

/**
 * Racine de l'interface (SPEC § 2.1) : barre d'onglets + hôte de navigation, sans en-tête
 * système. Chaque écran dessine son propre en-tête sous la barre de statut (edge-to-edge).
 */
@Composable
fun MuscleNoteApp(backupViewModel: BackupViewModel, externalDestinations: Flow<ExternalDestination>) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // Écrans plein écran (séance, sélecteur…) : pas de barre d'onglets.
    val onTab = currentDestination == null ||
        TopLevelDestination.entries.any { tab -> currentDestination.hierarchy.any { it.route == tab.route } }

    // Fichier partagé par une autre app (§ 5.5) : la confirmation d'import est dans Paramètres.
    LaunchedEffect(backupViewModel) {
        backupViewModel.showSettingsRequests.collect {
            navController.navigateToTab(TopLevelDestination.Settings)
        }
    }
    // Notification touchée : séance en cours au-dessus de l'écran actuel (minuteur, § 7.2), ou
    // onglet Nutrition (rappel de complément, § 7.3).
    LaunchedEffect(externalDestinations) {
        externalDestinations.collect { destination ->
            when (destination) {
                ExternalDestination.Workout -> navController.navigate(Routes.WORKOUT) { launchSingleTop = true }
                ExternalDestination.Nutrition -> navController.navigateToTab(TopLevelDestination.Nutrition)
            }
        }
    }

    Scaffold(
        containerColor = MuscleNoteTheme.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (onTab) {
                MuscleNoteBottomBar(
                    currentDestination = currentDestination,
                    onSelect = { navController.navigateToTab(it) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
        ) {
            composable(TopLevelDestination.Home.route) {
                HomeScreen(
                    onStartWorkout = { navController.navigate(Routes.START_WORKOUT) { launchSingleTop = true } },
                    onOpenWorkout = { navController.navigate(Routes.WORKOUT) { launchSingleTop = true } },
                    onEditWorkout = { navController.navigate(Routes.editWorkout(it)) { launchSingleTop = true } },
                )
            }
            composable(TopLevelDestination.Progress.route) {
                ProgressScreen(
                    onOpenExerciseHistory = { navController.navigate(Routes.exerciseHistory(it)) { launchSingleTop = true } },
                )
            }
            composable(TopLevelDestination.Nutrition.route) { NutritionScreen() }
            composable(TopLevelDestination.Settings.route) { SettingsScreen(backupViewModel) }

            composable(Routes.START_WORKOUT) {
                StartWorkoutScreen(
                    onClose = { navController.popBackStack() },
                    // « Commencer » remplace cet écran par la séance en cours (§ 2.3).
                    onStarted = {
                        navController.navigate(Routes.WORKOUT) {
                            popUpTo(Routes.START_WORKOUT) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.WORKOUT) {
                WorkoutScreen(
                    onBack = { navController.backToHome() },
                    onAddExercise = { navController.navigate(Routes.EXERCISE_PICKER) { launchSingleTop = true } },
                    onOpenHistory = { navController.navigate(Routes.exerciseHistory(it)) { launchSingleTop = true } },
                    onFinished = { navController.backToHome() },
                )
            }
            composable(Routes.EXERCISE_PICKER) {
                ExercisePickerScreen(onClose = { navController.popBackStack() })
            }
            composable(
                Routes.EXERCISE_HISTORY,
                arguments = listOf(navArgument(Routes.ARG_EXERCISE_ID) { type = NavType.StringType }),
            ) { entry ->
                ExerciseHistoryScreen(
                    exerciseId = entry.arguments?.getString(Routes.ARG_EXERCISE_ID).orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.EDIT_WORKOUT,
                arguments = listOf(navArgument(Routes.ARG_WORKOUT_ID) { type = NavType.StringType }),
            ) { entry ->
                EditWorkoutScreen(
                    workoutId = entry.arguments?.getString(Routes.ARG_WORKOUT_ID).orEmpty(),
                    onClose = { navController.popBackStack() },
                )
            }
        }
    }
}

/** Retour à l'Accueil depuis un écran plein écran (la séance en cours, par exemple). */
private fun NavHostController.backToHome() {
    if (!popBackStack(TopLevelDestination.Home.route, inclusive = false)) navigateToTab(TopLevelDestination.Home)
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
