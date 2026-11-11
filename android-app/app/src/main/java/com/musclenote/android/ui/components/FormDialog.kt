package com.musclenote.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musclenote.android.R
import com.musclenote.android.ui.theme.MuscleNoteTheme

/**
 * Modale de saisie de l'app (modifier la date d'une séance, nouvelle pesée…) : voile du système,
 * boîte `card` de 360 dp max, rayon 20, padding 24. Toucher le voile ou le retour ferme sans
 * enregistrer ([onDismiss]).
 */
@Composable
fun FormDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = MuscleNoteTheme.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(colors.card)
                .border(1.dp, colors.border, shape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** Titre de modale (18 sp gras) et sous-titre facultatif. */
@Composable
fun FormDialogTitle(title: String, subtitle: String? = null) {
    val colors = MuscleNoteTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = colors.foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = colors.mutedForeground, fontSize = 13.sp)
    }
}

/** Message d'erreur rouge d'une modale (saisie refusée). */
@Composable
fun FormDialogError(message: String) {
    Text(message, color = MuscleNoteTheme.colors.destructive, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

/** [Annuler] [✓ Enregistrer] en pied de modale. */
@Composable
fun FormDialogButtons(onCancel: () -> Unit, onSave: () -> Unit) {
    val colors = MuscleNoteTheme.colors
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val buttonShape = RoundedCornerShape(12.dp)
        Box(
            Modifier
                .weight(1f)
                .clip(buttonShape)
                .border(1.dp, colors.border, buttonShape)
                .clickable(onClick = onCancel)
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.common_cancel), color = colors.foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(
            Modifier
                .weight(1f)
                .clip(buttonShape)
                .background(colors.primary)
                .clickable(onClick = onSave)
                .padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.primaryForeground, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.common_save), color = colors.primaryForeground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Champ numérique de date ou d'heure (JJ, MM, AAAA, HH, mm), libellé facultatif au-dessus, tout le
 * texte sélectionné au focus ; « Suivant » passe au champ suivant, « OK » ([imeAction]) ferme le
 * clavier sur le dernier.
 */
@Composable
fun RowScope.DateField(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    maxLength: Int,
    label: String? = null,
    weight: Float = 1f,
    imeAction: ImeAction = ImeAction.Next,
) {
    val colors = MuscleNoteTheme.colors
    var value by remember { mutableStateOf(TextFieldValue(text)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) value = value.copy(selection = TextRange(0, value.text.length))
    }
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.weight(weight), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) Text(label, color = colors.mutedForeground, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        BasicTextField(
            value = value,
            onValueChange = { changed ->
                if (changed.text.length <= maxLength) {
                    val textChanged = changed.text != value.text
                    value = changed
                    if (textChanged) onTextChange(changed.text)
                }
            },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                color = colors.foreground,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .clip(shape)
                        .background(colors.background)
                        .border(1.dp, colors.border, shape)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (value.text.isEmpty()) {
                        Text(placeholder, color = colors.mutedForeground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    inner()
                }
            },
        )
    }
}

/** Séparateur `/` ou `:` entre deux [DateField], aligné sur le bas des champs. */
@Composable
fun DateSeparator(text: String) {
    Text(
        text,
        color = MuscleNoteTheme.colors.mutedForeground,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}
