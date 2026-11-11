package com.musclenote.android.ui.progress

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musclenote.android.ui.theme.MuscleNoteTheme

/** Une des 3 statistiques en tête d'onglet : valeur colorée et libellé. */
data class StatItem(val value: String, val label: String, val color: Color)

/** Rangée de 3 cartes de statistique (§ 2.10). */
@Composable
fun StatsRow(stats: List<StatItem>, modifier: Modifier = Modifier) {
    val colors = MuscleNoteTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (stat in stats) {
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(colors.card)
                    .border(1.dp, colors.border, shape)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // « 78,35–81,25 » doit tenir sur une ligne dans un tiers d'écran.
                BasicText(
                    stat.value,
                    style = LocalTextStyle.current.merge(TextStyle(color = stat.color, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 16.sp),
                )
                Text(stat.label, color = colors.mutedForeground, fontSize = 10.sp, lineHeight = 13.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Carte de l'onglet Progrès : fond `card`, bordure, rayon 14. */
@Composable
fun ProgressCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(padding),
        content = content,
    )
}

/** Titre de carte (14 sp gras) et sous-titre facultatif (11 sp). */
@Composable
fun CardTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    val colors = MuscleNoteTheme.colors
    Column(modifier) {
        Text(title, color = colors.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(subtitle, color = colors.mutedForeground, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Récapitulatif : lignes libellé / valeur séparées d'un trait (calories, eau). */
@Composable
fun RecapCard(title: String, rows: List<Pair<String, Pair<String, Color>>>) {
    val colors = MuscleNoteTheme.colors
    ProgressCard {
        CardTitle(title, Modifier.padding(bottom = 6.dp))
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider(thickness = 1.dp, color = colors.border)
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = colors.mutedForeground, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(value.first, color = value.second, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** État vide d'un onglet : icône ou émoji, titre, indication. */
@Composable
fun ProgressEmptyState(title: String, hint: String, @DrawableRes icon: Int? = null, emoji: String? = null) {
    val colors = MuscleNoteTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(36.dp))
        if (emoji != null) Text(emoji, fontSize = 36.sp)
        Text(title, color = colors.foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(hint, color = colors.mutedForeground, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
    }
}
