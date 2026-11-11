package com.musclenote.android.data.model

import androidx.annotation.StringRes
import com.musclenote.android.R
import com.musclenote.android.data.util.normalizeSearch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class PresetGroup { Ppl, Split, Other }

/**
 * Préréglages de séance (SPEC § 6.3, 🔄 D3), stockés dans `Workout.preset`.
 *
 * [labelFr] / [labelEn] doublent les chaînes `start_workout_preset_*` pour retrouver le
 * préréglage des anciennes séances d'après leur nom (§ 5.3) ; un test vérifie qu'ils restent
 * identiques aux ressources.
 */
@Serializable
enum class PresetId(
    val id: String,
    val group: PresetGroup,
    @param:StringRes val labelRes: Int,
    internal val labelFr: String,
    internal val labelEn: String,
) {
    @SerialName("push") Push("push", PresetGroup.Ppl, R.string.start_workout_preset_push, "Push", "Push"),
    @SerialName("pull") Pull("pull", PresetGroup.Ppl, R.string.start_workout_preset_pull, "Pull", "Pull"),
    @SerialName("legs") Legs("legs", PresetGroup.Ppl, R.string.start_workout_preset_legs, "Legs", "Legs"),
    @SerialName("pecs") Pecs("pecs", PresetGroup.Split, R.string.start_workout_preset_pecs, "Pecs", "Chest"),
    @SerialName("dos") Dos("dos", PresetGroup.Split, R.string.start_workout_preset_dos, "Dos", "Back"),
    @SerialName("shoulders") Shoulders("shoulders", PresetGroup.Split, R.string.start_workout_preset_shoulders, "Épaules", "Shoulders"),
    @SerialName("legsSplit") LegsSplit("legsSplit", PresetGroup.Split, R.string.start_workout_preset_legs_split, "Jambes", "Legs"),
    @SerialName("arms") Arms("arms", PresetGroup.Split, R.string.start_workout_preset_arms, "Bras", "Arms"),
    @SerialName("fullBody") FullBody("fullBody", PresetGroup.Other, R.string.start_workout_preset_full_body, "Full Body", "Full Body"),
    @SerialName("isolation") Isolation("isolation", PresetGroup.Other, R.string.start_workout_preset_isolation, "Isolation", "Isolation"),
    @SerialName("cardio") Cardio("cardio", PresetGroup.Other, R.string.start_workout_preset_cardio, "Cardio", "Cardio");

    companion object {
        fun fromId(id: String): PresetId? = entries.firstOrNull { it.id == id }

        /**
         * Préréglage d'une ancienne séance d'après son nom (§ 5.3) : nom normalisé comparé aux
         * libellés FR, puis EN. « Legs » donne donc [Legs] (le préréglage FR « Legs ») et non
         * [LegsSplit], dont le libellé EN est aussi « Legs ».
         */
        fun fromLegacyName(name: String): PresetId? {
            val wanted = normalizeSearch(name)
            if (wanted.isEmpty()) return null
            return entries.firstOrNull { normalizeSearch(it.labelFr) == wanted }
                ?: entries.firstOrNull { normalizeSearch(it.labelEn) == wanted }
        }
    }
}
