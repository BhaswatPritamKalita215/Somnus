# Somnus Project Status — Milestone 10 Hardening

**Branch**: `milestone-10-hardening`  
**Date**: October 19, 2026  
**Build Status**: ✅ `BUILD SUCCESSFUL` (50 actionable tasks, 0 failures)  
**Test Status**: ✅ 239 unit tests passed, 0 failures, 0 errors, 0 skipped across 43 test suites

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
| `ddb3160` | `docs(status)` | Create initial `SOMNUS_STATUS` documenting initial audit metrics. |
| `4a395ba` | `feat(network)` | Format `OpenFoodFactsClient` User-Agent dynamically from `BuildConfig.VERSION_NAME` and repository contact URL (`SomnusAndroid/<versionName> (<CONTACT>)`). |
| `1d28d9d` | `feat(nutrition)` | Add "Online food lookup" toggle in DataStore (default OFF), one-time consent prompt dialog when barcode is uncached, and unit test verification ensuring `OpenFoodFactsClient` is never called when toggle is OFF. |
| `d4fcc84` | `feat(attribution)` | Add plain text Open Food Facts & ODbL attribution to product detail view and new About section in Settings without auto-opened URLs. |

---

## 3. Verified Inquiries & Hardening Audit

### 3.1 Real PSQI in Release UI
* **Can a user answer a real PSQI in the release UI?**  
  **NO.**  
* **Screen Name**: `InsightsScreen` (under the `PsqiSection` tab).  
* **Details**: The 19 questions of the Pittsburgh Sleep Quality Index (PSQI) are copyrighted by the University of Pittsburgh (Buysse et al., 1989). Somnus intentionally does not ship the proprietary question text. Previously, the only interactive PSQI entry points were the `SyntheticRunnerCard` (synthetic test fixtures) and a rudimentary 6-field `CustomPsqiDialog` (with questions 5b–5j, 7, and 8 hardcoded to 0). With `SyntheticRunnerCard` now guarded behind `BuildConfig.DEBUG`, release builds present only the `InstrumentNoticeCard`, previous assessment history (`LatestPsqiCard`, `PsqiHistoryList`), and zero clinical questionnaire entry points.

### 3.2 Backup & Data Extraction (`android:allowBackup`)
* **Original Value**: `android:allowBackup="true"`.
* **Hardened Value**: `android:allowBackup="false"`.
* **Exclusions**: Explicit exclusions in `data_extraction_rules.xml` and `backup_rules.xml` for:
  - SQLite Database: `somnus_sleep.db` and database directory (`domain="database"`).
  - Jetpack DataStore: `somnus_user_preferences.preferences_pb` and `datastore` folder (`domain="file"`).
  - Shared Preferences: root directory (`domain="sharedpref"`).

### 3.3 Open Food Facts Client & Nutrition Verification
* **User-Agent Header**: Formatted dynamically as `SomnusAndroid/${BuildConfig.VERSION_NAME} (https://github.com/BhaswatPritamKalita215/Somnus.git)`.
* **Open Food Facts / ODbL Attribution**: Plain text attribution present in:
  1. Product detail view (`ProductFoundDialog` in `NutritionComponents.kt`).
  2. Settings screen `AboutCard` in `SettingsScreen.kt`.
  Text: `"Product data from Open Food Facts (openfoodfacts.org), made available under the Open Database License (ODbL)."` (Plain text only, no auto-opened URLs).
* **Online Food Lookup Setting**: Added "Online food lookup" toggle in DataStore (`UserPreferences.onlineFoodLookupEnabled`), default **OFF**.
* **Uncached Barcode Consent Dialog**: When a scanned barcode is missing from local cache and toggle is OFF, displays dialog:
  - Message: `"Look up this product on Open Food Facts? Only the barcode number is sent (your IP address is visible to their server)."`
  - Buttons: `"Allow and enable"` (activates preference and performs lookup) / `"Enter manually"` (opens manual food dialog).
* **Toggle Guard Guarantee**: With the toggle OFF, `OpenFoodFactsClient` is **never called** (verified by unit test in `NutritionViewModelTest`).
* **Food Log Nutrition Snapshot**: Immutable nutrition snapshots stored in `FoodLogEntryEntity`.
* **Repeat Scan Debouncing**: 2000ms debounce window in `BarcodeAnalyzer.kt`.
* **Assistant History Deletion**: "Clear Conversation" in `AssistantScreen` and "Delete All Sleep Data" in `SettingsScreen`.

---

## 4. Build & Unit Test Verification

### 4.1 Command Execution
```bash
.\gradlew.bat clean testDebugUnitTest assembleDebug --rerun-tasks
```
**Outcome**:
- **Exit Code**: `0` (Success)
- **Actionable Tasks**: 50 executed
- **Configuration Cache**: Reused / Validated

### 4.2 Unit Test Totals (Parsed from `app/build/test-results/testDebugUnitTest/*.xml`)
- **Total Test Suites**: 43
- **Total Tests Run**: 239 (+4 new unit tests added in Milestone 10)
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0

### 4.3 Test Suite Breakdown

| Test Suite | Tests | Failures | Errors | Skipped | Time (s) |
|---|---|---|---|---|---|
| `HealthConnectManagerTest` | 8 | 0 | 0 | 0 | 14.593 |
| `HealthConnectStepImportTest` | 5 | 0 | 0 | 0 | 0.426 |
| `PsqiDaoTest` | 3 | 0 | 0 | 0 | 0.554 |
| `SleepSessionDaoTest` | 8 | 0 | 0 | 0 | 0.807 |
| `SolDaoTest` | 4 | 0 | 0 | 0 | 0.334 |
| `SomnusDatabaseMigrationTest` | 5 | 0 | 0 | 0 | 0.501 |
| `OpenFoodFactsClientTest` | 6 | 0 | 0 | 0 | 0.337 |
| `AssistantRepositoryTest` | 4 | 0 | 0 | 0 | 0.608 |
| `DashboardRepositoryTest` | 5 | 0 | 0 | 0 | 0.738 |
| `FoodRepositoriesTest` | 3 | 0 | 0 | 0 | 0.261 |
| `SleepAnalyticsRepositoryTest` | 2 | 0 | 0 | 0 | 0.485 |
| `SleepSessionRepositoryValidationTest` | 5 | 0 | 0 | 0 | 0.404 |
| `MovementWindowAggregatorTest` | 5 | 0 | 0 | 0 | 0.003 |
| `PhoneStepTrackerTest` | 5 | 0 | 0 | 0 | 0.008 |
| `BarcodeValidatorTest` | 5 | 0 | 0 | 0 | 0.003 |
| `DurationCalculatorTest` | 8 | 0 | 0 | 0 | 0.021 |
| `IndianPackagedFoodTest` | 7 | 0 | 0 | 0 | 0.012 |
| `NutritionCalculatorTest` | 9 | 0 | 0 | 0 | 0.007 |
| `PsqiScoringEngineTest` | 11 | 0 | 0 | 0 | 0.013 |
| `SessionValidatorTest` | 8 | 0 | 0 | 0 | 0.006 |
| `SleepCalculationEdgeCasesTest` | 6 | 0 | 0 | 0 | 0.007 |
| `SolCalculatorTest` | 8 | 0 | 0 | 0 | 0.011 |
| `StepReconciliationEngineTest` | 6 | 0 | 0 | 0 | 0.015 |
| `CircularTimeCalculatorTest` | 6 | 0 | 0 | 0 | 0.006 |
| `SleepDataQualityAnalyzerTest` | 2 | 0 | 0 | 0 | 0.005 |
| `SleepInsightEngineTest` | 5 | 0 | 0 | 0 | 0.007 |
| `SleepMetricsCalculatorTest` | 5 | 0 | 0 | 0 | 0.004 |
| `SleepTrendAnalyzerTest` | 3 | 0 | 0 | 0 | 0.004 |
| `AssistantCacheTest` | 5 | 0 | 0 | 0 | 0.019 |
| `AssistantSafetyPolicyTest` | 7 | 0 | 0 | 0 | 0.046 |
| `ContextBuilderTest` | 3 | 0 | 0 | 0 | 1.519 |
| `IntentRouterTest` | 13 | 0 | 0 | 0 | 0.013 |
| `CrossMetricInsightEngineTest` | 9 | 0 | 0 | 0 | 0.076 |
| `SessionManagerTest` | 5 | 0 | 0 | 0 | 0.867 |
| `ActivityViewModelTest` | 4 | 0 | 0 | 0 | 1.757 |
| `AnalyticsViewModelTest` | 4 | 0 | 0 | 0 | 0.992 |
| `AssistantViewModelTest` | 5 | 0 | 0 | 0 | 0.499 |
| `SessionDetailViewModelTest` | 3 | 0 | 0 | 0 | 0.399 |
| `HistoryViewModelTest` | 2 | 0 | 0 | 0 | 0.285 |
| `HomeViewModelTest` | 5 | 0 | 0 | 0 | 0.597 |
| `InsightsViewModelTest` | 6 | 0 | 0 | 0 | 0.705 |
| `NutritionViewModelTest` | 9 | 0 | 0 | 0 | 1.612 |
| `SettingsViewModelTest` | 2 | 0 | 0 | 0 | 0.265 |
| **Total** | **239** | **0** | **0** | **0** | **~26.1s** |
