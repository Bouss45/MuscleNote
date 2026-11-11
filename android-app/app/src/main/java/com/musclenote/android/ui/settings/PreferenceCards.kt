package com.musclenote.android.ui.settings

import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.musclenote.android.R
import com.musclenote.android.data.model.ThemeMode
import com.musclenote.android.ui.theme.MuscleNoteTheme

/** Langue de l'app (🔄 D9) : celle du système, ou une surcharge FR / EN mémorisée par AppCompat. */
private enum class AppLanguage(val tag: String?) { System(null), French("fr"), English("en") }

private fun currentAppLanguage(): AppLanguage {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return AppLanguage.System
    return when (locales[0]?.language) {
        "fr" -> AppLanguage.French
        "en" -> AppLanguage.English
        else -> AppLanguage.System
    }
}

/**
 * Carte « Mes données » (§ 2.12, point 1) : séances terminées | exercices perso non archivés.
 */
@Composable
fun MyDataCard(sessions: Int, customExercises: Int) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(16.dp),
    ) {
        Text(
            stringResource(R.string.settings_my_data),
            color = colors.mutedForeground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            DataStat(sessions, stringResource(R.string.settings_stat_sessions), Modifier.weight(1f))
            Box(
                Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .background(colors.border),
            )
            DataStat(customExercises, stringResource(R.string.settings_stat_custom_exercises), Modifier.weight(1f))
        }
    }
}

@Composable
private fun DataStat(value: Int, label: String, modifier: Modifier = Modifier) {
    val colors = MuscleNoteTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value.toString(), color = colors.foreground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = colors.mutedForeground, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

/**
 * Langue (🔄 D9) : « Langue du système », « 🇫🇷 Français », « 🇬🇧 English » (libellés non traduits).
 * `setApplicationLocales` recrée l'activité tout de suite (effet immédiat) et mémorise le choix,
 * visible aussi dans les réglages de langue par app d'Android 13+. Les canaux de notification et
 * la notification d'un repos en cours suivent à la recréation (`MainActivity`).
 */
@Composable
fun LanguageCard() {
    val current = currentAppLanguage()
    PreferenceCard(R.drawable.ic_language, stringResource(R.string.settings_language_title), stringResource(R.string.settings_language_desc)) {
        for (language in AppLanguage.entries) {
            val label = when (language) {
                AppLanguage.System -> stringResource(R.string.settings_language_system)
                AppLanguage.French -> "🇫🇷 Français"
                AppLanguage.English -> "🇬🇧 English"
            }
            PreferenceChip(label, active = language == current) {
                if (language != current) {
                    val tag = language.tag
                    AppCompatDelegate.setApplicationLocales(
                        if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
                    )
                }
            }
        }
    }
}

/** Thème (🔄 D10) : Système / Clair / Sombre, effet immédiat. */
@Composable
fun ThemeCard(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    PreferenceCard(R.drawable.ic_contrast, stringResource(R.string.settings_theme_title), stringResource(R.string.settings_theme_desc)) {
        for (mode in ThemeMode.entries) {
            val label = stringResource(
                when (mode) {
                    ThemeMode.System -> R.string.settings_theme_system
                    ThemeMode.Light -> R.string.settings_theme_light
                    ThemeMode.Dark -> R.string.settings_theme_dark
                },
            )
            PreferenceChip(label, active = mode == selected) { if (mode != selected) onSelect(mode) }
        }
    }
}

/** Carte de préférence : icône, titre, description, puis puces. */
@Composable
private fun PreferenceCard(@DrawableRes icon: Int, title: String, description: String, chips: @Composable FlowRowScope.() -> Unit) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CardIcon(icon, colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(description, color = colors.mutedForeground, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = chips)
    }
}

/** Puce de préférence, cochée quand elle est active. */
@Composable
private fun PreferenceChip(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val content = if (active) colors.primaryForeground else colors.foreground
    Row(
        Modifier
            .clip(shape)
            .background(if (active) colors.primary else colors.muted)
            .border(1.dp, if (active) colors.primary else colors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = content, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (active) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = content, modifier = Modifier.size(13.dp))
    }
}

/** Encart d'information permanent (§ 2.12, point 5). */
@Composable
fun SettingsInfoBox() {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(15.dp))
        Text(stringResource(R.string.settings_info), color = colors.mutedForeground, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}
