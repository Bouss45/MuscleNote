package com.musclenote.android.data.repository

import com.musclenote.android.data.model.BodyWeightEntry
import com.musclenote.android.data.model.DailyTracker
import com.musclenote.android.data.model.ProteinGoal
import com.musclenote.android.data.model.Supplement
import com.musclenote.android.data.model.TakenRecord
import com.musclenote.android.data.nutrition.BodyWeightRules
import com.musclenote.android.data.nutrition.NutritionRules
import com.musclenote.android.data.nutrition.ProteinRules
import com.musclenote.android.data.nutrition.StockRules
import com.musclenote.android.data.nutrition.SupplementReminders
import com.musclenote.android.data.storage.JsonDocument
import com.musclenote.android.data.util.TimeSource
import com.musclenote.android.data.util.dayKey
import com.musclenote.android.data.util.newId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * Dépôt nutrition : pesées, compléments, « pris aujourd'hui » et suivi calories / eau
 * (SPEC § 3.11, § 3.12, § 4.2). Même discipline que [WorkoutRepository]. Chaque modification des
 * compléments est suivie d'une synchronisation de leurs alarmes ([reminders], § 7.3). Le jour des
 * journaux est le jour local au moment de la mutation.
 */
class NutritionRepository(
    private val bodyWeightsDoc: JsonDocument<List<BodyWeightEntry>>,
    private val supplementsDoc: JsonDocument<List<Supplement>>,
    private val takenDoc: JsonDocument<Map<String, String>>,
    private val trackerDoc: JsonDocument<DailyTracker>,
    private val time: TimeSource,
    private val reminders: SupplementReminders = SupplementReminders.None,
    private val idGenerator: () -> String = { newId() },
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
            val taken = NutritionRules.cleanTaken(storedTaken, supplements.mapTo(HashSet()) { it.id }, todayKey())
            if (taken != storedTaken) takenDoc.save(taken)

            _bodyWeights.value = BodyWeightRules.sortedNewestFirst(bodyWeightsDoc.read().orEmpty(), time.zone())
            _supplements.value = supplements
            _taken.value = taken
            _tracker.value = trackerDoc.read() ?: DailyTracker.DEFAULT
        }
    }

    // ─── Suivi du jour (§ 2.11, § 3.12) ───────────────────────────────────────────────

    /** « + Ajouter » : kcal du jour += [amount], résultat ≥ 0. */
    suspend fun addCalories(amount: Int) = updateTracker { NutritionRules.addCalories(it, todayKey(), amount) }

    /** « = Définir » et ⟲ : kcal du jour = [value], ≥ 0. */
    suspend fun setCaloriesToday(value: Int) = updateTracker { NutritionRules.setCalories(it, todayKey(), value) }

    /** Eau du jour += [deltaMl], résultat ≥ 0. */
    suspend fun addWater(deltaMl: Int) = updateTracker { NutritionRules.addWater(it, todayKey(), deltaMl) }

    /** « Total du jour » de l'eau (🔄 D35, § 15.10.2) : valeur du jour = [ml], ≥ 0 ; 0 la remet à zéro. */
    suspend fun setWaterToday(ml: Int) = updateTracker { NutritionRules.setWater(it, todayKey(), ml) }

    /** Objectif de calories à partir d'aujourd'hui (🔄 D15) ; ≤ 0 ignoré. */
    suspend fun setCalorieGoal(kcal: Int) = updateTracker { NutritionRules.setCalorieGoal(it, todayKey(), kcal) }

    /** Objectif d'eau (ml) à partir d'aujourd'hui ; ≤ 0 ignoré. */
    suspend fun setWaterGoal(ml: Int) = updateTracker { NutritionRules.setWaterGoal(it, todayKey(), ml) }

    /** Protéines du jour += [grams] (🔄 D42, § 15.16.3), résultat ≥ 0. */
    suspend fun addProtein(grams: Int) = updateTracker { NutritionRules.addProtein(it, todayKey(), grams) }

    /** « Total du jour » des protéines : valeur du jour = [grams], ≥ 0. */
    suspend fun setProteinToday(grams: Int) = updateTracker { NutritionRules.setProtein(it, todayKey(), grams) }

    /** Objectif de protéines (non daté, § 15.16.1), ramené aux règles avant d'être enregistré. */
    suspend fun setProteinGoal(goal: ProteinGoal) = updateTracker { it.copy(proteinGoal = ProteinRules.normalized(goal)) }

    private suspend fun updateTracker(transform: (DailyTracker) -> DailyTracker) {
        mutex.withLock {
            val current = _tracker.value
            val next = transform(current)
            if (next != current) {
                _tracker.value = next
                trackerDoc.save(next)
            }
        }
    }

    // ─── Compléments (§ 2.11, § 3.12) ─────────────────────────────────────────────────

    /** Coche ou décoche « pris aujourd'hui ». */
    suspend fun toggleTaken(supplementId: String) {
        mutex.withLock {
            val today = todayKey()
            val next = NutritionRules.toggleTaken(_taken.value, supplementId, today)
            commitTaken(next)
            // Historique des prises (§ 15.15.4, 🔄 D41) : la prise du jour suit la coche, avec les doses par
            // jour du complément (🔄 D42) ; le stock se décompte de l'historique.
            val taken = NutritionRules.isTaken(next, supplementId, today)
            val list = _supplements.value
            val updated = list.map {
                if (it.id == supplementId) it.copy(intakes = NutritionRules.recordIntake(it.intakes, today, taken, it.dosesPerDay)) else it
            }
            if (updated != list) commitSupplementData(updated)
        }
    }

    /**
     * Nouveau complément, en fin de liste ; [times] = rappels `(heure, minute)`, ignorés si
     * [remindersEnabled] est faux. Nom vide → rien (`null`).
     */
    suspend fun addSupplement(
        name: String,
        dosage: String,
        unit: String,
        remindersEnabled: Boolean,
        times: List<Pair<Int, Int>>,
    ): Supplement? {
        if (name.isBlank()) return null
        return mutex.withLock {
            val supplement = NutritionRules.newSupplement(name, dosage, unit, remindersEnabled, times, idGenerator)
            commitSupplements(_supplements.value + supplement)
            supplement
        }
    }

    /** Suppression (confirmée dans l'UI) : ses alarmes et son entrée « pris » disparaissent. */
    suspend fun deleteSupplement(supplementId: String) {
        mutex.withLock {
            val list = _supplements.value
            if (list.none { it.id == supplementId }) return
            commitSupplements(list.filterNot { it.id == supplementId })
            if (supplementId in _taken.value) commitTaken(_taken.value - supplementId)
        }
    }

    /** Interrupteur des rappels ; les rappels sont conservés quand il est coupé. */
    suspend fun setRemindersEnabled(supplementId: String, enabled: Boolean) =
        updateSupplement(supplementId) { it.copy(reminderEnabled = enabled) }

    /** « Ajouter un rappel » : dernier + 2 h, même minute (08:00 sans rappel). */
    suspend fun addReminder(supplementId: String) =
        updateSupplement(supplementId) { NutritionRules.addReminder(it, idGenerator) }

    /** Nouvelle heure d'un rappel, appliquée tout de suite. */
    suspend fun updateReminder(supplementId: String, reminderId: String, hour: Int, minute: Int) =
        updateSupplement(supplementId) { supplement ->
            supplement.copy(
                reminders = supplement.reminders.map {
                    if (it.id == reminderId) it.copy(hour = hour.coerceIn(0, 23), minute = minute.coerceIn(0, 59)) else it
                },
            )
        }

    suspend fun removeReminder(supplementId: String, reminderId: String) =
        updateSupplement(supplementId) { supplement -> supplement.copy(reminders = supplement.reminders.filterNot { it.id == reminderId }) }

    // ─── Stock et doses par jour (§ 15.16.4, 🔄 D42) : sans effet sur les alarmes ─────────────

    /** Stock saisi aujourd'hui, en doses (0 compris). */
    suspend fun setStock(supplementId: String, doses: Int) = updateSupplementData(supplementId) { StockRules.setStock(it, doses, todayKey()) }

    /** « Racheté » : [added] doses de plus. */
    suspend fun restock(supplementId: String, added: Int) = updateSupplementData(supplementId) { StockRules.restock(it, added, todayKey()) }

    /** « Ne plus suivre le stock ». */
    suspend fun clearStock(supplementId: String) = updateSupplementData(supplementId, StockRules::clearStock)

    /** Seuil du stock bas, en jours. */
    suspend fun setLowStockDays(supplementId: String, days: Int) = updateSupplementData(supplementId) { StockRules.setLowStockDays(it, days) }

    /** Doses par jour ; la prise d'aujourd'hui suit si le complément est coché. */
    suspend fun setDosesPerDay(supplementId: String, doses: Int) =
        updateSupplementData(supplementId) { StockRules.setDosesPerDay(it, doses, todayKey()) }

    private suspend fun updateSupplementData(supplementId: String, transform: (Supplement) -> Supplement) {
        mutex.withLock {
            val list = _supplements.value
            val next = list.map { if (it.id == supplementId) transform(it) else it }
            if (next != list) commitSupplementData(next)
        }
    }

    private suspend fun updateSupplement(supplementId: String, transform: (Supplement) -> Supplement) {
        mutex.withLock {
            val list = _supplements.value
            val next = list.map { if (it.id == supplementId) transform(it) else it }
            if (next != list) commitSupplements(next)
        }
    }

    /** Enregistre la liste, puis synchronise les alarmes (annule celles qui n'ont plus lieu d'être). */
    private suspend fun commitSupplements(next: List<Supplement>) {
        val previous = _supplements.value
        _supplements.value = next
        supplementsDoc.save(next)
        reminders.sync(previous) { _supplements.value }
    }

    /**
     * Historique des prises, stock ou doses par jour : les rappels ne changent pas, pas de
     * resynchronisation des alarmes.
     */
    private fun commitSupplementData(next: List<Supplement>) {
        _supplements.value = next
        supplementsDoc.save(next)
    }

    private fun commitTaken(next: TakenRecord) {
        _taken.value = next
        takenDoc.save(next)
    }

    private fun todayKey(): String = dayKey(time.today())

    // ─── Pesées (§ 3.11) ──────────────────────────────────────────────────────────────

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
