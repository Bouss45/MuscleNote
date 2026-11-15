package com.musclenote.android.ui.workout

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.util.parseDecimal
import com.musclenote.android.data.workout.SetEntryField
import com.musclenote.android.data.workout.SetEntryRules
import com.musclenote.android.notifications.AppNotifications
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.designsystem.components.InfoRow
import com.musclenote.android.ui.designsystem.components.SheetSurface
import com.musclenote.android.ui.designsystem.components.SheetTitle
import com.musclenote.android.ui.designsystem.components.StudioBottomSheet
import com.musclenote.android.ui.designsystem.components.StudioButton
import com.musclenote.android.ui.designsystem.components.statusHelp
import com.musclenote.android.ui.designsystem.theme.Studio
import kotlinx.coroutines.launch

/** Étapes de « Démarrer une séance » (§ 15.11.1). */
internal enum class StartStage { Type, Weight }

/**
 * « Démarrer une séance » (SPEC § 15.11.1) : un formulaire en bottom sheet, posé sur l'écran d'où il
 * part (Accueil, Journal, Progrès). Étape « Type de séance » (préréglages ou nom), puis « Poids de
 * corps » au pavé numérique ; avec [initialPreset] ou [initialName] (type choisi sur l'Accueil,
 * § 15.8.2), la sheet s'ouvre au poids. « Commencer » démarre la séance (§ 3.1) puis [onStarted]
 * ouvre la séance en cours (l'appelant ferme la sheet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartWorkoutSheet(
    initialPreset: PresetId?,
    initialName: String?,
    onDismiss: () -> Unit,
    onStarted: () -> Unit,
) {
    StudioBottomSheet(onDismissRequest = onDismiss) { inset ->
        StartWorkoutForm(initialPreset, initialName, onStarted, bottomInset = inset)
    }
}

/** Contenu de [StartWorkoutSheet], avec son état. [initialStage] force l'étape (aperçus). */
@Composable
internal fun StartWorkoutForm(
    initialPreset: PresetId?,
    initialName: String?,
    onStarted: () -> Unit,
    bottomInset: Dp,
    initialStage: StartStage? = null,
) {
    val container = LocalAppContainer.current
    val workouts by container.workouts.workouts.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val locale = appLocale()
    val field = SetEntryField.Weight

    val initialCustom = initialName?.trim()?.takeIf { initialPreset == null && it.isNotEmpty() }.orEmpty()
    var stage by rememberSaveable {
        mutableStateOf(initialStage ?: if (initialPreset != null || initialCustom.isNotEmpty()) StartStage.Weight else StartStage.Type)
    }
    var preset by rememberSaveable { mutableStateOf(initialPreset) }
    var customName by rememberSaveable { mutableStateOf(initialCustom) }
    // Prérempli avec le poids de la séance terminée la plus récente (0 = inconnu → vide).
    var weightText by rememberSaveable {
        mutableStateOf(workouts.firstOrNull()?.bodyWeight?.takeIf { it > 0 }?.let { SetEntryRules.format(it, field, locale) }.orEmpty())
    }
    var fresh by rememberSaveable { mutableStateOf(true) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }

    val presetLabel = preset?.let { stringResource(it.labelRes) }
    val finalName = presetLabel ?: customName.trim()

    /** `startWorkout` (§ 3.1), puis la séance en cours. */
    fun startNow() {
        val chosenPreset = preset
        val name = finalName
        val bodyWeight = parseDecimal(weightText)
        val job = container.launchMutation { container.workouts.startWorkout(bodyWeight, chosenPreset, name) }
        scope.launch {
            job.join()
            onStarted()
        }
    }
    // Le rappel est toujours le plus récent (rememberUpdatedState interne) : il voit la saisie actuelle.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startNow() }

    fun start() {
        if (starting) return
        if (parseDecimal(weightText) <= 0 || finalName.isEmpty()) {
            invalid = true
            haptics.warning()
            return
        }
        starting = true
        haptics.heavy()
        // 🔄 D6 : la permission est demandée d'abord ; un refus n'empêche pas de démarrer.
        if (AppNotifications.needsPermissionRequest(context)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startNow()
        }
    }

    BackHandler(enabled = stage == StartStage.Weight) { stage = StartStage.Type }

    when (stage) {
        StartStage.Type -> SheetSurface(Modifier.imePadding().verticalScroll(rememberScrollState()), bottomInset = bottomInset) {
            SheetTitle(
                stringResource(R.string.start_workout_title),
                AnnotatedString(stringResource(R.string.start_workout_subtitle)),
                messageStyle = Studio.typography.body,
            )
            WorkoutTypeFields(
                preset = preset,
                customName = customName,
                onSelectPreset = {
                    haptics.light()
                    preset = it
                    customName = ""
                },
                onCustomNameChange = {
                    customName = it
                    if (it.isNotBlank()) preset = null
                },
            )
            StudioButton(
                stringResource(R.string.start_workout_next),
                onClick = {
                    haptics.light()
                    stage = StartStage.Weight
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = finalName.isNotEmpty(),
            )
        }

        StartStage.Weight -> BodyWeightForm(
            title = stringResource(R.string.common_body_weight),
            help = if (invalid) {
                statusHelp(stringResource(R.string.start_workout_weight_invalid))
            } else {
                AnnotatedString(stringResource(R.string.start_workout_body_weight_subtitle))
            },
            text = weightText,
            fresh = fresh,
            onEdit = { text, nextFresh ->
                weightText = text
                fresh = nextFresh
                invalid = false
            },
            actionLabel = stringResource(R.string.start_workout_start),
            onAction = ::start,
            bottomInset = bottomInset,
            quickWeights = true,
            info = {
                InfoRow(
                    label = stringResource(R.string.start_workout_title),
                    value = finalName,
                    onClick = { stage = StartStage.Type },
                    showDivider = false,
                )
            },
        )
    }
}
