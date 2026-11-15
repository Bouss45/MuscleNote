package com.musclenote.android.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.musclenote.android.data.update.LatestRelease
import com.musclenote.android.data.update.UpdateRules

/**
 * Qui a installé l'app (SPEC § 3.17, § 15.14.1) : installée par Obtainium, la notification de mise à
 * jour est coupée par défaut et « Voir » ouvre Obtainium. Relu à chaque démarrage : une app
 * installée à la main puis mise à jour par Obtainium change de défaut. Règles dans [UpdateRules].
 */
object InstallSource {
    private const val TAG = "InstallSource"

    /** `true` si Obtainium (ou le nom du Play Store qu'il emprunte) a installé l'app. */
    fun isInstalledByObtainium(context: Context): Boolean = UpdateRules.isInstalledByObtainium(installers(context))

    /**
     * « Voir » une mise à jour (§ 15.14.4) : Obtainium s'il a installé l'app et qu'il s'ouvre, sinon la
     * page de la Release dans le navigateur.
     */
    fun viewUpdateIntent(context: Context, release: LatestRelease): Intent =
        obtainiumLaunchIntent(context) ?: Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl))

    /** Écran d'accueil d'Obtainium (paquets déclarés dans `<queries>`), `null` s'il n'est pas là. */
    fun obtainiumLaunchIntent(context: Context): Intent? =
        UpdateRules.obtainiumPackagesToOpen(installers(context))
            .firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }

    /** App d'installation et app à l'origine de l'installation (Android 11+), si connues. */
    private fun installers(context: Context): List<String> {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val info = pm.getInstallSourceInfo(context.packageName)
                listOfNotNull(info.installingPackageName, info.initiatingPackageName)
            } else {
                @Suppress("DEPRECATION")
                listOfNotNull(pm.getInstallerPackageName(context.packageName))
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Source d'installation inconnue", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Source d'installation inconnue", e)
            emptyList()
        }
    }
}
