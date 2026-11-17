package com.musclenote.android.data.model

import androidx.annotation.StringRes
import com.musclenote.android.R
import com.musclenote.android.data.util.normalizeWorkoutName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Groupes de préréglages, dans l'ordre d'affichage (§ 6.3) ; 🔄 D45 : « Haut / Bas » ajouté. */
enum class PresetGroup { Ppl, UpperLower, Split, Other }

/**
 * Préréglages de séance (SPEC § 6.3, 🔄 D3), stockés dans `Workout.preset`, dans l'ordre d'affichage
 * de leur groupe. 🔄 D45 : Haut du corps, Bas du corps, Abdos et Fessiers (§ 15.19.1).
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
    @SerialName("upperBody") UpperBody("upperBody", PresetGroup.UpperLower, R.string.start_workout_preset_upper_body, "Haut du corps", "Upper body"),
    @SerialName("lowerBody") LowerBody("lowerBody", PresetGroup.UpperLower, R.string.start_workout_preset_lower_body, "Bas du corps", "Lower body"),
    @SerialName("pecs") Pecs("pecs", PresetGroup.Split, R.string.start_workout_preset_pecs, "Pecs", "Chest"),
    @SerialName("dos") Dos("dos", PresetGroup.Split, R.string.start_workout_preset_dos, "Dos", "Back"),
    @SerialName("shoulders") Shoulders("shoulders", PresetGroup.Split, R.string.start_workout_preset_shoulders, "Épaules", "Shoulders"),
    @SerialName("legsSplit") LegsSplit("legsSplit", PresetGroup.Split, R.string.start_workout_preset_legs_split, "Jambes", "Legs (split)"),
    @SerialName("arms") Arms("arms", PresetGroup.Split, R.string.start_workout_preset_arms, "Bras", "Arms"),
    @SerialName("abs") Abs("abs", PresetGroup.Split, R.string.start_workout_preset_abs, "Abdos", "Abs"),
    @SerialName("glutes") Glutes("glutes", PresetGroup.Split, R.string.start_workout_preset_glutes, "Fessiers", "Glutes"),
    @SerialName("fullBody") FullBody("fullBody", PresetGroup.Other, R.string.start_workout_preset_full_body, "Full Body", "Full Body"),
    @SerialName("isolation") Isolation("isolation", PresetGroup.Other, R.string.start_workout_preset_isolation, "Isolation", "Isolation"),
    @SerialName("cardio") Cardio("cardio", PresetGroup.Other, R.string.start_workout_preset_cardio, "Cardio", "Cardio");

    companion object {
        fun fromId(id: String): PresetId? = entries.firstOrNull { it.id == id }

        /** Libellés normalisés → préréglage : tous les FR d'abord, puis les EN qui ne sont pas déjà pris. */
        private val byLabel: Map<String, PresetId> by lazy {
            buildMap {
                for (preset in PresetId.entries) putIfAbsent(normalizeWorkoutName(preset.labelFr), preset)
                for (preset in PresetId.entries) putIfAbsent(normalizeWorkoutName(preset.labelEn), preset)
            }
        }

        /**
         * Préréglage d'une séance d'après son nom (§ 5.3, 🔄 D45 § 15.19.2) : nom normalisé (casse,
         * accents, espaces en trop) comparé aux libellés FR, puis EN. « Legs » donne [Legs] : dans
         * l'app Expo, [LegsSplit] s'appelait aussi « Legs » en anglais (aujourd'hui « Legs (split) »),
         * et ces séances vont à [Legs]. Sert à la migration v1 comme au rattachement des séances
         * perso qui portent le nom d'un préréglage.
         */
        fun fromLegacyName(name: String): PresetId? {
            val wanted = normalizeWorkoutName(name)
            if (wanted.isEmpty()) return null
            return byLabel[wanted]
        }
    }
}
