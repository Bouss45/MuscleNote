package com.musclenote.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.musclenote.android.data.util.TimeSource
import java.time.LocalDate

/**
 * Jour local courant, relu à chaque reprise de l'écran : la semaine de la série hebdomadaire, la
 * valeur « aujourd'hui » et la fenêtre de 7 jours changent quand l'app revient après minuit.
 */
@Composable
fun rememberToday(time: TimeSource): LocalDate {
    var today by remember(time) { mutableStateOf(time.today()) }
    LifecycleResumeEffect(time) {
        today = time.today()
        onPauseOrDispose { }
    }
    return today
}
