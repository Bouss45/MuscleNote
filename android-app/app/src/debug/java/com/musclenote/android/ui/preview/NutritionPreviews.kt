package com.musclenote.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.musclenote.android.data.model.ProteinGoal
import com.musclenote.android.data.model.ProteinGoalMode
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.ui.designsystem.StateCaption
import com.musclenote.android.ui.designsystem.StudioComponentPreview
import com.musclenote.android.ui.designsystem.StudioComponentPreviews
import com.musclenote.android.ui.nutrition.DailyDay
import com.musclenote.android.ui.nutrition.MeasureDay
import com.musclenote.android.ui.nutrition.MeasureForm
import com.musclenote.android.ui.nutrition.MeasureSheetStart
import com.musclenote.android.ui.nutrition.MeasureStep
import com.musclenote.android.ui.nutrition.NewSupplementForm
import com.musclenote.android.ui.nutrition.NutritionMeasure
import com.musclenote.android.ui.nutrition.NutritionScreen
import com.musclenote.android.ui.nutrition.SupplementActions
import com.musclenote.android.ui.nutrition.SupplementSheetContent
import com.musclenote.android.ui.nutrition.SupplementStep
import com.musclenote.android.ui.nutrition.TodaySurface

/*
 * Onglet Nutrition (SPEC § 15.10, § 15.16) : le hub avec les données de PreviewData (1 850 kcal sur
 * 2 600, 1,5 L sur 2,5 L, 112 g de protéines sur 150, créatine prise, stocks, moyenne sur 7 jours),
 * sans donnée, et ses formulaires en bottom sheet.
 */

/** L'onglet en entier (sans la barre d'onglets) : groupe « Aujourd'hui », compléments, moyenne. */
@ScreenPreviews
@Composable
private fun NutritionScreenPreview() = ScreenPreview {
    NutritionScreen(insets = PreviewTabInsets)
}

/** Ce qu'un téléphone montre de l'onglet Nutrition. */
@PhonePreviews
@Composable
private fun NutritionScreenPhonePreview() = ScreenPreview {
    NutritionScreen(insets = PreviewTabInsets)
}

/** Première ouverture : rien de saisi aujourd'hui, aucun complément, pas de pesée ni de moyenne. */
@PhonePreviews
@Composable
private fun NutritionScreenEmptyPreview() = ScreenPreview(PreviewDataSet.Empty) {
    NutritionScreen(insets = PreviewTabInsets)
}

private val fixed150 = ProteinGoal(ProteinGoalMode.Fixed, perKg = 1.6, grams = 150)

/** Groupe « Aujourd'hui » : sous les objectifs (gris), puis dans l'objectif et objectifs atteints (vert). */
@StudioComponentPreviews
@Composable
private fun TodaySurfacePreview() = StudioComponentPreview {
    StateCaption("Sous l'objectif")
    TodaySurface(DailyDay(kcal = 1850, calorieGoal = 2800, ml = 750, waterGoal = 2000, grams = 112, proteinGoal = fixed150), onAdd = {}, onDetail = {})
    StateCaption("Dans l'objectif, eau et protéines atteintes")
    TodaySurface(
        DailyDay(kcal = 2700, calorieGoal = 2800, ml = 2250, waterGoal = 2000, grams = 131, latestWeight = 78.4),
        onAdd = {},
        onDetail = {},
    )
}

/** Au-dessus de l'objectif (rouge), protéines sans objectif (pas de pesée) ; puis rien de saisi. */
@StudioComponentPreviews
@Composable
private fun TodaySurfaceOverPreview() = StudioComponentPreview {
    StateCaption("Au-dessus, protéines sans objectif")
    TodaySurface(DailyDay(kcal = 3120, calorieGoal = 2800, ml = 0, waterGoal = 2000, grams = 40), onAdd = {}, onDetail = {})
    StateCaption("Rien de saisi")
    TodaySurface(DailyDay(kcal = 0, calorieGoal = 2800, ml = 0, waterGoal = 2000, proteinGoal = fixed150), onAdd = {}, onDetail = {})
}

private val caloriesDay = MeasureDay(NutritionMeasure.Calories, current = 1850, goal = 2800, average = 2410)
private val waterDay = MeasureDay(NutritionMeasure.Water, current = 750, goal = 2500, average = 2100)
private val proteinPerKgDay = MeasureDay(
    NutritionMeasure.Protein, current = 112, goal = 125, average = 138,
    proteinGoal = ProteinGoal.DEFAULT, latestWeight = 78.4, proteinPerKg = 1.6,
)

@Composable
private fun MeasureFormPreview(day: MeasureDay, start: MeasureSheetStart, step: MeasureStep? = null, text: String = "") =
    MeasureForm(day, start, onSave = { _, _ -> }, onSaveGoal = {}, onSaveProteinGoal = {}, onClose = {}, bottomInset = 0.dp, initialStep = step, initialText = text)

/** Formulaire Calories (bouton +) : « Ajouter » avec 350 kcal tapées, et le nouveau total. */
@StudioComponentPreviews
@Composable
private fun DailyEntryFormPreview() = StudioComponentPreview {
    MeasureFormPreview(caloriesDay, MeasureSheetStart.Add, text = "350")
}

/** Formulaire Protéines (bouton +) : « Ajouter » avec 30 g tapés. */
@StudioComponentPreviews
@Composable
private fun ProteinEntryFormPreview() = StudioComponentPreview {
    MeasureFormPreview(proteinPerKgDay, MeasureSheetStart.Add, text = "30")
}

/** Objectif d'eau, en litres, prérempli. */
@StudioComponentPreviews
@Composable
private fun DailyGoalFormPreview() = StudioComponentPreview {
    MeasureFormPreview(waterDay, MeasureSheetStart.Add, step = MeasureStep.Goal)
}

/** Objectif de protéines par kg : facteur 1,6, la dernière pesée et l'objectif qu'il donne. */
@StudioComponentPreviews
@Composable
private fun ProteinGoalFormPreview() = StudioComponentPreview {
    MeasureFormPreview(proteinPerKgDay, MeasureSheetStart.Add, step = MeasureStep.Goal)
}

/** Objectif de protéines sans aucune pesée : la valeur fixe seule. */
@StudioComponentPreviews
@Composable
private fun ProteinGoalNoWeightFormPreview() = StudioComponentPreview {
    MeasureFormPreview(
        MeasureDay(NutritionMeasure.Protein, current = 112, goal = 150, average = null, proteinGoal = fixed150),
        MeasureSheetStart.Add,
        step = MeasureStep.Goal,
    )
}

/** Détail des protéines (toucher la ligne) : aujourd'hui, objectif par kg, moyenne sur 7 jours. */
@StudioComponentPreviews
@Composable
private fun ProteinDetailPreview() = StudioComponentPreview {
    MeasureFormPreview(proteinPerKgDay, MeasureSheetStart.Detail)
}

/** Détail de l'eau, sans moyenne (aucun jour noté dans la semaine). */
@StudioComponentPreviews
@Composable
private fun WaterDetailPreview() = StudioComponentPreview {
    MeasureFormPreview(waterDay.copy(average = null), MeasureSheetStart.Detail)
}

/** « Nouveau complément » : nom saisi, unité g, un rappel à 08:00. */
@StudioComponentPreviews
@Composable
private fun NewSupplementFormPreview() = StudioComponentPreview {
    NewSupplementForm(onSave = { _, _, _, _, _ -> }, bottomInset = 0.dp, initialName = "Oméga-3", autoFocus = false)
}

/** Sheet d'un complément : deux rappels, stock suivi (38 jours). */
@StudioComponentPreviews
@Composable
private fun SupplementSheetPreview() = StudioComponentPreview {
    SupplementSheetContent(PreviewData.supplements.last(), SupplementActions.None, onClose = {}, bottomInset = 0.dp)
}

/** Sheet d'un complément au stock bas (créatine, 4 jours, en rouge). */
@StudioComponentPreviews
@Composable
private fun SupplementSheetLowStockPreview() = StudioComponentPreview {
    SupplementSheetContent(PreviewData.supplements.first(), SupplementActions.None, onClose = {}, bottomInset = 0.dp)
}

/** Sheet d'un complément sans dosage, rappels désactivés, aucun rappel, stock non suivi. */
@StudioComponentPreviews
@Composable
private fun SupplementSheetNoReminderPreview() = StudioComponentPreview {
    SupplementSheetContent(
        Supplement("whey", "Whey", "", "dose", reminderEnabled = false, reminders = emptyList()),
        SupplementActions.None,
        onClose = {},
        bottomInset = 0.dp,
    )
}

/** « Racheté » → « Ajouter du stock » (maquette `T2-Stock`) : il reste 4 jours, en rouge. */
@StudioComponentPreviews
@Composable
private fun RestockFormPreview() = StudioComponentPreview {
    SupplementSheetContent(PreviewData.supplements.first(), SupplementActions.None, onClose = {}, bottomInset = 0.dp, initialStep = SupplementStep.Restock)
}

/** Formulaire « Stock » d'un stock suivi : prérempli, jours restants, « Ne plus suivre le stock ». */
@StudioComponentPreviews
@Composable
private fun StockFormPreview() = StudioComponentPreview {
    SupplementSheetContent(PreviewData.supplements[1], SupplementActions.None, onClose = {}, bottomInset = 0.dp, initialStep = SupplementStep.Stock)
}
