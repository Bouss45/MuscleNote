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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.nutrition.BodyWeightRules
import com.musclenote.android.data.nutrition.StartWeightPrompt
import com.musclenote.android.data.util.parseDecimal
import com.musclenote.android.notifications.AppNotifications
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.formatNumber
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.components.rememberToday
import com.musclenote.android.ui.designsystem.components.ButtonVariant
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
 *
 * 🔄 D38 (§ 15.13.2) : le poids est facultatif — champ vide avec la dernière pesée en aide,
 * « Passer » démarre sans poids ; poids du jour déjà connu → pas de champ, la séance démarre sans
 * poids (celui du jour s'affiche sans être recopié).
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
    val weighIns by container.nutrition.bodyWeights.collectAsStateWithLifecycle()
    val today = rememberToday()
    val zone = container.time.zone()
    val prompt = remember(today, workouts, weighIns, zone) { BodyWeightRules.startPrompt(today, workouts, weighIns, zone) }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val initialCustom = initialName?.trim()?.takeIf { initialPreset == null && it.isNotEmpty() }.orEmpty()
    var stage by rememberSaveable {
        mutableStateOf(initialStage ?: if (initialPreset != null || initialCustom.isNotEmpty()) StartStage.Weight else StartStage.Type)
    }
    var preset by rememberSaveable { mutableStateOf(initialPreset) }
    var customName by rememberSaveable { mutableStateOf(initialCustom) }
    // 🔄 D38 : champ vide, la dernière pesée est dans l'aide (§ 15.13.2).
    var weightText by rememberSaveable { mutableStateOf("") }
    var fresh by rememberSaveable { mutableStateOf(true) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    // Poids retenu au moment de « Commencer » / « Passer » (0 = sans poids), relu après la permission.
    var chosenWeight by remember { mutableDoubleStateOf(0.0) }

    val presetLabel = preset?.let { stringResource(it.labelRes) }
    val finalName = presetLabel ?: customName.trim()

    /** `startWorkout` (§ 3.1), puis la séance en cours. */
    fun startNow() {
        val chosenPreset = preset
        val name = finalName
        val bodyWeight = chosenWeight
        val job = container.launchMutation { container.workouts.startWorkout(bodyWeight, chosenPreset, name) }
        scope.launch {
            job.join()
            onStarted()
        }
    }
    // Le rappel est toujours le plus récent (rememberUpdatedState interne) : il voit la saisie actuelle.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startNow() }

    /** Démarre avec [bodyWeight] kg (0 = sans poids : « Passer », ou poids du jour déjà connu). */
    fun start(bodyWeight: Double) {
        if (starting || finalName.isEmpty()) return
        chosenWeight = bodyWeight
        starting = true
        haptics.heavy()
        // 🔄 D6 : la permission est demandée d'abord ; un refus n'empêche pas de démarrer.
        if (AppNotifications.needsPermissionRequest(context)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startNow()
        }
    }

    /** « ✓ Commencer » du pavé : un poids > 0 est exigé, sinon l'aide propose « Passer ». */
    fun startWithTypedWeight() {
        val weight = parseDecimal(weightText)
        if (weight <= 0) {
            invalid = true
            haptics.warning()
            return
        }
        start(weight)
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
            if (prompt is StartWeightPrompt.WeighedToday) {
                // Poids du jour déjà connu : pas d'étape du poids, « Commencer » tout de suite.
                StudioButton(
                    stringResource(R.string.start_workout_start),
                    onClick = { start(0.0) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = finalName.isNotEmpty(),
                    icon = R.drawable.ds_ic_check,
                )
            } else {
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
        }

        StartStage.Weight -> when (prompt) {
            is StartWeightPrompt.WeighedToday -> WeighedTodayForm(
                weight = prompt.weight,
                workoutName = finalName,
                onChangeType = { stage = StartStage.Type },
                onStart = { start(0.0) },
                bottomInset = bottomInset,
            )
            is StartWeightPrompt.Ask -> AskWeightForm(
                prompt = prompt,
                invalid = invalid,
                text = weightText,
                fresh = fresh,
                onEdit = { text, nextFresh ->
                    weightText = text
                    fresh = nextFresh
                    invalid = false
                },
                workoutName = finalName,
                onChangeType = { stage = StartStage.Type },
                onStart = ::startWithTypedWeight,
                onSkip = { start(0.0) },
                bottomInset = bottomInset,
            )
        }
    }
}

/**
 * Étape « Poids de corps » sans poids du jour (§ 15.13.2) : champ vide, aide = la dernière pesée et son
 * ancienneté, pas partant de cette pesée, « Passer » (séance sans poids) sous la ligne du type.
 */
@Composable
private fun AskWeightForm(
    prompt: StartWeightPrompt.Ask,
    invalid: Boolean,
    text: String,
    fresh: Boolean,
    onEdit: (text: String, fresh: Boolean) -> Unit,
    workoutName: String,
    onChangeType: () -> Unit,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    bottomInset: Dp,
) {
    BodyWeightForm(
        title = stringResource(R.string.common_body_weight),
        help = if (invalid) {
            statusHelp(stringResource(R.string.start_workout_weight_or_skip))
        } else {
            AnnotatedString(lastWeighInHelp(prompt))
        },
        text = text,
        fresh = fresh,
        onEdit = onEdit,
        actionLabel = stringResource(R.string.start_workout_start),
        onAction = onStart,
        bottomInset = bottomInset,
        quickWeights = true,
        stepBase = prompt::stepBase,
        info = {
            InfoRow(
                label = stringResource(R.string.start_workout_title),
                value = workoutName,
                onClick = onChangeType,
                showDivider = false,
            )
            StudioButton(
                stringResource(R.string.start_workout_skip),
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Text,
            )
        },
    )
}

/** « Dernière pesée : 78,4 kg, il y a 3 jours. », ou « Ton poids aujourd'hui ? Facultatif. ». */
@Composable
private fun lastWeighInHelp(prompt: StartWeightPrompt.Ask): String {
    val weight = prompt.lastWeight ?: return stringResource(R.string.start_workout_weight_optional)
    val days = prompt.daysAgo ?: 0L
    val age = when (days) {
        0L -> stringResource(R.string.common_today_lower)
        1L -> stringResource(R.string.common_yesterday_lower)
        else -> pluralStringResource(R.plurals.common_days_ago, days.toInt(), days.toInt())
    }
    return stringResource(R.string.start_workout_weight_last, formatNumber(weight), age)
}

/**
 * Étape « Poids de corps » un jour déjà pesé (§ 15.13.2) : pas de champ, l'aide rappelle le poids du
 * jour, la ligne du type, puis « ✓ Commencer » (séance sans poids, celui du jour s'affiche).
 */
@Composable
private fun WeighedTodayForm(weight: Double, workoutName: String, onChangeType: () -> Unit, onStart: () -> Unit, bottomInset: Dp) {
    SheetSurface(bottomInset = bottomInset) {
        SheetTitle(
            stringResource(R.string.common_body_weight),
            AnnotatedString(stringResource(R.string.start_workout_weighed_today, formatNumber(weight))),
            messageStyle = Studio.typography.body,
        )
        InfoRow(
            label = stringResource(R.string.start_workout_title),
            value = workoutName,
            onClick = onChangeType,
            showDivider = false,
        )
        StudioButton(stringResource(R.string.start_workout_start), onClick = onStart, modifier = Modifier.fillMaxWidth(), icon = R.drawable.ds_ic_check)
    }
}
