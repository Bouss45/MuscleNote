package com.musclenote.android.data.model

import androidx.annotation.StringRes
import com.musclenote.android.R
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/** Moitié de la carte du corps : vue de face ou de dos (§ 15.17.4). */
enum class BodyView { Front, Back }

/**
 * Muscles de la carte du corps (SPEC § 15.17.1, 🔄 D43), dans l'ordre d'affichage. [id] = id de la
 * forme du SVG de la maquette, figé (stocké dans les exercices perso). [view] = la moitié de la carte
 * où il se voit le mieux (sheet « Muscle »).
 */
enum class Muscle(
    val id: String,
    @param:StringRes val nameRes: Int,
    @param:StringRes val scientificRes: Int,
    @param:StringRes val roleRes: Int,
    val view: BodyView,
) {
    Trapezes("trapezes", R.string.muscle_name_trapezes, R.string.muscle_scientific_trapezes, R.string.muscle_role_trapezes, BodyView.Back),
    EpaulesAvant("epaules-avant", R.string.muscle_name_epaules_avant, R.string.muscle_scientific_epaules_avant, R.string.muscle_role_epaules_avant, BodyView.Front),
    EpaulesCote("epaules-cote", R.string.muscle_name_epaules_cote, R.string.muscle_scientific_epaules_cote, R.string.muscle_role_epaules_cote, BodyView.Front),
    EpaulesArriere("epaules-arriere", R.string.muscle_name_epaules_arriere, R.string.muscle_scientific_epaules_arriere, R.string.muscle_role_epaules_arriere, BodyView.Back),
    Pectoraux("pectoraux", R.string.muscle_name_pectoraux, R.string.muscle_scientific_pectoraux, R.string.muscle_role_pectoraux, BodyView.Front),
    Biceps("biceps", R.string.muscle_name_biceps, R.string.muscle_scientific_biceps, R.string.muscle_role_biceps, BodyView.Front),
    Triceps("triceps", R.string.muscle_name_triceps, R.string.muscle_scientific_triceps, R.string.muscle_role_triceps, BodyView.Back),
    AvantBras("avant-bras", R.string.muscle_name_avant_bras, R.string.muscle_scientific_avant_bras, R.string.muscle_role_avant_bras, BodyView.Front),
    Abdominaux("abdominaux", R.string.muscle_name_abdominaux, R.string.muscle_scientific_abdominaux, R.string.muscle_role_abdominaux, BodyView.Front),
    Obliques("obliques", R.string.muscle_name_obliques, R.string.muscle_scientific_obliques, R.string.muscle_role_obliques, BodyView.Front),
    Dorsaux("dorsaux", R.string.muscle_name_dorsaux, R.string.muscle_scientific_dorsaux, R.string.muscle_role_dorsaux, BodyView.Back),
    Lombaires("lombaires", R.string.muscle_name_lombaires, R.string.muscle_scientific_lombaires, R.string.muscle_role_lombaires, BodyView.Back),
    Fessiers("fessiers", R.string.muscle_name_fessiers, R.string.muscle_scientific_fessiers, R.string.muscle_role_fessiers, BodyView.Back),
    Quadriceps("quadriceps", R.string.muscle_name_quadriceps, R.string.muscle_scientific_quadriceps, R.string.muscle_role_quadriceps, BodyView.Front),
    IschioJambiers("ischio-jambiers", R.string.muscle_name_ischio_jambiers, R.string.muscle_scientific_ischio_jambiers, R.string.muscle_role_ischio_jambiers, BodyView.Back),
    Adducteurs("adducteurs", R.string.muscle_name_adducteurs, R.string.muscle_scientific_adducteurs, R.string.muscle_role_adducteurs, BodyView.Front),
    Mollets("mollets", R.string.muscle_name_mollets, R.string.muscle_scientific_mollets, R.string.muscle_role_mollets, BodyView.Back);

    companion object {
        private val byId: Map<String, Muscle> = entries.associateBy { it.id }

        fun fromId(id: String): Muscle? = byId[id]

        /**
         * Lecture tolérante d'une liste de muscles (§ 15.17.8) : ids inconnus et doublons écartés,
         * ordre ramené à celui de la liste.
         */
        fun fromIds(ids: Iterable<String>): List<Muscle> = ids.mapNotNull(::fromId).distinct().sortedBy { it.ordinal }
    }
}

/**
 * Liste de muscles d'un exercice perso, écrite comme un tableau d'ids (§ 15.17.8). La lecture ne
 * refuse jamais : pas un tableau → vide, éléments qui ne sont pas un id connu écartés. Ainsi un fichier
 * écrit par une version future (muscle nouveau) ne rend pas `exercises.json` illisible.
 */
object MuscleListSerializer : KSerializer<List<Muscle>> {
    private val delegate = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<Muscle>) = delegate.serialize(encoder, value.map { it.id })

    override fun deserialize(decoder: Decoder): List<Muscle> {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return Muscle.fromIds(delegate.deserialize(decoder))
        val array = element as? JsonArray ?: return emptyList()
        return Muscle.fromIds(array.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content })
    }
}
