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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.musclenote.android.ui.exercise.ExerciseFicheScreen
import com.musclenote.android.ui.home.HomeScreen
import com.musclenote.android.ui.journal.JournalScreen
import com.musclenote.android.ui.navigation.Routes
import com.musclenote.android.ui.navigation.TopLevelDestination
import com.musclenote.android.ui.nutrition.NutritionScreen
import com.musclenote.android.ui.progress.ProgressScreen
import com.musclenote.android.ui.settings.BackupViewModel
import com.musclenote.android.ui.settings.SettingsScreen
import com.musclenote.android.ui.summary.WorkoutSummaryScreen
import com.musclenote.android.ui.workout.EditWorkoutScreen
import com.musclenote.android.ui.workout.StartWorkoutSheet
import com.musclenote.android.ui.workout.WorkoutScreen
import com.musclenote.android.ui.workout.rememberLiveTimes
import kotlinx.coroutines.flow.Flow

/** Écran demandé de l'extérieur de l'app (toucher d'une notification). */
enum class ExternalDestination { Workout, Nutrition, WeighIn }

/**
 * Racine de l'interface (SPEC § 15.8.1) : quatre onglets (Accueil, Journal, Progrès, Nutrition) et
 * Paramètres, ouvert par l'engrenage de l'Accueil, avec la barre d'onglets du design system et,
 * pendant une séance, la mini-barre (sauf sur l'Accueil, dont la surface « En cours » tient lieu).
 * Les écrans plein écran (séance, résumé, fiche exercice, éditeur) n'ont ni l'une ni l'autre. « Démarrer une
 * séance » est une bottom sheet posée sur l'écran qui l'ouvre (§ 15.11.1). Transitions en fondu,
 * suivies par le geste de retour prédictif. Chaque écran dessine son propre en-tête sous la barre
 * d'état (edge-to-edge). Le thème (`StudioTheme`) est posé par `MainActivity`.
 */
@Composable
fun MuscleNoteApp(backupViewModel: BackupViewModel, externalDestinations: Flow<ExternalDestination>) {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentWorkout by container.workouts.currentWorkout.collectAsStateWithLifecycle()
    val restTimer by container.restTimer.state.collectAsStateWithLifecycle()
    // « Démarrer une séance » (§ 15.11.1) : ouverte, et avec quel type (choisi sur l'Accueil).
    var startOpen by rememberSaveable { mutableStateOf(false) }
    var startPreset by rememberSaveable { mutableStateOf<String?>(null) }
    var startName by rememberSaveable { mutableStateOf<String?>(null) }
    // Rappel de pesée touché (§ 15.13.4) : Progrès ouvre « Nouvelle pesée » une fois, puis l'oublie.
    var pendingWeighIn by rememberSaveable { mutableStateOf(false) }
    fun openStart(preset: PresetId?, name: String?) {
        startPreset = preset?.id
        startName = name
        startOpen = true
    }

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
    // Notification touchée : la séance en cours (une seule fois dans la pile), l'onglet Nutrition
    // (rappel de complément, § 7.3) ou Progrès sur « Nouvelle pesée » (rappel de pesée, § 15.13.4).
    LaunchedEffect(externalDestinations) {
        externalDestinations.collect { destination ->
            when (destination) {
                ExternalDestination.Workout -> navController.openWorkout()
                ExternalDestination.Nutrition -> navController.navigateToTab(TopLevelDestination.Nutrition)
                ExternalDestination.WeighIn -> {
                    pendingWeighIn = true
                    navController.navigateToTab(TopLevelDestination.Progress)
                }
            }
        }
    }

    val motion = Studio.motion
    Scaffold(
        containerColor = Studio.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (selectedTab != null) {
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
                HomeScreen(
                    onOpenSettings = { navController.openSettings() },
                    onStartWorkout = ::openStart,
                    onOpenWorkout = { navController.openWorkout() },
                    onOpenSession = { navController.openSummary(it) },
                    onEditWorkout = { navController.navigate(Routes.editWorkout(it)) { launchSingleTop = true } },
                    onOpenJournal = { navController.navigateToTab(TopLevelDestination.Journal) },
                )
            }
            composable(TopLevelDestination.Journal.route) {
                JournalScreen(
                    onOpenSession = { navController.openSummary(it) },
                    onEditWorkout = { navController.navigate(Routes.editWorkout(it)) { launchSingleTop = true } },
                    onStartWorkout = { openStart(null, null) },
                    onOpenWorkout = { navController.openWorkout() },
                )
            }
            composable(TopLevelDestination.Progress.route) {
                ProgressScreen(
                    onOpenExercise = { navController.navigate(Routes.exerciseHistory(it)) { launchSingleTop = true } },
                    onStartWorkout = { openStart(null, null) },
                    onOpenWorkout = { navController.openWorkout() },
                    onOpenNutrition = { navController.navigateToTab(TopLevelDestination.Nutrition) },
                    openWeighIn = pendingWeighIn,
                    onWeighInOpened = { pendingWeighIn = false },
                )
            }
            composable(TopLevelDestination.Nutrition.route) { NutritionScreen() }
            composable(Routes.SETTINGS) {
                // Hub de second niveau de l'Accueil (§ 15.10.3) : ← y ramène.
                SettingsScreen(backupViewModel, onBack = { navController.popBackStack() })
            }

            composable(Routes.WORKOUT) {
                WorkoutScreen(
                    // « Réduire » : retour à l'écran d'où la séance a été ouverte (§ 15.8.1).
                    onBack = { if (!navController.popBackStack()) navController.navigateToTab(TopLevelDestination.Home) },
                    onOpenHistory = { navController.navigate(Routes.exerciseHistory(it)) { launchSingleTop = true } },
                    // Terminée : son résumé à la place de la séance (§ 15.12) ; abandonnée : l'Accueil.
                    onFinished = { workoutId -> if (workoutId != null) navController.openFinishedSummary(workoutId) else navController.backToHome() },
                )
            }
            composable(
                Routes.WORKOUT_SUMMARY,
                arguments = listOf(
                    navArgument(Routes.ARG_WORKOUT_ID) { type = NavType.StringType },
                    navArgument(Routes.ARG_FINISHED) {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                // Résumé de séance (§ 15.12) : « Terminer » ramène à l'Accueil, ← à l'écran d'origine.
                WorkoutSummaryScreen(
                    workoutId = entry.arguments?.getString(Routes.ARG_WORKOUT_ID).orEmpty(),
                    finished = entry.arguments?.getBoolean(Routes.ARG_FINISHED) == true,
                    onDone = { navController.backToHome() },
                    onBack = { navController.popBackStack() },
                    onEditWorkout = { navController.navigate(Routes.editWorkout(it)) { launchSingleTop = true } },
                )
            }
            composable(
                Routes.EXERCISE_HISTORY,
                arguments = listOf(navArgument(Routes.ARG_EXERCISE_ID) { type = NavType.StringType }),
            ) { entry ->
                // Fiche exercice (§ 15.9.1).
                ExerciseFicheScreen(
                    exerciseId = entry.arguments?.getString(Routes.ARG_EXERCISE_ID).orEmpty(),
                    onBack = { navController.popBackStack() },
                    onEditWorkout = { navController.navigate(Routes.editWorkout(it)) { launchSingleTop = true } },
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

    if (startOpen) {
        StartWorkoutSheet(
            initialPreset = startPreset?.let { PresetId.fromId(it) },
            initialName = startName,
            onDismiss = { startOpen = false },
            onStarted = {
                startOpen = false
                navController.openWorkout()
            },
        )
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
 * Ouvre la séance en cours (§ 15.8.1) : si elle est déjà dans la pile (sous la fiche d'un
 * exercice), on y revient ; sinon elle s'ouvre par-dessus l'écran affiché.
 */
private fun NavHostController.openWorkout() {
    if (!popBackStack(Routes.WORKOUT, inclusive = false)) navigate(Routes.WORKOUT) { launchSingleTop = true }
}

/** Paramètres au-dessus de l'Accueil (page de second niveau de l'Accueil). */
private fun NavHostController.openSettings() {
    if (currentDestination?.route == Routes.SETTINGS) return
    navigateToTab(TopLevelDestination.Home)
    navigate(Routes.SETTINGS) { launchSingleTop = true }
}

/** Résumé d'une séance terminée (§ 15.12), depuis le Journal ou la « Dernière séance » de l'Accueil. */
private fun NavHostController.openSummary(workoutId: String) {
    navigate(Routes.workoutSummary(workoutId)) { launchSingleTop = true }
}

/**
 * Fin de séance (§ 15.12.1) : le résumé prend la place de la séance en cours, juste au-dessus de
 * l'Accueil ; « Terminer » et le retour y reviennent.
 */
private fun NavHostController.openFinishedSummary(workoutId: String) {
    navigate(Routes.workoutSummary(workoutId, finished = true)) {
        popUpTo(TopLevelDestination.Home.route)
        launchSingleTop = true
    }
}

/** Retour à l'Accueil depuis un écran plein écran (le résumé d'une séance terminée, par exemple). */
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
