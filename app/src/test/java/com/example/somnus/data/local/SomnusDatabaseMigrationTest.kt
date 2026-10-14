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
}
