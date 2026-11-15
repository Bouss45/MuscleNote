package com.musclenote.android.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.musclenote.android.notifications.AppNotifications

/**
 * Demande de `POST_NOTIFICATIONS` pour les rappels (§ 7.1) : seulement si elle peut encore l'être ;
 * un refus n'empêche rien (les rappels ne sonnent simplement pas).
 */
@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context, launcher) {
        { if (AppNotifications.needsPermissionRequest(context)) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
}
