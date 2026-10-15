package com.walktape.app.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProfileEntity::class, ArtistEntity::class, AlbumEntity::class,
        SongEntity::class, GenreEntity::class, LibraryStateEntity::class,
        PlaylistEntity::class, PlaylistEntryEntity::class, LocalAudioEntity::class,
        QueueStateEntity::class, QueueEntryEntity::class],
    version = 6,
    exportSchema = true
)
abstract class WalkTapeDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun libraryDao(): LibraryDao
    abstract fun browseDao(): BrowseDao
    abstract fun queueDao(): QueueDao

    companion object {
        val MIGRATIONS: Array<Migration> = arrayOf(object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN credentialId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profile ADD COLUMN encryptedPassword TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profile ADD COLUMN allowLocalHttp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE profile SET isActive = 0")
            }
        }, object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `artist` (`profileId` INTEGER NOT NULL, `id` TEXT NOT NULL, `name` TEXT NOT NULL, `coverArt` TEXT, `albumCount` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `album` (`profileId` INTEGER NOT NULL, `id` TEXT NOT NULL, `name` TEXT NOT NULL, `artist` TEXT, `artistId` TEXT, `coverArt` TEXT, `songCount` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `year` INTEGER, `genre` TEXT, `created` TEXT, `isCompilation` INTEGER NOT NULL, `starred` TEXT, PRIMARY KEY(`profileId`, `id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `song` (`profileId` INTEGER NOT NULL, `id` TEXT NOT NULL, `parent` TEXT, `albumId` TEXT, `artistId` TEXT, `title` TEXT NOT NULL, `album` TEXT, `artist` TEXT, `albumArtist` TEXT, `displayArtist` TEXT, `track` INTEGER NOT NULL, `discNumber` INTEGER NOT NULL, `year` INTEGER, `genre` TEXT, `coverArt` TEXT, `duration` INTEGER NOT NULL, `bitRate` INTEGER NOT NULL, `suffix` TEXT, `contentType` TEXT, `size` INTEGER NOT NULL, `path` TEXT, `created` TEXT, `starred` TEXT, `type` TEXT, PRIMARY KEY(`profileId`, `id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `genre` (`profileId` INTEGER NOT NULL, `id` TEXT NOT NULL, `name` TEXT NOT NULL, `songCount` INTEGER NOT NULL, `albumCount` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `library_state` (`profileId` INTEGER NOT NULL, `lastSyncAt` INTEGER NOT NULL, `lastSyncOk` INTEGER NOT NULL, `artistCount` INTEGER NOT NULL, `albumCount` INTEGER NOT NULL, `songCount` INTEGER NOT NULL, PRIMARY KEY(`profileId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_profileId_albumId` ON `song` (`profileId`, `albumId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_profileId_artistId` ON `song` (`profileId`, `artistId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_profileId_title` ON `song` (`profileId`, `title`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_profileId_artistId` ON `album` (`profileId`, `artistId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_profileId_created` ON `album` (`profileId`, `created`)")
            }
        }, object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE album ADD COLUMN played TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE artist ADD COLUMN starred TEXT DEFAULT NULL")
                db.execSQL("CREATE TABLE IF NOT EXISTS playlist (profileId INTEGER NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, owner TEXT, isPublic INTEGER NOT NULL, comment TEXT, coverArt TEXT, songCount INTEGER NOT NULL, duration INTEGER NOT NULL, created TEXT, changed TEXT, fetchedAt INTEGER NOT NULL, PRIMARY KEY(profileId,id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS playlist_entry (profileId INTEGER NOT NULL, playlistId TEXT NOT NULL, position INTEGER NOT NULL, songId TEXT NOT NULL, title TEXT NOT NULL, artist TEXT, album TEXT, duration INTEGER NOT NULL, year INTEGER, genre TEXT, PRIMARY KEY(profileId,playlistId,position), FOREIGN KEY(profileId,playlistId) REFERENCES playlist(profileId,id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_entry_profileId_playlistId ON playlist_entry(profileId,playlistId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS local_audio (profileId INTEGER NOT NULL, songId TEXT NOT NULL, relativePath TEXT NOT NULL, bytes INTEGER NOT NULL, PRIMARY KEY(profileId,songId))")
            }
        }, object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS queue_state (profileId INTEGER NOT NULL, currentId TEXT, positionMs INTEGER NOT NULL, repeatMode INTEGER NOT NULL, shuffle INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(profileId), FOREIGN KEY(profileId) REFERENCES profile(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE TABLE IF NOT EXISTS queue_entry (profileId INTEGER NOT NULL, position INTEGER NOT NULL, shufflePosition INTEGER NOT NULL, entryId TEXT NOT NULL, songId TEXT NOT NULL, title TEXT NOT NULL, artist TEXT, album TEXT, durationMs INTEGER, PRIMARY KEY(profileId,position), FOREIGN KEY(profileId) REFERENCES queue_state(profileId) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_queue_entry_profileId_entryId ON queue_entry(profileId,entryId)")
            }
        }, object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE queue_entry ADD COLUMN originName TEXT")
            }
        })

        fun build(context: Context): WalkTapeDatabase =
            Room.databaseBuilder(context, WalkTapeDatabase::class.java, "walktape.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
