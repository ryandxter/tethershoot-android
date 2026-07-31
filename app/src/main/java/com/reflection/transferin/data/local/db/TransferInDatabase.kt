package com.reflection.transferin.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.reflection.transferin.data.local.dao.CameraMediaDao
import com.reflection.transferin.data.local.dao.NasConnectionDao
import com.reflection.transferin.data.local.dao.SessionDao
import com.reflection.transferin.data.local.dao.TransferQueueDao
import com.reflection.transferin.data.local.dao.UploadedFingerprintDao
import com.reflection.transferin.data.local.entity.CameraMediaItemEntity
import com.reflection.transferin.data.local.entity.NasConnectionEntity
import com.reflection.transferin.data.local.entity.SessionEntity
import com.reflection.transferin.data.local.entity.TransferQueueItemEntity
import com.reflection.transferin.data.local.entity.UploadedFingerprintEntity

@Database(
    entities = [
        TransferQueueItemEntity::class,
        SessionEntity::class,
        NasConnectionEntity::class,
        CameraMediaItemEntity::class,
        UploadedFingerprintEntity::class
    ],
    version = TransferInDatabase.VERSION,
    exportSchema = true
)
abstract class TransferInDatabase : RoomDatabase() {

    abstract fun transferQueueDao(): TransferQueueDao

    abstract fun sessionDao(): SessionDao

    abstract fun nasConnectionDao(): NasConnectionDao

    abstract fun cameraMediaDao(): CameraMediaDao

    abstract fun uploadedFingerprintDao(): UploadedFingerprintDao

    companion object {
        const val VERSION = 9
        const val DATABASE_NAME = "transferin.db"
    }
}