package com.musclenote.android.data.backup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.roundToInt

// Lecture tolérante de valeurs JSON quelconques (fichiers d'import). Sémantique calquée sur les
// tests de l'app Expo : un nombre est un nombre JSON fini (jamais une chaîne), etc.

internal fun JsonElement?.isAbsentOrNull(): Boolean = this == null || this is JsonNull

internal fun JsonElement?.stringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonElement?.numberOrNull(): Double? =
    (this as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }?.doubleOrNull?.takeIf { it.isFinite() }

internal fun JsonElement?.intOrNull(): Int? = numberOrNull()?.roundToIntOrNull()

internal fun JsonElement?.booleanOrNull(): Boolean? =
    (this as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }?.booleanOrNull

internal fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement?.arrayOrNull(): JsonArray? = this as? JsonArray

internal fun JsonElement?.isString(): Boolean = stringOrNull() != null

internal fun JsonElement?.isNonEmptyString(): Boolean = !stringOrNull().isNullOrEmpty()

internal fun JsonElement?.isFiniteNumber(): Boolean = numberOrNull() != null

/** Arrondi façon `Math.round` de JavaScript, `null` hors des bornes d'un `Int`. */
internal fun Double.roundToIntOrNull(): Int? =
    if (this >= Int.MIN_VALUE.toDouble() && this <= Int.MAX_VALUE.toDouble()) roundToInt() else null

/** Copie modifiable d'un objet JSON (l'ordre des clés est conservé). */
internal fun JsonObject.edit(block: MutableMap<String, JsonElement>.() -> Unit): JsonObject =
    JsonObject(LinkedHashMap(this).apply(block))
