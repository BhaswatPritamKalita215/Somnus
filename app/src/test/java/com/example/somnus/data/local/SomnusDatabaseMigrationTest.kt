package com.example.somnus.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SomnusDatabaseMigrationTest {

    @Test
    fun migration1To2_preservesExistingSessionsAndCreatesNewTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "test_migration_1_2.db")
        if (dbFile.exists()) dbFile.delete()

        val configuration = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create Schema Version 1
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `sleep_sessions` (
                            `id` TEXT NOT NULL,
                            `startTime` INTEGER NOT NULL,
                            `endTime` INTEGER,
                            `timezoneId` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `source` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            `notes` TEXT,
                            `isCorrected` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_sessions_status` ON `sleep_sessions` (`status`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_sessions_startTime` ON `sleep_sessions` (`startTime`)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val dbV1 = helper.writableDatabase

        // Insert a test session into v1
        dbV1.execSQL(
            """
            INSERT INTO sleep_sessions (id, startTime, endTime, timezoneId, status, source, createdAt, updatedAt, notes, isCorrected)
            VALUES ('sess-v1-original', 1700000000000, 1700028800000, 'UTC', 'COMPLETED', 'MANUAL', 1700000000000, 1700028800000, 'Milestone 1 Session', 0)
            """.trimIndent()
        )

        // Apply MIGRATION_1_2
        SomnusDatabase.MIGRATION_1_2.migrate(dbV1)

        // Verify existing session is preserved intact
        val sessionCursor = dbV1.query("SELECT id, notes FROM sleep_sessions WHERE id = 'sess-v1-original'")
        assertTrue(sessionCursor.moveToFirst())
        assertEquals("sess-v1-original", sessionCursor.getString(0))
        assertEquals("Milestone 1 Session", sessionCursor.getString(1))
        sessionCursor.close()

        // Verify sol_records table exists and accepts insertion
        dbV1.execSQL(
            """
            INSERT INTO sol_records (id, sessionId, reportingDate, zoneId, latencyMinutes, method, sourceIdentifier, qualityFlags, createdAt, updatedAt)
            VALUES ('sol-mig-1', 'sess-v1-original', '2026-10-14', 'UTC', 20, 'SELF_REPORTED', 'manual', '', 1700000000000, 1700000000000)
            """.trimIndent()
        )
        val solCursor = dbV1.query("SELECT id, latencyMinutes FROM sol_records WHERE id = 'sol-mig-1'")
        assertTrue(solCursor.moveToFirst())
        assertEquals("sol-mig-1", solCursor.getString(0))
        assertEquals(20, solCursor.getInt(1))
        solCursor.close()

        // Verify psqi_assessments table exists and accepts insertion
        dbV1.execSQL(
            """
            INSERT INTO psqi_assessments (id, startedAt, version, language, reportingPeriod, status, scoringVersion, globalScore, responsesJson)
            VALUES ('psqi-mig-1', 1700000000000, 'v1', 'en', 'Month', 'COMPLETED', 'v1', 0, '{}')
            """.trimIndent()
        )
        val psqiCursor = dbV1.query("SELECT id, globalScore FROM psqi_assessments WHERE id = 'psqi-mig-1'")
        assertTrue(psqiCursor.moveToFirst())
        assertEquals("psqi-mig-1", psqiCursor.getString(0))
        assertEquals(0, psqiCursor.getInt(1))
        psqiCursor.close()

        dbV1.close()
        dbFile.delete()
    }

    @Test
    fun migration2To3_preservesExistingData_andCreatesStepTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "test_migration_2_3.db")
        if (dbFile.exists()) dbFile.delete()

        val configuration = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create Schema Version 2
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `sleep_sessions` (
                            `id` TEXT NOT NULL,
                            `startTime` INTEGER NOT NULL,
                            `endTime` INTEGER,
                            `timezoneId` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `source` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            `notes` TEXT,
                            `isCorrected` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
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
                            `notes` TEXT
                        )
                        """.trimIndent()
                    )
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
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val dbV2 = helper.writableDatabase

        // Insert sample records in v2
        dbV2.execSQL("INSERT INTO sleep_sessions (id, startTime, timezoneId, status, source, createdAt, updatedAt, isCorrected) VALUES ('s2', 1000, 'UTC', 'COMPLETED', 'MANUAL', 1000, 1000, 0)")
        dbV2.execSQL("INSERT INTO sol_records (id, reportingDate, zoneId, latencyMinutes, method, sourceIdentifier, qualityFlags, createdAt, updatedAt) VALUES ('sol2', '2026-10-14', 'UTC', 15, 'SELF_REPORTED', 'manual', '', 1000, 1000)")
        dbV2.execSQL("INSERT INTO psqi_assessments (id, startedAt, version, language, reportingPeriod, status, scoringVersion, responsesJson) VALUES ('psqi2', 1000, 'v1', 'en', 'Month', 'COMPLETED', 'v1', '{}')")

        // Apply MIGRATION_2_3
        SomnusDatabase.MIGRATION_2_3.migrate(dbV2)

        // Verify v2 tables are preserved
        val sCursor = dbV2.query("SELECT id FROM sleep_sessions WHERE id = 's2'")
        assertTrue(sCursor.moveToFirst())
        assertEquals("s2", sCursor.getString(0))
        sCursor.close()

        val solCursor = dbV2.query("SELECT id FROM sol_records WHERE id = 'sol2'")
        assertTrue(solCursor.moveToFirst())
        assertEquals("sol2", solCursor.getString(0))
        solCursor.close()

        val psqiCursor = dbV2.query("SELECT id FROM psqi_assessments WHERE id = 'psqi2'")
        assertTrue(psqiCursor.moveToFirst())
        assertEquals("psqi2", psqiCursor.getString(0))
        psqiCursor.close()

        // Verify step_records table accepts insertion
        dbV2.execSQL(
            """
            INSERT INTO step_records (id, startTime, endTime, localDate, zoneId, stepCount, source, sourceIdentifier, createdAt)
            VALUES ('step-1', 1700000000000, 1700003600000, '2026-10-14', 'UTC', 1250, 'PHONE_HARDWARE', 'sensor', 1700003600000)
            """.trimIndent()
        )
        val stepCursor = dbV2.query("SELECT id, stepCount, source FROM step_records WHERE id = 'step-1'")
        assertTrue(stepCursor.moveToFirst())
        assertEquals("step-1", stepCursor.getString(0))
        assertEquals(1250, stepCursor.getLong(1))
        assertEquals("PHONE_HARDWARE", stepCursor.getString(2))
        stepCursor.close()

        // Verify step_checkpoints table accepts insertion
        dbV2.execSQL(
            """
            INSERT INTO step_checkpoints (id, bootTimestamp, lastSensorValue, accumulatedSteps, lastUpdateEpochMs, localDate)
            VALUES ('active_hardware_checkpoint', 50000, 1250.0, 1250, 1700003600000, '2026-10-14')
            """.trimIndent()
        )
        val cpCursor = dbV2.query("SELECT id, accumulatedSteps FROM step_checkpoints WHERE id = 'active_hardware_checkpoint'")
        assertTrue(cpCursor.moveToFirst())
        assertEquals("active_hardware_checkpoint", cpCursor.getString(0))
        assertEquals(1250, cpCursor.getLong(1))
        cpCursor.close()

        dbV2.close()
        dbFile.delete()
    }

    @Test
    fun migration3To4_preservesExistingDataAndCreatesFoodTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "test_migration_3_4.db")
        if (dbFile.exists()) dbFile.delete()

        val configuration = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create Schema Version 3
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `sleep_sessions` (
                            `id` TEXT NOT NULL,
                            `startTime` INTEGER NOT NULL,
                            `endTime` INTEGER,
                            `timezoneId` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `source` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            `notes` TEXT,
                            `isCorrected` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `sol_records` (
                            `id` TEXT NOT NULL,
                            `sessionId` TEXT,
                            `reportingDate` TEXT NOT NULL,
                            `zoneId` TEXT NOT NULL,
                            `latencyMinutes` INTEGER NOT NULL,
                            `method` TEXT NOT NULL,
                            `sourceIdentifier` TEXT NOT NULL,
                            `sourceDeviceModel` TEXT,
                            `qualityFlags` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `psqi_assessments` (
                            `id` TEXT NOT NULL,
                            `startedAt` INTEGER NOT NULL,
                            `completedAt` INTEGER,
                            `version` TEXT NOT NULL,
                            `language` TEXT NOT NULL,
                            `reportingPeriod` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `scoringVersion` TEXT NOT NULL,
                            `globalScore` INTEGER,
                            `component1Duration` INTEGER,
                            `component2Disturbance` INTEGER,
                            `component3Latency` INTEGER,
                            `component4Efficiency` INTEGER,
                            `component5Quality` INTEGER,
                            `component6Medication` INTEGER,
                            `component7DaytimeDysfunction` INTEGER,
                            `responsesJson` TEXT NOT NULL,
                            `calculationNotes` TEXT,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `step_records` (
                            `id` TEXT NOT NULL,
                            `startTime` INTEGER NOT NULL,
                            `endTime` INTEGER NOT NULL,
                            `localDate` TEXT NOT NULL,
                            `zoneId` TEXT NOT NULL,
                            `stepCount` INTEGER NOT NULL,
                            `source` TEXT NOT NULL,
                            `sourceIdentifier` TEXT,
                            `createdAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `step_checkpoints` (
                            `id` TEXT NOT NULL,
                            `bootTimestamp` INTEGER NOT NULL,
                            `lastSensorValue` REAL NOT NULL,
                            `accumulatedSteps` INTEGER NOT NULL,
                            `lastUpdateEpochMs` INTEGER NOT NULL,
                            `localDate` TEXT NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val dbV3 = helper.writableDatabase

        // Insert sample records in v3
        dbV3.execSQL("INSERT INTO sleep_sessions (id, startTime, timezoneId, status, source, createdAt, updatedAt, isCorrected) VALUES ('s3', 1000, 'UTC', 'COMPLETED', 'MANUAL', 1000, 1000, 0)")
        dbV3.execSQL("INSERT INTO step_records (id, startTime, endTime, localDate, zoneId, stepCount, source, createdAt) VALUES ('step-3', 1000, 2000, '2026-10-14', 'UTC', 500, 'PHONE_HARDWARE', 2000)")

        // Apply MIGRATION_3_4
        SomnusDatabase.MIGRATION_3_4.migrate(dbV3)

        // Verify v3 records are preserved
        val sCursor = dbV3.query("SELECT id FROM sleep_sessions WHERE id = 's3'")
        assertTrue(sCursor.moveToFirst())
        assertEquals("s3", sCursor.getString(0))
        sCursor.close()

        val stepCursor = dbV3.query("SELECT id, stepCount FROM step_records WHERE id = 'step-3'")
        assertTrue(stepCursor.moveToFirst())
        assertEquals("step-3", stepCursor.getString(0))
        assertEquals(500, stepCursor.getLong(1))
        stepCursor.close()

        // Verify food_products table accepts insertion
        dbV3.execSQL(
            """
            INSERT INTO food_products (
                id, barcode, barcodeFormat, name, brand, variant, packageDescription,
                servingSize, servingUnit, nutritionBasis, energyKcal, proteinGrams,
                carbohydratesGrams, fatGrams, fiberGrams, sugarGrams, sodiumMilligrams,
                source, sourceProductId, lastVerifiedAt, isUserModified, completeness, notes
            ) VALUES (
                'fp-1', '3017620422003', 'EAN_13', 'Nutella Hazelnut Spread', 'Ferrero', NULL, '400g jar',
                15.0, 'GRAMS', 'PER_100_G', 539.0, 6.3, 57.5, 30.9, NULL, 56.3, 42.0,
                'OPEN_FOOD_FACTS', '3017620422003', 1700000000000, 0, 'COMPLETE', NULL
            )
            """.trimIndent()
        )
        val fpCursor = dbV3.query("SELECT id, name, energyKcal FROM food_products WHERE barcode = '3017620422003'")
        assertTrue(fpCursor.moveToFirst())
        assertEquals("fp-1", fpCursor.getString(0))
        assertEquals("Nutella Hazelnut Spread", fpCursor.getString(1))
        assertEquals(539.0, fpCursor.getDouble(2), 0.01)
        fpCursor.close()

        // Verify food_log_entries table accepts insertion
        dbV3.execSQL(
            """
            INSERT INTO food_log_entries (
                id, productId, barcode, productName, brand, mealType, loggedAt, localDate,
                quantity, unit, numberOfServings, calculatedCalories, calculatedProtein,
                calculatedCarbs, calculatedFat, calculatedFiber, calculatedSugar,
                calculatedSodiumMg, source, externalRecordId
            ) VALUES (
                'log-1', 'fp-1', '3017620422003', 'Nutella Hazelnut Spread', 'Ferrero', 'BREAKFAST', 1700000000000, '2026-10-14',
                30.0, 'GRAMS', 2.0, 161.7, 1.89, 17.25, 9.27, NULL, 16.89, 12.6,
                'OPEN_FOOD_FACTS', NULL
            )
            """.trimIndent()
        )
        val logCursor = dbV3.query("SELECT id, calculatedCalories, mealType FROM food_log_entries WHERE id = 'log-1'")
        assertTrue(logCursor.moveToFirst())
        assertEquals("log-1", logCursor.getString(0))
        assertEquals(161.7, logCursor.getDouble(1), 0.01)
        assertEquals("BREAKFAST", logCursor.getString(2))
        logCursor.close()

        dbV3.close()
        dbFile.delete()
    }
}
