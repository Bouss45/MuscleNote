package com.musclenote.android.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Qui a installé l'app (SPEC § 3.17, 🔄 D25) : la vérification des mises à jour intégrée ne sert
 * que si aucun gestionnaire de mises à jour ne s'en charge. Relu à chaque démarrage : une app
 * installée à la main puis mise à jour par Obtainium cesse de vérifier d'elle-même.
 */
object InstallSource {
    private const val TAG = "InstallSource"

    /**
     * Gestionnaires qui suivent les Releases : Obtainium (versions GitHub et F-Droid), et le Play
     * Store, nom qu'Obtainium peut emprunter quand il installe par Shizuku (l'app n'y est pas).
     */
    private val UPDATE_MANAGERS = setOf(
        "dev.imranr.obtainium",
        "dev.imranr.obtainium.fdroid",
        "com.android.vending",
    )

    /** `true` si un gestionnaire de mises à jour a installé (ou fait installer) l'app. */
    fun isManagedByUpdater(context: Context): Boolean = installers(context).any { it in UPDATE_MANAGERS }

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
