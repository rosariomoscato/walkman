package com.walktape.app.core.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "profile",
    indices = [Index(value = ["name"], unique = true)]
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val serverUrl: String,
    val username: String,
    val isActive: Boolean = false,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "''") val credentialId: String = "",
    @ColumnInfo(defaultValue = "''") val encryptedPassword: String = "",
    @ColumnInfo(defaultValue = "0") val allowLocalHttp: Boolean = false,
) {
    override fun toString() = "ProfileEntity(id=$id, [redacted])"
}
