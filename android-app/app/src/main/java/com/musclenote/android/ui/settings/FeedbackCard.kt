package com.musclenote.android.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.musclenote.android.ui.theme.MuscleNoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Carte « Ton avis » (SPEC § 14.1, 🔄 D26) : type (puces), message (1 000 caractères, compteur),
 * pseudo facultatif, [Envoyer], informations jointes ; encart de résultat dessous (§ 14.4).
 *
 * Les champs gardent leur texte localement (saisie sans décalage) et le recopient à chaque frappe
 * dans le brouillon du dépôt, qui survit au changement d'onglet ; ils repartent du brouillon quand
 * il est vidé après un envoi réussi (`FeedbackRepository.resets`). L'envoi passe par
 * `launchMutation` : quitter l'écran ne l'interrompt pas.
 */
@Composable
fun FeedbackCard() {
    val container = LocalAppContainer.current
    val feedback = container.feedback
    val draft by feedback.draft.collectAsStateWithLifecycle()
    val resets by feedback.resets.collectAsStateWithLifecycle()
    val throttle by feedback.throttle.collectAsStateWithLifecycle()
    val sending by feedback.sending.collectAsStateWithLifecycle()
    val result by feedback.result.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val focusManager = LocalFocusManager.current
    val colors = MuscleNoteTheme.colors

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
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CardIcon(R.drawable.ic_forum, colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.feedback_title), color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.feedback_desc), color = colors.mutedForeground, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (type in FeedbackType.entries) {
                val active = type == draft.type
                val chipShape = RoundedCornerShape(12.dp)
                Box(
                    Modifier
                        .clip(chipShape)
                        .background(if (active) colors.primary else colors.muted)
                        .border(1.dp, if (active) colors.primary else colors.border, chipShape)
                        .clickable(enabled = !sending) {
                            haptics.light()
                            feedback.setType(type)
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(
                        stringResource(type.label()),
                        color = if (active) colors.primaryForeground else colors.foreground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FeedbackField(
                value = message,
                onValueChange = {
                    message = FeedbackRules.truncate(it, FeedbackRules.MESSAGE_MAX)
                    feedback.setMessage(message)
                },
                placeholder = stringResource(R.string.feedback_message_placeholder),
                enabled = !sending,
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            val count = FeedbackRules.length(message)
            Text(
                stringResource(
                    R.string.feedback_counter,
                    formatInteger(count.toDouble()),
                    formatInteger(FeedbackRules.MESSAGE_MAX.toDouble()),
                ),
                color = if (count >= FeedbackRules.MESSAGE_MAX) colors.destructive else colors.mutedForeground,
                fontSize = 11.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FeedbackField(
            value = name,
            onValueChange = {
                name = FeedbackRules.truncate(it, FeedbackRules.NAME_MAX)
                feedback.setName(name)
            },
            placeholder = stringResource(R.string.feedback_name_placeholder),
            enabled = !sending,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val buttonShape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (canSend || sending) 1f else 0.4f)
                    .clip(buttonShape)
                    .background(colors.primary)
                    .clickable(enabled = canSend) {
                        haptics.light()
                        focusManager.clearFocus()
                        container.launchMutation {
                            if (feedback.send(environment) == FeedbackOutcome.Sent) {
                                withContext(Dispatchers.Main) { haptics.success() }
                            }
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (sending) {
                    CircularProgressIndicator(color = colors.primaryForeground, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_send), contentDescription = null, tint = colors.primaryForeground, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.feedback_send), color = colors.primaryForeground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (waitMs > 0) {
                Text(
                    stringResource(R.string.feedback_wait, waitLabel(waitMs)),
                    color = colors.mutedForeground,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                painterResource(R.drawable.ic_info),
                contentDescription = null,
                tint = colors.mutedForeground,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(14.dp),
            )
            Text(
                stringResource(
                    R.string.feedback_attached,
                    environment.appVersion,
                    environment.androidVersion,
                    environment.device,
                    environment.language,
                ),
                color = colors.mutedForeground,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f),
            )
        }
    }

    result?.let { outcome ->
        StatusBox(
            success = outcome == FeedbackOutcome.Sent,
            message = when (outcome) {
                FeedbackOutcome.Sent -> stringResource(R.string.feedback_success)
                FeedbackOutcome.NoConnection -> stringResource(R.string.feedback_error_network)
                is FeedbackOutcome.RateLimited -> stringResource(R.string.feedback_error_rate_limited)
                is FeedbackOutcome.Failed -> stringResource(R.string.feedback_error_server, outcome.code)
            },
        )
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

/** Champ de saisie du formulaire : fond `input`, bordure, indication tant qu'il est vide. */
@Composable
private fun FeedbackField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    singleLine: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 4,
        maxLines = if (singleLine) 1 else 8,
        textStyle = LocalTextStyle.current.copy(color = colors.foreground, fontSize = 15.sp, lineHeight = 21.sp),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.input)
                    .border(1.dp, colors.border, shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, color = colors.mutedForeground, fontSize = 15.sp, lineHeight = 21.sp)
                inner()
            }
        },
    )
}
