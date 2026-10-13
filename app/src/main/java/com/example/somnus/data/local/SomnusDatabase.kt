package com.example.somnus.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * Main Room database for Somnus.
 *
 * Migration strategy:
 * Explicit schema migrations are mandated. Destructive fallback
 * ([fallbackToDestructiveMigration]) is explicitly disabled to prevent accidental loss
 * of personal sleep records during application upgrades.
 */
@Database(
    entities = [SleepSessionEntity::class],
    version = 1,
    exportSchema = true
)
abstract class SomnusDatabase : RoomDatabase() {

    abstract fun sleepSessionDao(): SleepSessionDao

    companion object {
        const val DATABASE_NAME = "somnus_sleep.db"

        /**
         * List of explicit Room migrations.
         * Any future schema increment from version N to N+1 must append its migration here.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun buildDatabase(context: Context, databaseName: String = DATABASE_NAME): SomnusDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                SomnusDatabase::class.java,
                databaseName
            )
                .addMigrations(*MIGRATIONS)
                // Note: We deliberately do NOT call fallbackToDestructiveMigration()
                .build()
        }

        fun buildInMemoryDatabase(context: Context): SomnusDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                SomnusDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
