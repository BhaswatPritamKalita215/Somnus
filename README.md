# Somnus — Milestone 1: Native Android Foundation

**Somnus** is a local-first, privacy-focused native Android sleep-tracking application built with Kotlin and Jetpack Compose. Somnus prioritizes detailed sleep records, measurable accuracy, transparent uncertainty, battery efficiency, and zero telemetry.

---

## 1. Product Philosophy & Scientific Honesty

- **Local-Only Architecture**: All data is stored strictly in a local SQLite database using Android Room. Somnus contains **no cloud backend, no remote database, no user accounts, no telemetry, no analytics SDKs, and no advertising services**. Your sleep data never leaves your physical device.
- **Scientific Honesty**: Milestone 1 implements manual tracking of bedtime and wake time. This is explicitly labeled in all interfaces and domain models as **Time in Bed (TIB)**. Somnus does **not** invent heart-rate, HRV, respiratory rate, or sleep-stage values (light, deep, REM). Domain interfaces distinguish manual records from future sensor estimates and wearable imports without treating them as scientifically equivalent.
- **Zero Sensor Battery Drain**: Milestone 1 requires no background services, microphone access, camera access, or continuous sensor polling. It persists timestamps rather than keeping a CPU wake-lock or running background timers.
- **Unambiguous Instant Time Arithmetic**: All timestamps are stored as UTC epoch milliseconds (`java.time.Instant`). Timezone information (`java.time.ZoneId`) is preserved separately to ensure local wall-clock representation accurately reflects your location, including across Daylight Saving Time (DST) transitions and midnight crossings.

---

## 2. Technical Stack

- **Language**: Kotlin 2.3+
- **UI Toolkit**: Jetpack Compose with Material 3
- **Design Identity**: Dark-first, restrained midnight-blue theme (`#080C16` obsidian background, `#82AAFF` astral blue primary, `#56CFE1` moonlight teal secondary) with full support for light mode
- **Architecture**: MVVM with repository interfaces and constructor injection (`AppContainer`)
- **Persistence**: Android Room (SQLite) with explicit migration management
- **Preferences**: Jetpack DataStore Preferences
- **Asynchronous Concurrency**: Kotlin Coroutines and reactive `StateFlow`
- **Testing**: JUnit 4, Kotlin Coroutines Test, Robolectric, AndroidX Arch Core Testing

---

## 3. Minimum Android SDK Rationale

- **`minSdk = 26` (Android 8.0 Oreo)**:
  - **Native `java.time`**: Complete native runtime support for `java.time.Instant`, `java.time.Duration`, `java.time.ZoneId`, and `java.time.ZonedDateTime` without desugaring performance or memory overhead.
  - **Modern Security & Cryptography**: Native modern platform keystore, SQLite capabilities, and notification channels.
  - **Market Reach**: Covers over 95% of active Android devices worldwide while providing high-precision timestamp arithmetic required for sleep science.
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
│   │   └── SourceSpecificRecords.kt # Typed models: Manual, PhoneEstimate, Wearable
│   └── analysis/
│       ├── DurationCalculator.kt    # Instant-based duration math, formatting, DST safety
│       └── SessionValidator.kt      # Strict validation rules for session intervals
├── data/
│   ├── local/
│   │   ├── SleepSessionEntity.kt    # Room entity with epoch-millisecond UTC instants
│   │   ├── SleepSessionDao.kt       # Transactional DAO enforcing single-active session
│   │   └── SomnusDatabase.kt        # Room database configuration & migration strategy
│   ├── repository/
│   │   ├── SleepSessionRepository.kt# Repository interface
│   │   └── SleepSessionRepositoryImpl.kt # Thread-safe repository implementation
│   └── preferences/
│       └── UserPreferencesRepository.kt # DataStore preferences (theme, policy ack)
├── session/
│   └── SessionManager.kt            # Lifecycle management, process survival, ticker
├── theme/
│   ├── Color.kt                     # Midnight-blue dark and ice-slate light palettes
│   ├── Theme.kt                     # Material 3 theme composable
│   └── Type.kt                      # Clean typography styles
└── ui/
    ├── SomnusApp.kt                 # Root navigation, bottom navigation bar, back handler
    ├── components/
    │   └── CommonComponents.kt      # TopBar, ScientificHonestyBadge, ConfirmDialog
    ├── home/
    │   ├── HomeScreen.kt            # Idle & Active tracking states, live ticker, recent nights
    │   └── HomeViewModel.kt         # Reactive home screen ViewModel
    ├── history/
    │   ├── HistoryScreen.kt         # Chronological completed sessions & aggregate stats
    │   └── HistoryViewModel.kt      # History screen ViewModel
    ├── detail/
    │   ├── SessionDetailScreen.kt   # Timestamp breakdown, time correction dialog, delete
    │   └── SessionDetailViewModel.kt# Detail ViewModel with validation & mutations
    └── settings/
        ├── SettingsScreen.kt        # Local-only policy, diagnostics, theme switch, delete all
        └── SettingsViewModel.kt     # Settings ViewModel
```

---

## 5. Functional Capabilities in Milestone 1

### Home & Active Session
- **Somnus Branding & Tracking Card**: Shows current status (Ready to Track vs. Session in Progress).
- **Start Session**: Single tap starts tracking time in bed. Persists the start instant to Room.
- **Active Session Ticker**: Recomputes elapsed duration from the stored start instant every second without keeping wake locks or draining battery.
- **Process Survival**: If the user closes the app, swiping it away, or the system terminates the process, reopening Somnus immediately detects the ongoing session from Room.
- **End Session with Confirmation**: Prompts confirmation, shows calculated duration in bed, allows adding optional user notes, and commits the session atomically.
- **Discard Session**: Option to cancel/discard an accidental session without logging it to history.

### History & Aggregates
- **Chronological List**: Displays completed sessions sorted newest first.
- **Informative Badging**: Displays the date, time interval, calculated duration, and "Time in bed (Manual)" badge. Corrected entries clearly display "Manually corrected".
- **Summary Statistics**: Computes total sessions, total time logged, and average time in bed.

### Session Details & Timestamps Correction
- **Full Metadata View**: Displays start instant, end instant, local timezone, duration, record source, creation instant, modification instant, user notes, and unique session ID (UUID).
- **Timestamp Correction**: Allows users to adjust recorded bedtime and wake time with live validation. Prevents invalid intervals (end before start, zero-length sessions, future timestamps). Marks the session as `isCorrected = true` and updates `updatedAt`.
- **Session Deletion**: User-accessible way to delete individual sessions with a confirmation dialog.

### Settings & Diagnostics
- **Local-Only Policy**: Explains the device-only storage architecture and zero-telemetry policy.
- **Diagnostics**: Displays application version, database engine (SQLite/Room), database schema version (v1), and total stored session count.
- **Theme Selection**: Switch between Dark (default midnight-blue), System, and Light mode, persisted via Jetpack DataStore.
- **Permanent Data Deletion**: "Delete All Sleep Sessions" with a strict confirmation dialog to permanently wipe local records.

---

## 6. Build and Test Instructions

### Prerequisites
- JDK 17 (e.g. Microsoft OpenJDK 17 or Eclipse Temurin 17)
- Android SDK Platform 34 (Android 14) and Build-Tools

### Running Automated Tests
Run all unit and persistence tests via the Gradle wrapper:

```bash
# Windows
.\gradlew.bat test

# Linux / macOS
./gradlew test
```

### Building the Debug APK
Build the installable debug APK:

```bash
# Windows
.\gradlew.bat assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 7. Automated Test Verification Summary

The project includes **39 automated tests** covering calculations, validation, persistence, and state management:

| Test Suite | Tests | Status | Scope |
| :--- | :---: | :---: | :--- |
| `DurationCalculatorTest` | 8 | Pass | Duration math, midnight crossings, DST fall-back transitions, formatting, negative clamp |
| `SessionValidatorTest` | 8 | Pass | Validation rules, future timestamps, zero duration, reversed timestamps, realistic limits |
| `SleepSessionDaoTest` | 8 | Pass | Room persistence, single-active session invariant, atomic transactions, query ordering |
| `SessionManagerTest` | 5 | Pass | Lifecycle coordination, process survival simulation, duplicate prevention, discard |
| `HomeViewModelTest` | 3 | Pass | Start/end/discard flows, active session state propagation |
| `SessionDetailViewModelTest` | 3 | Pass | Timestamp correction, validation rejection, individual session deletion |
| `HistoryViewModelTest` | 2 | Pass | Chronological sorting, total/average time calculation, empty state |
| `SettingsViewModelTest` | 2 | Pass | Full database wipe, DataStore theme mode persistence |

---

## 8. Known Limitations of Milestone 1

1. **Manual Tracking Only**: Only manual start and stop times (time in bed) are recorded.
2. **No Sensor Polling**: Phone sensor-based motion, acoustic, or light tracking is intentionally excluded to preserve battery and establish a clean baseline.
3. **No Sleep Architecture**: Does not predict sleep stages (light, deep, REM). Manually recorded time in bed is not conflated with physiological sleep duration.
4. **No External Imports**: Wearable hardware data import is not yet active (domain models are established for future compatibility).

---

## 9. Next Milestone (Milestone 2 Roadmap)

- **Phone-Based Sensor Tracking**:
  - Implement low-power stationary state detection using Android Activity Recognition / device stillness.
  - Implement screen-state and ambient light indicators with transparent uncertainty bounds (e.g., ±15 minute confidence intervals).
- **Battery Profiling**:
  - Profile power consumption using Android Energy Profiler to guarantee negligible overnight drain (< 2% battery drop).
- **Export Capabilities**:
  - Allow users to export their local database as JSON / CSV files.
