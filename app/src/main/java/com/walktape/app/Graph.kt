package com.walktape.app

import android.content.Context
import com.walktape.app.core.db.WalkTapeDatabase
import com.walktape.app.core.settings.SettingsRepository
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.ServerAddress
import com.walktape.app.core.net.Credentials
import com.walktape.app.core.profiles.CredentialVault
import com.walktape.app.core.profiles.ProfileRepository

@Suppress("StaticFieldLeak")
object Graph {
    private lateinit var appContext: Context
    lateinit var database: WalkTapeDatabase
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var profiles: ProfileRepository
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        database = WalkTapeDatabase.build(appContext)
        settingsRepository = SettingsRepository(appContext)
        profiles = ProfileRepository(database, CredentialVault(), appContext.filesDir)
    }

    fun app(): Context = appContext

    fun client(address: ServerAddress, credentials: Credentials) = OpenSubsonicClient(address, credentials)
}
