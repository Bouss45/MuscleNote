package com.musclenote.android.data.model

import androidx.annotation.StringRes
import com.musclenote.android.R
import com.musclenote.android.data.util.normalizeSearch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Groupes musculaires, dans l'ordre d'affichage (SPEC § 6.2). Sérialisés en chaînes exactes,
 * accents compris (§ 10.7).
 *
 * [labelFr] / [labelEn] doublent les chaînes `muscle_*` des ressources : la normalisation à
 * l'import (🔄 D16) doit reconnaître les deux langues quelle que soit la langue de l'app, et
 * reste testable sur la JVM. Un test vérifie qu'ils restent identiques aux ressources.
 */
@Serializable
enum class MuscleGroup(
    val id: String,
    @param:StringRes val labelRes: Int,
    internal val labelFr: String,
    internal val labelEn: String,
) {
    @SerialName("dos") Dos("dos", R.string.muscle_dos, "Dos", "Back"),
    @SerialName("pecs") Pecs("pecs", R.string.muscle_pecs, "Pecs", "Chest"),
    @SerialName("biceps") Biceps("biceps", R.string.muscle_biceps, "Biceps", "Biceps"),
    @SerialName("triceps") Triceps("triceps", R.string.muscle_triceps, "Triceps", "Triceps"),
    @SerialName("jambes") Jambes("jambes", R.string.muscle_jambes, "Jambes", "Legs"),
    @SerialName("épaules") Epaules("épaules", R.string.muscle_epaules, "Épaules", "Shoulders"),
    @SerialName("abdos") Abdos("abdos", R.string.muscle_abdos, "Abdos", "Abs"),
    @SerialName("cardio") Cardio("cardio", R.string.muscle_cardio, "Cardio", "Cardio"),
    @SerialName("avant-bras") AvantBras("avant-bras", R.string.muscle_avant_bras, "Avant-bras", "Forearms");

    companion object {
        /** Groupe par défaut du formulaire d'exercice, et repli des valeurs inconnues (D16). */
        val FALLBACK = Dos

        fun fromId(id: String): MuscleGroup? = entries.firstOrNull { it.id == id }

        /**
         * 🔄 D16 : valeur quelconque (import) → groupe. Normalisée (sans accents ni casse, espaces
         * et tirets ignorés) puis comparée aux 9 ids et aux libellés FR/EN ; inconnue → [FALLBACK].
         */
        fun fromLegacy(raw: String): MuscleGroup {
            fromId(raw)?.let { return it }
            val wanted = compact(raw)
            return entries.firstOrNull { group ->
                compact(group.id) == wanted || compact(group.labelFr) == wanted || compact(group.labelEn) == wanted
            } ?: FALLBACK
        }

        private fun compact(s: String) = normalizeSearch(s).replace(" ", "").replace("-", "")
    }
}
