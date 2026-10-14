# Somnus — Native Android Sleep Tracker (Milestone 1 + SOL & PSQI Extension)

**Somnus** is a local-first, privacy-focused native Android sleep-tracking application built with Kotlin and Jetpack Compose. Somnus prioritizes detailed sleep records, measurable accuracy, transparent uncertainty, battery efficiency, and zero telemetry.

---

## 1. Product Philosophy & Scientific Honesty

- **Local-Only Architecture**: All data is stored strictly in a local SQLite database using Android Room. Somnus contains **no cloud backend, no remote database, no user accounts, no telemetry, no analytics SDKs, and no advertising services**. Your sleep data never leaves your physical device.
- **Scientific Honesty**:
  - Sleep tracking distinguishes **Time in Bed (TIB)** from physiological sleep onset. Somnus does **not** invent heart-rate, HRV, respiratory rate, or sleep-stage values (light, deep, REM) that have not been collected.
  - Consumer wearables and phone accelerometers approximate sleep onset from physical stillness; phone motion alone does not identify physiological sleep onset with clinical accuracy.
  - Sourced observations are strictly segregated: Wearable imports and algorithmic estimates are preserved in parallel with self-reported values and **never overwrite** self-reported metrics.
- **PSQI Copyright & Instrument Compliance**:
  - The 19-item Pittsburgh Sleep Quality Index is a copyrighted instrument belonging to the University of Pittsburgh (Buysse et al., 1989).
  - Somnus does **not** ship a fabricated or unauthorized reproduction of the questionnaire. Instead, it provides the pure Kotlin scoring engine, persistent schema, and a synthetic verification test runner. When authorized questionnaire licensing is configured, the instrument plugs into the existing architecture.
- **Zero Sensor Battery Drain**: Somnus requires no background services, microphone access, camera access, or continuous sensor polling. It persists timestamps rather than keeping CPU wake-locks or background timers.
- **Unambiguous Instant Time Arithmetic**: Timestamps are stored as UTC epoch milliseconds (`java.time.Instant`). Timezone information (`java.time.ZoneId`) is preserved separately to ensure local wall-clock representation accurately reflects your location, including across Daylight Saving Time (DST) transitions and midnight crossings.

---

## 2. Technical Stack

- **Language**: Kotlin 2.3+
- **UI Toolkit**: Jetpack Compose with Material 3
- **Design Identity**: Dark-first, restrained midnight-blue theme (`#080C16` obsidian background, `#82AAFF` astral blue primary, `#56CFE1` moonlight teal secondary) with full support for light mode
- **Architecture**: MVVM with repository interfaces and constructor injection (`AppContainer`)
- **Persistence**: Android Room (SQLite) with explicit migration management (`SomnusDatabase` version 2)
- **Preferences**: Jetpack DataStore Preferences
- **Asynchronous Concurrency**: Kotlin Coroutines and reactive `StateFlow`
- **Testing**: JUnit 4, Kotlin Coroutines Test, Robolectric, AndroidX Arch Core Testing (72 passing tests)

---

## 3. Minimum Android SDK Rationale

- **`minSdk = 26` (Android 8.0 Oreo)**:
  - Complete native runtime support for `java.time.Instant`, `java.time.Duration`, `java.time.LocalDate`, `java.time.LocalTime`, and `java.time.ZoneId` without desugaring performance or memory overhead.
  - Native platform keystore and SQLite capabilities.
  - Covers over 95% of active Android devices worldwide while providing high-precision timestamp arithmetic required for sleep science.
- **`compileSdk = 36`** & **`targetSdk = 34` (Android 14)**:
  - Compiles against latest platform APIs while targeting stable Android 14 behavior.

---

## 4. Package Structure

The codebase is organized into cleanly separated packages with no unnecessary abstractions:

```
com.example.somnus/
├── SomnusApplication.kt              # Application class initializing AppContainer
├── MainActivity.kt                  # Edge-to-edge Compose host activity
├── di/
│   └── AppContainer.kt              # Constructor injection dependency container
├── domain/
│   ├── model/
│   │   ├── SleepSession.kt          # Persistent sleep session entity & interfaces
│   │   ├── SourceSpecificRecords.kt # Typed models: Manual, PhoneEstimate, Wearable
│   │   ├── SolRecord.kt             # SOL record, measurement methods & quality flags
│   │   ├── PsqiModels.kt            # PSQI responses, 7 component scores & global score
│   │   └── SyntheticPsqiFixtures.kt # Standard synthetic verification fixtures
│   └── analysis/
│       ├── DurationCalculator.kt    # Instant-based duration math, formatting, DST safety
│       ├── SessionValidator.kt      # Strict validation rules for session intervals
│       ├── SolCalculator.kt         # SOL duration, sanity checks, weekly/monthly summaries
│       └── PsqiScoringEngine.kt     # Pure Kotlin Buysse (1989) PSQI scoring engine
├── data/
│   ├── local/
│   │   ├── SleepSessionEntity.kt    # Room entity for sleep sessions
│   │   ├── SleepSessionDao.kt       # Transactional DAO enforcing single-active session
│   │   ├── SolRecordEntity.kt       # Room entity for SOL records (foreign key to session)
│   │   ├── SolRecordDao.kt          # SOL DAO (date ranges, session lookups)
│   │   ├── PsqiAssessmentEntity.kt  # Room entity for PSQI assessments
│   │   ├── PsqiDao.kt               # PSQI DAO (latest completed, history)
│   │   └── SomnusDatabase.kt        # Room database v2 with explicit MIGRATION_1_2
│   ├── repository/
│   │   ├── SleepSessionRepository.kt# Session repository interface & impl
│   │   ├── SolRepository.kt         # SOL repository interface & impl
│   │   └── PsqiRepository.kt        # PSQI repository interface & impl
│   └── preferences/
│       └── UserPreferencesRepository.kt # DataStore preferences (theme mode)
├── session/
│   └── SessionManager.kt            # Session lifecycle & active duration ticker
├── theme/
│   ├── Color.kt                     # Midnight-blue dark and ice-slate light palettes
│   ├── Theme.kt                     # Material 3 theme composable
│   └── Type.kt                      # Clean typography styles
└── ui/
    ├── SomnusApp.kt                 # Root navigation, bottom navigation bar (4 tabs)
    ├── components/
    │   └── CommonComponents.kt      # TopBar, ScientificHonestyBadge, ConfirmDialog
    ├── home/
    │   ├── HomeScreen.kt            # Active/Idle tracking, live ticker, recent sessions
    │   └── HomeViewModel.kt         # Home ViewModel
    ├── history/
    │   ├── HistoryScreen.kt         # Chronological completed sessions
    │   └── HistoryViewModel.kt      # History ViewModel
    ├── detail/
    │   ├── SessionDetailScreen.kt   # Timestamp breakdown, time correction dialog
    │   └── SessionDetailViewModel.kt# Detail ViewModel
    ├── insights/
    │   ├── InsightsScreen.kt        # Insights tab host (SOL, PSQI, Comparison)
    │   ├── InsightsViewModel.kt     # Insights state management & actions
    │   ├── SolSection.kt            # Nightly SOL cards, aggregates, entry dialog
    │   ├── SolTrendChart.kt         # Custom Canvas trend chart with 30m reference line
    │   ├── PsqiSection.kt           # PSQI component breakdown, synthetic runner, custom dialog
    │   └── CombinedSection.kt       # Multiscale comparison & educational analysis
    └── settings/
        ├── SettingsScreen.kt        # Local-only policy, diagnostics, multi-table deletion
        └── SettingsViewModel.kt     # Settings ViewModel
```

---

## 5. Functional Capabilities

### Track & Active Session
- **Somnus Tracking Card**: Shows current status (Ready to Track vs. Session in Progress).
- **Start Session**: Single tap starts tracking time in bed. Persists start instant to Room.
- **Active Session Ticker**: Recomputes elapsed duration from the stored start instant every second without keeping wake locks or draining battery.
- **Process Survival**: Reopening Somnus after app termination immediately restores tracking state from Room.
- **End Session with Confirmation**: Prompts confirmation, shows calculated duration in bed, allows adding optional user notes, and commits the session atomically.

### History & Detail Correction
- **Chronological List**: Displays completed sessions with start time, end time, total duration, and source labels.
- **Detail View**: Inspects exact start/end instants, local wall-clock times, timezone, and duration.
- **Time Correction Dialog**: Corrects start or end times with strict validation (positive duration, max 24h, rejection of future timestamps). Clearly marks sessions as `Edited`.

### Sleep Onset Latency (SOL)
- **Nightly Metric**: Tracks time taken from in-bed attempt-to-sleep to sleep onset.
- **Two Logging Modes**: Direct minute entry or time-based calculator (Attempt-to-sleep clock time vs. Sleep-onset clock time, handling midnight crossings).
- **Sourced Observations**: Explicitly classified as `SELF_REPORTED`, `ALGORITHM_ESTIMATE`, or `WEARABLE_IMPORT`.
- **Quality Indicators**: Automatically flags `UNCERTAIN_TIMING`, `EDITED_MANUALLY`, `OUTLIER_EXCESSIVE_LATENCY` (>120m), or `MISSING_SLEEP_ATTEMPT`.
- **Summaries**: Displays 7-day and 30-day summaries with Mean, Median, Min–Max, Recorded Nights, and Missing Nights.
- **Custom Trend Chart**: Canvas-drawn line chart plotting latency values with a 30-minute clinical reference threshold line.

### Pittsburgh Sleep Quality Index (PSQI)
- **Pure Kotlin Scoring Engine**: Full implementation of Buysse et al. (1989) scoring instructions.
  - Component 1: Subjective sleep quality (0–3)
  - Component 2: Sleep latency (Combines usual latency minutes and frequency of struggling <=30 min; 0–3)
  - Component 3: Sleep duration (>7h=0, 6–7h=1, 5–6h=2, <5h=3)
  - Component 4: Habitual sleep efficiency ((actual sleep / hours in bed) * 100; >=85%=0, 75–84%=1, 65–74%=2, <65%=3)
  - Component 5: Sleep disturbances (Sum of 9 disturbance items; 0–3)
  - Component 6: Use of sleep medication (0–3)
  - Component 7: Daytime dysfunction (0–3)
  - Global Score: Sum of components 1–7 (0–21; score > 5 indicates poor sleep quality).
- **Strict Scoring Invariants**:
  - Missing responses are **never** silently converted to zero.
  - Incomplete questionnaires are stored as `INCOMPLETE` with no global score fabricated.
  - Component scores strictly 0–3; Global score strictly 0–21.
- **Synthetic Test Runner**: One-tap execution of validated fixtures (`Optimal Sleep (0)`, `Moderate Disturbance (8)`, `Maximal Severity (21)`, `Incomplete Rejection`) or custom numerical parameter entry.

### Combined Multiscale Analysis
- **Micro vs. Macro Timescales**: Explains how nightly SOL (physiological micro-metric) complements monthly PSQI (psychometric macro-instrument).
- **Educational Distinction**: Clarifies why the application never substitutes arithmetic mean nightly SOL for PSQI Component 2, maintaining clinical validity.
- **Transparency on Sourced Divergence**: Explains differences between objective sensor estimates and subjective sleep state perception.

### Settings & Data Sovereignty
- **Diagnostics**: Real-time record counts for Sessions, SOL records, and PSQI assessments, alongside Room database schema version.
- **Appearance**: Seamless switching between Dark (Default), System, and Light themes via DataStore.
- **Permanent Data Deletion**: Two-step confirmation dialog allowing complete wipe of all local sleep sessions, SOL records, and PSQI assessments.

---

## 6. Verification & Test Suite

The test suite contains **72 automated tests** across domain logic, scoring engines, database DAOs, migrations, and ViewModels. All pass with 100% success rate:

```powershell
.\gradlew.bat test
```

Test coverage includes:
1. `DurationCalculatorTest`: Instant arithmetic, DST transitions, midnight crossings, duration formatting.
2. `SessionValidatorTest`: Start/end order, negative duration rejection, 24-hour limit, future instant rejection.
3. `SolCalculatorTest`: Latency minutes calculation, midnight crossing, onset before attempt rejection, quality flag evaluation, mean/median/range summaries.
4. `PsqiScoringEngineTest`: All 7 components, exact cutoffs, efficiency percentages, synthetic fixtures (0, 8, 21), incomplete item rejection.
5. `SleepSessionDaoTest`: Session insertion, query, active session status, atomic completion, updates.
6. `SolDaoTest`: Record persistence, date range queries, session foreign key cascade.
7. `PsqiDaoTest`: Assessment persistence, latest completed query, JSON responses serialization roundtrip.
8. `SomnusDatabaseMigrationTest`: Verifies `MIGRATION_1_2` successfully adds `sol_records` and `psqi_assessments` without losing existing version 1 sleep sessions.
9. `SessionManagerTest`: Active tracking lifecycle, start/end session handling, process restart survival.
10. `HomeViewModelTest`, `HistoryViewModelTest`, `SessionDetailViewModelTest`, `SettingsViewModelTest`, `InsightsViewModelTest`: Complete StateFlow emissions and user mutation flows.

---

## 7. Building the Application

Build the debug APK:
```powershell
.\gradlew.bat assembleDebug
```
Output artifact: `app/build/outputs/apk/debug/app-debug.apk`
