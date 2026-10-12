package com.walktape.app

import android.content.Context
import com.walktape.app.core.db.WalkTapeDatabase
import com.walktape.app.core.settings.SettingsRepository

@Suppress("StaticFieldLeak")
object Graph {
    private lateinit var appContext: Context
    lateinit var database: WalkTapeDatabase
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        database = WalkTapeDatabase.build(appContext)
        settingsRepository = SettingsRepository(appContext)
    }

    fun app(): Context = appContext
}
