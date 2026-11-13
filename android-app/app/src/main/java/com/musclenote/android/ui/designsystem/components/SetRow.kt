package com.musclenote.android.ui.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.ui.designsystem.theme.Studio

private val SetRowHeight = 56.dp
private val IndexColumn = 22.dp
private val PreviousColumn = 60.dp
private val CheckSize = 48.dp
private val ColumnGap = 8.dp
private val FieldHeight = 44.dp
private val FieldBleed = 8.dp
private val CaretWidth = 2.dp
private val CaretHeight = 26.dp
private val CheckBorder = 2.dp
private const val CheckPopFrom = 0.85f

/** État d'une ligne de série (DESIGN.md § 9.2). */
enum class SetRowState { Todo, Editing, Done, DoneRecord }

/** Champ d'une série : charge ou répétitions. */
enum class SetField { Weight, Reps }

/** Colonnes d'une ligne de série : n° 22 · « Préc. » 60 · kg · reps · case 48, espacées de 8. */
@Composable
private fun SetColumns(
    modifier: Modifier,
    index: @Composable () -> Unit,
    previous: @Composable () -> Unit,
    weight: @Composable RowScope.() -> Unit,
    reps: @Composable RowScope.() -> Unit,
    check: @Composable () -> Unit,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ColumnGap), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(IndexColumn)) { index() }
        Box(Modifier.width(PreviousColumn)) { previous() }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { weight() }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { reps() }
        Box(Modifier.width(CheckSize)) { check() }
    }
}

/** En-tête des colonnes de séries (# · PRÉC. · KG · REPS), `columnHeader` en capitales. */
@Composable
fun SetTableHeader(
    indexLabel: String,
    previousLabel: String,
    weightLabel: String,
    repsLabel: String,
    modifier: Modifier = Modifier,
) {
    @Composable
    fun Header(text: String) = Text(
        text.uppercaseForDisplay(),
        style = Studio.typography.columnHeader,
        color = Studio.colors.textSecondary,
        maxLines = 1,
    )
    SetColumns(
        modifier,
        index = { Header(indexLabel) },
        previous = { Header(previousLabel) },
        weight = { Header(weightLabel) },
        reps = { Header(repsLabel) },
        check = {},
    )
}

/**
 * Ligne de série — `SetRow` (DESIGN.md § 9.2) : 56 de haut, filet en bas. À faire (pré-remplie,
 * `textTertiary`), validée, validée avec record ; en saisie, le champ [editingField] est sur
 * `liveDim` avec un curseur — une série validée peut être en saisie ([SetRowState.Editing] =
 * série à faire en saisie sur la charge). « Préc. » se touche pour recopier la série précédente
 * ([onPreviousClick]). La case anime sa validation avec `Studio.motion.expressive`.
 */
@Composable
fun SetRow(
    index: String,
    previous: String,
    weight: String,
    reps: String,
    state: SetRowState,
    checkDescription: String,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
    editingField: SetField? = null,
    onFieldClick: ((SetField) -> Unit)? = null,
    onPreviousClick: (() -> Unit)? = null,
    previousClickLabel: String? = null,
    recordLabel: String? = null,
    showDivider: Boolean = true,
) {
    val colors = Studio.colors
    val done = state == SetRowState.Done || state == SetRowState.DoneRecord
    val activeField = editingField ?: SetField.Weight.takeIf { state == SetRowState.Editing }
    val editing = activeField != null
    SetColumns(
        modifier.height(SetRowHeight).bottomDivider(colors.separator, showDivider),
        index = {
            Text(
                index,
                style = Studio.typography.emphasis.copy(fontWeight = if (editing) FontWeight.Bold else FontWeight.Normal),
                color = if (editing) colors.textPrimary else colors.textSecondary,
            )
        },
        previous = {
            Text(
                previous,
                modifier = if (onPreviousClick != null) {
                    Modifier
                        .fillMaxWidth()
                        .height(SetRowHeight)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .clickable(onClickLabel = previousClickLabel, role = Role.Button, onClick = onPreviousClick)
                } else {
                    Modifier
                },
                style = Studio.typography.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        weight = {
            SetValue(weight, done, activeField == SetField.Weight, onFieldClick?.let { { it(SetField.Weight) } })
        },
        reps = {
            SetValue(reps, done, activeField == SetField.Reps, onFieldClick?.let { { it(SetField.Reps) } })
            if (state == SetRowState.DoneRecord && recordLabel != null) {
                Text(
                    recordLabel,
                    modifier = Modifier.padding(start = 6.dp),
                    style = Studio.typography.small.copy(fontWeight = FontWeight.Bold),
                    color = colors.up,
                    maxLines = 1,
                )
            }
        },
        check = { SetCheck(done, checkDescription, onCheck) },
    )
}

@Composable
private fun SetValue(value: String, done: Boolean, active: Boolean, onClick: (() -> Unit)?) {
    val colors = Studio.colors
    val click = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    if (active) {
        Row(
            Modifier
                .bleed(start = FieldBleed)
                .fillMaxWidth()
                .height(FieldHeight)
                .background(colors.liveDim, Studio.shapes.control)
                .then(click)
                .padding(start = FieldBleed),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, style = Studio.typography.numberMedium, color = colors.textPrimary, maxLines = 1)
            Box(
                Modifier
                    .padding(start = 2.dp)
                    .size(CaretWidth, CaretHeight)
                    .background(colors.liveText),
            )
        }
    } else {
        Text(
            value,
            modifier = if (onClick != null) {
                Modifier
                    .fillMaxWidth()
                    .height(SetRowHeight)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .then(click)
            } else {
                Modifier
            },
            style = Studio.typography.numberMedium.copy(fontWeight = if (done) FontWeight.Bold else FontWeight.SemiBold),
            color = if (done) colors.textPrimary else colors.textTertiary,
            maxLines = 1,
        )
    }
}

@Composable
private fun SetCheck(done: Boolean, description: String, onCheck: () -> Unit) {
    CheckBoxVisual(
        done,
        Modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Checkbox, onClick = onCheck)
            .semantics { contentDescription = description },
    )
}

/**
 * Case de série, sans action : 48, contour 2 `textTertiary` ; cochée, `inverse` avec ✓ et le
 * rebond `expressive`. Sert aussi de fin de ligne à cocher ([ListRowTrailing.Check]).
 */
@Composable
internal fun CheckBoxVisual(done: Boolean, modifier: Modifier = Modifier) {
    val colors = Studio.colors
    val scale = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(done) }
    val expressive = Studio.motion.expressive
    LaunchedEffect(done) {
        if (done && !previous) {
            scale.snapTo(CheckPopFrom)
            scale.animateTo(1f, expressive.spec())
        }
        previous = done
    }
    Box(
        Modifier
            .size(CheckSize)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .then(
                if (done) {
                    Modifier.background(colors.inverse, Studio.shapes.control)
                } else {
                    Modifier.border(CheckBorder, colors.textTertiary, Studio.shapes.control)
                },
            )
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (done) StudioIcon(R.drawable.ds_ic_check, contentDescription = null, tint = colors.onInverse)
    }
}

private val AddRowHeight = 48.dp
private val AddRowIconGap = 6.dp
private val AddRowPressBleed = 12.dp
private val AddRowPressRadius = 12.dp

/**
 * Ligne « + Série » — `AddSetRow` (DESIGN.md § 9.2) : sous la dernière série d'un bloc exercice,
 * toujours visible (action fréquente). Icône `add` en texte secondaire, libellé `actionSmall`,
 * alignés sur la colonne des numéros ; pressée : fond `surfaceRaised` qui déborde de 12.
 */
@Composable
fun AddSetRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed = source.pressed()
    Row(
        modifier
            .fillMaxWidth()
            .height(AddRowHeight)
            .pressedBackground(pressed, Studio.colors.surfaceRaised, AddRowPressBleed, AddRowPressRadius)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AddRowIconGap),
    ) {
        StudioIcon(R.drawable.ds_ic_add, contentDescription = null, size = IconSize.Row, tint = Studio.colors.textSecondary)
        Text(label, style = Studio.typography.actionSmall, color = Studio.colors.textPrimary, maxLines = 1)
    }
}
