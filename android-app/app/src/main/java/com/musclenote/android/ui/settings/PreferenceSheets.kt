package com.musclenote.android.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.musclenote.android.R
import com.musclenote.android.data.model.ThemeMode
import com.musclenote.android.data.timer.RestTimerRules
import com.musclenote.android.ui.designsystem.components.FilterChip
import com.musclenote.android.ui.designsystem.components.SegmentedControl
import com.musclenote.android.ui.designsystem.components.SheetSurface
import com.musclenote.android.ui.designsystem.components.SheetTitle
import com.musclenote.android.ui.designsystem.components.StudioBottomSheet
import com.musclenote.android.ui.designsystem.theme.Studio

/** Langue de l'app (🔄 D9) : celle du système, ou une surcharge FR / EN mémorisée par AppCompat. */
internal enum class AppLanguage(val tag: String?) { System(null), French("fr"), English("en") }

internal fun currentAppLanguage(): AppLanguage {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return AppLanguage.System
    return when (locales[0]?.language) {
        "fr" -> AppLanguage.French
        "en" -> AppLanguage.English
        else -> AppLanguage.System
    }
}

/** « Langue du système », « Français », « English » (noms des langues non traduits, sans drapeau). */
@Composable
internal fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.System -> stringResource(R.string.settings_language_system)
    AppLanguage.French -> "Français"
    AppLanguage.English -> "English"
}

/** Ordre des thèmes proposés : Sombre (par défaut), Clair, Système (🔄 D35). */
internal val THEME_OPTIONS = listOf(ThemeMode.Dark, ThemeMode.Light, ThemeMode.System)

@Composable
internal fun themeLabel(theme: ThemeMode): String = stringResource(
    when (theme) {
        ThemeMode.Dark -> R.string.settings_theme_dark
        ThemeMode.Light -> R.string.settings_theme_light
        ThemeMode.System -> R.string.settings_theme_system
    },
)

/** `45 s`, `1 min`, `1 min 30` (§ 2.12). */
@Composable
internal fun restDurationLabel(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return when {
        minutes == 0 -> stringResource(R.string.settings_rest_timer_seconds, rest)
        rest == 0 -> stringResource(R.string.settings_rest_timer_minutes, minutes)
        else -> stringResource(R.string.settings_rest_timer_minutes_seconds, minutes, rest)
    }
}

/** Sheet d'un réglage à options (§ 15.10.3) : titre, phrase d'aide, puis les options. */
@Composable
private fun OptionsSheetContent(title: String, help: String, bottomInset: Dp, options: @Composable ColumnScope.() -> Unit) {
    SheetSurface(bottomInset = bottomInset) {
        SheetTitle(title, AnnotatedString(help), messageStyle = Studio.typography.body)
        options()
    }
}

/**
 * Langue (🔄 D9) : Système · Français · English. `setApplicationLocales` recrée l'activité et
 * mémorise le choix (visible aussi dans les réglages de langue par app d'Android 13+) ; la sheet se
 * ferme d'abord, pour ne pas se rouvrir après la recréation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(onDismiss: () -> Unit) {
    StudioBottomSheet(onDismissRequest = onDismiss) { inset ->
        LanguageSheetContent(
            selected = currentAppLanguage(),
            onSelect = { language ->
                onDismiss()
                if (language != currentAppLanguage()) {
                    val tag = language.tag
                    AppCompatDelegate.setApplicationLocales(
                        if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
                    )
                }
            },
            bottomInset = inset,
        )
    }
}

@Composable
internal fun LanguageSheetContent(selected: AppLanguage, onSelect: (AppLanguage) -> Unit, bottomInset: Dp = 0.dp) {
    OptionsSheetContent(stringResource(R.string.settings_language_title), stringResource(R.string.settings_language_help), bottomInset) {
        val options = AppLanguage.entries
        SegmentedControl(
            options = options.map { if (it == AppLanguage.System) stringResource(R.string.settings_language_system_short) else languageLabel(it) },
            selectedIndex = options.indexOf(selected),
            onSelect = { onSelect(options[it]) },
        )
    }
}

/** Thème (🔄 D10, D35) : Sombre · Clair · Système ; appliqué tout de suite, la sheet se ferme. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSheet(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    StudioBottomSheet(onDismissRequest = onDismiss) { inset -> ThemeSheetContent(selected, onSelect, inset) }
}

@Composable
internal fun ThemeSheetContent(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, bottomInset: Dp = 0.dp) {
    OptionsSheetContent(stringResource(R.string.settings_theme_title), stringResource(R.string.settings_theme_help), bottomInset) {
        SegmentedControl(
            options = THEME_OPTIONS.map { themeLabel(it) },
            selectedIndex = THEME_OPTIONS.indexOf(selected),
            onSelect = { index -> THEME_OPTIONS[index].takeIf { it != selected }?.let(onSelect) },
        )
    }
}

/**
 * Minuteur de repos (§ 2.12) : préréglages de 1 à 5 min ; la durée choisie vaut pour les repos
 * démarrés ensuite (§ 7.2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestTimerSheet(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    StudioBottomSheet(onDismissRequest = onDismiss) { inset -> RestTimerSheetContent(selected, onSelect, inset) }
}

@Composable
internal fun RestTimerSheetContent(selected: Int, onSelect: (Int) -> Unit, bottomInset: Dp = 0.dp) {
    OptionsSheetContent(stringResource(R.string.settings_rest_timer_title), stringResource(R.string.settings_rest_timer_desc), bottomInset) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xs), verticalArrangement = Arrangement.spacedBy(Studio.spacing.xs)) {
            for (seconds in RestTimerRules.PRESETS) {
                FilterChip(restDurationLabel(seconds), selected = seconds == selected, onClick = { onSelect(seconds) })
            }
        }
    }
}
