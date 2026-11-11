package com.musclenote.android.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.musclenote.android.data.update.LatestRelease

/** Ouvre [url] dans le navigateur ; sans navigateur, ne fait rien. */
fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Log.w("OpenUrl", "Aucune app pour ouvrir $url", e)
    }
}

/**
 * « Télécharger » une mise à jour (§ 3.17) : l'APK de la Release dans le navigateur (il suffit
 * ensuite de l'ouvrir pour l'installer par-dessus), ou à défaut la page de la Release.
 */
fun downloadUpdate(context: Context, release: LatestRelease) = openUrl(context, release.apkUrl ?: release.pageUrl)
