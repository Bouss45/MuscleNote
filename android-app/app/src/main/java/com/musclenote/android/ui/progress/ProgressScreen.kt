package com.musclenote.android.ui.progress

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.catalog.exerciseDefinitions
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.progress.ProgressStats
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.ScreenHeader
import com.musclenote.android.ui.components.color
import com.musclenote.android.ui.components.rememberDateFormats
import com.musclenote.android.ui.components.rememberToday
import com.musclenote.android.ui.components.textColor
import com.musclenote.android.ui.theme.MuscleNoteTheme

/** Onglet affiché ; sa [key] est mémorisée (état sauvegardé). */
private sealed interface ProgressTab {
    val key: String

    data object Weight : ProgressTab {
        override val key = "weight"
    }

    data object Calories : ProgressTab {
        override val key = "calories"
    }

    data object Water : ProgressTab {
        override val key = "water"
    }

    data class Muscle(val group: MuscleGroup) : ProgressTab {
        override val key: String get() = "muscle:${group.id}"
    }

    companion object {
        fun fromKey(key: String): ProgressTab = when (key) {
            Calories.key -> Calories
            Water.key -> Water
            else -> key.removePrefix("muscle:").takeIf { key.startsWith("muscle:") }
                ?.let(MuscleGroup::fromId)
                ?.let(::Muscle)
                ?: Weight
        }
    }
}

/**
 * Onglet Progrès (SPEC § 2.10) : rangée défilante d'onglets — Poids corps, Calories, Eau, puis un
 * onglet par groupe musculaire avec son nombre de séances — et le contenu de l'onglet choisi.
 * Calculs au § 3.10 ([ProgressStats]). [onOpenExerciseHistory] ouvre l'historique d'un exercice.
 */
@Composable
fun ProgressScreen(onOpenExerciseHistory: (exerciseId: String) -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val workouts by container.workouts.workouts.collectAsStateWithLifecycle()
    val customExercises by container.workouts.customExercises.collectAsStateWithLifecycle()
    val bodyWeights by container.nutrition.bodyWeights.collectAsStateWithLifecycle()
    val tracker by container.nutrition.tracker.collectAsStateWithLifecycle()
    val zone = container.time.zone()
    val today = rememberToday()
    val dates = rememberDateFormats(zone)
    val colors = MuscleNoteTheme.colors

    var tabKey by rememberSaveable { mutableStateOf(ProgressTab.Weight.key) }
    val tab = ProgressTab.fromKey(tabKey)
    var showWeighIn by rememberSaveable { mutableStateOf(false) }

    // Archivés compris : ils nomment encore l'historique (§ 3.9).
    val definitions = remember(customExercises) { exerciseDefinitions(customExercises) }
    val muscleCounts = remember(workouts, definitions) { ProgressStats.muscleSessionCounts(workouts, definitions) }
    val calorieDays = remember(tracker) { ProgressStats.dailySeries(tracker.caloriesLog).size }
    val waterDays = remember(tracker) { ProgressStats.dailySeries(tracker.waterLog).size }

    val scroll = rememberScrollState()
    LaunchedEffect(tabKey) { scroll.scrollTo(0) }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.progress_title))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
        ) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabChip(
                    label = stringResource(R.string.progress_tab_weight),
                    color = colors.info,
                    active = tab == ProgressTab.Weight,
                    icon = R.drawable.ic_person,
                    onClick = { tabKey = ProgressTab.Weight.key },
                )
                TabChip(
                    label = stringResource(R.string.progress_tab_calories),
                    color = colors.calories,
                    active = tab == ProgressTab.Calories,
                    emoji = "🔥",
                    badge = calorieDays,
                    onClick = { tabKey = ProgressTab.Calories.key },
                )
                TabChip(
                    label = stringResource(R.string.progress_tab_water),
                    color = colors.water,
                    active = tab == ProgressTab.Water,
                    emoji = "💧",
                    badge = waterDays,
                    onClick = { tabKey = ProgressTab.Water.key },
                )
                for (group in MuscleGroup.entries) {
                    val count = muscleCounts[group] ?: 0
                    val muscleTab = ProgressTab.Muscle(group)
                    TabChip(
                        label = stringResource(group.labelRes),
                        color = group.color,
                        contentColor = group.textColor,
                        active = tab == muscleTab,
                        badge = count,
                        dimmed = count == 0,
                        onClick = { tabKey = muscleTab.key },
                    )
                }
            }

            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (tab) {
                    ProgressTab.Weight -> BodyWeightTab(
                        workouts = workouts,
                        bodyWeights = bodyWeights,
                        zone = zone,
                        dates = dates,
                        onAddWeighIn = { showWeighIn = true },
                        onRemoveWeighIn = { date -> container.launchMutation { container.nutrition.removeBodyWeight(date) } },
                    )
                    ProgressTab.Calories -> DailyTab(DailyKind.Calories, tracker, today, dates)
                    ProgressTab.Water -> DailyTab(DailyKind.Water, tracker, today, dates)
                    is ProgressTab.Muscle -> MuscleGroupTab(
                        group = tab.group,
                        workouts = workouts,
                        definitions = definitions,
                        zone = zone,
                        dates = dates,
                        onOpenHistory = onOpenExerciseHistory,
                    )
                }
            }
        }
    }

    if (showWeighIn) {
        WeighInDialog(
            today = today,
            onDismiss = { showWeighIn = false },
            onSave = { day, weight ->
                showWeighIn = false
                container.launchMutation { container.nutrition.addBodyWeight(day, weight) }
            },
        )
    }
}

/**
 * Onglet de la rangée (§ 2.10) : actif → fond couleur à 13 %, bordure couleur, texte et icône
 * [contentColor] (nuance lisible en thème clair, § 6.2), badge plein couleur à texte blanc ;
 * inactif → `card`, texte `mutedForeground`. [dimmed] : groupe sans séance, opacité 50 %.
 */
@Composable
private fun TabChip(
    label: String,
    color: Color,
    active: Boolean,
    onClick: () -> Unit,
    contentColor: Color = color,
    @DrawableRes icon: Int? = null,
    emoji: String? = null,
    badge: Int = 0,
    dimmed: Boolean = false,
) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val content = if (active) contentColor else colors.mutedForeground
    Row(
        Modifier
            .alpha(if (dimmed) 0.5f else 1f)
            .clip(shape)
            .background(if (active) color.copy(alpha = 0.13f) else colors.card)
            .border(1.5.dp, if (active) color else colors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        if (emoji != null) Text(emoji, fontSize = 13.sp)
        Text(label, color = content, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (badge > 0) {
            Box(
                Modifier
                    .height(18.dp)
                    .defaultMinSize(minWidth = 18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) color else colors.muted)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    badge.toString(),
                    color = if (active) Color.White else colors.mutedForeground,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
