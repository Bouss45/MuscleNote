package com.musclenote.android.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import com.musclenote.android.data.model.ThemeMode

/**
 * Thème Clair / Sombre choisi dans l'app transmis au système (Android 12+) : l'écran de démarrage
 * (`windowSplashScreenBackground`) et le fond de fenêtre suivent alors ce choix, et non le thème
 * du système (§ 9.1). Le système mémorise ce mode pour l'app ; un mode qui change le thème effectif
 * recrée l'activité (comme un changement de langue).
 */
object SystemNightMode {
    /** Dernier mode transmis dans ce processus : rien n'est renvoyé tant que le choix ne change pas. */
    @Volatile
    private var applied: ThemeMode? = null

    fun apply(context: Context, theme: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || applied == theme) return
        val manager = context.getSystemService(UiModeManager::class.java) ?: return
        manager.setApplicationNightMode(
            when (theme) {
                // « Système » : plus de surcharge, le thème du système s'applique.
                ThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
            },
        )
        applied = theme
    }
}
