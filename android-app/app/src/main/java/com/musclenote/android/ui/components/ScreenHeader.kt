package com.musclenote.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musclenote.android.ui.theme.InterFontFamily
import com.musclenote.android.ui.theme.MuscleNoteTheme

/**
 * En-tête d'écran d'onglet : titre 28 sp Bold sous la barre de statut, marges latérales
 * 20 dp, séparateur bas 1 dp `border` (comme l'app Expo).
 */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier) {
    val colors = MuscleNoteTheme.colors
    Column(modifier.fillMaxWidth().statusBarsPadding()) {
        Text(
            text = title,
            color = colors.foreground,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
        )
        HorizontalDivider(thickness = 1.dp, color = colors.border)
    }
}
