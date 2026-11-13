package com.musclenote.android.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.ui.designsystem.components.AddSetRow
import com.musclenote.android.ui.designsystem.components.BottomActionBar
import com.musclenote.android.ui.designsystem.components.ChipTone
import com.musclenote.android.ui.designsystem.components.DateTitleBar
import com.musclenote.android.ui.designsystem.components.ExerciseBlockHeader
import com.musclenote.android.ui.designsystem.components.FicheTitle
import com.musclenote.android.ui.designsystem.components.FicheTopBar
import com.musclenote.android.ui.designsystem.components.FilterChip
import com.musclenote.android.ui.designsystem.components.FormHeroField
import com.musclenote.android.ui.designsystem.components.HeroNumber
import com.musclenote.android.ui.designsystem.components.InfoRow
import com.musclenote.android.ui.designsystem.components.KeypadHeader
import com.musclenote.android.ui.designsystem.components.KeypadPanel
import com.musclenote.android.ui.designsystem.components.ListRow
import com.musclenote.android.ui.designsystem.components.ListRowTrailing
import com.musclenote.android.ui.designsystem.components.LiveHeader
import com.musclenote.android.ui.designsystem.components.LiveSessionCard
import com.musclenote.android.ui.designsystem.components.MetricSurface
import com.musclenote.android.ui.designsystem.components.NumericKeypad
import com.musclenote.android.ui.designsystem.components.PeriodSelector
import com.musclenote.android.ui.designsystem.components.RestBanner
import com.musclenote.android.ui.designsystem.components.ScreenTitleBar
import com.musclenote.android.ui.designsystem.components.SectionHeader
import com.musclenote.android.ui.designsystem.components.SegmentedControl
import com.musclenote.android.ui.designsystem.components.SetProgress
import com.musclenote.android.ui.designsystem.components.SetRow
import com.musclenote.android.ui.designsystem.components.SetRowState
import com.musclenote.android.ui.designsystem.components.SetTableHeader
import com.musclenote.android.ui.designsystem.components.Stat
import com.musclenote.android.ui.designsystem.components.StatRow
import com.musclenote.android.ui.designsystem.components.StudioChip
import com.musclenote.android.ui.designsystem.components.StudioIconButton
import com.musclenote.android.ui.designsystem.components.StudioNavigationBar
import com.musclenote.android.ui.designsystem.components.Trend
import com.musclenote.android.ui.designsystem.components.TrendCurve
import com.musclenote.android.ui.designsystem.components.WeekBar
import com.musclenote.android.ui.designsystem.components.WeekBars
import com.musclenote.android.ui.designsystem.templates.FicheTemplate
import com.musclenote.android.ui.designsystem.templates.FormSheetTemplate
import com.musclenote.android.ui.designsystem.templates.HubTemplate
import com.musclenote.android.ui.designsystem.templates.LiveTemplate
import com.musclenote.android.ui.designsystem.templates.ScreenInsets
import com.musclenote.android.ui.designsystem.theme.Studio

/*
 * Les quatre gabarits (DESIGN.md § 10), remplis comme les écrans de la maquette (Accueil, Progrès,
 * Séance en cours, Résumé, Fiche exercice, Stock) avec des données d'exemple. Ce ne sont pas les
 * écrans de l'app : ils prouvent que gabarits + composants suffisent à les reproduire.
 */

/** Insets de la maquette : 40 pour la barre d'état, 24 pour la barre de navigation. */
private val MockupInsets = ScreenInsets(top = 40.dp, bottom = 24.dp)

@StudioScreenPreviews
@Composable
private fun HubTemplateAccueilPreview() = StudioPreviewTheme {
    HubTemplate(
        titleBar = { DateTitleBar("Jeudi 12 novembre", "Paramètres", {}) },
        highlight = {
            LiveSessionCard(
                liveLabel = "En cours",
                sessionName = "Push",
                elapsed = "17:48",
                actionLabel = "Reprendre",
                onAction = {},
                restLabel = "Repos",
                restTime = "1:48",
                restDetail = "développé couché, série 3",
                setsDone = 2,
                setsTotal = 16,
                progressStart = "2 / 16 séries",
                progressEnd = "78,4 kg",
            )
        },
        navigation = { StudioNavigationBar(SampleNavItems, 0, {}, bottomInset = it) },
        insets = MockupInsets,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.s)) {
            SectionHeader("Cette semaine", linkLabel = "Journal", onLinkClick = {})
            StatRow(listOf(Stat("2/3", "séances"), Stat("4 sem.", "série en cours"), Stat("9 sem.", "record")))
            WeekBars(
                listOf(WeekBar(3, true), WeekBar(3, true), WeekBar(2, false), WeekBar(3, true), WeekBar(4, true), WeekBar(3, true), WeekBar(3, true), WeekBar(2, false)),
                maxCount = 4,
                caption = "8 dernières semaines · 3 séances = semaine validée",
                contentDescription = "8 dernières semaines",
                modifier = Modifier.padding(top = Studio.spacing.xxs),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
            SectionHeader("Récentes", linkLabel = "Tout voir", onLinkClick = {})
            Column {
                ListRow("Pull", subtitle = "mer. 11 nov. · 1 h 12", trailing = ListRowTrailing.Value("6 840 kg"))
                ListRow("Legs", subtitle = "lun. 9 nov. · 1 h 31", trailing = ListRowTrailing.Value("11 420 kg"))
                ListRow("Push", subtitle = "jeu. 5 nov. · 1 h 18", trailing = ListRowTrailing.Value("7 960 kg"), showDivider = false)
            }
        }
    }
}

@StudioScreenPreviews
@Composable
private fun HubTemplateProgresPreview() = StudioPreviewTheme {
    HubTemplate(
        titleBar = { ScreenTitleBar("Progrès") },
        highlight = {
            MetricSurface(
                label = "Poids de corps · aujourd'hui",
                value = "78,4",
                unit = "kg",
                points = SampleBodyWeight,
                curveDescription = "Poids de corps de 68,0 à 78,4 kg depuis février",
                addDescription = "Ajouter une pesée",
                onAdd = {},
                delta = "+10,4 kg",
                trend = Trend.Up,
                deltaContext = "depuis février · objectif prise de masse",
            )
        },
        navigation = { StudioNavigationBar(SampleNavItems, 2, {}, bottomInset = it) },
        insets = MockupInsets,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
            SectionHeader("Records", linkLabel = "Tout voir", onLinkClick = {})
            Column {
                ListRow("Développé couché", subtitle = "1RM estimé · 5 nov.", trailing = ListRowTrailing.Value("101,3 kg", "+2,6 kg", Trend.Up))
                ListRow("Tirage vertical", subtitle = "Charge max · 15 juil.", trailing = ListRowTrailing.Value("71 kg", "+2,5 kg", Trend.Up), showDivider = false)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
            SectionHeader("Corps")
            Column {
                ListRow("Mensurations", subtitle = "Bras 38,5 cm · 10 nov.", leadingIcon = R.drawable.ds_ic_straighten, trailing = ListRowTrailing.Chevron, onClick = {})
                ListRow("Photos", subtitle = "12 photos · dernière le 1 nov.", leadingIcon = R.drawable.ds_ic_photo_camera, trailing = ListRowTrailing.Chevron, onClick = {})
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
            SectionHeader("Exercices", linkLabel = "Tout voir", onLinkClick = {})
            ListRow("Tirage horizontal", subtitle = "11 séances", trailing = ListRowTrailing.Value("72 kg", "−5,0 kg", Trend.Down), showDivider = false)
        }
    }
}

@StudioScreenPreviews
@Composable
private fun LiveTemplateSeancePreview() = StudioPreviewTheme {
    LiveTemplate(
        header = { LiveHeader("Push", "78,4 kg · 2 / 16 séries", "17:48", {}) },
        progress = { SetProgress(done = 2, total = 16) },
        bottom = { inset ->
            KeypadPanel(bottomInset = inset) {
                KeypadHeader(listOf("Kg · série 3", "Reps"), selectedIndex = 0, onSelect = {}, onHide = {})
                NumericKeypad(
                    onDigit = {},
                    onDelete = {},
                    stepDownLabel = "−2,5",
                    onStepDown = {},
                    stepUpLabel = "+2,5",
                    onStepUp = {},
                    actionLabel = "Valider la série",
                    onAction = {},
                    actionIcon = R.drawable.ds_ic_check,
                )
            }
        },
        insets = MockupInsets,
    ) {
        Column {
            ExerciseBlockHeader("Développé couché", {}, {}) {
                StudioChip("3 × 8 · 80 kg", icon = R.drawable.ds_ic_history)
                StudioChip("+2,5 kg", tone = ChipTone.Up)
            }
            Spacer(Modifier.height(Studio.spacing.m))
            SetTableHeader("#", "Préc.", "Kg", "Reps")
            SetRow("1", "80 × 8", "82,5", "8", SetRowState.Done, "Série 1 validée", {})
            SetRow("2", "80 × 8", "82,5", "8", SetRowState.Done, "Série 2 validée", {})
            RestBanner("Repos", "1:48", "sur 2:30", 0.28f, "−30 s", "+30 s", "Passer", {}, {}, {})
            SetRow("3", "80 × 8", "82,5", "8", SetRowState.Editing, "Valider la série 3", {}, showDivider = false)
            AddSetRow("Série", {})
        }
    }
}

@StudioScreenPreviews
@Composable
private fun FicheTemplateResumePreview() = StudioPreviewTheme {
    FicheTemplate(
        topBar = {
            FicheTopBar({}, navigationIcon = R.drawable.ds_ic_close, navigationDescription = "Fermer", actions = {
                StudioIconButton(R.drawable.ds_ic_edit_note, "Ajouter une note", {})
            })
        },
        titleBlock = { FicheTitle("Push", overline = "Séance terminée · jeu. 12 nov. · 18:04", titleStyle = Studio.typography.titleScreen) },
        hero = { HeroNumber("8 240", label = "Volume total", unit = "kg", delta = "+280 kg", trend = Trend.Up, deltaContext = "vs Push du jeu. 5 nov.") },
        stats = { StatRow(listOf(Stat("1 h 14", "durée"), Stat("16", "séries"), Stat("5", "exercices"))) },
        bottomAction = { BottomActionBar("Enregistrer la séance", {}, bottomInset = it) },
        insets = MockupInsets,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
            SectionHeader("Records battus", trailingText = "2", trailingTrend = Trend.Up)
            Column {
                ListRow("Développé couché", subtitle = "1RM estimé · 82,5 × 8", trailing = ListRowTrailing.Value("104,5 kg", "+3,2 kg", Trend.Up))
                ListRow("Extension triceps poulie", subtitle = "Charge max · 3 × 12", trailing = ListRowTrailing.Value("27,5 kg", "+2,5 kg", Trend.Up), showDivider = false)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
            SectionHeader("Par rapport au 5 nov.")
            Column {
                ListRow("Développé incliné haltères", subtitle = "3 × 10 · 30 kg", trailing = ListRowTrailing.DeltaOnly("+2 kg", Trend.Up))
                ListRow("Dips lestés", subtitle = "3 × 8 · +10 kg", trailing = ListRowTrailing.DeltaOnly("identique", Trend.Neutral))
                ListRow("Élévations latérales", subtitle = "4 × 12 · 10 kg", trailing = ListRowTrailing.DeltaOnly("−12 reps", Trend.Down), showDivider = false)
            }
        }
    }
}

@StudioScreenPreviews
@Composable
private fun FicheTemplateExercicePreview() = StudioPreviewTheme {
    FicheTemplate(
        topBar = { FicheTopBar({}, actions = { StudioIconButton(R.drawable.ds_ic_more_vert, "Plus d'options", {}) }) },
        titleBlock = { FicheTitle("Développé couché", subtitle = "Pecs · 19 séances depuis février") },
        segmented = { SegmentedControl(listOf("1RM estimé", "Charge max", "Volume"), 0, {}) },
        hero = {
            HeroNumber("101,3", label = "Dernière séance · jeu. 5 nov.", unit = "kg", delta = "+19,0 kg", trend = Trend.Up, deltaContext = "depuis le 12 févr.")
        },
        curve = { TrendCurve(SampleCurve, "1RM estimé de février à novembre, de 82,3 à 101,3 kg", startLabel = "févr.", endLabel = "nov.") },
        period = { PeriodSelector(listOf("1M", "3M", "6M", "1A", "Tout"), 4, {}) },
        stats = { StatRow(listOf(Stat("80 × 8", "meilleure série"), Stat("80 kg", "charge max"), Stat("1 920", "volume max, kg"))) },
        insets = MockupInsets,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
            SectionHeader("Séances", trailingText = "19")
            Column {
                ListRow("jeu. 5 nov.", tags = listOf("80 × 8", "80 × 8", "80 × 8"), trailing = ListRowTrailing.Value("101,3", "Record", Trend.Up))
                ListRow("jeu. 22 oct.", tags = listOf("80 × 7", "80 × 7", "80 × 6"), trailing = ListRowTrailing.Value("98,7"), showDivider = false)
            }
        }
    }
}

@StudioScreenPreviews
@Composable
private fun FormSheetTemplateStockPreview() = StudioPreviewTheme {
    Box(Modifier.fillMaxSize()) {
        HubTemplate(titleBar = { ScreenTitleBar("Nutrition") }, insets = MockupInsets) {
            StatRow(listOf(Stat("1 840", "kcal / 2 600"), Stat("1,5 L", "eau / 2,5 L"), Stat("112 g", "protéines / 150")))
            Column(verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
                SectionHeader("Compléments")
                Column {
                    ListRow("Protéine whey", subtitle = "2 doses par jour", trailing = ListRowTrailing.Value("12 jours"))
                    ListRow("Créatine", subtitle = "5 g par jour", trailing = ListRowTrailing.Value("4 jours", valueTrend = Trend.Down))
                    ListRow("Oméga-3", subtitle = "2 gélules par jour", trailing = ListRowTrailing.Value("38 jours"), showDivider = false)
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Studio.colors.scrim))
        val down = Studio.colors.down
        FormSheetTemplate(
            title = "Ajouter du stock",
            help = buildAnnotatedString {
                append("Créatine · il reste 20 g, soit ")
                withStyle(SpanStyle(color = down, fontWeight = FontWeight.SemiBold)) { append("4 jours") }
            },
            field = { FormHeroField("500", "g ajoutés") },
            quickEntries = {
                FilterChip("+100 g", false, {})
                FilterChip("+250 g", false, {})
                FilterChip("+500 g", true, {})
                FilterChip("+1 kg", false, {})
            },
            info = {
                InfoRow("Nouveau stock", "520 g · 104 jours")
                InfoRow("Me prévenir", "à 7 jours", onClick = {}, showDivider = false)
            },
            bottomInset = MockupInsets.bottom,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            NumericKeypad(
                onDigit = {},
                onDelete = {},
                stepDownLabel = "−50",
                onStepDown = {},
                stepUpLabel = "+50",
                onStepUp = {},
                actionLabel = "Ajouter",
                onAction = {},
            )
        }
    }
}
