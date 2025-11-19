package io.loyaltyloop.app

import android.app.Application
import android.util.Log
import io.loyaltyloop.app.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import co.touchlab.kermit.LogcatWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

class LoyaltyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 1. Настраиваем Kermit писать в Logcat
        Logger.setLogWriters(LogcatWriter())

        // 2. ВАЖНО: Разрешаем писать ВСЕ логи (даже самые мелкие)
        Logger.setMinSeverity(Severity.Verbose)

        // Запускаем DI
        startKoin {
            androidLogger()
            androidContext(this@LoyaltyApp)
            modules(appModule)
        }
    }
}