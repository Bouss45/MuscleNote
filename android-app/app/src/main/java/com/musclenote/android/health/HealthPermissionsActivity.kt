package com.musclenote.android.health

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musclenote.android.MuscleNoteApplication
import com.musclenote.android.R
import com.musclenote.android.data.model.ThemeMode
import com.musclenote.android.ui.theme.MuscleNoteTheme

/**
 * Politique de confidentialité exigée par Health Connect (SPEC § 15.3) : ouverte depuis son écran
 * d'autorisation et ses réglages (Android 14+ : alias `VIEW_PERMISSION_USAGE` +
 * `HEALTH_PERMISSIONS` ; avant : `ACTION_SHOW_PERMISSIONS_RATIONALE`). Sans elle, Health Connect
 * refuse la demande d'autorisation sans rien afficher. Thème et langue de l'app.
 */
class HealthPermissionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as MuscleNoteApplication).container
        setContent {
            val preferences by container.preferences.preferences.collectAsStateWithLifecycle()
            val darkTheme = when (preferences.theme) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            MuscleNoteTheme(darkTheme = darkTheme) {
                HealthPrivacyScreen(onBack = ::finish)
            }
        }
    }
}

@Composable
private fun HealthPrivacyScreen(onBack: () -> Unit) {
    val colors = MuscleNoteTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.common_back),
                    tint = colors.foreground,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                stringResource(R.string.health_privacy_title),
                color = colors.foreground,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (text in PARAGRAPHS) Paragraph(text)
        }
    }
}

private val PARAGRAPHS = listOf(
    R.string.health_privacy_written,
    R.string.health_privacy_when,
    R.string.health_privacy_nothing_else,
    R.string.health_privacy_stop,
)

@Composable
private fun Paragraph(@StringRes text: Int) {
    val colors = MuscleNoteTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Text(
        stringResource(text),
        color = colors.foreground,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.border, shape)
            .padding(16.dp),
    )
}
