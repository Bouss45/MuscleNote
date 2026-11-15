package com.musclenote.android.ui.workout

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import com.musclenote.android.R
import com.musclenote.android.data.util.parseDecimal
import com.musclenote.android.data.workout.SetEntryField
import com.musclenote.android.data.workout.SetEntryRules
import com.musclenote.android.ui.components.MINUS_SIGN
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.designsystem.components.FilterChip
import com.musclenote.android.ui.designsystem.components.FormHeroField
import com.musclenote.android.ui.designsystem.components.NumericKeypad
import com.musclenote.android.ui.designsystem.templates.FormSheetTemplate
import com.musclenote.android.ui.designsystem.theme.Studio

/** Raccourcis de poids de corps (§ 2.3). */
private val QUICK_WEIGHTS = listOf(55, 60, 65, 70, 75, 80, 85, 90, 95, 100)

/** Pas des touches − / + du poids de corps, comme la pesée (§ 15.9.3). */
private const val WEIGHT_STEP = 0.1

/**
 * Formulaire « Poids de corps » au pavé numérique (§ 15.11.1, § 15.11.3) : champ héros en kg,
 * raccourcis 55 … 100 ([quickWeights], au démarrage d'une séance), lignes d'information ([info]),
 * pas de ±0,1 et touche principale [actionLabel]. Le texte tapé et son état « neuf » (la prochaine
 * touche remplace la valeur) sont gardés par l'appelant ([onEdit]).
 */
@Composable
internal fun BodyWeightForm(
    title: String,
    help: AnnotatedString,
    text: String,
    fresh: Boolean,
    onEdit: (text: String, fresh: Boolean) -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
    bottomInset: Dp,
    quickWeights: Boolean = false,
    info: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val locale = appLocale()
    val haptics = rememberHaptics()
    val field = SetEntryField.Weight
    val separator = SetEntryRules.decimalSeparator(locale)
    val stepLabel = SetEntryRules.format(WEIGHT_STEP, field, locale)

    fun step(direction: Int) {
        SetEntryRules.step(parseDecimal(text), direction, field, locale, step = WEIGHT_STEP)?.let {
            haptics.light()
            onEdit(SetEntryRules.format(it, field, locale), true)
        }
    }

    FormSheetTemplate(
        title = title,
        help = help,
        bottomInset = bottomInset,
        field = { FormHeroField(text, unit = "kg") },
        quickEntries = if (quickWeights) {
            {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xs),
                ) {
                    val current = parseDecimal(text)
                    for (quick in QUICK_WEIGHTS) {
                        FilterChip(
                            quick.toString(),
                            selected = current == quick.toDouble(),
                            onClick = {
                                haptics.light()
                                onEdit(SetEntryRules.format(quick.toDouble(), field, locale), true)
                            },
                        )
                    }
                }
            }
        } else {
            null
        },
        info = info,
    ) {
        NumericKeypad(
            onDigit = { key -> SetEntryRules.type(text, fresh, key, field, locale)?.let { onEdit(it, false) } },
            onDelete = { onEdit(SetEntryRules.backspace(text, fresh), false) },
            stepDownLabel = "$MINUS_SIGN$stepLabel",
            onStepDown = { step(-1) },
            stepUpLabel = "+$stepLabel",
            onStepUp = { step(+1) },
            actionLabel = actionLabel,
            onAction = onAction,
            actionIcon = R.drawable.ds_ic_check,
            decimalSeparator = separator,
        )
    }
}
