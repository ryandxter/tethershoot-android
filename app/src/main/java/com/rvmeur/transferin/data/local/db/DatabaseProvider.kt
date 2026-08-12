package com.rvmeur.transferin.data.local.db

import android.content.Context
import androidx.room.Room

object DatabaseProvider {

    @Volatile
    private var INSTANCE: TransferInDatabase? = null

    fun getDatabase(context: Context): TransferInDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                TransferInDatabase::class.java,
                TransferInDatabase.DATABASE_NAME
            )
                .addMigrations(Migration_8_9.MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .build()
                .also { instance ->
                    INSTANCE = instance
                }
        }
    }
}