package com.musclenote.android.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musclenote.android.ui.designsystem.theme.Studio

/** Colonne des numéros de [SetRow] et de l'icône de [AddSetRow] : le trait tombe en son milieu. */
private val LinkColumn = 22.dp

/** Écart entre la colonne du trait et le libellé, celui de l'icône et du texte de [AddSetRow]. */
private val LinkLabelGap = 6.dp

private val LinkMinHeight = 24.dp
private val LinkVerticalPadding = 4.dp
private val LinkStroke = 2.dp

/**
 * Lien de supersérie (DESIGN.md § 9.2, SPEC § 15.20.2, 🔄 D46) : sous le dernier élément d'un bloc
 * exercice lié au suivant, un trait vertical fin (2, bouts arrondis, `textTertiary`) au milieu de la
 * colonne des numéros — sous l'icône de « + Série » — et, à sa droite, le libellé [label] (« Supersérie »,
 * `caption`, `textSecondary`), aligné sur celui de « + Série ». Pas de couleur : ni jaune ni vert.
 *
 * Hauteur minimale 24, elle grandit avec le libellé (texte agrandi). Le trait couvre aussi [extendBelow]
 * sous le composant — l'écart de la liste jusqu'au bloc suivant —, pour relier les deux blocs. TalkBack
 * lit [description] (« Supersérie : Développé couché, puis Tractions »).
 */
@Composable
fun SupersetLink(label: String, description: String, modifier: Modifier = Modifier, extendBelow: Dp = 0.dp) {
    val color = Studio.colors.textTertiary
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = LinkMinHeight)
            .drawBehind {
                val x = LinkColumn.toPx() / 2
                val stroke = LinkStroke.toPx()
                // Bouts arrondis : le trait s'arrête d'un demi-trait avant chaque bord.
                drawLine(
                    color = color,
                    start = Offset(x, stroke / 2),
                    end = Offset(x, size.height + extendBelow.toPx() - stroke / 2),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
            .clearAndSetSemantics { contentDescription = description }
            .padding(vertical = LinkVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Spacer(Modifier.width(LinkColumn + LinkLabelGap))
        Text(label, style = Studio.typography.caption, color = Studio.colors.textSecondary)
    }
}
