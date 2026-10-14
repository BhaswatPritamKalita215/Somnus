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
}
