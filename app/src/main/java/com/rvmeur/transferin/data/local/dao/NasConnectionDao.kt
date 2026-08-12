package com.rvmeur.transferin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rvmeur.transferin.data.local.entity.NasConnectionEntity

@Dao
interface NasConnectionDao {

    @Query("SELECT * FROM nas_connection WHERE id = 1 LIMIT 1")
    suspend fun getConnectionOrNull(): NasConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(connection: NasConnectionEntity)

    @Query("UPDATE nas_connection SET defaultRemoteFolder = :folder WHERE id = 1")
    suspend fun updateDefaultRemoteFolder(folder: String)
}