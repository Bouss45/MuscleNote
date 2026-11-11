package com.musclenote.android.data.repository

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.model.TakenRecord
import com.musclenote.android.data.nutrition.BodyWeightRules
import com.musclenote.android.data.storage.JsonDocument
import com.musclenote.android.data.util.TimeSource
import com.musclenote.android.data.util.dayKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * Dépôt nutrition : pesées, compléments, « pris aujourd'hui » et suivi calories / eau
 * (SPEC § 3.11, § 3.12, § 4.2). Même discipline que [WorkoutRepository].
 */
class NutritionRepository(
    private val bodyWeightsDoc: JsonDocument<List<BodyWeightEntry>>,
    private val supplementsDoc: JsonDocument<List<Supplement>>,
    private val takenDoc: JsonDocument<Map<String, String>>,
    private val trackerDoc: JsonDocument<DailyTracker>,
    private val time: TimeSource,
) {
    private val mutex = Mutex()

    private val _bodyWeights = MutableStateFlow<List<BodyWeightEntry>>(emptyList())

    /** Pesées, de la plus récente à la plus ancienne. */
    val bodyWeights: StateFlow<List<BodyWeightEntry>> = _bodyWeights.asStateFlow()

    private val _supplements = MutableStateFlow<List<Supplement>>(emptyList())
    val supplements: StateFlow<List<Supplement>> = _supplements.asStateFlow()

    private val _taken = MutableStateFlow<TakenRecord>(emptyMap())
    val taken: StateFlow<TakenRecord> = _taken.asStateFlow()

    private val _tracker = MutableStateFlow(DailyTracker.DEFAULT)
    val tracker: StateFlow<DailyTracker> = _tracker.asStateFlow()

    suspend fun load() {
        mutex.withLock {
            val supplements = supplementsDoc.read().orEmpty()
            val storedTaken = takenDoc.read().orEmpty()
            // § 3.12 (🔄 D15) : on ne garde que les prises d'aujourd'hui de compléments existants.
            val today = dayKey(time.today())
            val ids = supplements.mapTo(HashSet()) { it.id }
            val taken = storedTaken.filter { (id, day) -> id in ids && day == today }
            if (taken != storedTaken) takenDoc.save(taken)

            _bodyWeights.value = BodyWeightRules.sortedNewestFirst(bodyWeightsDoc.read().orEmpty(), time.zone())
            _supplements.value = supplements
            _taken.value = taken
            _tracker.value = trackerDoc.read() ?: DailyTracker.DEFAULT
        }
    }

    /**
     * Nouvelle pesée (§ 3.11) pour le jour local [day], à midi : remplace celle du même jour.
     * Refusée (`false`) hors de `0 < poids ≤ 500`.
     */
    suspend fun addBodyWeight(day: LocalDate, weight: Double): Boolean {
        if (!BodyWeightRules.isValidWeight(weight)) return false
        mutex.withLock { commitBodyWeights(BodyWeightRules.add(_bodyWeights.value, day, weight, time.zone())) }
        return true
    }

    /** Suppression immédiate d'une pesée, par sa date exacte. */
    suspend fun removeBodyWeight(date: String) {
        mutex.withLock {
            val next = BodyWeightRules.remove(_bodyWeights.value, date)
            if (next != _bodyWeights.value) commitBodyWeights(next)
        }
    }

    private fun commitBodyWeights(next: List<BodyWeightEntry>) {
        _bodyWeights.value = next
        bodyWeightsDoc.save(next)
    }

    /** Import (§ 5.4) : documents présents remplacés, écriture attendue. */
    suspend fun replaceFromBackup(
        bodyWeights: List<BodyWeightEntry>?,
        supplements: List<Supplement>?,
        taken: TakenRecord?,
        tracker: DailyTracker?,
    ) {
        mutex.withLock {
            bodyWeights?.let { bodyWeightsDoc.saveNow(it) }
            supplements?.let { supplementsDoc.saveNow(it) }
            taken?.let { takenDoc.saveNow(it) }
            tracker?.let { trackerDoc.saveNow(it) }
        }
    }
}
