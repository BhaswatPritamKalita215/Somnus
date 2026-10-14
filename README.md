# Somnus — Native Android Sleep Tracker (Milestones 1–8)

**Somnus** is a local-first, privacy-focused native Android sleep, activity, and nutrition-tracking application built with Kotlin and Jetpack Compose. Somnus prioritizes detailed records, measurable accuracy, transparent uncertainty, battery efficiency, and zero telemetry.

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
- **Persistence**: Android Room (SQLite) with explicit migration management (`SomnusDatabase` version 4 with `MIGRATION_3_4`) and enforced foreign keys (`PRAGMA foreign_keys = ON;`)
- **Barcode & Camera**: CameraX 1.4.1 and Google ML Kit Barcode Scanning 17.3.0 for on-device food barcode scanning
- **Remote Product Catalog**: Open Food Facts API v2 (ODbL) with pure JSON parsing and offline caching
- **Preferences**: Jetpack DataStore Preferences
- **Asynchronous Concurrency**: Kotlin Coroutines and reactive `StateFlow`
- **Testing**: JUnit 4, Kotlin Coroutines Test, Robolectric, AndroidX Arch Core Testing (150+ passing tests)

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

### Barcode Scanning, Food Database & Calorie Tracking (Milestone 7)
- **On-Device Barcode Scanner**:
  - Leverages CameraX and on-device Google ML Kit Barcode Scanning for fast recognition of EAN-13, EAN-8, UPC-A, UPC-E, and QR codes.
  - Zero camera frame uploading, streaming, or persistence. Camera frames are processed strictly in ephemeral memory on the device.
  - Throttled with a 2000ms debouncing window to eliminate repeated lookup storms.
  - Interactive reticle overlay, torch toggle, and graceful camera permission rationale handling.
- **Local-First Product Catalogue (`food_products`)**:
  - Persisted in Room database version 4 with schema indexes on `barcode` and `name`.
  - Distinguishes data sources (`LOCAL_USER`, `OPEN_FOOD_FACTS`, `HEALTH_CONNECT`, `VERIFIED_CATALOGUE`).
  - Transparent attribution to Open Food Facts (ODbL).
  - Offline-first cache: products fetched from Open Food Facts are saved locally for instant offline reuse.
- **Scientific Honesty in Nutrition Math (`NutritionCalculator`)**:
  - Unit-aware ratio calculations supporting per-100g, per-100ml, and per-serving label bases.
  - Strict preservation of `null` for unmeasured nutrients — unlisted nutrients are **never** fabricated or defaulted to `0.0`.
  - Immutable snapshots in `food_log_entries`: historical food logs never mutate if product catalogue details are updated later.
- **Daily Food Diary & Analytics**:
  - Meal grouping for Breakfast, Lunch, Dinner, and Snacks.
  - Energy intake vs daily calorie target (customizable in settings/dialog).
  - Macronutrient breakdown (Protein, Carbs, Fat) with calorie percentage distribution.
  - 7-Day interactive calorie intake bar chart comparing daily intake against target line.
- **Health Connect Energy Expenditure Integration**:
  - Reads active calories burned and total energy expenditure from Health Connect where available.
  - Strictly segregates dietary intake from physical energy expenditure.

### India-First Food Scanner & FSSAI Compliance
- **Indian Barcode Support**:
  - Prioritizes EAN-13 (GTIN-13) and EAN-8 (for ₹5 and ₹10 small packs / sachets commonly sold in Indian kirana stores), alongside UPC-A/UPC-E for imported products and QR codes.
  - Recognizes GS1 India country code prefix (`890`) while strictly guaranteeing that prefix is never used as proof of manufacturing or used to reject valid international barcodes (e.g. imported chocolates, beverages).
  - Preserves leading zeros faithfully (`BarcodeValidator.normalizeBarcode`) with standard Modulo-10 check digit verification.
- **Indian Packaged-Food Catalogue**:
  - Accommodates core Indian categories: Biscuits & Cookies (Parle-G, Britannia), Namkeen & Savoury Snacks (Haldiram's, Bikaji), Instant Noodles & Ready Meals (Maggi, Yippee), Packaged Dairy (Amul, Mother Dairy), Chips & Savoury Foods (Kurkure, Lay's India), Chocolates & Sweets (Cadbury Dairy Milk India), and Regional Indian Snacks.
  - Full dual-language support: English names and Devanagari Hindi (`nameHi`) names and aliases (e.g. `मैगी`, `पारले-जी`, `हल्दीराम`).
  - Search DAO matches across English names, Hindi Devanagari names, brands, brand aliases, and categories.
- **FSSAI Nutrition-Label Handling**:
  - Fully implements Indian Food Safety and Standards Authority of India (FSSAI) mandatory fields: Energy (kcal), Protein, Carbohydrates, Total Sugars, **Added Sugars**, Total Fat, **Saturated Fat**, **Trans Fat**, and **Sodium vs Salt**.
  - **Strict Chemical Distinction**: Sodium (Na) in mg and Salt (NaCl) in g are preserved as distinct non-interchangeable entities, preventing lossy conversions. Precise stoichiometric conversions ($\text{Salt (g)} \approx \text{Sodium (mg)} \times 0.00254$) are provided transparently.
  - Support for FSSAI License numbers (`fssaiLicenseNumber`).
- **Package & Portion Calculations**:
  - Accurately computes whole-pack and fractional-pack consumption from net package weight/volume (e.g. 1 pack of 79.9g Parle-G on a 454 kcal/100g basis = 362.7 kcal).
  - Never assumes net package weight equals 1 serving.
- **Room Migration 4 $\to$ 5**:
  - `MIGRATION_4_5` seamlessly adds Hindi name, brand aliases JSON, category, net quantity, FSSAI nutrient fields, and FSSAI license columns to `food_products` and `food_log_entries` without data loss.

### Settings & Data Sovereignty
- **Diagnostics**: Real-time record counts for Sessions, SOL records, PSQI assessments, Steps, Food Products, and Log Entries, alongside Room database schema version 5.
- **Appearance**: Seamless switching between Dark (Default), System, and Light themes via DataStore.
- **Daily Targets**: Configurable daily step goal and daily calorie intake target.
- **Permanent Data Deletion**: Two-step confirmation dialog allowing complete atomic wipe of all local records.

---

## 7. Verification & Test Suite

The test suite contains **181 automated tests** across domain logic, scoring engines, database DAOs, migrations, analytics calculators, step tracking reconciliation, Open Food Facts parsing, and ViewModels. All pass with 100% success rate:

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
24. `SomnusDatabaseMigrationTest`: Migration verification from v1 to v2, v2 to v3, v3 to v4, and v4 to v5 (adding Hindi name, brand aliases, category, net quantity, FSSAI fields, and data preservation).
25. `NutritionCalculatorTest`: Portion scaling across per-100g, per-100ml, and per-serving bases; null vs 0.0 preservation; immutable log entry snapshots; and multi-meal daily summary aggregation.
26. `OpenFoodFactsClientTest`: Pure JSON parsing of Open Food Facts v2 responses, serving size unit extraction, unlisted products, and Indian FSSAI / Hindi field parsing.
27. `FoodRepositoriesTest`: Local-first caching, offline fallback, deduplication, and daily log queries.
28. `NutritionViewModelTest`: UI StateFlow emissions, barcode scanner state machine, portion adjustment, manual food entry, and calorie target updates.
29. `BarcodeValidatorTest`: Leading-zero preservation (UPC-A, EAN-8), Modulo-10 GS1 check digit verification, GS1 India 890 prefix detection, and format inference without national discrimination.
30. `IndianPackagedFoodTest`: Real Indian packaged food profiles across categories (Parle-G, Maggi Noodles, Haldiram's Aloo Bhujia, Amul Butter, Kurkure, Cadbury Dairy Milk, ₹5 EAN-8 sachet, imported Swiss Toblerone), package-level vs 100g math, and FSSAI salt vs sodium chemical separation.
31. `CrossMetricInsightEngineTest`: Activity vs sleep duration median split mathematics, circular bedtime shift statistics, energy balance comparisons with honesty caveats, minimum sample size suppression ($N < 3$), and causation refusal guarantees.
32. `DashboardRepositoryTest`: Multi-domain reactive joining, clinical sleep date alignment preventing midnight double-counting, partial day data handling, and 7-day data coverage scoring across completeness tiers.

---

## 8. Milestone 8: Unified Health Dashboard & Cross-Metric Insights

Milestone 8 unifies Somnus's sleep, activity, and nutrition domains into a single health dashboard:

### 1. Unified Daily Dashboard
- **Date Navigation**: Seamless day stepping and calendar date selection. Overnight sleep crossing midnight is mapped to the clinical sleep opportunity date, ensuring consistent alignment with steps and meals.
- **Sleep Section**: Latest overnight sleep record with duration, measurement qualification badge (manual time-in-bed vs. phone sensor estimate vs. wearable import), bedtime/wake times, and integrated SOL and PSQI summaries.
- **Activity Section**: Reconciled daily step totals, progress bar against user goal, source provenance badge, and quick navigation to the Activity screen.
- **Nutrition Section**: Logged dietary energy (kcal), calorie target progress, meal-by-meal breakdown (Breakfast, Lunch, Dinner, Snack), and macronutrient distribution (carbs, protein, fat).
- **Energy Expenditure Section**: Active calories burned (physical movement) vs. total calories burned (active + BMR) from Health Connect with honest missing-data handling (never conflating unrecorded periods with zero calories).

### 2. Cross-Metric Analytical Insights Engine
Pure domain statistical engine evaluating cross-domain relationships:
- **Activity vs. Sleep Duration**: Compares sleep duration on higher-activity days vs. lower-activity days using a median step split.
- **Activity vs. Bedtime Schedule**: Evaluates bedtime consistency and schedule shifts between active and inactive days using circular statistics.
- **Energy Balance**: Compares logged dietary intake against recorded energy expenditure, explicitly noting that unlogged meals underestimate intake and unmeasured BMR is excluded.
- **7-Day Multi-Metric Trends**: Compares rolling 7-day sleep duration and physical activity trajectories against the preceding week.
- **Scientific Honesty Invariants**:
  - **Association $\neq$ Causation**: Insights explicitly describe statistical associations in recorded data and strictly refuse to claim causal links.
  - **Minimum Thresholds**: Requires at least 3 paired days before drawing conclusions; displays transparent collection progress indicators when observations are insufficient.
  - **Zero Fabrication**: Insights only evaluate persisted records from Room and Health Connect.

### 3. Multi-Domain Data Coverage Tracking
- Evaluates data completeness over a 7-day inspection window across 4 domains (Sleep, Steps, Nutrition, Energy).
- Classifies coverage into transparent completeness tiers: **Comprehensive** ($\ge 75\%$), **Moderate** ($\ge 50\%$), **Sparse** ($\ge 25\%$), and **Minimal** ($< 25\%$).

---

## 9. Physical Device Validation Protocol

- [Physical Device Validation Protocol](docs/VALIDATION_PROTOCOL.md) — Standardized testing protocol covering process death survival, stale session recovery, overlapping collision rejection, Health Connect sync, phone sensor tracking, and battery drain profiling.
- [Validation & Engineering Report](docs/VALIDATION_REPORT.md) — Comprehensive technical report covering codebase audit, prioritized defects, three-tier validation framework, and scientific honesty guarantees.

---

## 9. Building the Application

Build the debug APK:
```powershell
.\gradlew.bat assembleDebug
```
Output artifact: `app/build/outputs/apk/debug/app-debug.apk`
