package com.musclenote.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Retours haptiques de l'app (SPEC § 9.3), sur les types Compose du système. */
class Haptics(private val feedback: HapticFeedback) {
    /** Ajouts, sélection d'une puce. */
    fun light() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** Valider une série, retirer un élément. */
    fun medium() = feedback.performHapticFeedback(HapticFeedbackType.ContextClick)

    /** Démarrer une séance. */
    fun heavy() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)

    /** Déplacement pendant un glisser-déposer. */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)

    /** Enregistrement réussi, séance terminée. */
    fun success() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** Saisie refusée, suppression. */
    fun warning() = feedback.performHapticFeedback(HapticFeedbackType.Reject)
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}
