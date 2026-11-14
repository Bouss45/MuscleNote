package com.musclenote.android.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.BuildConfig
import com.musclenote.android.R
import com.musclenote.android.data.feedback.FeedbackEnvironment
import com.musclenote.android.data.feedback.FeedbackOutcome
import com.musclenote.android.data.feedback.FeedbackRules
import com.musclenote.android.data.feedback.FeedbackType
import com.musclenote.android.ui.LocalAppContainer
import com.musclenote.android.ui.components.appLocale
import com.musclenote.android.ui.components.formatInteger
import com.musclenote.android.ui.components.rememberHaptics
import com.musclenote.android.ui.designsystem.components.FilterChip
import com.musclenote.android.ui.designsystem.components.SheetSurface
import com.musclenote.android.ui.designsystem.components.SheetTitle
import com.musclenote.android.ui.designsystem.components.StudioBottomSheet
import com.musclenote.android.ui.designsystem.components.StudioButton
import com.musclenote.android.ui.designsystem.components.StudioTextField
import com.musclenote.android.ui.designsystem.theme.Studio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Lignes visibles du message : 4, puis il grandit jusqu'à 8 et défile (§ 14.1). */
private const val MESSAGE_MIN_LINES = 4
private const val MESSAGE_MAX_LINES = 8

/**
 * Sheet « Ton avis » (SPEC § 14.1, § 15.10.3, 🔄 D26) : type (puces), message (1 000 caractères,
 * compteur), pseudo facultatif, [Envoyer], informations jointes, résultat (§ 14.4).
 *
 * Les champs gardent leur texte localement (saisie sans décalage) et le recopient à chaque frappe
 * dans le brouillon du dépôt, qui survit à la fermeture de la sheet et au changement d'onglet ; ils
 * repartent du brouillon quand il est vidé après un envoi réussi (`FeedbackRepository.resets`).
 * L'envoi passe par `launchMutation` : fermer la sheet ne l'interrompt pas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackSheet(onDismiss: () -> Unit) {
    StudioBottomSheet(onDismissRequest = onDismiss) { inset -> FeedbackSheetContent(bottomInset = inset) }
}

@Composable
internal fun FeedbackSheetContent(bottomInset: Dp = 0.dp) {
    val container = LocalAppContainer.current
    val feedback = container.feedback
    val draft by feedback.draft.collectAsStateWithLifecycle()
    val resets by feedback.resets.collectAsStateWithLifecycle()
    val throttle by feedback.throttle.collectAsStateWithLifecycle()
    val sending by feedback.sending.collectAsStateWithLifecycle()
    val result by feedback.result.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val focusManager = LocalFocusManager.current

    var message by remember(resets) { mutableStateOf(feedback.draft.value.message) }
    var name by remember(resets) { mutableStateOf(feedback.draft.value.name) }

    // Délai entre deux envois (§ 14.3), recalculé à chaque changement de seconde affichée.
    var waitMs by remember { mutableLongStateOf(feedback.remainingWait()) }
    LaunchedEffect(throttle) {
        while (true) {
            waitMs = feedback.remainingWait()
            if (waitMs <= 0) break
            val untilNextSecond = waitMs % 1000
            delay(if (untilNextSecond == 0L) 1000 else untilNextSecond)
        }
    }

    // Informations jointes (§ 14.2) : mêmes valeurs que le rapport de plantage, langue des textes.
    val language = appLocale().toLanguageTag()
    val environment = remember(language) {
        FeedbackEnvironment(
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            language = language,
        )
    }

    val canSend = !sending && waitMs <= 0 && FeedbackRules.cleanMessage(message) != null
    SheetSurface(Modifier.imePadding().verticalScroll(rememberScrollState()), bottomInset = bottomInset) {
        SheetTitle(stringResource(R.string.feedback_title), AnnotatedString(stringResource(R.string.feedback_desc)), messageStyle = Studio.typography.body)

        Row(horizontalArrangement = Arrangement.spacedBy(Studio.spacing.xs)) {
            for (type in FeedbackType.entries) {
                FilterChip(
                    stringResource(type.label()),
                    selected = type == draft.type,
                    onClick = {
                        if (sending) return@FilterChip
                        haptics.light()
                        feedback.setType(type)
                    },
                )
            }
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Studio.spacing.xxs)) {
            StudioTextField(
                value = message,
                onValueChange = {
                    message = FeedbackRules.truncate(it, FeedbackRules.MESSAGE_MAX)
                    feedback.setMessage(message)
                },
                placeholder = stringResource(R.string.feedback_message_placeholder),
                minLines = MESSAGE_MIN_LINES,
                maxLines = MESSAGE_MAX_LINES,
                enabled = !sending,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            val count = FeedbackRules.length(message)
            val full = count >= FeedbackRules.MESSAGE_MAX
            Text(
                stringResource(R.string.feedback_counter, formatInteger(count.toDouble()), formatInteger(FeedbackRules.MESSAGE_MAX.toDouble())),
                modifier = Modifier.fillMaxWidth(),
                style = Studio.typography.caption.copy(fontWeight = if (full) FontWeight.SemiBold else FontWeight.Normal),
                color = if (full) Studio.colors.textPrimary else Studio.colors.textSecondary,
                textAlign = TextAlign.End,
            )
        }

        StudioTextField(
            value = name,
            onValueChange = {
                name = FeedbackRules.truncate(it, FeedbackRules.NAME_MAX)
                feedback.setName(name)
            },
            placeholder = stringResource(R.string.feedback_name_placeholder),
            singleLine = true,
            enabled = !sending,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Studio.spacing.xs)) {
            StudioButton(
                stringResource(if (sending) R.string.feedback_sending else R.string.feedback_send),
                onClick = {
                    haptics.light()
                    focusManager.clearFocus()
                    container.launchMutation {
                        if (feedback.send(environment) == FeedbackOutcome.Sent) {
                            withContext(Dispatchers.Main) { haptics.success() }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSend,
            )
            if (waitMs > 0) {
                Text(
                    stringResource(R.string.feedback_wait, waitLabel(waitMs)),
                    modifier = Modifier.fillMaxWidth(),
                    style = Studio.typography.caption,
                    color = Studio.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Text(
            stringResource(
                R.string.feedback_attached,
                environment.appVersion,
                environment.androidVersion,
                environment.device,
                environment.language,
            ),
            style = Studio.typography.caption,
            color = Studio.colors.textSecondary,
        )

        result?.let { outcome ->
            StatusText(
                when (outcome) {
                    FeedbackOutcome.Sent -> stringResource(R.string.feedback_success)
                    FeedbackOutcome.NoConnection -> stringResource(R.string.feedback_error_network)
                    is FeedbackOutcome.RateLimited -> stringResource(R.string.feedback_error_rate_limited)
                    is FeedbackOutcome.Failed -> stringResource(R.string.feedback_error_server, outcome.code)
                },
            )
        }
    }
}

private fun FeedbackType.label(): Int = when (this) {
    FeedbackType.Idea -> R.string.feedback_type_idea
    FeedbackType.Bug -> R.string.feedback_type_bug
    FeedbackType.Other -> R.string.feedback_type_other
}

/** « 42 s », puis en minutes arrondies au-dessus à partir d'une minute : « 3 min » (§ 14.1). */
@Composable
private fun waitLabel(waitMs: Long): String {
    val seconds = FeedbackRules.waitSeconds(waitMs)
    return if (seconds < 60) {
        stringResource(R.string.settings_rest_timer_seconds, seconds.toInt())
    } else {
        stringResource(R.string.settings_rest_timer_minutes, ((seconds + 59) / 60).toInt())
    }
}
