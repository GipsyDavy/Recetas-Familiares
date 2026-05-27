package org.gipsybuho.recetasfamiliares

import android.app.Application
import org.gipsybuho.recetasfamiliares.core.AppContainer

class RecetasApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
