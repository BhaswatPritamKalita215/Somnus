package com.example.somnus.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Main Room database for Somnus.
 *
 * Migration strategy:
 * Explicit schema migrations are mandated. Destructive fallback
 * ([fallbackToDestructiveMigration]) is explicitly disabled to prevent accidental loss
 * of personal sleep records during application upgrades.
 */
@Database(
    entities = [
        SleepSessionEntity::class,
        SolRecordEntity::class,
        PsqiAssessmentEntity::class,
        StepRecordEntity::class,
        StepCounterCheckpointEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class SomnusDatabase : RoomDatabase() {

    abstract fun sleepSessionDao(): SleepSessionDao
    abstract fun solRecordDao(): SolRecordDao
    abstract fun psqiDao(): PsqiDao
    abstract fun stepRecordDao(): StepRecordDao
    abstract fun stepCheckpointDao(): StepCheckpointDao

    /**
     * Atomically clears all sleep-tracking and activity data tables in a single database transaction.
     * Prevents partial deletion states if interrupted.
     */
    @androidx.room.Transaction
    open suspend fun clearAllDataTables() {
        stepCheckpointDao().deleteAll()
        stepRecordDao().deleteAll()
        solRecordDao().deleteAll()
        psqiDao().deleteAll()
        sleepSessionDao().deleteAll()
    }

    companion object {
        const val DATABASE_NAME = "somnus_sleep.db"

        /**
         * Enforces SQLite foreign key constraints on every database connection open.
         */
        private val DB_CALLBACK = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL("PRAGMA foreign_keys = ON;")
            }
        }

        /**
         * Migration from version 1 (sessions only) to version 2
         * adding Sleep Onset Latency (sol_records) and Pittsburgh Sleep Quality Index (psqi_assessments).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create sol_records table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sol_records` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `sessionId` TEXT,
                        `reportingDate` TEXT NOT NULL,
                        `zoneId` TEXT NOT NULL,
                        `latencyMinutes` INTEGER NOT NULL,
                        `method` TEXT NOT NULL,
                        `sourceIdentifier` TEXT NOT NULL,
                        `sourceDeviceMetadata` TEXT,
                        `qualityFlags` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `attemptToSleepTime` INTEGER,
                        `sleepOnsetTime` INTEGER,
                        `notes` TEXT,
                        FOREIGN KEY(`sessionId`) REFERENCES `sleep_sessions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sol_records_reportingDate` ON `sol_records` (`reportingDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sol_records_sessionId` ON `sol_records` (`sessionId`)")

                // Create psqi_assessments table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `psqi_assessments` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `startedAt` INTEGER NOT NULL,
                        `completedAt` INTEGER,
                        `version` TEXT NOT NULL,
                        `language` TEXT NOT NULL,
                        `reportingPeriod` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `scoringVersion` TEXT NOT NULL,
                        `globalScore` INTEGER,
                        `component1` INTEGER,
                        `component2` INTEGER,
                        `component3` INTEGER,
                        `component4` INTEGER,
                        `component5` INTEGER,
                        `component6` INTEGER,
                        `component7` INTEGER,
                        `responsesJson` TEXT NOT NULL,
                        `notes` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_psqi_assessments_startedAt` ON `psqi_assessments` (`startedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_psqi_assessments_status` ON `psqi_assessments` (`status`)")
            }
        }

        /**
         * Migration from version 2 to version 3
         * adding Step Tracking tables: `step_records` and `step_checkpoints`.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create step_records table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `step_records` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `startTime` INTEGER NOT NULL,
                        `endTime` INTEGER NOT NULL,
                        `localDate` TEXT NOT NULL,
                        `zoneId` TEXT NOT NULL,
                        `stepCount` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `sourceIdentifier` TEXT NOT NULL,
                        `deviceModel` TEXT,
                        `externalRecordId` TEXT,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_step_records_localDate` ON `step_records` (`localDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_step_records_startTime` ON `step_records` (`startTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_step_records_externalRecordId` ON `step_records` (`externalRecordId`)")

                // Create step_checkpoints table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `step_checkpoints` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `bootTimestamp` INTEGER NOT NULL,
                        `lastSensorValue` REAL NOT NULL,
                        `accumulatedSteps` INTEGER NOT NULL,
                        `lastUpdateEpochMs` INTEGER NOT NULL,
                        `localDate` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * List of explicit Room migrations.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun buildDatabase(context: Context, databaseName: String = DATABASE_NAME): SomnusDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                SomnusDatabase::class.java,
                databaseName
            )
                .addMigrations(*MIGRATIONS)
                .addCallback(DB_CALLBACK)
                // Note: We deliberately do NOT call fallbackToDestructiveMigration()
                .build()
        }

        fun buildInMemoryDatabase(context: Context): SomnusDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                SomnusDatabase::class.java
            )
                .addCallback(DB_CALLBACK)
                .allowMainThreadQueries()
                .build()
        }
    }
}
