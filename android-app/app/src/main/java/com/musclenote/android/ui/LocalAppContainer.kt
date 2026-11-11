package com.musclenote.android.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.musclenote.android.data.AppContainer

/** Accès aux dépôts depuis les écrans (fourni par MainActivity). */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer non fourni")
}
