package com.musclenote.android.ui.nutrition

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musclenote.android.R
import com.musclenote.android.data.nutrition.CalorieTone
import com.musclenote.android.data.nutrition.NutritionRules
import com.musclenote.android.data.util.parseDecimal
import com.musclenote.android.data.util.parseIntegerOrNull
import com.musclenote.android.ui.components.formatInteger
import com.musclenote.android.ui.components.formatOneDecimal
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.theme.MuscleNoteTheme
import kotlin.math.roundToInt

private enum class CalorieMode { Add, Set }

private const val INPUT_MAX_LENGTH = 6

/**
 * Carte Calories du jour (§ 2.11) : kcal du jour sur l'objectif, barre plafonnée à 100 %, couleur
 * selon le ratio non plafonné (🔄 D15). L'engrenage ouvre l'éditeur d'objectif (appliqué à partir
 * d'aujourd'hui) ; sinon « + Ajouter » / « = Définir » et ⟲.
 */
@Composable
fun CaloriesCard(
    kcal: Int,
    goal: Int,
    onAdd: (Int) -> Unit,
    onSet: (Int) -> Unit,
    onReset: () -> Unit,
    onGoalChange: (Int) -> Unit,
) {
    val colors = MuscleNoteTheme.colors
    val haptics = rememberHaptics()
    var editingGoal by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(CalorieMode.Add) }
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val tint = when (NutritionRules.calorieTone(kcal, goal)) {
        CalorieTone.Over -> colors.destructive
        CalorieTone.OnTarget -> colors.success
        CalorieTone.Below -> colors.primary
    }

    fun submit() {
        // Champ vide, illisible ou négatif → rien.
        val value = parseIntegerOrNull(input.text)?.takeIf { it >= 0 } ?: return
        haptics.light()
        if (mode == CalorieMode.Add) onAdd(value) else onSet(value)
        input = TextFieldValue()
    }

    TrackCard {
        TrackHeader("🔥", stringResource(R.string.progress_tab_calories), onGear = { editingGoal = !editingGoal })
        TrackValue(formatInteger(kcal.toDouble()), unit = null, goal = "/ ${formatInteger(goal.toDouble())} kcal", tint = tint)
        ProgressBar(if (goal > 0) kcal.toFloat() / goal else 0f, tint)
        if (editingGoal) {
            GoalEditor(
                initial = goal.toString(),
                keyboardType = KeyboardType.Number,
                placeholder = stringResource(R.string.nutrition_cal_goal_placeholder),
                onSave = { text ->
                    // Valeur ≤ 0 ou illisible ignorée ; l'éditeur se ferme dans tous les cas.
                    parseIntegerOrNull(text)?.takeIf { it > 0 }?.let(onGoalChange)
                    editingGoal = false
                },
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (option in CalorieMode.entries) {
                    val active = option == mode
                    val shape = RoundedCornerShape(8.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(if (active) colors.primary else colors.muted)
                            .border(1.dp, if (active) colors.primary else colors.border, shape)
                            .clickable { mode = option }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(if (option == CalorieMode.Add) R.string.nutrition_cal_add else R.string.nutrition_cal_set),
                            color = if (active) colors.primaryForeground else colors.mutedForeground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                AmountInput(input, { input = it }, stringResource(R.string.nutrition_cal_placeholder), KeyboardType.Number, ::submit, Modifier.weight(1f))
                SquareButton(if (mode == CalorieMode.Add) R.drawable.ic_add else R.drawable.ic_check, colors.primary, primaryTint = true, onClick = ::submit)
                SquareButton(
                    R.drawable.ic_restart_alt,
                    colors.mutedForeground,
                    onClick = {
                        haptics.light()
                        onReset()
                    },
                )
            }
        }
    }
}

/**
 * Carte Eau du jour (§ 2.11) : `750 ml`, ou `1,5 L` dès 1000 ml, sur l'objectif en litres ; verte
 * une fois l'objectif atteint. Objectif saisi en litres, stocké en ml ; ajout en ml et ⟲.
 */
@Composable
fun WaterCard(ml: Int, goalMl: Int, onAdd: (Int) -> Unit, onReset: () -> Unit, onGoalChange: (Int) -> Unit) {
    val colors = MuscleNoteTheme.colors
    val haptics = rememberHaptics()
    var editingGoal by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val tint = if (ml >= goalMl) colors.success else colors.primary

    fun submit() {
        val value = parseIntegerOrNull(input.text)?.takeIf { it > 0 } ?: return
        haptics.light()
        onAdd(value)
        input = TextFieldValue()
    }

    TrackCard {
        TrackHeader("💧", stringResource(R.string.progress_tab_water), onGear = { editingGoal = !editingGoal })
        if (ml < 1000) {
            TrackValue(formatInteger(ml.toDouble()), unit = "ml", goal = "/ ${formatOneDecimal(goalMl / 1000.0)} L", tint = tint)
        } else {
            TrackValue(formatOneDecimal(ml / 1000.0), unit = "L", goal = "/ ${formatOneDecimal(goalMl / 1000.0)} L", tint = tint)
        }
        ProgressBar(if (goalMl > 0) ml.toFloat() / goalMl else 0f, tint)
        if (editingGoal) {
            GoalEditor(
                initial = formatOneDecimal(goalMl / 1000.0),
                keyboardType = KeyboardType.Decimal,
                placeholder = stringResource(R.string.nutrition_water_goal_placeholder),
                onSave = { text ->
                    val liters = parseDecimal(text)
                    if (liters > 0) onGoalChange((liters * 1000).roundToInt())
                    editingGoal = false
                },
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                AmountInput(input, { input = it }, stringResource(R.string.nutrition_water_placeholder), KeyboardType.Number, ::submit, Modifier.weight(1f))
                SquareButton(R.drawable.ic_add, colors.primary, primaryTint = true, onClick = ::submit)
                SquareButton(
                    R.drawable.ic_restart_alt,
                    colors.mutedForeground,
                    enabled = ml > 0,
                    onClick = {
                        haptics.light()
                        onReset()
                    },
                )
            }
        }
    }
}

@Composable
private fun TrackCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Émoji, titre et engrenage de l'objectif. */
@Composable
private fun TrackHeader(emoji: String, title: String, onGear: () -> Unit) {
    val colors = MuscleNoteTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(emoji, fontSize = 18.sp)
        Text(title, color = colors.foreground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(onClick = onGear),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.chart_goal),
                tint = colors.mutedForeground,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** Grand nombre du jour (24 sp, unité 18 sp) et « / objectif » (11 sp). */
@Composable
private fun TrackValue(value: String, unit: String?, goal: String, tint: Color) {
    val colors = MuscleNoteTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.alignByBaseline()) {
            Text(value, color = tint, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alignByBaseline())
            if (unit != null) {
                Text(" $unit", color = tint, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alignByBaseline())
            }
        }
        Text(goal, color = colors.mutedForeground, fontSize = 11.sp, modifier = Modifier.alignByBaseline())
    }
}

/** Barre de 6 dp, remplissage plafonné à 100 %. */
@Composable
private fun ProgressBar(fraction: Float, tint: Color) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(MuscleNoteTheme.colors.progressTrack),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(shape)
                .background(tint),
        )
    }
}

/** Éditeur d'objectif : champ prérempli, focus automatique, tout sélectionné ; ✓ ou « OK » du clavier. */
@Composable
private fun GoalEditor(initial: String, keyboardType: KeyboardType, placeholder: String, onSave: (String) -> Unit) {
    val colors = MuscleNoteTheme.colors
    var value by remember { mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length))) }
    val focusRequester = remember { FocusRequester() }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        AmountInput(value, { value = it }, placeholder, keyboardType, { onSave(value.text) }, Modifier.weight(1f).focusRequester(focusRequester))
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.primary)
                .clickable { onSave(value.text) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = stringResource(R.string.common_save), tint = colors.primaryForeground, modifier = Modifier.size(15.dp))
        }
    }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
}

/** Champ numérique d'une carte ; « OK » du clavier valide ([onDone]). Tout sélectionné au focus. */
@Composable
private fun AmountInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MuscleNoteTheme.colors
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) onValueChange(value.copy(selection = TextRange(0, value.text.length)))
    }
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value = value,
        onValueChange = { if (it.text.length <= INPUT_MAX_LENGTH) onValueChange(it) },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(color = colors.foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.input)
                    .border(1.dp, colors.border, shape)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                if (value.text.isEmpty()) Text(placeholder, color = colors.mutedForeground, fontSize = 14.sp)
                inner()
            }
        },
    )
}

/** Bouton carré 34 dp : action principale (fond `primary` à 13 %) ou secondaire (`muted`). */
@Composable
private fun SquareButton(
    @DrawableRes icon: Int,
    tint: Color,
    onClick: () -> Unit,
    primaryTint: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(34.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(shape)
            .background(if (primaryTint) colors.primary.copy(alpha = 0.13f) else colors.muted)
            .border(1.dp, if (primaryTint) colors.primary.copy(alpha = 0.27f) else colors.border, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}
