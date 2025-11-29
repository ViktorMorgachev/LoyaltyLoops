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
import io.loyaltyloop.app.services.PushService
import io.loyaltyloop.shared.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.getKoin

class LoyaltyApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

        if (AppConfig.featureFlags.pushEnabled) {
            appScope.launch {
                runCatching { getKoin().get<PushService>().register() }
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel()
    }
}