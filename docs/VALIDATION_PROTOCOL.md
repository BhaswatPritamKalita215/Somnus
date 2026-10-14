# Somnus — Physical Device Validation Protocol

**Document Version:** 1.0 (Milestone 5)  
**Target Platform:** Android 8.0 (API 26) through Android 15+ (API 35+)  
**App Version:** 1.2.0 (Validation & Reliability)

---

## 1. Objectives & Scope

This protocol establishes a standardized procedure for evaluating the **Somnus** sleep-tracking application on physical Android devices. It validates that the app behaves reliably under real-world operating conditions: process termination, device reboots, battery exhaustion, background throttling, sensor limitations, and external health data synchronization.

---

## 2. Test Environments & Device Matrix

To ensure realistic cross-version and hardware coverage, physical validation should be performed across at least two device profiles:

| Profile | OS Version | Hardware Characteristics | Validation Focus |
|---|---|---|---|
| **Modern Flagship** | Android 14 / 15 (API 34+) | System Health Connect, high-rate IMU, aggressive OEM power management (e.g. Pixel, Galaxy) | System Health Connect integration, granular power saver modes, background restrictions |
| **Legacy / Budget** | Android 10–13 (API 29–33) | APK-based Health Connect, low-power accelerometer, limited RAM (3–4 GB) | Low memory killer (LMK), Play Store Health Connect APK, process death survival |

---

## 3. Physical Test Protocols

### Protocol A: Manual Sleep Session Lifecycle & Process Death Survival

**Objective:** Verify that manual tracking persists reliably across app termination and device reboots without relying on CPU wake-locks or memory buffers.

1. **Start Tracking:**
   - Tap "Start Sleep Session" at bedtime (e.g., 23:00).
   - Verify that the card updates immediately to **SESSION IN PROGRESS** and displays an active elapsed timer.
2. **Process Death Simulation:**
   - Force-close the app from the Recent Apps switcher or execute:
     ```bash
     adb shell am force-stop com.example.somnus
     ```
   - Relaunch the application.
   - **Verification:** The active session must resume immediately with the correct elapsed duration calculated dynamically from the persistent start instant in SQLite.
3. **Device Reboot Simulation:**
   - Restart the physical device during an active session.
   - Launch Somnus after boot.
   - **Verification:** The session remains active with no data corruption or state loss.
4. **Completion:**
   - Tap "End Session", enter optional notes (e.g. "Slept well, quiet room"), and tap "Save & Complete".
   - **Verification:** Session appears in "Recent Nights" and History tab with exact duration and timestamp metadata.

---

### Protocol B: Stale Active Session Recovery (>36 Hours)

**Objective:** Validate that sessions accidentally left running beyond the realistic clinical duration (36 hours) do not trap the user in an uncompletable state.

1. **Simulation:**
   - On an active session, use SQLite or time travel to simulate >36 hours elapsed:
     ```bash
     adb shell date -s "20261016.120000"
     ```
   - Or start a session, verify elapsed > 36 hours.
2. **UI Verification:**
   - The card status must switch from "SESSION IN PROGRESS" to **"STALE SESSION (>36h)"** with an error banner explaining that the session was left active.
   - The primary button must change to **"Recover Session"**.
3. **Recovery Execution:**
   - Tap "Recover Session". A modal dialog appears requesting the user's estimated sleep duration (e.g. 7.5 hours).
   - Enter estimated hours and correction notes. Tap "Save & Complete".
   - **Verification:**
     - The session is saved to History with status `COMPLETED` and `isCorrected = true`.
     - Notes clearly prepend `[Recovered stale session]`.
     - The home screen immediately returns to the idle state.

---

### Protocol C: Overlapping Collision Rejection & Data Integrity

**Objective:** Prevent logically impossible overlapping sleep intervals from being recorded or edited.

1. **Collision on Completion:**
   - With an existing completed session from 22:00 to 06:00:
   - Attempt to start and end a second session whose proposed window falls within 23:00 to 05:00.
   - **Verification:** Somnus rejects the collision with an explanatory snackbar: *"Completed session overlaps with an existing session"*.
2. **Collision on Manual Correction:**
   - Navigate to Session Detail for an existing record.
   - Tap "Edit Timestamps" and adjust start/end times to intersect with an adjacent completed session.
   - Tap Save.
   - **Verification:** Edit is rejected and the original record remains uncorrupted.

---

### Protocol D: Health Connect Capability, Permissions & Sync

**Objective:** Verify Health Connect detection, permission workflows, duplicate prevention, and non-destructive conflict handling.

1. **Capability State Detection:**
   - On a device without Health Connect: App must report `NOT_INSTALLED` or `NOT_SUPPORTED`.
   - On a device with Health Connect installed: App reports `AVAILABLE`.
2. **Permission Handling:**
   - Trigger Health Connect synchronization.
   - If permission is denied in the system dialog, Somnus catches the security exception gracefully and displays a message rather than crashing.
   - If permission is granted, sync proceeds.
3. **Deduplication Test:**
   - Sync records from Health Connect. Verify that records with identical start and end timestamps already recorded in Somnus are counted as duplicates and not re-inserted.
4. **Non-Destructive Conflict Guarantee:**
   - When an imported wearable record overlaps with a self-reported manual session:
   - **Verification:** Somnus flags a conflict and **NEVER** overwrites or deletes the self-reported manual record.

---

### Protocol E: Phone Sensor Tracking, Movement Epochs & Interruptions

**Objective:** Validate that accelerometer data is aggregated into constant-space statistical epochs without CPU or battery exhaustion.

1. **Stationary on Nightstand Test:**
   - Place phone on a flat bedside table during tracking for 35 minutes.
   - **Verification:** Aggregator records standard deviation $\sigma < 0.05 \text{ m/s}^2$ and transitions epoch categorization to `OFF_PERSON_STATIONARY`.
2. **Bed Movement / Restlessness Test:**
   - Place phone on the mattress; turn over or shift position periodically.
   - **Verification:** Accelerometer registers mild vibrations ($0.05 \le \sigma < 0.35 \text{ m/s}^2$), categorizing epochs as `MINOR_RESTLESSNESS`.
3. **Handling / Active Motion Test:**
   - Pick up phone, walk or interact with screen.
   - **Verification:** Epochs register peak $> 12.0 \text{ m/s}^2$, categorized as `ACTIVE_MOVEMENT`.
4. **Interruption Auditing:**
   - Enable Battery Saver mode in Android Settings during tracking.
   - **Verification:** Somnus records a `TrackingInterruption` event with reason `BATTERY_SAVER_ENABLED` and tracks interruption duration.

---

### Protocol F: Battery Drain Profiling & Memory Footprint

**Objective:** Ensure background tracking consumes less than 1.5% battery per 8-hour tracking window.

1. **Battery Measurement Procedure:**
   - Charge device to 100%. Disconnect charger.
   - Reset battery stats via ADB:
     ```bash
     adb shell dumpsys batterystats --reset
     ```
   - Start Somnus sleep session and lock device screen for 8 hours overnight.
   - In the morning, unlock device, end session, and run:
     ```bash
     adb shell dumpsys batterystats com.example.somnus
     ```
2. **Acceptance Criteria:**
   - Battery consumption attributed to Somnus over 8 hours must be **< 1.5%** of total battery capacity.
   - Memory footprint (`adb shell dumpsys meminfo com.example.somnus`) must remain stable without growth (heap allocation $< 45 \text{ MB}$).

---

### Protocol G: Database Atomic Wipe & Foreign Key Enforcement

**Objective:** Verify that multi-table deletions are atomic and SQLite foreign keys are strictly enforced.

1. **Atomic Wipe:**
   - With multiple sessions, SOL records, and PSQI assessments stored:
   - Navigate to Settings -> "Delete All Stored Data". Confirm deletion.
   - **Verification:** All 3 tables are cleared atomically inside a single database transaction. No orphan child records or partial deletion states occur.
2. **Foreign Key Integrity:**
   - Verify via tests that child `sol_records` referencing `sleep_sessions(id)` enforce SQLite referential integrity (`PRAGMA foreign_keys = ON;`).
