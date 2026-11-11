package com.musclenote.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.musclenote.android.R

/**
 * Inter via les polices téléchargeables Google (🔄 D12, SPEC § 9.2). Tant que la police n'est
 * pas disponible (premier lancement hors ligne, appareil sans Play Services), Compose affiche
 * la police système.
 */
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val inter = GoogleFont("Inter")

val InterFontFamily = FontFamily(
    Font(googleFont = inter, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = inter, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = inter, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = inter, fontProvider = googleFontProvider, weight = FontWeight.Bold),
)

private val base = Typography()

private fun TextStyle.inter() = copy(fontFamily = InterFontFamily)

/** Échelle Material 3 en Inter ; les écrans choisissent leurs tailles exactes (§ 9.2). */
val MuscleNoteTypography = Typography(
    displayLarge = base.displayLarge.inter(),
    displayMedium = base.displayMedium.inter(),
    displaySmall = base.displaySmall.inter(),
    headlineLarge = base.headlineLarge.inter(),
    headlineMedium = base.headlineMedium.inter(),
    headlineSmall = base.headlineSmall.inter(),
    titleLarge = base.titleLarge.inter(),
    titleMedium = base.titleMedium.inter(),
    titleSmall = base.titleSmall.inter(),
    bodyLarge = base.bodyLarge.inter(),
    bodyMedium = base.bodyMedium.inter(),
    bodySmall = base.bodySmall.inter(),
    labelLarge = base.labelLarge.inter(),
    labelMedium = base.labelMedium.inter(),
    labelSmall = base.labelSmall.inter(),
)
