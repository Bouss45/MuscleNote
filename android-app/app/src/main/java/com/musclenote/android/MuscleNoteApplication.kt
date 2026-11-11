package com.musclenote.android

import android.app.Application
import com.musclenote.android.data.AppContainer
import java.io.File

class MuscleNoteApplication : Application() {

    /** Données de l'app, créées une fois par processus (SPEC § 4.2). */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(File(filesDir, "data"))
    }
}
