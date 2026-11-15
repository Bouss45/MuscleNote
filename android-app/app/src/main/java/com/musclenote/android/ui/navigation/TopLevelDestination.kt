package com.musclenote.android.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.musclenote.android.R

/** Les quatre onglets (SPEC § 15.8.1), dans l'ordre de la barre de navigation. */
enum class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val selectedIcon: Int,
    @param:DrawableRes val unselectedIcon: Int,
) {
    Home("home", R.string.tabs_home, R.drawable.ds_ic_home_filled, R.drawable.ds_ic_home),
    Journal("journal", R.string.tabs_journal, R.drawable.ds_ic_calendar_month_filled, R.drawable.ds_ic_calendar_month),
    Progress("progress", R.string.tabs_progress, R.drawable.ds_ic_monitoring_filled, R.drawable.ds_ic_monitoring),
    Nutrition("nutrition", R.string.tabs_nutrition, R.drawable.ds_ic_restaurant_filled, R.drawable.ds_ic_restaurant),
}
