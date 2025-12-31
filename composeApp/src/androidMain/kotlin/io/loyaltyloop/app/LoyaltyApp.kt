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
import com.yandex.mapkit.MapKitFactory
import io.loyaltyloop.app.services.PushService
import io.loyaltyloop.shared.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.getKoin

import android.content.Context
import android.app.ActivityManager

class LoyaltyApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        
        // Skip initialization in secondary processes (like :phoenix for restart)
        if (!isMainProcess()) {
            return
        }

        // 1. Настраиваем Kermit писать в Logcat
        Logger.setLogWriters(LogcatWriter())
        if (!AppConfig.isProd){
            Logger.setMinSeverity(Severity.Info)
        }


        MapKitFactory.setApiKey(io.loyaltyloop.app.config.AppConfig.MAP_API_KEY)
        MapKitFactory.initialize(this)

        // Запускаем DI
        startKoin {
            if (!AppConfig.isProd){
                androidLogger()
            }
            androidContext(this@LoyaltyApp)
            modules(appModule)
        }

        appScope.launch {
            runCatching { getKoin().get<PushService>().register() }
        }
    }

    private fun isMainProcess(): Boolean {
        val pid = android.os.Process.myPid()
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processName = activityManager.runningAppProcesses?.find { it.pid == pid }?.processName
        return processName == packageName
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel()
    }
}