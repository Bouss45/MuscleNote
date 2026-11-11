package com.musclenote.android.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.musclenote.android.R

/** Les quatre onglets (SPEC § 2.1), dans l'ordre de la barre de navigation. */
enum class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val selectedIcon: Int,
    @param:DrawableRes val unselectedIcon: Int,
) {
    Home("home", R.string.tabs_home, R.drawable.ic_home_filled, R.drawable.ic_home),
    Progress("progress", R.string.tabs_progress, R.drawable.ic_trending_up, R.drawable.ic_trending_up),
    Nutrition("nutrition", R.string.tabs_nutrition, R.drawable.ic_favorite_filled, R.drawable.ic_favorite),
    Settings("settings", R.string.tabs_settings, R.drawable.ic_settings_filled, R.drawable.ic_settings),
}
