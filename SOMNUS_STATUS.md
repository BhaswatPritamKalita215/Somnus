# Somnus Project Status — Milestone 10 Hardening

**Branch**: `milestone-10-hardening`  
**Date**: October 18, 2026  
**Build Status**: ✅ `BUILD SUCCESSFUL` (50 actionable tasks, 0 failures)  
**Test Status**: ✅ 235 unit tests passed, 0 failures, 0 errors, 0 skipped across 43 test suites

---

## 1. Executive Summary

Milestone 10 represents the security, privacy, and integrity hardening milestone for Somnus. All modifications were implemented directly on branch `milestone-10-hardening` across isolated, atomic commits without merging to `master`.

---

## 2. Milestone 10 Commits on `milestone-10-hardening`

| Commit | Type | Description |
|---|---|---|
| `4995c20` | `feat(assistant)` | Remove `delay(20)` cadence from `DeterministicLocalAssistantModelProvider`, emit full text immediately, and rename UI labels from neural inference connotations to **"Local rules assistant"**. |
| `7c86347` | `feat(psqi)` | Guard `SyntheticPsqiFixtures` and `SyntheticRunnerCard` behind `BuildConfig.DEBUG` (`buildConfig = true` in `build.gradle.kts`). Release builds completely exclude synthetic runner. |
| `0bc35be` | `fix(manifest)` | Remove unused Health Connect `READ_NUTRITION` and `WRITE_NUTRITION` permissions from `AndroidManifest.xml`. |
| `871021f` | `fix(security)` | Disable `android:allowBackup` (`false`) and configure explicit exclusions for Room DB (`somnus_sleep.db`) and DataStore (`somnus_user_preferences.preferences_pb`) in `data_extraction_rules.xml` and `backup_rules.xml`. |

---

## 3. Verified Repository Inquiries & Security Audit

### 3.1 Real PSQI in Release UI
* **Does the release UI let a user answer a real PSQI?**  
  **NO.**  
  * **Evidence**: The 19 questions of the Pittsburgh Sleep Quality Index (PSQI) are copyrighted by the University of Pittsburgh (Buysse et al., 1989). Somnus intentionally does not ship the proprietary question text. Previously, the only interactive PSQI entry points were the `SyntheticRunnerCard` (synthetic test fixtures) and a rudimentary 6-field `CustomPsqiDialog` (with questions 5b–5j, 7, and 8 hardcoded to 0). With `SyntheticRunnerCard` now guarded behind `BuildConfig.DEBUG`, release builds present only the `InstrumentNoticeCard`, previous assessment history (`LatestPsqiCard`, `PsqiHistoryList`), and zero clinical questionnaire entry points.

### 3.2 Backup & Data Extraction (`android:allowBackup`)
* **Original Value**: `android:allowBackup="true"`.
* **Hardened Value**: `android:allowBackup="false"`.
* **Exclusions**: Replaced placeholder TODO in `data_extraction_rules.xml` and `backup_rules.xml` with explicit `<exclude>` entries for:
  - SQLite Database: `somnus_sleep.db` and database directory (`domain="database"`).
  - Jetpack DataStore: `somnus_user_preferences.preferences_pb` and `datastore` folder (`domain="file"`).
  - Shared Preferences: root directory (`domain="sharedpref"`).

### 3.3 Open Food Facts Client & Nutrition Verification
* **Does `OpenFoodFactsClient` set a User-Agent?**  
  **YES.** In `OpenFoodFactsClient.kt`:
  ```kotlin
  private const val USER_AGENT = "Somnus - Android - Version 1.4.0 (contact: local-first-sleep-tracker)"
  ...
  setRequestProperty("User-Agent", USER_AGENT)
  ```
* **Does the UI show Open Food Facts / ODbL attribution?**  
  **PARTIALLY.**  
  - The UI displays an `"Open Food Facts"` chip in `NutritionComponents.kt` (`ProductSourceChip`) when displaying scanned product details.
  - **ODbL (Open Database License) attribution is NOT shown anywhere in the UI.**
* **Is online lookup optional in Settings?**  
  **NO.** Scanned barcodes missing from local Room cache automatically initiate a network query via `OpenFoodFactsClient`. There is no toggle in `UserPreferencesRepository` or `SettingsScreen` to disable online lookup.
* **Does the food log store a nutrition snapshot?**  
  **YES.** `FoodLogEntryEntity` captures calculated macro- and micronutrients at the moment of logging (`calculatedCalories`, `calculatedProtein`, `calculatedCarbs`, `calculatedFat`, `calculatedSaturatedFat`, `calculatedTransFat`, `calculatedFiber`, `calculatedSugar`, `calculatedAddedSugars`, `calculatedSodiumMg`, `calculatedSaltG`). Later edits to the cached product definition do not alter historical logs.
* **Is repeat-scan debounced?**  
  **YES.** `BarcodeAnalyzer.kt` enforces a 2000ms debounce threshold (`debouncePeriodMs = 2000L`):
  ```kotlin
  if (rawValue == lastScannedCode && (currentTime - lastScannedTimestamp) < debouncePeriodMs) {
      imageProxy.close()
      return
  }
  ```
* **Is there a way to delete assistant history?**  
  **YES.**
  - In `AssistantScreen.kt`, the top App Bar contains a `DeleteSweep` action button that invokes `viewModel.clearConversation()`, which deletes all rows from `assistant_messages` via `AssistantRepository.clearConversation()`.
  - In `SettingsScreen.kt`, the "Delete All Sleep Data" action calls `SomnusDatabase.clearAllDataTables()`, which deletes all records across all tables, including `assistant_messages`.

---

## 4. Build & Unit Test Verification

### 4.1 Command Execution
```bash
.\gradlew.bat clean testDebugUnitTest assembleDebug --rerun-tasks
```
**Outcome**:
- **Exit Code**: `0` (Success)
- **Execution Time**: 6 minutes 43 seconds
- **Actionable Tasks**: 50 executed

### 4.2 Unit Test Totals (Parsed from `app/build/test-results/testDebugUnitTest/*.xml`)
- **Total Test Suites**: 43
- **Total Tests Run**: 235
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0

### 4.3 Test Suite Breakdown

| Test Suite | Tests | Failures | Errors | Skipped | Time (s) |
|---|---|---|---|---|---|
| `HealthConnectManagerTest` | 8 | 0 | 0 | 0 | 14.105 |
| `HealthConnectStepImportTest` | 5 | 0 | 0 | 0 | 0.510 |
| `PsqiDaoTest` | 3 | 0 | 0 | 0 | 0.491 |
| `SleepSessionDaoTest` | 8 | 0 | 0 | 0 | 0.712 |
| `SolDaoTest` | 4 | 0 | 0 | 0 | 0.455 |
| `SomnusDatabaseMigrationTest` | 5 | 0 | 0 | 0 | 0.462 |
| `OpenFoodFactsClientTest` | 5 | 0 | 0 | 0 | 0.331 |
| `AssistantRepositoryTest` | 4 | 0 | 0 | 0 | 0.641 |
| `DashboardRepositoryTest` | 5 | 0 | 0 | 0 | 0.769 |
| `FoodRepositoriesTest` | 3 | 0 | 0 | 0 | 0.261 |
| `SleepAnalyticsRepositoryTest` | 2 | 0 | 0 | 0 | 0.184 |
| `SleepSessionRepositoryValidationTest` | 5 | 0 | 0 | 0 | 0.460 |
| `MovementWindowAggregatorTest` | 5 | 0 | 0 | 0 | 0.004 |
| `PhoneStepTrackerTest` | 5 | 0 | 0 | 0 | 0.007 |
| `BarcodeValidatorTest` | 5 | 0 | 0 | 0 | 0.005 |
| `DurationCalculatorTest` | 8 | 0 | 0 | 0 | 0.026 |
| `IndianPackagedFoodTest` | 7 | 0 | 0 | 0 | 0.014 |
| `NutritionCalculatorTest` | 9 | 0 | 0 | 0 | 0.009 |
| `PsqiScoringEngineTest` | 11 | 0 | 0 | 0 | 0.013 |
| `SessionValidatorTest` | 8 | 0 | 0 | 0 | 0.007 |
| `SleepCalculationEdgeCasesTest` | 6 | 0 | 0 | 0 | 0.004 |
| `SolCalculatorTest` | 8 | 0 | 0 | 0 | 0.014 |
| `StepReconciliationEngineTest` | 6 | 0 | 0 | 0 | 0.011 |
| `CircularTimeCalculatorTest` | 6 | 0 | 0 | 0 | 0.001 |
| `SleepDataQualityAnalyzerTest` | 2 | 0 | 0 | 0 | 0.007 |
| `SleepInsightEngineTest` | 5 | 0 | 0 | 0 | 0.009 |
| `SleepMetricsCalculatorTest` | 5 | 0 | 0 | 0 | 0.006 |
| `SleepTrendAnalyzerTest` | 3 | 0 | 0 | 0 | 0.004 |
| `AssistantCacheTest` | 5 | 0 | 0 | 0 | 0.015 |
| `AssistantSafetyPolicyTest` | 7 | 0 | 0 | 0 | 0.019 |
| `ContextBuilderTest` | 3 | 0 | 0 | 0 | 0.367 |
| `IntentRouterTest` | 13 | 0 | 0 | 0 | 0.023 |
| `CrossMetricInsightEngineTest` | 9 | 0 | 0 | 0 | 0.030 |
| `SessionManagerTest` | 5 | 0 | 0 | 0 | 0.400 |
| `ActivityViewModelTest` | 4 | 0 | 0 | 0 | 0.643 |
| `AnalyticsViewModelTest` | 4 | 0 | 0 | 0 | 0.412 |
| `AssistantViewModelTest` | 5 | 0 | 0 | 0 | 0.301 |
| `SessionDetailViewModelTest` | 3 | 0 | 0 | 0 | 0.289 |
| `HistoryViewModelTest` | 2 | 0 | 0 | 0 | 0.160 |
| `HomeViewModelTest` | 5 | 0 | 0 | 0 | 0.431 |
| `InsightsViewModelTest` | 6 | 0 | 0 | 0 | 0.515 |
| `NutritionViewModelTest` | 6 | 0 | 0 | 0 | 0.620 |
| `SettingsViewModelTest` | 2 | 0 | 0 | 0 | 0.206 |
| **Total** | **235** | **0** | **0** | **0** | **~24.1s** |
