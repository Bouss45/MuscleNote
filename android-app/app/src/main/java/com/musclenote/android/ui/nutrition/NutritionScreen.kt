package com.musclenote.android.ui.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.util.dayKey
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.ScreenHeader
import com.musclenote.android.ui.components.rememberToday
import com.musclenote.android.ui.theme.MuscleNoteTheme

/**
 * Onglet Nutrition (SPEC § 2.11) : suivi du jour (calories et eau, objectifs datés). Le jour
 * affiché est le jour courant de l'app (§ 3.12) : il change à minuit et au retour au premier plan.
 */
@Composable
fun NutritionScreen(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val nutrition = container.nutrition
    val tracker by nutrition.tracker.collectAsStateWithLifecycle()
    val todayKey = dayKey(rememberToday())

    Column(modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.nutrition_title))
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(stringResource(R.string.nutrition_section_today))
            CaloriesCard(
                kcal = tracker.caloriesLog[todayKey] ?: 0,
                goal = DailyTracker.goalFor(tracker.calorieGoalHistory, todayKey) ?: tracker.calorieGoal,
                onAdd = { amount -> container.launchMutation { nutrition.addCalories(amount) } },
                onSet = { value -> container.launchMutation { nutrition.setCaloriesToday(value) } },
                onReset = { container.launchMutation { nutrition.setCaloriesToday(0) } },
                onGoalChange = { kcal -> container.launchMutation { nutrition.setCalorieGoal(kcal) } },
            )
            WaterCard(
                ml = tracker.waterLog[todayKey] ?: 0,
                goalMl = DailyTracker.goalFor(tracker.waterGoalHistory, todayKey) ?: tracker.waterGoal,
                onAdd = { ml -> container.launchMutation { nutrition.addWater(ml) } },
                onReset = { container.launchMutation { nutrition.resetWaterToday() } },
                onGoalChange = { ml -> container.launchMutation { nutrition.setWaterGoal(ml) } },
            )
        }
    }
}

/** Libellé de section en capitales (« SUIVI DU JOUR », « COMPLÉMENTS »). */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = MuscleNoteTheme.colors.mutedForeground,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = modifier.padding(bottom = 2.dp),
    )
}
