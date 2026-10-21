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
        StepCounterCheckpointEntity::class,
        FoodProductEntity::class,
        FoodLogEntryEntity::class,
        AssistantMessageEntity::class,
        SleepCheckInEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class SomnusDatabase : RoomDatabase() {

    abstract fun sleepSessionDao(): SleepSessionDao
    abstract fun solRecordDao(): SolRecordDao
    abstract fun psqiDao(): PsqiDao
    abstract fun stepRecordDao(): StepRecordDao
    abstract fun stepCheckpointDao(): StepCheckpointDao
    abstract fun foodProductDao(): FoodProductDao
    abstract fun foodLogDao(): FoodLogDao
    abstract fun assistantMessageDao(): AssistantMessageDao
    abstract fun sleepCheckInDao(): SleepCheckInDao

    /**
     * Atomically clears all sleep-tracking, activity, nutrition, and assistant data tables in a single database transaction.
     * Prevents partial deletion states if interrupted.
     */
    @androidx.room.Transaction
    open suspend fun clearAllDataTables() {
        sleepCheckInDao().deleteAll()
        assistantMessageDao().deleteAll()
        foodLogDao().deleteAll()
        foodProductDao().deleteAll()
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
         * Migration from version 3 to version 4
         * adding Nutrition and Food Catalogue tables: `food_products` and `food_log_entries`.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create food_products table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `food_products` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `barcode` TEXT NOT NULL,
                        `barcodeFormat` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `brand` TEXT,
                        `variant` TEXT,
                        `packageDescription` TEXT,
                        `servingSize` REAL,
                        `servingUnit` TEXT NOT NULL,
                        `nutritionBasis` TEXT NOT NULL,
                        `energyKcal` REAL NOT NULL,
                        `proteinGrams` REAL,
                        `carbohydratesGrams` REAL,
                        `fatGrams` REAL,
                        `fiberGrams` REAL,
                        `sugarGrams` REAL,
                        `sodiumMilligrams` REAL,
                        `source` TEXT NOT NULL,
                        `sourceProductId` TEXT,
                        `lastVerifiedAt` INTEGER NOT NULL,
                        `isUserModified` INTEGER NOT NULL,
                        `completeness` TEXT NOT NULL,
                        `notes` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_barcode` ON `food_products` (`barcode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_name` ON `food_products` (`name`)")

                // Create food_log_entries table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `food_log_entries` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `productId` TEXT,
                        `barcode` TEXT,
                        `productName` TEXT NOT NULL,
                        `brand` TEXT,
                        `mealType` TEXT NOT NULL,
                        `loggedAt` INTEGER NOT NULL,
                        `localDate` TEXT NOT NULL,
                        `quantity` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `numberOfServings` REAL,
                        `calculatedCalories` REAL NOT NULL,
                        `calculatedProtein` REAL,
                        `calculatedCarbs` REAL,
                        `calculatedFat` REAL,
                        `calculatedFiber` REAL,
                        `calculatedSugar` REAL,
                        `calculatedSodiumMg` REAL,
                        `source` TEXT NOT NULL,
                        `externalRecordId` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_log_entries_localDate` ON `food_log_entries` (`localDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_log_entries_loggedAt` ON `food_log_entries` (`loggedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_log_entries_barcode` ON `food_log_entries` (`barcode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_log_entries_externalRecordId` ON `food_log_entries` (`externalRecordId`)")
            }
        }

        /**
         * Migration from version 4 to version 5
         * adding India-First FSSAI fields, Hindi support, brand aliases, and package serving fields.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Alter food_products table
                db.execSQL("ALTER TABLE `food_products` ADD COLUMN `nameHi` TEXT")
                db.execSQL("ALTER TABLE `food_products` ADD COLUMN `brandAliasesJson` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `food_products` ADD COLUMN `category` TEXT")
                db.execSQL("ALTER TABLE `food_products` ADD COLUMN `netQuantityValue` REAL")
                db.execSQL("ALTER TABLE `food_products` ADD COLUMN `netQuantityUnit` TEXT NOT NULL DEFAULT 'GRAMS'")
                db.execSQL("ALTER TABLE `food_products` ADD COLUMN `addedSugarsGrams` REAL")
                db.execSQL("ALTER TABLE `food_products` ADD COLUMN `saturatedFatGrams` REAL")
                db.execSQL("ALTER TABLE `food_products` ADD COLUMN `transFatGrams` REAL")
                db.execSQL("ALTER TABLE `food_products` ADD COLUMN `saltGrams` REAL")
                db.execSQL("ALTER TABLE `food_products` ADD COLUMN `fssaiLicenseNumber` TEXT")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_nameHi` ON `food_products` (`nameHi`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_products_category` ON `food_products` (`category`)")

                // Alter food_log_entries table
                db.execSQL("ALTER TABLE `food_log_entries` ADD COLUMN `calculatedSaturatedFat` REAL")
                db.execSQL("ALTER TABLE `food_log_entries` ADD COLUMN `calculatedTransFat` REAL")
                db.execSQL("ALTER TABLE `food_log_entries` ADD COLUMN `calculatedAddedSugars` REAL")
                db.execSQL("ALTER TABLE `food_log_entries` ADD COLUMN `calculatedSaltG` REAL")
            }
        }

        /**
         * Migration from version 5 to version 6
         * adding assistant_messages table for token-optimized local AI assistant conversation history.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `assistant_messages` (
                        `id` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `timestampEpochMs` INTEGER NOT NULL,
                        `wasLocalHandled` INTEGER NOT NULL,
                        `inputTokens` INTEGER NOT NULL,
                        `outputTokens` INTEGER NOT NULL,
                        `latencyMs` INTEGER NOT NULL,
                        `intentType` TEXT,
                        `referencesJson` TEXT NOT NULL,
                        `isError` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_assistant_messages_timestampEpochMs` ON `assistant_messages` (`timestampEpochMs`)")
            }
        }

        /**
         * Migration from version 6 to version 7
         * adding sleep_check_ins table for original post-sleep morning check-in self-reports.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sleep_check_ins` (
                        `id` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `moodScore` INTEGER,
                        `moodLabel` TEXT,
                        `restedScore` INTEGER,
                        `notes` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `isSelfReported` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`sessionId`) REFERENCES `sleep_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sleep_check_ins_sessionId` ON `sleep_check_ins` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_check_ins_createdAt` ON `sleep_check_ins` (`createdAt`)")
            }
        }

        /**
         * List of explicit Room migrations.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

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
