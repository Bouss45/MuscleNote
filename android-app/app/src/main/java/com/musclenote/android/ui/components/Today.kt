package com.musclenote.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.ui.LocalAppContainer
import java.time.LocalDate

/**
 * Jour local courant de l'app (§ 3.12) : change à minuit, au retour au premier plan et au
 * changement de fuseau (`AppContainer.currentDay`). Série hebdomadaire, valeur « aujourd'hui »,
 * fenêtre de 7 jours et « pris aujourd'hui » suivent.
 */
@Composable
fun rememberToday(): LocalDate {
    val today by LocalAppContainer.current.currentDay.today.collectAsStateWithLifecycle()
    return today
}
