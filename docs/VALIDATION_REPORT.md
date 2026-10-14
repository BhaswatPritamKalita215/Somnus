# Somnus — Milestone 5: Validation, Reliability & Engineering Report

**Date:** March 2026  
**Application Version:** 1.2.0 (Milestone 5)  
**Database Schema Version:** 2  
**Test Suite Status:** 123 passing unit and integration tests (100% success)  
**Build Status:** Clean debug APK assembled (`assembleDebug` exit code 0)

---

## 1. Executive Summary & Codebase Audit

Milestone 5 focuses on validating the Somnus application, hardening database persistence, eliminating edge-case traps, establishing empirical reliability boundaries, and validating Health Connect and phone sensor integrations against physical reality.

### Feature Status Audit: Implemented vs. Partially Implemented vs. Planned

| Feature Area | Documented Status | Reality in Codebase | Action Taken in Milestone 5 |
|---|---|---|---|
| **Manual Sleep Tracking** | Fully Implemented | Functional Room persistence, reactive Compose UI | Hardened against stale sessions (>36h) and overlapping collisions |
| **Active Session State** | Fully Implemented | StateFlow from Room; survives process death | Added stale detection (>36h) and recovery dialog in UI |
| **SOL Tracking & Calculations** | Fully Implemented | Persistent `sol_records`, weekly/monthly statistics | Added SQLite foreign key enforcement (`PRAGMA foreign_keys = ON;`) |
| **PSQI Scoring & Validation** | Fully Implemented | Buysse (1989) scoring engine, Room table, fixtures | Preserved; included in atomic multi-table wipe transactions |
| **Sleep Analytics & Trends** | Fully Implemented | Circular time math, sleep-day merging, baseline comparisons | Validated edge cases (DST transitions, midnight crossings) |
| **Health Connect Integration** | Planned in M2 | Dependency added, but no manager, permission, or sync layer | Implemented `HealthConnectManager`, client wrapper, and test doubles |
| **Phone Sensor Tracking** | Planned in M3 | Enum `PHONE_ESTIMATE` existed without sensor sampling | Implemented `PhoneSensorTracker`, constant-space Welford aggregator, and interruption auditing |

---

## 2. Prioritized Defect Audit & Resolutions

### Category 1: Critical Data-Integrity & Safety Issues (Fixed)

1. **Foreign Key Enforcement in SQLite Connections:**
   - *Defect:* Although `SolRecordEntity` declared `@ForeignKey` referencing `sleep_sessions(id)`, Room does not enable SQLite foreign keys by default.
   - *Fix:* Executed `PRAGMA foreign_keys = ON;` in `RoomDatabase.Callback.onOpen()` for all SQLite connections in `SomnusDatabase`.
2. **Stale Active Session Trap (>36 Hours):**
   - *Defect:* If a user forgot to stop a session and left it active past 36 hours, `SessionValidator.validate` rejected duration > 36h. If the user tried to end the session at `Instant.now()`, the transaction failed with an error, leaving the active session permanently stuck.
   - *Fix:* Added `recoverStaleActiveSession(id, correctedEndTime, notes)` to `SleepSessionRepository` and `SessionManager`, and surfaced a prominent "Recover Stale Session" dialog in `HomeScreen` allowing the user to supply their estimated wake time or discard the session.
3. **Overlapping Session Collisions:**
   - *Defect:* A user could edit a completed session or complete an active session such that its interval collided with another existing completed session.
   - *Fix:* Added `findOverlappingSessions` to `SleepSessionDao` and enforced overlap checks in `startSession`, `endSession`, `updateSession`, and `insertCompletedSession`.
4. **Non-Atomic Multi-Table Wipe Race:**
   - *Defect:* `SettingsViewModel.deleteAllData()` previously issued sequential delete calls across repositories without a shared transaction boundary.
   - *Fix:* Added `@Transaction suspend fun clearAllDataTables()` to `SomnusDatabase` which atomically wipes `sol_records`, `psqi_assessments`, and `sleep_sessions` in strict foreign-key order.

### Category 2: Major Reliability & Accuracy Issues (Fixed)

1. **Health Connect Capability & Sync Validation:**
   - *Requirement:* Detect device support, distinguish installed vs uninstalled vs update required, validate permissions, and prevent duplicate or destructive imports.
   - *Fix:* Implemented `HealthConnectManager` and `HealthConnectClientWrapper` with exact interval deduplication and a non-destructive conflict guarantee (wearable imports never overwrite manual self-reports).
2. **Phone Accelerometer Memory Bloat & Battery Drain:**
   - *Requirement:* Mobile accelerometer sampling at 50–100 Hz generates millions of floats overnight, exhausting heap space and battery if buffered naively.
   - *Fix:* Implemented `MovementWindowAggregator` using online Welford's algorithm to stream mean, variance, and peak acceleration in constant space ($O(1)$ memory) into 60-second summary epochs.
3. **Tracking Interruption Auditing:**
   - *Requirement:* OS memory reclamation (LMK), battery saver restrictions, or device reboots cause unmonitored tracking gaps.
   - *Fix:* Implemented `TrackingInterruption` logging in `PhoneSensorTracker` with reason categorization (`BATTERY_SAVER_ENABLED`, `LOW_BATTERY_SHUTDOWN`, `SYSTEM_KILLED_SERVICE`, `DEVICE_REBOOT`).

### Category 3: Minor Usability & Maintainability Issues (Fixed)

1. **Circular Mean Statistical Edge Cases:**
   - Verified that bedtime and wake-time averaging across midnight (e.g., 23:30 and 00:30) correctly resolves to 00:00 midnight via vector circular trigonometry rather than misleading linear arithmetic (12:00 noon).
2. **Daylight Saving Time (DST) Transitions:**
   - Verified that US/EU Spring forward (clock skips 02:00 $\rightarrow$ 03:00) computes 7 physical hours, and Fall backward (clock repeats 02:00 $\rightarrow$ 01:00) computes 9 physical hours via UTC epoch millisecond arithmetic.

---

## 3. The Three-Tier Validation Framework

Somnus evaluates all sleep data under a rigorous three-tier validation framework:

```
┌────────────────────────────────────────────────────────┐
│ Tier 1: Implementation Correctness                     │
│ - Schema migrations & foreign key enforcement          │
│ - Zero data loss on process death / reboot             │
│ - Timestamp validation & collision prevention          │
├────────────────────────────────────────────────────────┤
│ Tier 2: Measurement Reliability                        │
│ - Constant-space 60-second epoch aggregation           │
│ - Interruption tracking (battery saver, LMK, reboots)   │
│ - Exact deduplication of wearable imports              │
├────────────────────────────────────────────────────────┤
│ Tier 3: Measurement Accuracy (Scientific Reality)      │
│ - Mobile accelerometers CANNOT measure sleep stages    │
│ - Stationary on nightstand != physiological sleep      │
│ - Time in Bed (TIB) clearly segregated from onset       │
└────────────────────────────────────────────────────────┘
```

### Scientific Honesty Guarantee: What Phone Sensors Can and Cannot Measure

1. **What Phone Sensors CAN Genuinely Measure:**
   - Device physical stillness (standard deviation of acceleration $\sigma < 0.05 \text{ m/s}^2$).
   - Gross movement, bed repositioning, or handling vibrations.
   - Invariant resting state on a firm surface (nightstand detection).
   - Ambient power status (charging vs discharging) and screen illumination events.
2. **What Phone Sensors CANNOT Measure (and Somnus Refuses to Fabricate):**
   - **Sleep Architecture / Stages:** Accelerometers cannot detect REM, Light, or Deep (slow-wave) sleep. Clinical polysomnography (PSG) requires electroencephalography (EEG), electrooculography (EOG), and electromyography (EMG).
   - **Physiological Sleep Onset:** A motionless person lying awake in bed is physically indistinguishable from a sleeping person via phone motion alone.
   - **Continuous Heart Rate / HRV:** Somnus does not synthesize pseudo-cardiac metrics from the phone microphone or camera.

---

## 4. Test Verification Suite Summary

The automated test suite was expanded from 99 tests in Milestone 4 to **123 tests in Milestone 5**, all executing with 100% pass rate:

- **Persistence & DAO Tests (4 suites, 32 tests):**
  - `SomnusDatabaseMigrationTest`: Verified migration v1 $\rightarrow$ v2 preserves sessions and creates new tables.
  - `SleepSessionDaoTest`: Single active session invariant, start/complete transitions.
  - `SolDaoTest`: Foreign key mapping, date range queries.
  - `PsqiDaoTest`: Assessment completion and historical lookup.
- **Repository Validation Tests (2 suites, 20 tests):**
  - `SleepSessionRepositoryValidationTest`: Foreign key enforcement (`PRAGMA foreign_keys = ON;`), atomic multi-table wipe (`clearAllDataTables()`), overlap topologies (start overlap, end overlap, enclosed, spanning), and stale session recovery.
  - `SleepAnalyticsRepositoryTest`: Multi-session merging and reactive analytics pipeline.
- **Health Connect Integration Tests (1 suite, 7 tests):**
  - `HealthConnectManagerTest`: Availability states (Available, Update Required, Not Installed, Not Supported), permission denial handling, empty record handling, wearable source attribution, exact deduplication, and non-destructive conflict handling.
- **Sensor Aggregator & Interruption Tests (1 suite, 5 tests):**
  - `MovementWindowAggregatorTest`: Stillness, 30-epoch nightstand stationary, minor restlessness, active movement, and unmonitored interruption categories using Welford's streaming algorithm.
- **Domain Analysis & Edge Case Tests (5 suites, 35 tests):**
  - `SleepCalculationEdgeCasesTest`: DST spring forward, DST fall backward, midnight crossing circular mean, zero duration rejection, negative duration rejection, >36h duration rejection.
  - `SessionValidatorTest`: Time window validation, clock skew allowance.
  - `DurationCalculatorTest`: Timestamp formatting and elapsed calculations.
  - `SolCalculatorTest`: SOL sanity checks and weekly averages.
  - `PsqiScoringEngineTest`: Buysse 7 component algorithms and global score.
- **ViewModel & UI StateFlow Tests (7 suites, 24 tests):**
  - `HomeViewModelTest`, `HistoryViewModelTest`, `SessionDetailViewModelTest`, `SettingsViewModelTest`, `AnalyticsViewModelTest`, `InsightsViewModelTest`.

---

## 5. Build & Packaging Verification

- **Gradle Build:** `.\gradlew.bat assembleDebug` completed with exit code 0.
- **APK Target:** `app/build/outputs/apk/debug/app-debug.apk` successfully generated.
- **Local-Only Guarantee:** ProGuard/R8 and dependency audit confirm zero cloud endpoints, zero analytics SDKs, zero telemetry, and zero advertising libraries.
