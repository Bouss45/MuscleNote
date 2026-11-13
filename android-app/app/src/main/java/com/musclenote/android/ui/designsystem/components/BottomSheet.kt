package com.musclenote.android.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.designsystem.theme.Studio

private val HandleWidth = 36.dp
private val HandleHeight = 4.dp
private val HandleShape = RoundedCornerShape(2.dp)
private val SheetTop = 10.dp
private val SheetGap = 14.dp
private val SheetTitleGap = 6.dp

/** Poignée de bottom sheet : 36 × 4, `surfacePressed`, centrée. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(HandleWidth, HandleHeight).background(Studio.colors.surfacePressed, HandleShape))
    }
}

/**
 * Corps d'une bottom sheet (DESIGN.md § 9.2) : `surfaceRaised`, coins du haut 20, poignée,
 * contenu espacé de [spacing]. En bas : [bottomInset] (inset de navigation) + 4 au moins.
 */
@Composable
fun SheetSurface(
    modifier: Modifier = Modifier,
    spacing: Dp = SheetGap,
    bottomPadding: Dp = Studio.spacing.screen,
    bottomInset: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Studio.colors.surfaceRaised, Studio.shapes.sheet)
            .padding(
                start = Studio.spacing.screen,
                end = Studio.spacing.screen,
                top = SheetTop,
                bottom = bottomPadding + bottomInset,
            ),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        SheetHandle()
        content()
    }
}

/**
 * Titre de sheet (`titleSection`) et son texte en texte secondaire : `paragraph` pour une
 * confirmation, `body` pour la phrase d'aide d'un formulaire.
 */
@Composable
fun SheetTitle(
    title: String,
    message: AnnotatedString?,
    modifier: Modifier = Modifier,
    gap: Dp = SheetTitleGap,
    messageStyle: TextStyle = Studio.typography.paragraph,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        Text(title, style = Studio.typography.titleSection)
        if (message != null) Text(message, style = messageStyle, color = Studio.colors.textSecondary)
    }
}

/**
 * Contenu d'une sheet de confirmation (« Terminer la séance ? », suppression) : titre, phrase,
 * bouton principal (toujours inverse, jamais rouge) et bouton texte.
 */
@Composable
fun ConfirmSheetContent(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    SheetSurface(modifier, bottomInset = bottomInset) {
        SheetTitle(title, AnnotatedString(message))
        StudioButton(confirmLabel, onConfirm, Modifier.fillMaxWidth())
        StudioButton(dismissLabel, onDismiss, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
    }
}

/**
 * Bottom sheet modale — `StudioBottomSheet` : la `ModalBottomSheet` de Material aux couleurs du
 * design system (`scrim`, sans ombre ni poignée Material : [content] pose son [SheetSurface]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.(bottomInset: Dp) -> Unit,
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = Studio.shapes.sheet,
        containerColor = Studio.colors.surfaceRaised,
        contentColor = Studio.colors.textPrimary,
        tonalElevation = 0.dp,
        scrimColor = Studio.colors.scrim,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        content(bottomInset)
    }
}
