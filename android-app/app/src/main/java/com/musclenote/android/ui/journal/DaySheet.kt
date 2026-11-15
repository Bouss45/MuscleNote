package com.musclenote.android.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.R
import com.musclenote.android.data.journal.CalendarRules
import com.musclenote.android.data.journal.DayJournal
import com.musclenote.android.data.journal.SupplementIntake
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.DateFormats
import com.musclenote.android.ui.components.formatInteger
import com.musclenote.android.ui.components.formatNumber
import com.musclenote.android.ui.components.rememberDateFormats
import com.musclenote.android.ui.components.rememberToday
import com.musclenote.android.ui.components.workoutDisplayName
import com.musclenote.android.ui.designsystem.components.ButtonVariant
import com.musclenote.android.ui.designsystem.components.ListGroup
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.SectionHeader
import com.musclenote.android.ui.designsystem.components.SheetSurface
import com.musclenote.android.ui.designsystem.components.SheetTitle
import com.musclenote.android.ui.designsystem.components.StudioBottomSheet
import com.musclenote.android.ui.designsystem.components.StudioButton
import com.musclenote.android.ui.designsystem.theme.Studio
import com.musclenote.android.ui.nutrition.gramsText
import com.musclenote.android.ui.nutrition.waterGoalText
import com.musclenote.android.ui.nutrition.waterText
import java.time.LocalDate

/**
 * Sheet « Journée » (SPEC § 15.15.2, 🔄 D41) : ce qui a été noté le [day], en listes groupées —
 * séances (toucher → [onOpenSession], la sheet se ferme), poids, compléments pris, calories, eau et
 * protéines (🔄 D42) ;
 * les sections vides sont masquées, un jour sans rien n'a qu'une phrase. Lecture seule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DaySheet(day: LocalDate, onOpenSession: (workoutId: String) -> Unit, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val workouts by container.workouts.workouts.collectAsStateWithLifecycle()
    val bodyWeights by container.nutrition.bodyWeights.collectAsStateWithLifecycle()
    val tracker by container.nutrition.tracker.collectAsStateWithLifecycle()
    val supplements by container.nutrition.supplements.collectAsStateWithLifecycle()
    val taken by container.nutrition.taken.collectAsStateWithLifecycle()
    val zone = container.time.zone()
    val today = rememberToday()
    val dates = rememberDateFormats(zone)
    val journal = remember(day, today, workouts, bodyWeights, tracker, supplements, taken, zone) {
        CalendarRules.dayJournal(day, today, workouts, bodyWeights, tracker, supplements, taken, zone)
    }
    StudioBottomSheet(onDismissRequest = onDismiss, grouped = true) { inset ->
        DaySheetContent(
            journal = journal,
            title = dates.fullDate(day, today),
            dates = dates,
            onOpenSession = { id ->
                onDismiss()
                onOpenSession(id)
            },
            onClose = onDismiss,
            bottomInset = inset,
        )
    }
}

/** Contenu de la sheet « Journée » (aperçus compris). */
@Composable
internal fun DaySheetContent(
    journal: DayJournal,
    title: String,
    dates: DateFormats,
    onOpenSession: (workoutId: String) -> Unit,
    onClose: () -> Unit,
    bottomInset: Dp = 0.dp,
) {
    // Une journée chargée (plusieurs séances, compléments), surtout en grand texte, défile.
    SheetSurface(Modifier.verticalScroll(rememberScrollState()), grouped = true, spacing = Studio.spacing.l, bottomInset = bottomInset) {
        SheetTitle(title, if (journal.isEmpty) AnnotatedString(stringResource(R.string.journal_day_empty)) else null)
        if (journal.sessions.isNotEmpty()) {
            DaySection(stringResource(R.string.journal_day_sessions)) {
                journal.sessions.forEachIndexed { index, summary ->
                    SessionRow(
                        summary = summary,
                        dates = dates,
                        onOpen = { onOpenSession(summary.workout.id) },
                        onLongPress = null,
                        showDivider = index < journal.sessions.lastIndex,
                        timeOfDay = true,
                    )
                }
            }
        }
        journal.weight?.let { weight ->
            DaySection(stringResource(R.string.journal_day_weight)) {
                ListRow(
                    title = weight.fromWorkout?.let { stringResource(R.string.journal_day_weight_from_session, workoutDisplayName(it)) }
                        ?: stringResource(R.string.journal_day_weigh_in),
                    trailing = ListRowTrailing.Value(stringResource(R.string.common_kg_value, formatNumber(weight.kg))),
                    showDivider = false,
                )
            }
        }
        if (journal.supplements.isNotEmpty()) {
            DaySection(stringResource(R.string.journal_day_supplements)) {
                journal.supplements.forEachIndexed { index, intake ->
                    ListRow(
                        title = intake.supplement.name,
                        subtitle = intakeSubtitle(intake),
                        showDivider = index < journal.supplements.lastIndex,
                    )
                }
            }
        }
        // 🔄 D42 : la section « Nutrition » gagne les protéines du jour (§ 15.16.7).
        if (journal.calories > 0 || journal.waterMl > 0 || journal.proteinGrams > 0) {
            DaySection(stringResource(R.string.journal_day_nutrition)) {
                if (journal.calories > 0) {
                    ListRow(
                        title = stringResource(R.string.progress_tab_calories),
                        subtitle = stringResource(R.string.nutrition_goal_only, formatInteger(journal.calorieGoal.toDouble())),
                        trailing = ListRowTrailing.Value(stringResource(R.string.common_kcal_value, formatInteger(journal.calories.toDouble()))),
                        showDivider = journal.waterMl > 0 || journal.proteinGrams > 0,
                    )
                }
                if (journal.waterMl > 0) {
                    ListRow(
                        title = stringResource(R.string.progress_tab_water),
                        subtitle = stringResource(R.string.journal_day_goal, waterGoalText(journal.waterGoal)),
                        trailing = ListRowTrailing.Value(waterText(journal.waterMl)),
                        showDivider = journal.proteinGrams > 0,
                    )
                }
                if (journal.proteinGrams > 0) {
                    // Sans objectif : celui des protéines n'est pas daté (§ 15.16.1).
                    ListRow(
                        title = stringResource(R.string.nutrition_protein),
                        trailing = ListRowTrailing.Value(gramsText(journal.proteinGrams)),
                        showDivider = false,
                    )
                }
            }
        }
        StudioButton(stringResource(R.string.common_close), onClose, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
    }
}

/** Une section de la sheet : étiquette, 8, groupe. */
@Composable
private fun DaySection(label: String, rows: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Studio.spacing.xs)) {
        SectionHeader(label, grouped = true)
        ListGroup { rows() }
    }
}

/** « 5 g par prise », puis « 2 doses » à partir de deux doses ; `null` sans rien à dire. */
@Composable
private fun intakeSubtitle(intake: SupplementIntake): String? {
    val supplement = intake.supplement
    val dosage = supplement.dosage.takeIf { it.isNotBlank() }?.let { stringResource(R.string.nutrition_per_intake, it, supplement.unit) }
    val doses = if (intake.doses >= 2) pluralStringResource(R.plurals.journal_day_doses, intake.doses, intake.doses) else null
    return listOfNotNull(dosage, doses).joinToString(" · ").ifEmpty { null }
}
