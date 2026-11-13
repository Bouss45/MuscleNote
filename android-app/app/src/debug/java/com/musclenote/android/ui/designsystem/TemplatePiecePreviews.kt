package com.musclenote.android.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.ui.designsystem.components.BottomActionBar
import com.musclenote.android.ui.designsystem.components.ChipTone
import com.musclenote.android.ui.designsystem.components.CurvePoint
import com.musclenote.android.ui.designsystem.components.DateTitleBar
import com.musclenote.android.ui.designsystem.components.ExerciseBlockHeader
import com.musclenote.android.ui.designsystem.components.FicheTitle
import com.musclenote.android.ui.designsystem.components.FicheTopBar
import com.musclenote.android.ui.designsystem.components.FormHeroField
import com.musclenote.android.ui.designsystem.components.InfoRow
import com.musclenote.android.ui.designsystem.components.LiveHeader
import com.musclenote.android.ui.designsystem.components.MetricSurface
import com.musclenote.android.ui.designsystem.components.ScreenTitleBar
import com.musclenote.android.ui.designsystem.components.SetProgress
import com.musclenote.android.ui.designsystem.components.StudioChip
import com.musclenote.android.ui.designsystem.components.StudioIconButton
import com.musclenote.android.ui.designsystem.components.Trend
import com.musclenote.android.ui.designsystem.components.WeekBar
import com.musclenote.android.ui.designsystem.components.WeekBars
import com.musclenote.android.ui.designsystem.theme.Studio

/* Pièces des gabarits (DESIGN.md § 9.3), chacune avec ses variantes. */

/** Poids de corps depuis février (courbe compacte du hub Progrès). */
val SampleBodyWeight = curveFromSvg(
    xs = listOf(2.0f, 18.9f, 32.1f, 44.2f, 61.1f, 78.0f, 94.9f, 111.7f, 128.6f, 145.5f, 162.4f, 179.3f, 196.1f, 229.9f, 246.8f, 263.7f, 280.6f, 297.4f, 314.3f, 330.0f),
    ys = listOf(42.3f, 40.2f, 35.7f, 32.5f, 31.5f, 26.3f, 23.1f, 18.6f, 19.3f, 17.9f, 16.5f, 15.8f, 16.2f, 14.8f, 13.0f, 11.3f, 9.9f, 8.9f, 7.1f, 6.1f),
)

/** Points d'une courbe relevés sur le tracé SVG de la maquette (y vers le bas). */
fun curveFromSvg(xs: List<Float>, ys: List<Float>): List<CurvePoint> = xs.zip(ys) { x, y -> CurvePoint(x, -y) }

@StudioComponentPreviews
@Composable
private fun TitleBarsPreview() = StudioComponentPreview {
    StateCaption("Hub")
    ScreenTitleBar("Progrès")
    StateCaption("Hub avec action")
    ScreenTitleBar("Nutrition", action = { StudioIconButton(R.drawable.ds_ic_add, "Ajouter", {}) })
    StateCaption("Accueil")
    DateTitleBar("Jeudi 12 novembre", "Paramètres", {})
    StateCaption("Fiche")
    FicheTopBar({}, actions = { StudioIconButton(R.drawable.ds_ic_more_vert, "Plus d'options", {}) })
    FicheTitle("Développé couché", subtitle = "Pecs · 19 séances depuis février")
    StateCaption("Résumé")
    FicheTopBar({}, navigationIcon = R.drawable.ds_ic_close, navigationDescription = "Fermer", actions = {
        StudioIconButton(R.drawable.ds_ic_edit_note, "Ajouter une note", {})
    })
    FicheTitle("Push", overline = "Séance terminée · jeu. 12 nov. · 18:04", titleStyle = Studio.typography.titleScreen)
    StateCaption("En direct")
    LiveHeader("Push", "78,4 kg · 2 / 16 séries", "17:48", {})
    SetProgress(done = 2, total = 16)
}

@StudioComponentPreviews
@Composable
private fun ProgressPiecesPreview() = StudioComponentPreview {
    StateCaption("Progression des séries")
    SetProgress(done = 2, total = 16)
    StateCaption("Barres des semaines")
    WeekBars(
        listOf(WeekBar(3, true), WeekBar(3, true), WeekBar(2, false), WeekBar(3, true), WeekBar(4, true), WeekBar(3, true), WeekBar(3, true), WeekBar(2, false)),
        maxCount = 4,
        caption = "8 dernières semaines · 3 séances = semaine validée",
        contentDescription = "8 dernières semaines : 3, 3, 2, 3, 4, 3, 3, puis 2 cette semaine",
    )
}

@StudioComponentPreviews
@Composable
private fun ExerciseBlockHeaderPreview() = StudioComponentPreview {
    ExerciseBlockHeader("Développé couché", {}, {}) {
        StudioChip("3 × 8 · 80 kg", icon = R.drawable.ds_ic_history)
        StudioChip("+2,5 kg", tone = ChipTone.Up)
    }
}

@StudioComponentPreviews
@Composable
private fun MetricSurfacePreview() = StudioComponentPreview {
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
}

@StudioComponentPreviews
@Composable
private fun FormPiecesPreview() = StudioComponentPreview {
    StateCaption("Champ héros en saisie")
    FormHeroField("500", "g ajoutés")
    StateCaption("Champ héros au repos")
    FormHeroField("500", "g ajoutés", editing = false)
    StateCaption("Lignes d'information")
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        InfoRow("Nouveau stock", "520 g · 104 jours")
        InfoRow("Me prévenir", "à 7 jours", onClick = {}, showDivider = false)
    }
}

@StudioComponentPreviews
@Composable
private fun BottomActionBarPreview() = StudioComponentPreview {
    BottomActionBar("Enregistrer la séance", {})
    BottomActionBar("Nouvelle mesure", {}, icon = R.drawable.ds_ic_add)
}
