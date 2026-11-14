package com.musclenote.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.ui.designsystem.StateCaption
import com.musclenote.android.ui.designsystem.StudioComponentPreview
import com.musclenote.android.ui.designsystem.StudioComponentPreviews
import com.musclenote.android.ui.designsystem.theme.StudioTheme
import com.musclenote.android.ui.nutrition.DailyDay
import com.musclenote.android.ui.nutrition.DailyEntryForm
import com.musclenote.android.ui.nutrition.NewSupplementForm
import com.musclenote.android.ui.nutrition.NutritionScreen
import com.musclenote.android.ui.nutrition.SupplementSheetContent
import com.musclenote.android.ui.nutrition.TodaySurface
import com.musclenote.android.ui.progress.DailyKind
import com.musclenote.android.ui.theme.MuscleNoteTheme

/*
 * Onglet Nutrition (SPEC § 15.10) : le hub avec les données de PreviewData (1 850 kcal sur 2 600,
 * 1,5 L sur 2,5 L, créatine prise), sans donnée, et ses formulaires en bottom sheet.
 */

/** L'onglet en entier (sans la barre d'onglets) : surface « Aujourd'hui », compléments. */
@ScreenPreviews
@Composable
private fun NutritionScreenPreview() = ScreenPreview {
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) { NutritionScreen(insets = PreviewTabInsets) }
}

/** Ce qu'un téléphone montre de l'onglet Nutrition. */
@PhonePreviews
@Composable
private fun NutritionScreenPhonePreview() = ScreenPreview {
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) { NutritionScreen(insets = PreviewTabInsets) }
}

/** Première ouverture : rien de saisi aujourd'hui, aucun complément. */
@PhonePreviews
@Composable
private fun NutritionScreenEmptyPreview() = ScreenPreview(PreviewDataSet.Empty) {
    StudioTheme(darkTheme = MuscleNoteTheme.colors.isDark) { NutritionScreen(insets = PreviewTabInsets) }
}

/** Surface « Aujourd'hui » : sous l'objectif (gris), dans l'objectif (vert). */
@StudioComponentPreviews
@Composable
private fun TodaySurfacePreview() = StudioComponentPreview {
    StateCaption("Sous l'objectif")
    TodaySurface(DailyDay(kcal = 1850, calorieGoal = 2800, ml = 750, waterGoal = 2000), takenCount = 1, supplementCount = 3, onCalories = {}, onWater = {})
    StateCaption("Dans l'objectif, eau atteinte")
    TodaySurface(DailyDay(kcal = 2700, calorieGoal = 2800, ml = 2250, waterGoal = 2000), takenCount = 3, supplementCount = 3, onCalories = {}, onWater = {})
}

/** Surface « Aujourd'hui » au-dessus de l'objectif (rouge), sans complément ; puis rien de saisi. */
@StudioComponentPreviews
@Composable
private fun TodaySurfaceOverPreview() = StudioComponentPreview {
    StateCaption("Au-dessus, sans complément")
    TodaySurface(DailyDay(kcal = 3120, calorieGoal = 2800, ml = 0, waterGoal = 2000), takenCount = 0, supplementCount = 0, onCalories = {}, onWater = {})
    StateCaption("Rien de saisi")
    TodaySurface(DailyDay(kcal = 0, calorieGoal = 2800, ml = 0, waterGoal = 2000), takenCount = 0, supplementCount = 2, onCalories = {}, onWater = {})
}

/** Formulaire Calories : « Ajouter » avec 350 kcal tapées, et le nouveau total. */
@StudioComponentPreviews
@Composable
private fun DailyEntryFormPreview() = StudioComponentPreview {
    DailyEntryForm(DailyKind.Calories, current = 1850, goal = 2800, onSave = { _, _ -> }, onSaveGoal = {}, bottomInset = 0.dp, initialText = "350")
}

/** Objectif d'eau, en litres, prérempli. */
@StudioComponentPreviews
@Composable
private fun DailyGoalFormPreview() = StudioComponentPreview {
    DailyEntryForm(DailyKind.Water, current = 750, goal = 2500, onSave = { _, _ -> }, onSaveGoal = {}, bottomInset = 0.dp, initialGoal = true)
}

/** « Nouveau complément » : nom saisi, unité g, un rappel à 08:00. */
@StudioComponentPreviews
@Composable
private fun NewSupplementFormPreview() = StudioComponentPreview {
    NewSupplementForm(onSave = { _, _, _, _, _ -> }, bottomInset = 0.dp, initialName = "Oméga-3", autoFocus = false)
}

/** Sheet d'un complément : deux rappels. */
@StudioComponentPreviews
@Composable
private fun SupplementSheetPreview() = StudioComponentPreview {
    SupplementSheetContent(PreviewData.supplements.last(), {}, {}, { _, _, _ -> }, {}, {}, onClose = {}, bottomInset = 0.dp)
}

/** Sheet d'un complément sans dosage, rappels désactivés et aucun rappel. */
@StudioComponentPreviews
@Composable
private fun SupplementSheetNoReminderPreview() = StudioComponentPreview {
    SupplementSheetContent(
        Supplement("whey", "Whey", "", "dose", reminderEnabled = false, reminders = emptyList()),
        {}, {}, { _, _, _ -> }, {}, {}, onClose = {}, bottomInset = 0.dp,
    )
}
