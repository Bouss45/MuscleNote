package com.musclenote.android.data.repository

import com.musclenote.android.data.feedback.FeedbackDraft
import com.musclenote.android.data.feedback.FeedbackEnvironment
import com.musclenote.android.data.feedback.FeedbackOutcome
import com.musclenote.android.data.feedback.FeedbackRules
import com.musclenote.android.data.feedback.FeedbackThrottle
import com.musclenote.android.data.feedback.FeedbackType
import com.musclenote.android.data.feedback.WebhookReply
import com.musclenote.android.data.storage.JsonDocument
import com.musclenote.android.data.util.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Où envoyer un avis (le webhook Discord sur l'appareil, un faux dans les tests). */
interface FeedbackSender {
    /** Envoie le corps JSON ; lève une exception si la requête n'aboutit pas (pas de réseau…). */
    suspend fun post(json: String): WebhookReply
}

/**
 * Envoi d'un avis (SPEC § 14, 🔄 D26). [sender] `null` = pas d'URL de webhook au build : la
 * section est masquée ([enabled]). Le brouillon n'est gardé qu'en mémoire ; les délais entre deux
 * envois sont enregistrés dans `feedback.json`. Un envoi à la fois, et seulement sur demande.
 */
class FeedbackRepository(
    private val doc: JsonDocument<FeedbackThrottle>,
    private val time: TimeSource,
    private val sender: FeedbackSender?,
) {
    val enabled: Boolean get() = sender != null

    private val mutex = Mutex()

    private val _draft = MutableStateFlow(FeedbackDraft.EMPTY)

    /** Formulaire en cours de saisie (§ 14.1). */
    val draft: StateFlow<FeedbackDraft> = _draft.asStateFlow()

    private val _resets = MutableStateFlow(0)

    /** Incrémenté quand le formulaire est vidé après un envoi : les champs repartent du brouillon. */
    val resets: StateFlow<Int> = _resets.asStateFlow()

    private val _throttle = MutableStateFlow(FeedbackThrottle.DEFAULT)
    val throttle: StateFlow<FeedbackThrottle> = _throttle.asStateFlow()

    private val _sending = MutableStateFlow(false)

    /** Envoi en cours (indicateur, formulaire désactivé). */
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _result = MutableStateFlow<FeedbackOutcome?>(null)

    /** Issue du dernier envoi, effacée dès que le formulaire est modifié (§ 14.4). */
    val result: StateFlow<FeedbackOutcome?> = _result.asStateFlow()

    suspend fun load() {
        mutex.withLock { _throttle.value = doc.read() ?: FeedbackThrottle.DEFAULT }
    }

    fun setType(type: FeedbackType) = edit { it.copy(type = type) }

    /** Message saisi, limité à 1 000 caractères (un collage trop long est tronqué). */
    fun setMessage(message: String) = edit { it.copy(message = FeedbackRules.truncate(message, FeedbackRules.MESSAGE_MAX)) }

    /** Pseudo saisi, limité à 32 caractères. */
    fun setName(name: String) = edit { it.copy(name = FeedbackRules.truncate(name, FeedbackRules.NAME_MAX)) }

    /** Modification du formulaire : ignorée pendant un envoi ; efface l'issue affichée. */
    private fun edit(transform: (FeedbackDraft) -> FeedbackDraft) {
        if (_sending.value) return
        val before = _draft.value
        _draft.update(transform)
        if (_draft.value != before) _result.value = null
    }

    /** Attente avant le prochain envoi (ms), 0 s'il est permis (§ 14.3). */
    fun remainingWait(): Long = FeedbackRules.remainingWait(_throttle.value, time.now().toEpochMilli())

    /**
     * « Envoyer » : envoie le brouillon avec les informations jointes. `null` si rien n'a été
     * envoyé (pas de webhook, message vide, envoi déjà en cours, délai en cours). Succès →
     * formulaire vidé et une minute d'attente ; 429 → attente demandée par Discord ; sinon le
     * brouillon est gardé et un nouvel essai est permis tout de suite.
     */
    suspend fun send(environment: FeedbackEnvironment): FeedbackOutcome? {
        val sender = sender ?: return null
        if (!_sending.compareAndSet(expect = false, update = true)) return null
        try {
            val draft = _draft.value
            if (!FeedbackRules.isSendable(draft) || remainingWait() > 0) return null
            _result.value = null
            val outcome = try {
                FeedbackRules.outcome(sender.post(FeedbackRules.payload(draft, environment)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FeedbackOutcome.NoConnection
            }
            val now = time.now().toEpochMilli()
            when (outcome) {
                FeedbackOutcome.Sent -> {
                    updateThrottle { it.copy(lastSentAt = now) }
                    _draft.value = FeedbackDraft.EMPTY
                    _resets.update { it + 1 }
                }
                is FeedbackOutcome.RateLimited -> updateThrottle { it.copy(blockedUntil = now + outcome.retryAfterMs) }
                FeedbackOutcome.NoConnection, is FeedbackOutcome.Failed -> Unit
            }
            _result.value = outcome
            return outcome
        } finally {
            _sending.value = false
        }
    }

    private suspend fun updateThrottle(transform: (FeedbackThrottle) -> FeedbackThrottle) {
        mutex.withLock {
            val next = transform(_throttle.value)
            if (next == _throttle.value) return
            _throttle.value = next
            doc.save(next)
        }
    }
}
