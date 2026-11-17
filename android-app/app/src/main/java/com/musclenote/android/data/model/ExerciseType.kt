package com.musclenote.android.data.model

import kotlin.math.roundToLong
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Type d'un exercice (SPEC § 15.18.1, 🔄 D44) : « charge × reps » (défaut, comportement d'avant D44 :
 * force, dont le poids du corps) ou « au temps ». Écrit `"reps"` / `"timed"` ; le défaut est omis.
 */
@Serializable(with = ExerciseTypeSerializer::class)
enum class ExerciseType(val id: String) {
    Reps("reps"),
    Timed("timed");

    companion object {
        /** Lecture tolérante (§ 15.18.8) : absent ou inconnu (version future) → [Reps]. */
        fun fromId(id: String?): ExerciseType = entries.firstOrNull { it.id == id } ?: Reps
    }
}

/**
 * Ce qu'on saisit dans une série d'un exercice (§ 4.1, § 15.18.1) : le groupe `cardio` décide du
 * cardio, sinon le type décide entre force (charge × reps) et au temps (durée et lest).
 */
enum class SetKind {
    Strength,
    Cardio,
    Timed;

    companion object {
        fun of(muscleGroup: MuscleGroup, type: ExerciseType): SetKind = when {
            muscleGroup == MuscleGroup.Cardio -> Cardio
            type == ExerciseType.Timed -> Timed
            else -> Strength
        }
    }
}

/** `type` d'un exercice : jamais refusé à la lecture, une valeur inconnue vaut [ExerciseType.Reps]. */
object ExerciseTypeSerializer : KSerializer<ExerciseType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ExerciseType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ExerciseType) = encoder.encodeString(value.id)

    override fun deserialize(decoder: Decoder): ExerciseType {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return ExerciseType.fromId(decoder.decodeString())
        return ExerciseType.fromId((element as? JsonPrimitive)?.takeIf { it.isString }?.content)
    }
}

/**
 * Durée d'une série au temps, en secondes (§ 15.18.8) : lue sans jamais refuser — pas un nombre →
 * absente ; nombre → arrondi à l'entier (demi vers +∞), négatif → 0.
 */
object HoldSecondsSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("HoldSeconds", PrimitiveKind.INT).nullable

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }

    override fun deserialize(decoder: Decoder): Int? {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeInt()
        if (element is JsonNull) return null
        return secondsOf(element as? JsonPrimitive)
    }

    /** Nombre JSON → secondes entières ≥ 0 ; une chaîne, un booléen ou un nombre non fini → `null`. */
    fun secondsOf(primitive: JsonPrimitive?): Int? {
        if (primitive == null || primitive.isString) return null
        val number = primitive.doubleOrNull?.takeIf { it.isFinite() } ?: return null
        return number.roundToLong().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }
}
