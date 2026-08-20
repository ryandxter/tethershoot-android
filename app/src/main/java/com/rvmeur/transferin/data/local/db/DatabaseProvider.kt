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
                .addMigrations(
                    Migration_8_9.MIGRATION_8_9,
                    Migration_9_10.MIGRATION_9_10,
                    Migration_10_11.MIGRATION_10_11
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { instance ->
                    INSTANCE = instance
                }
        }
    }
}