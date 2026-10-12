package com.walktape.app.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProfileEntity::class],
    version = 2,
    exportSchema = true
)
abstract class WalkTapeDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao

    companion object {
        val MIGRATIONS: Array<Migration> = arrayOf(object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN credentialId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profile ADD COLUMN encryptedPassword TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profile ADD COLUMN allowLocalHttp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE profile SET isActive = 0")
            }
        })

        fun build(context: Context): WalkTapeDatabase =
            Room.databaseBuilder(context, WalkTapeDatabase::class.java, "walktape.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
