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

    @Test
    fun migration4To5_preservesExistingDataAndAddsIndianFssaiFields() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "test_migration_4_5.db")
        if (dbFile.exists()) dbFile.delete()

        val configuration = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create Schema Version 4
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
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val dbV4 = helper.writableDatabase

        // Insert sample records in v4
        dbV4.execSQL(
            """
            INSERT INTO food_products (
                id, barcode, barcodeFormat, name, brand, servingUnit, nutritionBasis,
                energyKcal, proteinGrams, carbohydratesGrams, fatGrams, source, lastVerifiedAt, isUserModified, completeness
            ) VALUES (
                'fp-v4', '8901030383748', 'EAN_13', 'Kissan Fresh Tomato Ketchup', 'Kissan', 'GRAMS', 'PER_100_G',
                130.0, 1.2, 31.0, 0.0, 'LOCAL_USER', 1700000000000, 0, 'COMPLETE'
            )
            """.trimIndent()
        )

        dbV4.execSQL(
            """
            INSERT INTO food_log_entries (
                id, productId, barcode, productName, mealType, loggedAt, localDate,
                quantity, unit, calculatedCalories, calculatedProtein, calculatedCarbs, calculatedFat, source
            ) VALUES (
                'log-v4', 'fp-v4', '8901030383748', 'Kissan Fresh Tomato Ketchup', 'SNACK', 1700000000000, '2026-10-14',
                15.0, 'GRAMS', 19.5, 0.18, 4.65, 0.0, 'LOCAL_USER'
            )
            """.trimIndent()
        )

        // Apply MIGRATION_4_5
        SomnusDatabase.MIGRATION_4_5.migrate(dbV4)

        // Verify existing v4 data preserved
        val fpCursor = dbV4.query("SELECT id, name, energyKcal, brandAliasesJson FROM food_products WHERE id = 'fp-v4'")
        assertTrue(fpCursor.moveToFirst())
        assertEquals("fp-v4", fpCursor.getString(0))
        assertEquals("Kissan Fresh Tomato Ketchup", fpCursor.getString(1))
        assertEquals(130.0, fpCursor.getDouble(2), 0.01)
        assertEquals("[]", fpCursor.getString(3)) // Default value for new column
        fpCursor.close()

        val logCursor = dbV4.query("SELECT id, calculatedCalories, calculatedSaltG FROM food_log_entries WHERE id = 'log-v4'")
        assertTrue(logCursor.moveToFirst())
        assertEquals("log-v4", logCursor.getString(0))
        assertEquals(19.5, logCursor.getDouble(1), 0.01)
        assertTrue(logCursor.isNull(2)) // Nullable new column defaults to null
        logCursor.close()

        // Insert new record with Indian FSSAI fields, Hindi name, and net quantity
        dbV4.execSQL(
            """
            INSERT INTO food_products (
                id, barcode, barcodeFormat, name, nameHi, brand, brandAliasesJson, category,
                packageDescription, netQuantityValue, netQuantityUnit, servingSize, servingUnit,
                nutritionBasis, energyKcal, proteinGrams, carbohydratesGrams, sugarGrams, addedSugarsGrams,
                fatGrams, saturatedFatGrams, transFatGrams, sodiumMilligrams, saltGrams, fssaiLicenseNumber,
                source, lastVerifiedAt, isUserModified, completeness
            ) VALUES (
                'fp-india-1', '8901719101053', 'EAN_13', 'Parle-G Original Gluco Biscuits', 'पारले-जी ग्लूकोज बिस्कुट',
                'Parle', '["Parle-G", "Parle G"]', 'Biscuits & Cookies', '79.9g pack', 79.9, 'GRAMS',
                26.6, 'GRAMS', 'PER_100_G', 454.0, 6.5, 78.2, 25.5, 24.0, 13.0, 6.0, 0.0, 280.0, 0.71,
                '10012022000122', 'VERIFIED_CATALOGUE', 1700000000000, 0, 'COMPLETE'
            )
            """.trimIndent()
        )

        val indiaCursor = dbV4.query(
            "SELECT barcode, nameHi, category, netQuantityValue, addedSugarsGrams, saltGrams, fssaiLicenseNumber " +
                    "FROM food_products WHERE barcode = '8901719101053'"
        )
        assertTrue(indiaCursor.moveToFirst())
        assertEquals("8901719101053", indiaCursor.getString(0))
        assertEquals("पारले-जी ग्लूकोज बिस्कुट", indiaCursor.getString(1))
        assertEquals("Biscuits & Cookies", indiaCursor.getString(2))
        assertEquals(79.9, indiaCursor.getDouble(3), 0.01)
        assertEquals(24.0, indiaCursor.getDouble(4), 0.01)
        assertEquals(0.71, indiaCursor.getDouble(5), 0.001)
        assertEquals("10012022000122", indiaCursor.getString(6))
        indiaCursor.close()

        dbV4.close()
        dbFile.delete()
    }

    @Test
    fun migration_5_to_6_creates_assistant_messages_table_and_preserves_existing_data() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "test_migration_5_6.db")
        if (dbFile.exists()) dbFile.delete()

        val configuration = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `food_products` (
                            `id` TEXT NOT NULL PRIMARY KEY,
                            `barcode` TEXT NOT NULL,
                            `barcodeFormat` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `brand` TEXT,
                            `category` TEXT,
                            `packageDescription` TEXT,
                            `netQuantityValue` REAL,
                            `netQuantityUnit` TEXT,
                            `servingSize` REAL,
                            `servingUnit` TEXT NOT NULL,
                            `nutritionBasis` TEXT NOT NULL,
                            `energyKcal` REAL NOT NULL,
                            `proteinGrams` REAL,
                            `carbohydratesGrams` REAL,
                            `fatGrams` REAL,
                            `source` TEXT NOT NULL,
                            `lastVerifiedAt` INTEGER NOT NULL,
                            `isUserModified` INTEGER NOT NULL,
                            `completeness` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val dbV5 = helper.writableDatabase

        // Insert existing data into v5 tables
        dbV5.execSQL(
            """
            INSERT INTO food_products (
                id, barcode, barcodeFormat, name, brand, category,
                packageDescription, netQuantityValue, netQuantityUnit, servingSize, servingUnit,
                nutritionBasis, energyKcal, proteinGrams, carbohydratesGrams, fatGrams,
                source, lastVerifiedAt, isUserModified, completeness
            ) VALUES (
                'fp-v5', '8901030383748', 'EAN_13', 'Kissan Fresh Tomato Ketchup', 'Kissan', 'Condiments',
                '1kg bottle', 1000.0, 'GRAMS', 15.0, 'GRAMS',
                'PER_100_G', 130.0, 1.2, 31.0, 0.0,
                'LOCAL_USER', 1700000000000, 0, 'COMPLETE'
            )
            """.trimIndent()
        )

        // Apply MIGRATION_5_6
        SomnusDatabase.MIGRATION_5_6.migrate(dbV5)

        // Verify existing v5 data is preserved
        val fpCursor = dbV5.query("SELECT id, name, energyKcal FROM food_products WHERE id = 'fp-v5'")
        assertTrue(fpCursor.moveToFirst())
        assertEquals("fp-v5", fpCursor.getString(0))
        assertEquals("Kissan Fresh Tomato Ketchup", fpCursor.getString(1))
        assertEquals(130.0, fpCursor.getDouble(2), 0.01)
        fpCursor.close()

        // Insert and verify assistant_messages table
        dbV5.execSQL(
            """
            INSERT INTO assistant_messages (
                id, role, content, timestampEpochMs, wasLocalHandled,
                inputTokens, outputTokens, latencyMs, intentType, referencesJson, isError
            ) VALUES (
                'msg-1', 'ASSISTANT', 'You slept 7 hours and 45 minutes.', 1700000000000,
                1, 0, 0, 15, 'LATEST_SLEEP', '[]', 0
            )
            """.trimIndent()
        )

        val msgCursor = dbV5.query(
            "SELECT id, role, content, intentType, wasLocalHandled, latencyMs FROM assistant_messages WHERE id = 'msg-1'"
        )
        assertTrue(msgCursor.moveToFirst())
        assertEquals("msg-1", msgCursor.getString(0))
        assertEquals("ASSISTANT", msgCursor.getString(1))
        assertEquals("You slept 7 hours and 45 minutes.", msgCursor.getString(2))
        assertEquals("LATEST_SLEEP", msgCursor.getString(3))
        assertEquals(1, msgCursor.getInt(4))
        assertEquals(15, msgCursor.getInt(5))
        msgCursor.close()

        // Verify index on timestampEpochMs exists
        val indexCursor = dbV5.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_assistant_messages_timestampEpochMs'"
        )
        assertTrue("Expected index_assistant_messages_timestampEpochMs to exist", indexCursor.moveToFirst())
        indexCursor.close()

        dbV5.close()
        dbFile.delete()
    }
}
