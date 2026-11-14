package com.musclenote.android.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.musclenote.android.R
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.model.RestTimerState
import com.musclenote.android.data.model.Workout
import com.musclenote.android.ui.components.workoutDisplayName
import com.musclenote.android.ui.designsystem.components.MiniSessionBar
import com.musclenote.android.ui.designsystem.components.NavItem
import com.musclenote.android.ui.designsystem.components.StudioNavigationBar
import com.musclenote.android.ui.designsystem.templates.systemScreenInsets
import com.musclenote.android.ui.designsystem.theme.Studio
import com.musclenote.android.ui.designsystem.theme.StudioTheme
import com.musclenote.android.ui.exercise.ExerciseFicheScreen
import com.musclenote.android.ui.home.HomeScreen
import com.musclenote.android.ui.journal.JournalScreen
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
import com.musclenote.android.ui.workout.rememberLiveTimes
import kotlinx.coroutines.flow.Flow

/** Écran demandé de l'extérieur de l'app (toucher d'une notification). */
enum class ExternalDestination { Workout, Nutrition }

/**
 * Racine de l'interface (SPEC § 15.8.1) : quatre onglets (Accueil, Journal, Progrès, Nutrition) et
 * Paramètres, ouvert par l'engrenage de l'Accueil, avec la barre d'onglets du design system et,
 * pendant une séance, la mini-barre (sauf sur l'Accueil, dont la surface « En cours » tient lieu).
 * Les écrans plein écran (séance, démarrage, sélecteur, historique, éditeur) n'ont ni l'une ni
 * l'autre. Transitions en fondu, suivies par le geste de retour prédictif. Chaque écran dessine son
 * propre en-tête sous la barre d'état (edge-to-edge).
 */
@Composable
fun MuscleNoteApp(backupViewModel: BackupViewModel, externalDestinations: Flow<ExternalDestination>) {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentWorkout by container.workouts.currentWorkout.collectAsStateWithLifecycle()
    val restTimer by container.restTimer.state.collectAsStateWithLifecycle()
    val darkTheme = MuscleNoteTheme.colors.isDark

    // Paramètres est une page de second niveau de l'Accueil : son onglet reste sélectionné.
    val selectedTab = when {
        currentDestination == null -> TopLevelDestination.Home
        currentDestination.route == Routes.SETTINGS -> TopLevelDestination.Home
        else -> TopLevelDestination.entries.firstOrNull { tab -> currentDestination.isTab(tab) }
    }
    val onHome = currentDestination == null || currentDestination.isTab(TopLevelDestination.Home)

    // Fichier partagé par une autre app (§ 5.5) : la confirmation d'import est dans Paramètres.
    LaunchedEffect(backupViewModel) {
        backupViewModel.showSettingsRequests.collect { navController.openSettings() }
    }
    // Notification touchée : la séance en cours (une seule fois dans la pile), ou l'onglet Nutrition
    // (rappel de complément, § 7.3).
    LaunchedEffect(externalDestinations) {
        externalDestinations.collect { destination ->
            when (destination) {
                ExternalDestination.Workout -> navController.openWorkout()
                ExternalDestination.Nutrition -> navController.navigateToTab(TopLevelDestination.Nutrition)
            }
        }
    }

    val motion = Studio.motion
    Scaffold(
        containerColor = MuscleNoteTheme.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (selectedTab != null) {
                StudioTheme(darkTheme = darkTheme) {
                    AppBottomBar(
                        selected = selectedTab,
                        workout = currentWorkout.takeUnless { onHome },
                        rest = restTimer,
                        onSelect = { tab ->
                            // Depuis Paramètres, l'onglet Accueil (sélectionné) ramène à l'Accueil.
                            if (tab == TopLevelDestination.Home && currentDestination?.route == Routes.SETTINGS) {
                                navController.popBackStack()
                            } else {
                                navController.navigateToTab(tab)
                            }
                        },
                        onResume = { navController.openWorkout() },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(motion.standard.spec()) },
            exitTransition = { fadeOut(motion.standard.spec()) },
            popEnterTransition = { fadeIn(motion.standard.spec()) },
            popExitTransition = { fadeOut(motion.standard.spec()) },
        ) {
            composable(TopLevelDestination.Home.route) {
                StudioTheme(darkTheme = darkTheme) {
                    HomeScreen(
                        onOpenSettings = { navController.openSettings() },
                        onStartWorkout = { preset, name -> navController.openStartWorkout(preset, name) },
                        onOpenWorkout = { navController.openWorkout() },
                        onEditWorkout = { navController.navigate(Routes.editWorkout(it)) { launchSingleTop = true } },
                        onOpenJournal = { navController.navigateToTab(TopLevelDestination.Journal) },
                    )
                }
            }
            composable(TopLevelDestination.Journal.route) {
                StudioTheme(darkTheme = darkTheme) {
                    JournalScreen(
                        onEditWorkout = { navController.navigate(Routes.editWorkout(it)) { launchSingleTop = true } },
                        onStartWorkout = { navController.openStartWorkout(null, null) },
                        onOpenWorkout = { navController.openWorkout() },
                    )
                }
            }
            composable(TopLevelDestination.Progress.route) {
                StudioTheme(darkTheme = darkTheme) {
                    ProgressScreen(
                        onOpenExercise = { navController.navigate(Routes.exerciseHistory(it)) { launchSingleTop = true } },
                        onStartWorkout = { navController.openStartWorkout(null, null) },
                        onOpenWorkout = { navController.openWorkout() },
                        onOpenNutrition = { navController.navigateToTab(TopLevelDestination.Nutrition) },
                    )
                }
            }
            composable(TopLevelDestination.Nutrition.route) {
                StudioTheme(darkTheme = darkTheme) { NutritionScreen() }
            }
            composable(Routes.SETTINGS) {
                // Hub de second niveau de l'Accueil (§ 15.10.3) : ← y ramène.
                StudioTheme(darkTheme = darkTheme) {
                    SettingsScreen(backupViewModel, onBack = { navController.popBackStack() })
                }
            }

            composable(
                Routes.START_WORKOUT,
                arguments = listOf(
                    navArgument(Routes.ARG_PRESET) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(Routes.ARG_NAME) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                StartWorkoutScreen(
                    onClose = { navController.popBackStack() },
                    // « Commencer » remplace cet écran par la séance en cours (§ 2.3).
                    onStarted = {
                        navController.navigate(Routes.WORKOUT) {
                            popUpTo(Routes.START_WORKOUT) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    initialPreset = entry.arguments?.getString(Routes.ARG_PRESET)?.let { PresetId.fromId(it) },
                    initialName = entry.arguments?.getString(Routes.ARG_NAME),
                )
            }
            composable(Routes.WORKOUT) {
                // Écran migré au design system « Studio épuré » (DESIGN.md), au thème choisi dans l'app.
                StudioTheme(darkTheme = darkTheme) {
                    WorkoutScreen(
                        // « Réduire » : retour à l'écran d'où la séance a été ouverte (§ 15.8.1).
                        onBack = { if (!navController.popBackStack()) navController.navigateToTab(TopLevelDestination.Home) },
                        onAddExercise = { navController.navigate(Routes.EXERCISE_PICKER) { launchSingleTop = true } },
                        onOpenHistory = { navController.navigate(Routes.exerciseHistory(it)) { launchSingleTop = true } },
                        onFinished = { navController.backToHome() },
                    )
                }
            }
            composable(Routes.EXERCISE_PICKER) {
                ExercisePickerScreen(onClose = { navController.popBackStack() })
            }
            composable(
                Routes.EXERCISE_HISTORY,
                arguments = listOf(navArgument(Routes.ARG_EXERCISE_ID) { type = NavType.StringType }),
            ) { entry ->
                // Fiche exercice (§ 15.9.1), migrée au design system.
                StudioTheme(darkTheme = darkTheme) {
                    ExerciseFicheScreen(
                        exerciseId = entry.arguments?.getString(Routes.ARG_EXERCISE_ID).orEmpty(),
                        onBack = { navController.popBackStack() },
                        onEditWorkout = { navController.navigate(Routes.editWorkout(it)) { launchSingleTop = true } },
                    )
                }
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

private fun NavDestination.isTab(tab: TopLevelDestination): Boolean = hierarchy.any { it.route == tab.route }

/**
 * Bas des hubs (§ 15.8.1) : la mini-barre de la séance en cours ([workout] non nul), 8 au-dessus
 * de la barre d'onglets et dans les marges, puis la barre d'onglets avec l'inset de navigation.
 */
@Composable
private fun AppBottomBar(
    selected: TopLevelDestination,
    workout: Workout?,
    rest: RestTimerState?,
    onSelect: (TopLevelDestination) -> Unit,
    onResume: () -> Unit,
) {
    val tabs = TopLevelDestination.entries
    Column {
        if (workout != null) {
            Column(Modifier.padding(horizontal = Studio.spacing.screen)) {
                SessionMiniBar(workout, rest, onResume)
                Spacer(Modifier.height(Studio.spacing.xs))
            }
        }
        StudioNavigationBar(
            items = tabs.map { NavItem(stringResource(it.labelRes), it.unselectedIcon, it.selectedIcon) },
            selectedIndex = tabs.indexOf(selected),
            onSelect = { onSelect(tabs[it]) },
            bottomInset = systemScreenInsets().bottom,
        )
    }
}

/** Mini-barre : « Push · repos 1:48 » pendant un repos, sinon « Push · 17:48 » ; « Reprendre ». */
@Composable
private fun SessionMiniBar(workout: Workout, rest: RestTimerState?, onResume: () -> Unit) {
    val times = rememberLiveTimes(workout, rest, LocalAppContainer.current.time)
    MiniSessionBar(
        sessionName = workoutDisplayName(workout),
        status = if (times.rest != null) stringResource(R.string.mini_bar_rest) else null,
        time = times.rest ?: times.elapsed,
        actionLabel = stringResource(R.string.home_resume),
        onResume = onResume,
    )
}

/**
 * Ouvre la séance en cours (§ 15.8.1) : si elle est déjà dans la pile (sous l'historique d'un
 * exercice ou le sélecteur), on y revient ; sinon elle s'ouvre par-dessus l'écran affiché.
 */
private fun NavHostController.openWorkout() {
    if (!popBackStack(Routes.WORKOUT, inclusive = false)) navigate(Routes.WORKOUT) { launchSingleTop = true }
}

/** « Démarrer une séance » : à l'étape 1, ou à l'étape du poids avec un type (§ 15.8.2). */
private fun NavHostController.openStartWorkout(preset: PresetId?, name: String?) {
    navigate(Routes.startWorkout(preset, name)) { launchSingleTop = true }
}

/** Paramètres au-dessus de l'Accueil (page de second niveau de l'Accueil). */
private fun NavHostController.openSettings() {
    if (currentDestination?.route == Routes.SETTINGS) return
    navigateToTab(TopLevelDestination.Home)
    navigate(Routes.SETTINGS) { launchSingleTop = true }
}

/** Retour à l'Accueil depuis un écran plein écran (la séance terminée, par exemple). */
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
