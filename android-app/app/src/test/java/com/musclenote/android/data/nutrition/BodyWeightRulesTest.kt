package com.musclenote.android.data.nutrition

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.Workout
import com.musclenote.android.data.util.formatInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Pesées manuelles (§ 3.11), poids du jour et dernière pesée connue (§ 15.13.1, 🔄 D38). */
class BodyWeightRulesTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")

    @Test
    fun `une pesée est datée à midi local du jour choisi`() {
        // Hiver : midi à Paris = 11 h UTC ; été : 10 h UTC.
        val winter = BodyWeightRules.add(emptyList(), LocalDate.parse("2026-01-15"), 80.0, zone)
        assertEquals(listOf(BodyWeightEntry("2026-01-15T11:00:00.000Z", 80.0)), winter)
        assertEquals("2026-07-15T10:00:00.000Z", BodyWeightRules.add(emptyList(), LocalDate.parse("2026-07-15"), 80.0, zone).single().date)
    }

    @Test
    fun `une pesée remplace celle du même jour local et la liste reste triée`() {
        val entries = listOf(
            BodyWeightEntry("2026-03-14T11:00:00.000Z", 79.0),
            BodyWeightEntry("2026-03-10T22:30:00.000Z", 80.0), // 23 h 30 le 10 à Paris
            BodyWeightEntry("2026-03-08T11:00:00.000Z", 81.0),
        )
        val next = BodyWeightRules.add(entries, LocalDate.parse("2026-03-10"), 79.5, zone)
        assertEquals(
            listOf(
                BodyWeightEntry("2026-03-14T11:00:00.000Z", 79.0),
                BodyWeightEntry("2026-03-10T11:00:00.000Z", 79.5),
                BodyWeightEntry("2026-03-08T11:00:00.000Z", 81.0),
            ),
            next,
        )
    }

    @Test
    fun `suppression par égalité exacte de la date`() {
        val entries = listOf(BodyWeightEntry("2026-03-14T11:00:00.000Z", 79.0), BodyWeightEntry("2026-03-08T11:00:00.000Z", 81.0))
        assertEquals(listOf(entries[1]), BodyWeightRules.remove(entries, "2026-03-14T11:00:00.000Z"))
        assertEquals(entries, BodyWeightRules.remove(entries, "2026-03-14T11:00:00Z"))
    }

    @Test
    fun `le poids doit être compris entre 0 exclu et 500`() {
        assertFalse(BodyWeightRules.isValidWeight(0.0))
        assertFalse(BodyWeightRules.isValidWeight(-70.0))
        assertTrue(BodyWeightRules.isValidWeight(0.1))
        assertTrue(BodyWeightRules.isValidWeight(500.0))
        assertFalse(BodyWeightRules.isValidWeight(500.1))
    }

    // ─── Poids du jour, dernière pesée connue, poids d'une séance (§ 15.13.1) ────────

    /** Jeudi 12 novembre 2026, heure d'hiver à Paris (UTC+1). */
    private val today = LocalDate.parse("2026-11-12")

    private fun workout(id: String, date: String, bodyWeight: Double) =
        Workout(id = id, name = "Push", date = date, bodyWeight = bodyWeight, exercises = emptyList())

    /** Pesée du jour [day] à midi local, comme l'enregistre l'app. */
    private fun weighIn(day: String, weight: Double) =
        BodyWeightEntry(formatInstant(BodyWeightRules.noonOf(LocalDate.parse(day), zone)), weight)

    @Test
    fun `pesé ce jour-là - une pesée, une séance terminée ou la séance en cours avec un poids`() {
        assertEquals(78.4, BodyWeightRules.weightOn(today, emptyList(), listOf(weighIn("2026-11-12", 78.4)), zone)!!.weight, 0.0)
        assertEquals(79.0, BodyWeightRules.weightOn(today, listOf(workout("w", "2026-11-12T17:30:00.000Z", 79.0)), emptyList(), zone)!!.weight, 0.0)
        // La séance en cours (passée avec les séances terminées) compte comme une séance de son jour.
        val current = workout("current", "2026-11-12T07:15:00.000Z", 78.0)
        val finished = workout("hier", "2026-11-11T17:00:00.000Z", 80.0)
        assertEquals(78.0, BodyWeightRules.weightOn(today, listOf(current, finished), emptyList(), zone)!!.weight, 0.0)
    }

    @Test
    fun `pas pesé ce jour-là - séance du jour sans poids, pesée d'un autre jour, jour local du fuseau`() {
        assertNull(BodyWeightRules.weightOn(today, listOf(workout("w", "2026-11-12T17:30:00.000Z", 0.0)), emptyList(), zone))
        assertNull(BodyWeightRules.weightOn(today, emptyList(), listOf(weighIn("2026-11-11", 78.4)), zone))
        // 23 h 30 UTC le 12 = 0 h 30 le 13 à Paris : pas le 12.
        assertNull(BodyWeightRules.weightOn(today, listOf(workout("w", "2026-11-12T23:30:00.000Z", 79.0)), emptyList(), zone))
        assertNull(BodyWeightRules.weightOn(today, emptyList(), emptyList(), zone))
    }

    @Test
    fun `la pesée du jour l'emporte sur le poids de la séance du jour`() {
        val day = BodyWeightRules.weightOn(today, listOf(workout("w", "2026-11-12T17:30:00.000Z", 79.0)), listOf(weighIn("2026-11-12", 78.4)), zone)!!
        assertEquals(78.4, day.weight, 0.0)
        assertTrue(day.weighIn)
    }

    @Test
    fun `dernière pesée connue - le poids du jour le plus récent jusqu'au jour donné`() {
        val workouts = listOf(
            workout("sans-poids", "2026-11-11T17:00:00.000Z", 0.0),
            workout("lundi", "2026-11-08T17:00:00.000Z", 80.0),
        )
        val weighIns = listOf(weighIn("2026-11-14", 77.0), weighIn("2026-11-10", 78.4))
        val latest = BodyWeightRules.latestKnown(today, workouts, weighIns, zone)!!
        assertEquals(LocalDate.parse("2026-11-10"), latest.day)
        assertEquals(78.4, latest.weight, 0.0)
        assertEquals(80.0, BodyWeightRules.latestKnown(LocalDate.parse("2026-11-09"), workouts, weighIns, zone)!!.weight, 0.0)
        assertNull(BodyWeightRules.latestKnown(LocalDate.parse("2026-11-07"), workouts, weighIns, zone))
    }

    @Test
    fun `poids d'une séance - le sien, sinon la dernière pesée connue à son jour, sinon rien`() {
        val withWeight = workout("w", "2026-11-12T17:00:00.000Z", 81.0)
        assertEquals(81.0, BodyWeightRules.sessionWeight(withWeight, emptyList(), listOf(weighIn("2026-11-12", 78.4)), zone)!!, 0.0)

        val skipped = workout("passée", "2026-11-12T17:00:00.000Z", 0.0)
        // Le jour même compris (pesée de midi, séance du soir).
        assertEquals(78.4, BodyWeightRules.sessionWeight(skipped, listOf(skipped), listOf(weighIn("2026-11-12", 78.4)), zone)!!, 0.0)
        // Sinon une autre séance plus ancienne.
        val older = workout("mardi", "2026-11-10T17:00:00.000Z", 80.0)
        assertEquals(80.0, BodyWeightRules.sessionWeight(skipped, listOf(skipped, older), emptyList(), zone)!!, 0.0)
        // Une pesée postérieure ne compte pas, rien de connu → rien.
        assertNull(BodyWeightRules.sessionWeight(skipped, listOf(skipped), listOf(weighIn("2026-11-13", 78.0)), zone))
        assertNull(BodyWeightRules.sessionWeight(skipped, listOf(skipped), emptyList(), zone))
        assertNull(BodyWeightRules.sessionWeight(workout("??", "pas une date", 0.0), emptyList(), listOf(weighIn("2026-11-12", 78.4)), zone))
    }

    @Test
    fun `le 0 n'est jamais une valeur - poids nul ou négatif ignoré partout`() {
        val zero = workout("zéro", "2026-11-12T17:00:00.000Z", 0.0)
        val negative = workout("négatif", "2026-11-11T17:00:00.000Z", -3.0)
        // Rien d'affiché : le poids d'une séance est absent, jamais 0.
        assertNull(BodyWeightRules.sessionWeight(zero, listOf(zero, negative), emptyList(), zone))
        assertNull(BodyWeightRules.sessionWeight(negative, listOf(zero, negative), emptyList(), zone))
        // Ni poids du jour ni point de courbe (une pesée à 0 venue d'un fichier non plus).
        assertTrue(BodyWeightRules.dailyWeights(listOf(zero, negative), listOf(weighIn("2026-11-10", 0.0)), zone).isEmpty())
    }

    @Test
    fun `étape du poids - pas de champ quand le poids du jour est connu`() {
        assertEquals(
            StartWeightPrompt.WeighedToday(78.4),
            BodyWeightRules.startPrompt(today, emptyList(), listOf(weighIn("2026-11-12", 78.4)), zone),
        )
        assertEquals(
            StartWeightPrompt.WeighedToday(79.0),
            BodyWeightRules.startPrompt(today, listOf(workout("matin", "2026-11-12T07:00:00.000Z", 79.0)), emptyList(), zone),
        )
    }

    @Test
    fun `étape du poids - champ vide, aide = dernière pesée et son ancienneté en jours locaux`() {
        assertEquals(StartWeightPrompt.Ask(78.4, 1), BodyWeightRules.startPrompt(today, emptyList(), listOf(weighIn("2026-11-11", 78.4)), zone))
        // Séance d'aujourd'hui passée sans pesée : on demande encore, avec la dernière pesée.
        val skippedToday = workout("passée", "2026-11-12T07:00:00.000Z", 0.0)
        assertEquals(StartWeightPrompt.Ask(78.4, 3), BodyWeightRules.startPrompt(today, listOf(skippedToday), listOf(weighIn("2026-11-09", 78.4)), zone))
        // Séance d'hier soir à 23 h 30 (heure de Paris) : 1 jour, pas 0.
        assertEquals(StartWeightPrompt.Ask(80.0, 1), BodyWeightRules.startPrompt(today, listOf(workout("hier", "2026-11-11T22:30:00.000Z", 80.0)), emptyList(), zone))
        assertEquals(StartWeightPrompt.Ask(null, null), BodyWeightRules.startPrompt(today, emptyList(), emptyList(), zone))
    }

    @Test
    fun `étape du poids - champ vide, les pas partent de la dernière pesée`() {
        assertEquals(78.4, StartWeightPrompt.Ask(78.4, 3).stepBase(""), 0.0)
        assertEquals(80.5, StartWeightPrompt.Ask(78.4, 3).stepBase("80,5"), 0.0)
        assertEquals(0.0, StartWeightPrompt.Ask(null, null).stepBase(""), 0.0)
    }
}
