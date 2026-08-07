# Coding Conventions

**Analysis Date:** 2026-08-07

## 1. Asynchronous Programming & Concurrency
Kotlin Coroutines are used exclusively for background processing, file access, and network I/O.

- **Dispatchers:** Use appropriate dispatchers for different operations:
  - `Dispatchers.IO` for file I/O, Room database, SFTP network requests.
  - `Dispatchers.Main` for UI interactions and Compose State updates.
- **Scopes:** Prefer constructor-injected scopes or lifecycle-aware scopes (`viewModelScope` in ViewModels, `lifecycleScope` in Activities/Services).
- **Streams:** Use `StateFlow` for state caching (e.g., UI States, connection status) and `SharedFlow` for single-shot events (e.g., toast alerts, navigations).

## 2. Architecture & MVVM Pattern
- **Unidirectional Data Flow (UDF):** Compositions read from an immutable `UiState` and emit events upward to the ViewModel.
- **State Separation:** ViewModels expose a single read-only `StateFlow<UiState>` wrapper. For example, `SessionActiveViewModel` exposes `SessionUiState`.
- **Dependency Injection:** Done manually via factories (`ConnectCameraViewModelFactory.kt`, `SessionViewModelFactory.kt`) passing dependencies from `DatabaseProvider` or singleton repositories.

## 3. Database & Local Persistence
- **Room DAOs:** Operations that fetch data dynamically must return `Flow<T>` to auto-update UI when data changes. Long-running transactions or updates must use `suspend` keyword.
- **Migrations:** Database migration logic (e.g., `Migration_8_9.kt`) must contain raw SQLite execute statements and are integrated in `DatabaseProvider.kt` with version checks.

## 4. Error Handling & Resilience
- **Network Interruptions:** SFTP connectivity uses a pool (`SftpSessionPool.kt`) combined with health monitoring (`SftpSessionHealthMonitor.kt`) and reconnectors (`SftpSessionReconnector.kt`).
- **USB Uncoupling:** Detaching USB triggers a stabilization period (2 seconds) managed by `UsbDisconnectHandler.kt` to prevent crash spikes due to rapid plug-unplug cycles.
- **WorkManager Policies:** Failed queue uploads in `TransferUploadWorker.kt` return `Result.retry()` with exponential backoff configurations.

## 5. Coding Style & Formatting
- **Standard Kotlin:** Follow official Kotlin coding conventions (line length, camelCase, naming conventions).
- **Sealed Interfaces:** Used for state representations, such as `ConnectionState` in USB detach/attach events or `ReconnectionState` during SFTP reconnection attempts.
- **Code Comments:** Document complex hardware configurations, vendor-specific MTP workarounds, and database migrations.

---

*Conventions analysis: 2026-08-07*
