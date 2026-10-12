package com.walktape.app.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [ProfileEntity::class],
    version = 1,
    exportSchema = true
)
abstract class WalkTapeDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao

    companion object {
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): WalkTapeDatabase =
            Room.databaseBuilder(context, WalkTapeDatabase::class.java, "walktape.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
