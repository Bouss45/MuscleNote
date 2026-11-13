package com.musclenote.android.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.ui.designsystem.components.AlwaysPressedInteractionSource
import com.musclenote.android.ui.designsystem.components.ChipTone
import com.musclenote.android.ui.designsystem.components.FilterChip
import com.musclenote.android.ui.designsystem.components.HeroNumber
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.LiveHeroNumber
import com.musclenote.android.ui.designsystem.components.PeriodSelector
import com.musclenote.android.ui.designsystem.components.SectionHeader
import com.musclenote.android.ui.designsystem.components.SegmentedControl
import com.musclenote.android.ui.designsystem.components.Stat
import com.musclenote.android.ui.designsystem.components.StatRow
import com.musclenote.android.ui.designsystem.components.StudioChip
import com.musclenote.android.ui.designsystem.components.Trend
import com.musclenote.android.ui.designsystem.components.TrendCurve

/*
 * Planche « Composants 1 · Données et listes » (DESIGN.md § 9.1) : un aperçu par composant, avec
 * ses états, et les échantillons réutilisés par la planche complète (BoardPreviews.kt).
 */

/** 1RM estimé de février à novembre (points de la courbe de la maquette). */
val SampleCurve = curveFromSvg(
    xs = listOf(4.0f, 23.1f, 42.1f, 61.2f, 80.2f, 99.3f, 118.3f, 137.4f, 156.4f, 175.5f, 194.5f, 213.6f, 261.2f, 280.3f, 299.3f, 318.4f, 337.4f, 346.9f, 366.0f),
    ys = listOf(136.2f, 117.0f, 103.2f, 97.8f, 97.8f, 93.6f, 79.2f, 79.2f, 75.0f, 60.0f, 60.0f, 79.2f, 97.8f, 60.0f, 56.4f, 40.8f, 40.8f, 37.8f, 22.2f),
)

@Composable
fun HeroNumberSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StateCaption("Normal")
        HeroNumber("101,3", label = "1RM estimé", unit = "kg", delta = "+19,0 kg", trend = Trend.Up, deltaContext = "depuis le 12 févr.")
        StateCaption("En direct")
        LiveHeroNumber("17:48", label = "En cours · Push")
        StateCaption("Au toucher de la courbe")
        HeroNumber("95,0", label = "jeu. 18 juin", unit = "kg", delta = "+12,7 kg", trend = Trend.Up, deltaContext = "depuis le 12 févr.")
    }
}

@Composable
fun SectionHeaderSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Récentes", linkLabel = "Tout voir", onLinkClick = {})
        SectionHeader("Cette semaine", subtitle = "Encore 1 séance pour valider")
    }
}

@Composable
fun StatRowSample() {
    StatRow(listOf(Stat("2/3", "séances"), Stat("4 sem.", "série"), Stat("9 sem.", "record")))
}

@Composable
fun ListRowSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StateCaption("Simple")
        ListRow("Mensurations", leadingIcon = R.drawable.ds_ic_straighten, trailing = ListRowTrailing.Chevron, onClick = {})
        StateCaption("Avec valeur")
        ListRow("Pull", subtitle = "mer. 11 nov. · 1 h 12", trailing = ListRowTrailing.Value("6 840 kg"))
        StateCaption("Avec variation colorée — hausse, baisse")
        Column {
            ListRow("Tirage vertical", subtitle = "14 séances", trailing = ListRowTrailing.Value("71 kg", "+18,0 kg", Trend.Up))
            ListRow("Tirage horizontal", subtitle = "11 séances", trailing = ListRowTrailing.Value("72 kg", "−5,0 kg", Trend.Down))
        }
        StateCaption("Avec puces")
        ListRow("jeu. 5 nov.", tags = listOf("80 × 8", "80 × 8", "80 × 8"), trailing = ListRowTrailing.Value("101,3", "Record", Trend.Up))
        StateCaption("Pressée")
        ListRow(
            "Pull",
            subtitle = "mer. 11 nov. · 1 h 12",
            trailing = ListRowTrailing.Value("6 840 kg"),
            onClick = {},
            interactionSource = AlwaysPressedInteractionSource(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipSamples() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StudioChip("3 × 8 · 80 kg", icon = R.drawable.ds_ic_history)
        StudioChip("+2,5 kg", tone = ChipTone.Up)
        StudioChip("−12 reps", tone = ChipTone.Down)
        StudioChip("En cours", tone = ChipTone.Live)
        StudioChip("Record", tone = ChipTone.Record)
        FilterChip("Pecs", selected = true, onClick = {})
        FilterChip("Dos", selected = false, onClick = {})
    }
}

@Composable
fun CurveSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StateCaption("Au repos")
        TrendCurve(SampleCurve, "1RM estimé de février à novembre", startLabel = "févr.", endLabel = "nov.")
        StateCaption("Au toucher — le héros affiche la valeur du point")
        TrendCurve(SampleCurve, "Courbe touchée au 18 juin, 95,0 kg", selectedIndex = 9, selectedLabel = "18 juin")
    }
}

@Composable
fun PeriodSelectorSample() {
    PeriodSelector(listOf("1M", "3M", "6M", "1A", "Tout"), selectedIndex = 4, onSelect = {})
}

@Composable
fun SegmentedSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SegmentedControl(listOf("1RM estimé", "Charge max", "Volume"), selectedIndex = 0, onSelect = {})
        SegmentedControl(listOf("Kg · série 3", "Reps"), selectedIndex = 0, onSelect = {})
    }
}

@Composable
fun SectionAssemblySample() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader("Records", linkLabel = "Tout voir", onLinkClick = {})
        Column {
            ListRow("Développé couché", subtitle = "1RM estimé · 5 nov.", trailing = ListRowTrailing.Value("101,3 kg", "+2,6 kg", Trend.Up))
            ListRow("Tirage vertical", subtitle = "Charge max · 15 juil.", trailing = ListRowTrailing.Value("71 kg", "+2,5 kg", Trend.Up), showDivider = false)
        }
    }
}

@StudioComponentPreviews
@Composable
private fun HeroNumberPreview() = StudioComponentPreview { HeroNumberSamples() }

@StudioComponentPreviews
@Composable
private fun SectionHeaderPreview() = StudioComponentPreview { SectionHeaderSamples() }

@StudioComponentPreviews
@Composable
private fun StatRowPreview() = StudioComponentPreview { StatRowSample() }

@StudioComponentPreviews
@Composable
private fun ListRowPreview() = StudioComponentPreview { ListRowSamples() }

@StudioComponentPreviews
@Composable
private fun ChipPreview() = StudioComponentPreview { ChipSamples() }

@StudioComponentPreviews
@Composable
private fun TrendCurvePreview() = StudioComponentPreview { CurveSamples() }

@StudioComponentPreviews
@Composable
private fun PeriodSelectorPreview() = StudioComponentPreview { PeriodSelectorSample() }

@StudioComponentPreviews
@Composable
private fun SegmentedControlPreview() = StudioComponentPreview { SegmentedSamples() }

@StudioComponentPreviews
@Composable
private fun SectionAssemblyPreview() = StudioComponentPreview { SectionAssemblySample() }
