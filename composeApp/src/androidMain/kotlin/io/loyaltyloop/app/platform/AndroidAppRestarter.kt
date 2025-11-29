package io.loyaltyloop.app.platform

import android.content.Context
import com.jakewharton.processphoenix.ProcessPhoenix

class AndroidAppRestarter(
    private val context: Context
) : AppRestarter {

    override fun restartApp() {
        ProcessPhoenix.triggerRebirth(context.applicationContext)
    }
}


