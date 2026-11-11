package com.musclenote.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musclenote.android.R
import com.musclenote.android.data.catalog.ExerciseDefinition
import com.musclenote.android.data.model.MuscleGroup
import com.musclenote.android.data.util.normalizeSearch
import com.musclenote.android.ui.theme.MuscleNoteTheme

/**
 * Filtre des sélecteurs d'exercices (§ 2.7) : groupe **et** texte, le texte étant cherché dans
 * le nom **affiché** (langue courante pour les intégrés), sans casse ni accents (§ 10.6).
 */
@Composable
fun filterExercises(definitions: List<ExerciseDefinition>, query: String, group: MuscleGroup?): List<ExerciseDefinition> {
    val resources = LocalResources.current
    return remember(definitions, query, group, resources) {
        val wanted = normalizeSearch(query)
        definitions.filter { definition ->
            val name = definition.nameRes?.let { resources.getString(it) } ?: definition.customName.orEmpty()
            (group == null || definition.muscleGroup == group) && (wanted.isEmpty() || normalizeSearch(name).contains(wanted))
        }
    }
}

/** Champ de recherche (« Chercher un exercice... », ✕ pour vider) au-dessus des puces de groupes. */
@Composable
fun ExerciseSearchBar(
    search: String,
    onSearchChange: (String) -> Unit,
    group: MuscleGroup?,
    onGroupChange: (MuscleGroup?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.card)
                .border(1.dp, colors.border, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = search,
                onValueChange = onSearchChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = colors.foreground, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (search.isEmpty()) {
                            Text(stringResource(R.string.exercise_picker_search_placeholder), color = colors.mutedForeground, fontSize = 15.sp)
                        }
                        inner()
                    }
                },
            )
            if (search.isNotEmpty()) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable { onSearchChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(16.dp))
                }
            }
        }
        MuscleGroupChips(selected = group, onSelect = onGroupChange, allowAll = true)
    }
}

/**
 * Rangée défilante de puces de groupes (§ 2.7). En filtre ([allowAll]) : « Tous » en tête, et
 * toucher la puce active la désélectionne. En champ de formulaire : un groupe toujours choisi.
 */
@Composable
fun MuscleGroupChips(
    selected: MuscleGroup?,
    onSelect: (MuscleGroup?) -> Unit,
    modifier: Modifier = Modifier,
    allowAll: Boolean = false,
    chipBackground: Color = MuscleNoteTheme.colors.card,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (allowAll) {
            Chip(stringResource(R.string.common_all), active = selected == null, background = chipBackground) { onSelect(null) }
        }
        for (group in MuscleGroup.entries) {
            Chip(stringResource(group.labelRes), active = selected == group, background = chipBackground) {
                onSelect(if (allowAll && selected == group) null else group)
            }
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, background: Color, onClick: () -> Unit) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (active) colors.primary else background)
            .border(1.dp, if (active) colors.primary else colors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = if (active) colors.primaryForeground else colors.foreground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
