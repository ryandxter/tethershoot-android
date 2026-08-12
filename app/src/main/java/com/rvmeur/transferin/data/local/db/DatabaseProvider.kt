package com.rvmeur.transferin.data.local.db

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                    // Auto-seed Google Drive credentials
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val dao = instance.nasConnectionDao()
                            if (dao.getConnectionOrNull() == null) {
                                dao.upsert(
                                    com.rvmeur.transferin.data.local.entity.NasConnectionEntity(
                                        id = 1,
                                        name = "Google Drive",
                                        host = "343575435197-q6tnos0o1cgt8c0ir8alne21gitioj7i.apps.googleusercontent.com",
                                        port = 443,
                                        username = "1//04pNkKuwSxfBlCgYIARAAGAQSNwF-L9IreJaNDRo0TrGbYyqhkKPm6dRdMfjlajeo6-CieM7om1hYz16YoAKl594nyzgdauxO12o",
                                        password = "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",
                                        defaultRemoteFolder = "root",
                                        protocol = "GOOGLE_DRIVE",
                                        useHttps = true,
                                        startingFolder = "RVM_Photos"
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
        }
    }
}