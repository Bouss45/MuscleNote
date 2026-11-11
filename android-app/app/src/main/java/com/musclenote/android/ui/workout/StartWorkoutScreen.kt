package com.musclenote.android.ui.workout

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.model.PresetId
import com.musclenote.android.data.util.formatDecimal
import com.musclenote.android.data.util.parseDecimal
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.theme.MuscleNoteTheme
import kotlinx.coroutines.launch

private enum class StartStep { Name, Weight }

/** Raccourcis de poids de corps (§ 2.3, étape 2). */
private val QUICK_WEIGHTS = listOf(55, 60, 65, 70, 75, 80, 85, 90, 95, 100)

private const val WEIGHT_MAX_LENGTH = 6

/**
 * Démarrer une séance (SPEC § 2.3) : étape 1, préréglage ou nom personnalisé ; étape 2, poids de
 * corps. « Commencer » démarre la séance (§ 3.1) puis [onStarted] remplace cet écran par la
 * séance en cours.
 */
@Composable
fun StartWorkoutScreen(onClose: () -> Unit, onStarted: () -> Unit) {
    val container = LocalAppContainer.current
    val workouts by container.workouts.workouts.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val locale = appLocale()

    var step by rememberSaveable { mutableStateOf(StartStep.Name) }
    var preset by rememberSaveable { mutableStateOf<PresetId?>(null) }
    var customSelected by rememberSaveable { mutableStateOf(false) }
    var customName by rememberSaveable { mutableStateOf("") }
    // Prérempli avec le poids de la séance terminée la plus récente (0 = inconnu → vide).
    var weightText by rememberSaveable {
        mutableStateOf(workouts.firstOrNull()?.bodyWeight?.takeIf { it > 0 }?.let { formatDecimal(it, locale) }.orEmpty())
    }
    var starting by remember { mutableStateOf(false) }

    val presetLabel = preset?.let { stringResource(it.labelRes) }
    val finalName = presetLabel ?: if (customSelected) customName.trim() else ""
    val weight = parseDecimal(weightText)

    BackHandler(enabled = step == StartStep.Weight) { step = StartStep.Name }

    val colors = MuscleNoteTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Box(
            Modifier
                .padding(start = 14.dp, top = 12.dp, bottom = 8.dp)
                .size(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .clickable { if (step == StartStep.Weight) step = StartStep.Name else onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (step == StartStep.Weight) R.drawable.ic_arrow_back else R.drawable.ic_close),
                contentDescription = stringResource(if (step == StartStep.Weight) R.string.common_back else R.string.common_cancel),
                tint = colors.foreground,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 40.dp),
        ) {
            when (step) {
                StartStep.Name -> NameStep(
                    preset = preset,
                    customSelected = customSelected,
                    customName = customName,
                    canContinue = finalName.isNotEmpty(),
                    onSelectPreset = {
                        haptics.light()
                        preset = it
                        customSelected = false
                        customName = ""
                    },
                    onSelectCustom = {
                        haptics.light()
                        preset = null
                        customSelected = true
                    },
                    onCustomNameChange = { customName = it },
                    onNext = {
                        haptics.light()
                        step = StartStep.Weight
                    },
                )

                StartStep.Weight -> WeightStep(
                    name = finalName,
                    weightText = weightText,
                    canStart = weight > 0 && !starting,
                    onWeightChange = { weightText = it.take(WEIGHT_MAX_LENGTH) },
                    onQuickWeight = {
                        haptics.light()
                        weightText = it.toString()
                    },
                    onStart = {
                        if (weight > 0 && finalName.isNotEmpty() && !starting) {
                            starting = true
                            haptics.heavy()
                            val chosenPreset = preset
                            val job = container.launchMutation { container.workouts.startWorkout(weight, chosenPreset, finalName) }
                            scope.launch {
                                job.join()
                                onStarted()
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NameStep(
    preset: PresetId?,
    customSelected: Boolean,
    customName: String,
    canContinue: Boolean,
    onSelectPreset: (PresetId) -> Unit,
    onSelectCustom: () -> Unit,
    onCustomNameChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    StepTitle(stringResource(R.string.start_workout_title), stringResource(R.string.start_workout_subtitle))
    WorkoutNameFields(
        preset = preset,
        customSelected = customSelected,
        customName = customName,
        onSelectPreset = onSelectPreset,
        onSelectCustom = onSelectCustom,
        onCustomNameChange = onCustomNameChange,
    )

    Spacer(Modifier.height(28.dp))
    PrimaryButton(
        text = stringResource(R.string.start_workout_next),
        trailingIcon = R.drawable.ic_arrow_forward,
        enabled = canContinue,
        onClick = onNext,
    )
}

@Composable
private fun WeightStep(
    name: String,
    weightText: String,
    canStart: Boolean,
    onWeightChange: (String) -> Unit,
    onQuickWeight: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val colors = MuscleNoteTheme.colors
    Box(
        Modifier
            .padding(bottom = 24.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.primary.copy(alpha = 0.09f))
            .border(1.dp, colors.primary.copy(alpha = 0.33f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(name, color = colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
    StepTitle(stringResource(R.string.common_body_weight), stringResource(R.string.start_workout_body_weight_subtitle))

    val focusRequester = remember { FocusRequester() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = RoundedCornerShape(16.dp)
        BasicTextField(
            value = weightText,
            onValueChange = onWeightChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                color = colors.foreground,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = (-1).sp,
            ),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .width(160.dp)
                .height(90.dp)
                .clip(shape)
                .background(colors.card)
                .border(2.dp, colors.primary, shape)
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (weightText.isEmpty()) {
                        Text(
                            "75",
                            color = colors.mutedForeground,
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp,
                        )
                    }
                    inner()
                }
            },
        )
        Text("kg", color = colors.mutedForeground, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
    }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (quick in QUICK_WEIGHTS) {
            val active = weightText == quick.toString()
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .clip(shape)
                    .background(if (active) colors.primary else colors.card)
                    .border(1.dp, if (active) colors.primary else colors.border, shape)
                    .clickable { onQuickWeight(quick) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    quick.toString(),
                    color = if (active) colors.primaryForeground else colors.foreground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    PrimaryButton(
        text = stringResource(R.string.start_workout_start),
        leadingIcon = R.drawable.ic_play_arrow,
        enabled = canStart,
        onClick = onStart,
    )
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    val colors = MuscleNoteTheme.colors
    Text(
        title,
        color = colors.foreground,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        modifier = Modifier.padding(bottom = 6.dp),
    )
    Text(subtitle, color = colors.mutedForeground, fontSize = 15.sp, modifier = Modifier.padding(bottom = 28.dp))
}

/** Grand bouton plein (§ 2.3) : `primary` si actif, `muted` sinon. */
@Composable
private fun PrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    @DrawableRes leadingIcon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
) {
    val colors = MuscleNoteTheme.colors
    val content = if (enabled) colors.primaryForeground else colors.mutedForeground
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) colors.primary else colors.muted)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let { Icon(painterResource(it), contentDescription = null, tint = content, modifier = Modifier.size(20.dp)) }
        Text(text, color = content, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        trailingIcon?.let { Icon(painterResource(it), contentDescription = null, tint = content, modifier = Modifier.size(18.dp)) }
    }
}
