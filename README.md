# Somnus — Native Android Sleep Tracker (Milestones 1–6)

**Somnus** is a local-first, privacy-focused native Android sleep-tracking application built with Kotlin and Jetpack Compose. Somnus prioritizes detailed sleep records, physical activity tracking, measurable accuracy, transparent uncertainty, battery efficiency, and zero telemetry.

---

## 1. Product Philosophy & Scientific Honesty

- **Local-Only Architecture**: All data is stored strictly in a local SQLite database using Android Room. Somnus contains **no cloud backend, no remote database, no user accounts, no telemetry, no analytics SDKs, and no advertising services**. Your sleep and step data never leaves your physical device.
- **Scientific Honesty**:
  - Sleep tracking distinguishes **Time in Bed (TIB)** from physiological sleep onset. Somnus does **not** invent heart-rate, HRV, respiratory rate, or sleep-stage values (light, deep, REM) that have not been collected.
  - Consumer wearables and phone accelerometers approximate sleep onset from physical stillness; phone motion alone does not identify physiological sleep onset with clinical accuracy.
  - Sourced observations are strictly segregated: Wearable imports and algorithmic estimates are preserved in parallel with self-reported values and **never overwrite** self-reported metrics.
  - **Step Tracking & Activity**: Somnus reconciles steps intelligently without blind summation. Wearable on-body steps and phone sensor steps for overlapping intervals are deduplicated to avoid false double-counting, while non-overlapping intervals (e.g. phone steps while watch was charging) are combined.
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
- **Persistence**: Android Room (SQLite) with explicit migration management (`SomnusDatabase` version 3) and enforced foreign keys (`PRAGMA foreign_keys = ON;`)
- **Preferences**: Jetpack DataStore Preferences
- **Asynchronous Concurrency**: Kotlin Coroutines and reactive `StateFlow`
- **Testing**: JUnit 4, Kotlin Coroutines Test, Robolectric, AndroidX Arch Core Testing (144 passing tests)

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
│   ├── analysis/
│   │   ├── DurationCalculator.kt    # Instant-based duration math, formatting, DST safety
│   │   ├── SessionValidator.kt      # Strict validation rules for session intervals
│   │   ├── SolCalculator.kt         # SOL duration, sanity checks, weekly/monthly summaries
│   │   └── PsqiScoringEngine.kt     # Pure Kotlin Buysse (1989) PSQI scoring engine
│   └── analytics/
│       ├── CircularTimeCalculator.kt # Vector circular mean and circular standard deviation
│       ├── SleepAnalyticsModels.kt   # Day summaries, period metrics, comparisons, insights
│       ├── SleepMetricsCalculator.kt # Interval merging, nap handling, clinical sleep-day
│       ├── SleepTrendAnalyzer.kt     # Baseline comparison with reliability qualification
│       ├── SleepDataQualityAnalyzer.kt # Coverage scoring, missing/conflicted night tracking
│       └── SleepInsightEngine.kt     # Deterministic rules-based insight generation
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
│   │   ├── PsqiRepository.kt        # PSQI repository interface & impl
│   │   ├── SleepAnalyticsRepository.kt # Analytics repository interface
│   │   └── SleepAnalyticsRepositoryImpl.kt # Reactive flow aggregator on Dispatchers.IO
│   └── preferences/
│       └── UserPreferencesRepository.kt # DataStore preferences (theme mode)
├── session/
│   └── SessionManager.kt            # Session lifecycle & active duration ticker
├── theme/
│   ├── Color.kt                     # Midnight-blue dark and ice-slate light palettes
│   ├── Theme.kt                     # Material 3 theme composable
│   └── Type.kt                      # Clean typography styles
└── ui/
    ├── SomnusApp.kt                 # Root navigation, bottom navigation bar (5 tabs)
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
    ├── analytics/
    │   ├── AnalyticsScreen.kt       # Milestone 4 Dashboard host & date range selector
    │   ├── AnalyticsViewModel.kt    # Reactive StateFlow emissions & date range filter
    │   └── components/
    │       ├── AnalyticsCharts.kt   # Canvas duration trend & schedule consistency charts
    │       └── AnalyticsCards.kt    # Overview, comparison, data quality, and insight cards
    ├── insights/
    │   ├── InsightsScreen.kt        # Instruments tab host (SOL & PSQI)
    │   ├── InsightsViewModel.kt     # Instruments state management & actions
    │   ├── SolSection.kt            # Nightly SOL cards, aggregates, entry dialog
    │   ├── SolTrendChart.kt         # Custom Canvas trend chart with 30m reference line
    │   ├── PsqiSection.kt           # PSQI component breakdown, synthetic runner, custom dialog
    │   └── CombinedSection.kt       # Multiscale comparison & educational analysis
    └── settings/
        ├── SettingsScreen.kt        # Local-only policy, diagnostics, multi-table deletion
        └── SettingsViewModel.kt     # Settings ViewModel
```

---

## 5. Milestone 4: Sleep Analytics, Trends & Personalized Insights

Milestone 4 introduces a dedicated, testable domain analytics engine and an interactive Jetpack Compose dashboard.

### Core Sleep Analytics & Statistics
1. **Clinical Sleep Date Attribution**:
   - Overnight sessions crossing midnight or beginning between 00:00 and 06:00 are attributed to the clinical sleep opportunity of the preceding calendar evening.
2. **Interval Merging (Zero Double-Counting)**:
   - When multiple sources overlap on the same night (e.g. user manual log plus wearable Health Connect import), intervals $[s_i, e_i]$ are sorted and merged. Overlapping minutes are never double-counted.
3. **Circular Statistics Across Midnight**:
   - Conventional arithmetic averaging of wall-clock times fails across midnight (e.g. averaging 23:30 and 00:30 yields 12:00 noon).
   - Somnus uses unit vector averaging:
     $$\theta_i = \frac{\text{minute}_i}{1440} \times 2\pi, \quad \bar{C} = \frac{1}{N}\sum\cos\theta_i, \quad \bar{S} = \frac{1}{N}\sum\sin\theta_i$$
     $$\bar{\theta} = \operatorname{atan2}(\bar{S}, \bar{C})$$
     giving 00:00 midnight with mathematical precision.
4. **Circular Schedule Variability**:
   - Circular standard deviation:
     $$s = \sqrt{-2\ln R} \times \frac{1440}{2\pi}$$
     where $R = \sqrt{\bar{C}^2 + \bar{S}^2}$.
   - Measures day-to-day bedtime and wake-time dispersion (high consistency: $s < 30$ min; variable: $s \ge 60$ min).

### Date Range Filtering & Baseline Comparisons
- **Filtering Options**: Presets for Last 7 Days, Last 30 Days, Last 90 Days, and Custom ranges.
- **Period Comparison Engine**:
  - Compares current period against an immediately preceding baseline period of identical duration.
  - Computes absolute differences and percentage changes.
  - **Scientific Qualification**: Requires at least 3 valid nights in both periods and an equivalence ratio $\ge 0.5$. Unqualified comparisons are explicitly flagged as preliminary rather than presenting false statistical confidence.

### Observational Honesty & Provenance
- **Tracking Quality Scoring**:
  - `EXCELLENT`: $\ge 80\%$ coverage
  - `ADEQUATE`: $50\% - 79\%$ coverage
  - `SPARSE`: $25\% - 49\%$ coverage
  - `INSUFFICIENT`: $< 25\%$ coverage or $< 2$ nights
- **Missing Data Reporting**: Exact missing night counts and dates are surfaced without judgmental phrasing.
- **Conflicted Overlaps**: Days with multiple overlapping sources are highlighted with full source breakdown (Manual, Phone Estimate, Wearable Import).
- **Physiological Biomarker Placeholders**: Heart rate, HRV, SpO₂, and respiratory rate are charted only when actual records exist from wearable hardware, with explicit gap states.

### Deterministic Rules-Based Insight Engine
- **100% On-Device**: Generated entirely in Kotlin without remote cloud services, telemetry, or external AI models.
- **Traceable Observations**:
  - Sleep duration insights (shorter vs longer than baseline, short sleep warnings).
  - Bedtime consistency insights (regular vs shifting schedules).
  - Sleep Onset Latency (prolonged latency $> 30$ min).
  - Subjective vs objective comparison incorporating monthly PSQI global scores.
- **Evidence Rating**: Every insight displays an evidence level (`ROBUST`, `MODERATE`, `PRELIMINARY`).
- **Non-Diagnostic Language**: Adheres strictly to scientific limitations without claiming medical diagnostic capability.

### Interactive Compose Analytics Dashboard
- **Overview Metrics Grid**: Mean actual sleep, bedtime/wake-time circular averages, duration variability, and average SOL.
- **Custom Canvas Duration Trend Chart**: Plots daily durations against an average benchmark dashed reference line, distinguishing recorded nights from missing night baseline indicators.
- **Custom Canvas Schedule Consistency Chart**: Visualizes day-to-day bedtime and wake-time intervals crossing midnight with connecting lines and color-coded nodes.
- **Selected Day Inspector Card**: Interactive drill-down card displaying actual sleep, time in bed, bedtime, wake time, latency, sources, and session count for any selected night.

---

## 6. Functional Capabilities (Milestones 1–3)

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

### Settings & Data Sovereignty
- **Diagnostics**: Real-time record counts for Sessions, SOL records, and PSQI assessments, alongside Room database schema version.
- **Appearance**: Seamless switching between Dark (Default), System, and Light themes via DataStore.
- **Permanent Data Deletion**: Two-step confirmation dialog allowing complete wipe of all local sleep sessions, SOL records, and PSQI assessments.

---

## 7. Verification & Test Suite

The test suite contains **144 automated tests** across domain logic, scoring engines, database DAOs, migrations, analytics calculators, step tracking reconciliation, and ViewModels. All pass with 100% success rate:

```powershell
.\gradlew.bat test
```

### Complete Test Coverage
1. `CircularTimeCalculatorTest`: Midnight crossing (23:30 + 00:30 $\to$ 00:00 midnight), circular dispersion, precision rounding, and consistency ratings.
2. `SleepMetricsCalculatorTest`: Clinical sleep date assignment, overlapping interval deduplication, daytime nap vs overnight separation, and period metrics.
3. `SleepTrendAnalyzerTest`: Period comparison, duration difference, percentage change, schedule shifts, and reliability qualification thresholds.
4. `SleepDataQualityAnalyzerTest`: Coverage ratio calculation, quality tier classification (EXCELLENT, ADEQUATE, SPARSE, INSUFFICIENT), missing dates, and conflicted nights.
5. `SleepInsightEngineTest`: Deterministic insight generation, baseline building suppression, duration warnings, consistency insights, SOL insights, and PSQI integration.
6. `SleepAnalyticsRepositoryTest`: Reactive Flow aggregation across Room sessions, SOL records, and PSQI assessments on `Dispatchers.IO`.
7. `AnalyticsViewModelTest`: UI StateFlow emissions, date range preset switching (7d, 30d, 90d), custom date ranges, and interactive day selection.
8. `DurationCalculatorTest`: Instant arithmetic, DST transitions, midnight crossings, duration formatting.
9. `SessionValidatorTest`: Start/end order, negative duration rejection, 24-hour limit, future instant rejection.
10. `SolCalculatorTest`: Latency minutes calculation, midnight crossing, onset before attempt rejection, quality flag evaluation, mean/median/range summaries.
11. `PsqiScoringEngineTest`: All 7 components, exact cutoffs, efficiency percentages, synthetic fixtures (0, 8, 21), incomplete item rejection.
12. `SleepSessionDaoTest`, `SolDaoTest`, `PsqiDaoTest`: Room persistence, queries, updates, deletes, and foreign key cascades.
13. `SomnusDatabaseMigrationTest`: Migration verification from v1 to v2 and v2 to v3 (step tracking tables and checkpoints).
14. `SessionManagerTest`: Active tracking lifecycle, process restart survival.
15. `HomeViewModelTest`, `HistoryViewModelTest`, `SessionDetailViewModelTest`, `SettingsViewModelTest`, `InsightsViewModelTest`: Complete StateFlow emissions and user mutation flows.
16. `SleepSessionRepositoryValidationTest`: SQLite foreign key constraint enforcement (`PRAGMA foreign_keys = ON;`), atomic multi-table wipe (`clearAllDataTables()`), complete overlap topology checks, and stale session recovery.
17. `HealthConnectManagerTest`: Capability detection (`AVAILABLE`, `UPDATE_REQUIRED`, `NOT_INSTALLED`, `NOT_SUPPORTED`), permission denial handling, wearable metadata attribution, interval deduplication, and non-destructive conflict prevention.
18. `MovementWindowAggregatorTest`: Streaming Welford algorithm, constant-space $O(1)$ memory, stillness, 30-epoch nightstand stationary, minor restlessness, active movement, and unmonitored interruption categories.
19. `SleepCalculationEdgeCasesTest`: DST spring forward (7 physical hours), DST fall backward (9 physical hours), circular mean midnight crossing, zero duration rejection, negative duration rejection, and >36h duration rejection.
20. `StepReconciliationEngineTest`: Multi-source step reconciliation, overlapping interval deduplication without double counting, wearable prioritization, disjoint interval summation, 24-hour hourly distribution, and date range calculation.
21. `PhoneStepTrackerTest`: Hardware step sensor cumulative baseline, reboot detection, counter reset handling, non-negative delta guarantee, and midnight date rollover.
22. `HealthConnectStepImportTest`: Health Connect step sync, permission denial handling, device model / package attribution, and deduplication by external record ID.
23. `ActivityViewModelTest`: UI StateFlow emissions, goal progress, custom step goal editing, and Health Connect sync orchestration.

---

## 8. Milestone 5 Engineering & Validation Documentation

- [Physical Device Validation Protocol](docs/VALIDATION_PROTOCOL.md) — Standardized testing protocol covering process death survival, stale session recovery, overlapping collision rejection, Health Connect sync, phone sensor tracking, and battery drain profiling.
- [Validation & Engineering Report](docs/VALIDATION_REPORT.md) — Comprehensive technical report covering codebase audit, prioritized defects, three-tier validation framework, and scientific honesty guarantees.

---

## 9. Building the Application

Build the debug APK:
```powershell
.\gradlew.bat assembleDebug
```
Output artifact: `app/build/outputs/apk/debug/app-debug.apk`
