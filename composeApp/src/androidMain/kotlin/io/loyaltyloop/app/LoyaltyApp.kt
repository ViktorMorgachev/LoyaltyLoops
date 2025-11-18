package io.loyaltyloop.app

import android.app.Application
import io.loyaltyloop.app.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class LoyaltyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Запускаем DI
        startKoin {
            androidLogger()
            androidContext(this@LoyaltyApp)
            modules(appModule)
        }
    }
}