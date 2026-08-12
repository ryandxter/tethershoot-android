# Testing Practices

**Analysis Date:** 2026-08-07

The testing suite contains both local unit tests and instrumented Android UI tests.

## 1. Test Architecture

### Local Unit Tests (`app/src/test/`)
Run on the local JVM for fast feedback. Focused on business rules, database parsing, and connection logic.
- **Framework:** JUnit 4
- **Mocking Library:** MockK (`io.mockk:mockk:1.13.9`)
- **Coroutines Testing:** `kotlinx-coroutines-test:1.8.0` with `StandardTestDispatcher` and `runTest` to control virtual time.

### Instrumented Tests (`app/src/androidTest/`)
Run on an Android Emulator or physical device. Focused on database migrations, Compose UI layouts, and foreground services.
- **Runner:** `androidx.test.runner.AndroidJUnitRunner`
- **Compose UI Tests:** Compose UI Test rules (`androidx.compose.ui.test.junit4`)
- **Espresso:** Used for system interactions (`androidx.test.espresso:espresso-core`)

## 2. Typical Testing Scenarios

- **Database Parser (CTG):** Unit tests check parsing logic against mock camera DB files to ensure stability.
- **SFTP Reconnect:** Simulates connection dropouts and verifies the circuit breaker trips, backoff periods escalate, and sessions are re-pooled.
- **Worker Execution:** Tests WorkManager constraints, verifying worker triggers and queue updates when retry occurs.
- **UI Compose layouts:** Verifies correct button bindings, text indicators (e.g. "Connecting", "Tethered"), and progress indicators.

## 3. Commands

Run all local unit tests:
```bash
./gradlew test
```

Run specific unit test (e.g., `ExampleUnitTest`):
```bash
./gradlew :app:testDebugUnitTest --tests "com.rvmeur.transferin.ExampleUnitTest"
```

Run instrumented tests (requires connected emulator/device):
```bash
./gradlew connectedAndroidTest
```

---

*Testing analysis: 2026-08-07*
