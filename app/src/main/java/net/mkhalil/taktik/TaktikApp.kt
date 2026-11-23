package net.mkhalil.taktik

import android.app.Application
import net.mkhalil.taktik.di.ServiceLocator

class TaktikApp : Application() {
    lateinit var services: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        services = ServiceLocator(this)
    }

    companion object {
        @Volatile private var instance: TaktikApp? = null
        val services: ServiceLocator get() = checkNotNull(instance).services
    }
}
