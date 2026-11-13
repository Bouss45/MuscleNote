package com.musclenote.android.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musclenote.android.R
import com.musclenote.android.ui.designsystem.components.AddSetRow
import com.musclenote.android.ui.designsystem.components.AlwaysPressedInteractionSource
import com.musclenote.android.ui.designsystem.components.ButtonSize
import com.musclenote.android.ui.designsystem.components.ButtonVariant
import com.musclenote.android.ui.designsystem.components.ConfirmSheetContent
import com.musclenote.android.ui.designsystem.components.EmptyState
import com.musclenote.android.ui.designsystem.components.KeypadHeader
import com.musclenote.android.ui.designsystem.components.KeypadPanel
import com.musclenote.android.ui.designsystem.components.LiveSessionCard
import com.musclenote.android.ui.designsystem.components.MiniSessionBar
import com.musclenote.android.ui.designsystem.components.NavItem
import com.musclenote.android.ui.designsystem.components.NextSessionCard
import com.musclenote.android.ui.designsystem.components.NumericKeypad
import com.musclenote.android.ui.designsystem.components.RestBanner
import com.musclenote.android.ui.designsystem.components.RestFinishedBanner
import com.musclenote.android.ui.designsystem.components.SetRow
import com.musclenote.android.ui.designsystem.components.SetField
import com.musclenote.android.ui.designsystem.components.SetRowState
import com.musclenote.android.ui.designsystem.components.SetTableHeader
import com.musclenote.android.ui.designsystem.components.StudioButton
import com.musclenote.android.ui.designsystem.components.StudioIconButton
import com.musclenote.android.ui.designsystem.components.StudioNavigationBar
import com.musclenote.android.ui.designsystem.components.StudioTextField
import com.musclenote.android.ui.designsystem.theme.Studio

/*
 * Planche « Composants 2 · Actions et saisie » (DESIGN.md § 9.2) : un aperçu par composant, avec
 * ses états, et les échantillons réutilisés par la planche complète (BoardPreviews.kt).
 */

@Composable
private fun ButtonGridRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.width(80.dp), style = Studio.typography.caption.copy(fontWeight = FontWeight.SemiBold))
        content()
    }
}

@Composable
fun ButtonSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ButtonGridRow("") {
            listOf("Normal", "Pressé", "Désactivé").forEach {
                Text(it, Modifier.weight(1f), style = Studio.typography.small, color = Studio.colors.textSecondary)
            }
        }
        ButtonGridRow("Principal") {
            StudioButton("Démarrer", {}, Modifier.weight(1f))
            StudioButton("Démarrer", {}, Modifier.weight(1f), interactionSource = AlwaysPressedInteractionSource())
            StudioButton("Démarrer", {}, Modifier.weight(1f), enabled = false)
        }
        ButtonGridRow("Secondaire") {
            StudioButton("Ajouter", {}, Modifier.weight(1f), ButtonVariant.Secondary)
            StudioButton("Ajouter", {}, Modifier.weight(1f), ButtonVariant.Secondary, interactionSource = AlwaysPressedInteractionSource())
            StudioButton("Ajouter", {}, Modifier.weight(1f), ButtonVariant.Secondary, enabled = false)
        }
        ButtonGridRow("Texte") {
            StudioButton("Annuler", {}, Modifier.weight(1f), ButtonVariant.Text)
            StudioButton("Annuler", {}, Modifier.weight(1f), ButtonVariant.Text, interactionSource = AlwaysPressedInteractionSource())
            StudioButton("Annuler", {}, Modifier.weight(1f), ButtonVariant.Text, enabled = false)
        }
        ButtonGridRow("Icône") {
            Box(Modifier.weight(1f)) { StudioIconButton(R.drawable.ds_ic_settings, "Paramètres", {}) }
            Box(Modifier.weight(1f)) {
                StudioIconButton(R.drawable.ds_ic_settings, "Paramètres", {}, interactionSource = AlwaysPressedInteractionSource())
            }
            Box(Modifier.weight(1f)) { StudioIconButton(R.drawable.ds_ic_settings, "Paramètres", {}, enabled = false) }
        }
    }
}

@Composable
fun HighlightSurfaceSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StateCaption("En direct")
        LiveSessionCard(
            liveLabel = "En cours",
            sessionName = "Push",
            elapsed = "17:48",
            actionLabel = "Reprendre",
            onAction = {},
            restLabel = "Repos",
            restTime = "1:48",
            restDetail = "développé couché, série 3",
        )
        StateCaption("Prochaine séance")
        NextSessionCard(
            caption = "Prochaine séance",
            title = "Push",
            detail = "Dernier Push il y a 7 jours · 5 exercices",
            types = listOf("Push", "Pull", "Legs", "Autre…"),
            selectedType = 0,
            onSelectType = {},
            actionLabel = "Démarrer",
            onAction = {},
        )
    }
}

@Composable
fun SetRowSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SetTableHeader("#", "Préc.", "Kg", "Reps")
        StateCaption("À faire — pré-remplie")
        SetRow("3", "80 × 8", "82,5", "8", SetRowState.Todo, "Valider la série 3", {})
        StateCaption("En saisie")
        SetRow("3", "80 × 8", "82,5", "8", SetRowState.Editing, "Valider la série 3", {})
        StateCaption("Validée")
        SetRow("1", "80 × 8", "82,5", "8", SetRowState.Done, "Série 1 validée", {})
        StateCaption("Validée avec record")
        SetRow("3", "80 × 8", "85", "8", SetRowState.DoneRecord, "Série 3 validée, record", {}, recordLabel = "Record")
        StateCaption("Ajouter une série")
        AddSetRow("Série", {})
    }
}

@Composable
fun RestBannerSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StateCaption("En cours")
        RestBanner(
            label = "Repos",
            remaining = "1:48",
            total = "sur 2:30",
            progress = 0.28f,
            minusLabel = "−30 s",
            plusLabel = "+30 s",
            skipLabel = "Passer",
            onMinus = {},
            onPlus = {},
            onSkip = {},
        )
        StateCaption("Terminé")
        RestFinishedBanner("Repos terminé", "2:30 · à toi, série 3", "Relancer", {})
    }
}

@Composable
fun EmptyStateSample() {
    EmptyState(
        R.drawable.ds_ic_straighten,
        title = "Aucune mesure pour l'instant",
        message = "Ajoute ton tour de bras ou de taille pour suivre son évolution.",
        actionLabel = "Ajouter une mesure",
        onAction = {},
    )
}

@Composable
fun BottomSheetSample() {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(Studio.shapes.surface)
            .background(Studio.colors.scrim)
            .padding(top = 40.dp),
    ) {
        ConfirmSheetContent(
            title = "Terminer la séance ?",
            message = "16 séries en 1 h 14. Tu pourras corriger le résumé ensuite.",
            confirmLabel = "Terminer",
            onConfirm = {},
            dismissLabel = "Continuer la séance",
            onDismiss = {},
        )
    }
}

@Composable
fun KeypadSample(shape: androidx.compose.ui.graphics.Shape = Studio.shapes.surface) {
    KeypadPanel(shape = shape) {
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
}

val SampleNavItems = listOf(
    NavItem("Accueil", R.drawable.ds_ic_home, R.drawable.ds_ic_home_filled),
    NavItem("Journal", R.drawable.ds_ic_calendar_month, R.drawable.ds_ic_calendar_month_filled),
    NavItem("Progrès", R.drawable.ds_ic_monitoring, R.drawable.ds_ic_monitoring_filled),
    NavItem("Nutrition", R.drawable.ds_ic_restaurant, R.drawable.ds_ic_restaurant_filled),
)

@Composable
fun MiniBarAndNavSample() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniSessionBar("Push", "repos", "1:48", "Reprendre", {})
        StudioNavigationBar(SampleNavItems, selectedIndex = 0, onSelect = {})
    }
}

@StudioComponentPreviews
@Composable
private fun ButtonPreview() = StudioComponentPreview { ButtonSamples() }

@StudioComponentPreviews
@Composable
private fun HighlightSurfacePreview() = StudioComponentPreview { HighlightSurfaceSamples() }

@StudioComponentPreviews
@Composable
private fun SetRowPreview() = StudioComponentPreview { SetRowSamples() }

@StudioComponentPreviews
@Composable
private fun RestBannerPreview() = StudioComponentPreview { RestBannerSamples() }

@StudioComponentPreviews
@Composable
private fun EmptyStatePreview() = StudioComponentPreview { EmptyStateSample() }

@StudioComponentPreviews
@Composable
private fun BottomSheetPreview() = StudioComponentPreview { BottomSheetSample() }

@StudioComponentPreviews
@Composable
private fun NumericKeypadPreview() = StudioComponentPreview { KeypadSample() }

@StudioComponentPreviews
@Composable
private fun NavigationPreview() = StudioComponentPreview { MiniBarAndNavSample() }

/** Hors maquette (séance en cours, § 15.7) : série validée en saisie sur les reps, « Préc. » à toucher. */
@StudioComponentPreviews
@Composable
private fun SetRowEditingDonePreview() = StudioComponentPreview {
    StateCaption("Validée, en saisie (reps)")
    SetRow("2", "80 × 8", "82,5", "9", SetRowState.Done, "Série 2 validée", {}, editingField = SetField.Reps, onPreviousClick = {})
    StateCaption("À faire, en saisie (charge), sans « Préc. »")
    SetRow("5", "—", "82,", "8", SetRowState.Todo, "Valider la série 5", {}, editingField = SetField.Weight, showDivider = false)
}

/** Hors maquette : en-tête du pavé avec « Supprimer la série » (séance en cours, § 15.7.4). */
@StudioComponentPreviews
@Composable
private fun KeypadHeaderDeletePreview() = StudioComponentPreview {
    KeypadPanel(shape = Studio.shapes.surface) {
        KeypadHeader(listOf("Kg · série 3", "Reps"), selectedIndex = 1, onSelect = {}, onHide = {}, onDelete = {}, deleteDescription = "Supprimer la série")
    }
    KeypadPanel(shape = Studio.shapes.surface) {
        KeypadHeader(listOf("Min · série 1", "Km", "Km/h", "%"), selectedIndex = 2, onSelect = {}, onHide = {}, onDelete = {}, deleteDescription = "Supprimer la série")
    }
}

/** Hors maquette : champ texte (note d'un exercice), vide puis rempli. */
@StudioComponentPreviews
@Composable
private fun TextFieldPreview() = StudioComponentPreview {
    StateCaption("Vide")
    StudioTextField("", {}, placeholder = "Ajouter une note…")
    StateCaption("Rempli")
    StudioTextField("Prise un peu plus serrée, coudes à 45°", {}, placeholder = "Ajouter une note…")
}
