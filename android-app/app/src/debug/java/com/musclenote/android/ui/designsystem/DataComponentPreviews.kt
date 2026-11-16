package com.musclenote.android.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.data.catalog.ExerciseCatalog
import com.musclenote.android.data.journal.CalendarRules
import com.musclenote.android.data.model.BodyView
import com.musclenote.android.data.model.Muscle
import com.musclenote.android.ui.designsystem.components.BodyMap
import com.musclenote.android.ui.designsystem.components.BodyMapSize
import com.musclenote.android.ui.designsystem.components.MuscleLegend
import com.musclenote.android.ui.designsystem.theme.Studio
import com.musclenote.android.ui.designsystem.components.AlwaysPressedInteractionSource
import com.musclenote.android.ui.designsystem.components.CalendarDay
import com.musclenote.android.ui.designsystem.components.CalendarMonth
import com.musclenote.android.ui.designsystem.components.ChipTone
import com.musclenote.android.ui.designsystem.components.CurveHeightDense
import com.musclenote.android.ui.designsystem.components.CurvePoint
import com.musclenote.android.ui.designsystem.components.FilterChip
import com.musclenote.android.ui.designsystem.components.HeroNumber
import com.musclenote.android.ui.designsystem.components.ListGroup
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.LiveHeroNumber
import com.musclenote.android.ui.designsystem.components.MiniTrendCurve
import com.musclenote.android.ui.designsystem.components.MonthCalendar
import com.musclenote.android.ui.designsystem.components.PeriodSelector
import com.musclenote.android.ui.designsystem.components.SectionHeader
import com.musclenote.android.ui.designsystem.components.SegmentedControl
import com.musclenote.android.ui.designsystem.components.Stat
import com.musclenote.android.ui.designsystem.components.StatRow
import com.musclenote.android.ui.designsystem.components.StudioChip
import com.musclenote.android.ui.designsystem.components.Trend
import com.musclenote.android.ui.designsystem.components.TrendCurve
import java.time.LocalDate
import java.time.YearMonth

/*
 * Planche « Composants 1 · Données et listes groupées » (DESIGN.md § 9.1) : un aperçu par
 * composant, avec ses états, et les échantillons réutilisés par la planche complète
 * (BoardPreviews.kt).
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

/** Étiquettes de groupe (v2) : capitales, lien ou mention à droite en casse normale. */
@Composable
fun GroupLabelSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Récentes", linkLabel = "Tout voir", onLinkClick = {}, grouped = true)
        SectionHeader("Cette semaine", trailingText = "3 séances", grouped = true)
    }
}

/** Rangée de stats dans un groupe (v2), sous son étiquette. */
@Composable
fun GroupedStatRowSample() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Cette semaine", grouped = true)
        ListGroup {
            StatRow(listOf(Stat("3/3", "séances"), Stat("5 sem.", "série"), Stat("9 sem.", "record")), Modifier.padding(16.dp))
        }
    }
}

/** Groupes de liste (v2) : simple, valeur, variation ; case, ligne pressée. */
@Composable
fun ListGroupSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StateCaption("Simple, valeur, variation")
        ListGroup {
            ListRow("Mensurations", leadingIcon = R.drawable.ds_ic_straighten, trailing = ListRowTrailing.Chevron, onClick = {})
            ListRow("Pull", subtitle = "mer. 11 nov. · 1 h 12", trailing = ListRowTrailing.Value("6 840 kg"))
            ListRow("Tirage vertical", subtitle = "14 séances", trailing = ListRowTrailing.Value("71 kg", "+18,0 kg", Trend.Up))
            ListRow("Tirage horizontal", subtitle = "11 séances", trailing = ListRowTrailing.Value("72 kg", "−5,0 kg", Trend.Down), showDivider = false)
        }
        StateCaption("Case, ligne pressée")
        ListGroup {
            ListRow(
                "Créatine",
                subtitle = "5 g · rappel à 08:00",
                trailing = ListRowTrailing.Check(checked = true, onToggle = {}, toggleLabel = "Pris aujourd'hui"),
                onClick = {},
            )
            ListRow(
                "Legs",
                subtitle = "lun. 9 nov. · 1 h 31",
                trailing = ListRowTrailing.Value("11 420 kg"),
                onClick = {},
                interactionSource = AlwaysPressedInteractionSource(),
            )
            ListRow("Langue", trailing = ListRowTrailing.Value("Système"), showDivider = false, onClick = {})
        }
    }
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
private fun GroupLabelPreview() = StudioComponentPreview { GroupLabelSamples() }

@StudioComponentPreviews
@Composable
private fun GroupedStatRowPreview() = StudioComponentPreview { GroupedStatRowSample() }

@StudioComponentPreviews
@Composable
private fun ListGroupPreview() = StudioComponentPreview { ListGroupSamples() }

@StudioComponentPreviews
@Composable
private fun ListRowPreview() = StudioComponentPreview { ListRowSamples() }

@StudioComponentPreviews
@Composable
private fun ChipPreview() = StudioComponentPreview { ChipSamples() }

/** Puce inverse (🔄 D43) : les muscles choisis dans le filtre « Par muscle », à côté d'une puce de filtre. */
@StudioComponentPreviews
@Composable
private fun ChipInversePreview() = StudioComponentPreview {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StudioChip("Pecs", tone = ChipTone.Inverse, onClick = {})
        StudioChip("Triceps", tone = ChipTone.Inverse, onClick = {})
        FilterChip("Pecs", selected = true, onClick = {})
    }
}

/** Hors maquette (SPEC § 15.13.3) : puces de filtre à choix multiple, jours du rappel de pesée (lues comme cases). */
@OptIn(ExperimentalLayoutApi::class)
@StudioComponentPreviews
@Composable
private fun FilterChipMultiPreview() = StudioComponentPreview {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("lun." to true, "mar." to false, "mer." to true, "jeu." to false, "ven." to true, "sam." to false, "dim." to false)
            .forEach { (day, selected) -> FilterChip(day, selected = selected, onClick = {}, multiSelect = true) }
    }
}

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

/** Courbe en baisse : la même série, à l'envers. */
private val SampleFalling = SampleCurve.map { it.copy(y = -it.y) }

/** Hors maquette (SPEC § 15.9) : courbe en hausse, en baisse, période vide. */
@StudioComponentPreviews
@Composable
private fun TrendCurveVariantsPreview() = StudioComponentPreview {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StateCaption("En hausse")
        TrendCurve(SampleCurve, "En hausse", startLabel = "12 févr.", endLabel = "5 nov.")
        StateCaption("En baisse")
        TrendCurve(SampleFalling, "En baisse", startLabel = "12 févr.", endLabel = "5 nov.")
        StateCaption("Période vide")
        TrendCurve(emptyList(), "Aucune séance", emptyText = "Choisis une période plus longue.")
    }
}

/** Hors maquette (SPEC § 15.9) : un seul point, ligne d'objectif (section « Calories et eau »). */
@StudioComponentPreviews
@Composable
private fun TrendCurveGoalPreview() = StudioComponentPreview {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StateCaption("Un seul point")
        TrendCurve(SampleCurve.takeLast(1), "Une séance", endLabel = "5 nov.")
        StateCaption("Ligne d'objectif (calories, 150)")
        TrendCurve(
            listOf(2310f, 2580f, 2450f, 2720f, 2390f, 2640f, 1850f).mapIndexed { i, kcal -> CurvePoint(i.toFloat(), kcal) },
            "Calories",
            height = CurveHeightDense,
            startLabel = "6 nov.",
            endLabel = "12 nov.",
            goal = 2600f,
            goalLabel = "Objectif",
        )
    }
}

/** Hors maquette (SPEC § 15.9.2) : lignes d'exercice avec mini-courbe, hausse, baisse, identique, une séance. */
@StudioComponentPreviews
@Composable
private fun ListRowCurvePreview() = StudioComponentPreview {
    Column {
        StateCaption("Mini-courbe seule")
        MiniTrendCurve(SampleCurve)
        ListRow(
            "Développé couché",
            subtitle = "19 séances · record 101,3 kg",
            trailing = ListRowTrailing.CurveValue(SampleCurve, "101,3 kg", "+19,0 kg", Trend.Up),
            onClick = {},
        )
        ListRow(
            "Tirage horizontal",
            subtitle = "11 séances · record 77 kg",
            trailing = ListRowTrailing.CurveValue(SampleFalling, "72 kg", "\u22125,0 kg", Trend.Down),
            onClick = {},
        )
        ListRow(
            "Élévations latérales",
            subtitle = "6 séances · record 10 kg",
            trailing = ListRowTrailing.CurveValue(listOf(CurvePoint(0f, 10f), CurvePoint(1f, 10f)), "10 kg", "identique", Trend.Neutral),
            onClick = {},
        )
        ListRow(
            "Dips",
            subtitle = "1 séance · record 12 reps",
            trailing = ListRowTrailing.CurveValue(emptyList(), "12 reps"),
            onClick = {},
            showDivider = false,
        )
    }
}

/**
 * Hors maquette : ligne à cocher (échauffement, § 15.7.1), case séparée (complément, § 15.10.1), valeur
 * avant la case et valeur suivie d'un bouton + plein (🔄 D42, § 15.16).
 */
@StudioComponentPreviews
@Composable
private fun ListRowCheckPreview() = StudioComponentPreview {
    ListRow("Cardio léger", subtitle = "5-10 min vélo ou rameur", trailing = ListRowTrailing.Check(true), onClick = {})
    ListRow("Rotation des épaules", subtitle = "15 reps chaque côté", trailing = ListRowTrailing.Check(false), onClick = {}, showDivider = false)
    StateCaption("Case séparée : la case bascule, la ligne ouvre (complément)")
    ListRow(
        "Créatine",
        subtitle = "5 g par prise · Rappel à 08:00",
        trailing = ListRowTrailing.Check(true, onToggle = {}, toggleLabel = "Pris aujourd'hui"),
        onClick = {},
        showDivider = false,
    )
    StateCaption("Valeur avant la case : jours de stock, stock bas en rouge (§ 15.16.4)")
    ListRow(
        "Protéine whey",
        subtitle = "30 g par prise · 2 doses par jour",
        trailing = ListRowTrailing.Check(true, onToggle = {}, toggleLabel = "Pris aujourd'hui", value = "12 jours", valueDescription = "Stock : 12 jours"),
        onClick = {},
    )
    ListRow(
        "Créatine",
        subtitle = "5 g par prise · Rappel à 08:00",
        trailing = ListRowTrailing.Check(
            false, onToggle = {}, toggleLabel = "Pris aujourd'hui",
            value = "4 jours", valueTrend = Trend.Down, valueDescription = "Stock bas : 4 jours",
        ),
        onClick = {},
        showDivider = false,
    )
    StateCaption("Valeur et bouton + plein : eau, protéines (§ 15.16.2)")
    ListGroup {
        ListRow(
            "Eau",
            subtitle = "objectif 2,5 L",
            trailing = ListRowTrailing.ValueAction("1,5 L", R.drawable.ds_ic_add, "Ajouter de l'eau", {}, valueTrend = Trend.Neutral),
            onClick = {},
        )
        ListRow(
            "Protéines",
            subtitle = "objectif 150 g atteint",
            trailing = ListRowTrailing.ValueAction("152 g", R.drawable.ds_ic_add, "Ajouter des protéines", {}, valueTrend = Trend.Up),
            onClick = {},
            showDivider = false,
        )
    }
    StateCaption("Puces de suggestion à toucher : hausse, même charge")
    StudioChip("+2,5 kg", tone = ChipTone.Up, onClick = {})
    StudioChip("Même charge", onClick = {})
}

/**
 * Un mois d'exemple du calendrier : jours à séance [sessionDays], aujourd'hui [today] (les jours
 * suivants à venir), résumé [summary].
 */
fun sampleCalendarMonth(month: YearMonth, title: String, sessionDays: Set<Int>, today: LocalDate, summary: String): CalendarMonth =
    CalendarMonth(
        title = title,
        cells = CalendarRules.monthGrid(month).cells.map { date ->
            date?.let {
                CalendarDay(
                    date = it,
                    label = "${it.dayOfMonth}",
                    hasSession = it.dayOfMonth in sessionDays,
                    isToday = it == today,
                    isUpcoming = it > today,
                    description = "${it.dayOfMonth}",
                )
            }
        },
        summary = summary,
    )

/** Novembre 2026 de la maquette (`T2-Journal`) : séances les 2, 4, 5, 9, 11 et 12, aujourd'hui le 15. */
val SampleCalendarNovember = sampleCalendarMonth(
    YearMonth.of(2026, 11),
    "Novembre 2026",
    setOf(2, 4, 5, 9, 11, 12),
    LocalDate.of(2026, 11, 15),
    "6 séances en novembre",
)

/** Calendrier du mois (SPEC § 15.15.1) : une page par mois de [months], ouvert sur le dernier. */
@Composable
fun MonthCalendarSample(months: List<CalendarMonth> = listOf(SampleCalendarNovember)) {
    val state = rememberPagerState(initialPage = months.lastIndex) { months.size }
    MonthCalendar(
        state = state,
        month = { months[it] },
        weekdayLabels = listOf("L", "M", "M", "J", "V", "S", "D"),
        previousDescription = "Mois précédent",
        nextDescription = "Mois suivant",
        openDayLabel = "Voir la journée",
        onDayClick = {},
    )
}

/**
 * Hors planche (SPEC § 15.15.1, 🔄 D41) : le mois de la maquette (séances, aujourd'hui cerclé,
 * jours à venir, mois précédent accessible) ; aujourd'hui avec une séance, sans séance ce mois-là.
 */
@StudioComponentPreviews
@Composable
private fun MonthCalendarPreview() = StudioComponentPreview {
    StateCaption("Mois courant : séances, aujourd'hui, jours à venir")
    MonthCalendarSample(
        listOf(
            sampleCalendarMonth(YearMonth.of(2026, 10), "Octobre 2026", setOf(5, 7, 9), LocalDate.of(2026, 11, 15), "3 séances en octobre"),
            SampleCalendarNovember,
        ),
    )
    StateCaption("Aujourd'hui avec une séance ; premier mois, sur 4 rangées")
    MonthCalendarSample(
        listOf(sampleCalendarMonth(YearMonth.of(2027, 2), "Février 2027", setOf(10), LocalDate.of(2027, 2, 10), "1 séance en février")),
    )
}

/**
 * Carte du corps (DESIGN.md § 9.1, SPEC § 15.17.4, maquette `T2-Carte-corps`) : petite taille du
 * développé couché (principal, secondaires) sur le fond et dans un groupe, une vue seule de la sheet
 * « Muscle », grande taille du filtre avec deux muscles sélectionnés.
 */
@StudioComponentPreviews
@Composable
private fun BodyMapPreview() = StudioComponentPreview {
    StateCaption("Petite (150) : principal, secondaires, non travaillés — sur le fond, puis dans un groupe")
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BodyMap(BodyMapSize.Small, primary = BenchPrimary, secondary = BenchSecondary, onMuscleClick = {})
        BodyMap(
            BodyMapSize.Small,
            Modifier.background(Studio.colors.surfaceRaised, Studio.shapes.surface).padding(8.dp),
            primary = BenchPrimary,
            secondary = BenchSecondary,
            onRaised = true,
        )
    }
    StateCaption("Une vue (140) : face, dos")
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BodyMap(BodyMapSize.SingleView, primary = listOf(Muscle.Pectoraux), view = BodyView.Front)
        BodyMap(BodyMapSize.SingleView, primary = listOf(Muscle.Dorsaux), view = BodyView.Back)
    }
    StateCaption("Grande (360), filtre : Pecs et Triceps sélectionnés")
    BodyMap(BodyMapSize.Large, selected = listOf(Muscle.Pectoraux, Muscle.Triceps), selectable = true, onMuscleClick = {})
}

/** Légende de la fiche à côté de la petite carte (« Muscles travaillés », SPEC § 15.17.5). */
@StudioComponentPreviews
@Composable
private fun MuscleLegendPreview() = StudioComponentPreview {
    StateCaption("Développé couché : un principal, deux secondaires")
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        BodyMap(BodyMapSize.Small, primary = BenchPrimary, secondary = BenchSecondary, onMuscleClick = {})
        MuscleLegend(BenchPrimary, BenchSecondary, onMuscleClick = {}, modifier = Modifier.weight(1f))
    }
    StateCaption("Soulevé de terre : trois principaux, cinq secondaires")
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        val deadlift = ExerciseCatalog.find("deadlift")!!
        BodyMap(BodyMapSize.Small, primary = deadlift.primaryMuscles, secondary = deadlift.secondaryMuscles, onMuscleClick = {})
        MuscleLegend(deadlift.primaryMuscles, deadlift.secondaryMuscles, onMuscleClick = {}, modifier = Modifier.weight(1f))
    }
}

private val BenchPrimary = listOf(Muscle.Pectoraux)
private val BenchSecondary = listOf(Muscle.EpaulesAvant, Muscle.Triceps)
