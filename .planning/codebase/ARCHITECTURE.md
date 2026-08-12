# Architecture

**Analysis Date:** 2026-08-07

The application is structured using **Clean Architecture** principles combined with **MVVM** pattern in the Presentation layer, leveraging Jetpack Compose. This ensures clear boundaries between UI, domain business logic, and external data sources (USB camera, local DB, remote SFTP/WebDAV server).

```mermaid
graph TD
    UI[UI Components / Compose] --> VM[ViewModels]
    VM --> UC[Use Cases]
    VM --> DM_REP[Domain Repository Interfaces]
    UC --> DM_REP
    DM_REP <|-- DT_REP[Data Repository Implementations]
    DT_REP --> Local[Room Database / Entities / DAOs]
    DT_REP --> Remote[SFTP / WebDAV / Network Clients]
    DT_REP --> USB[USB / PTP / MTP Source Helpers]
    
    SubGraph_Runtime[Session Runtime]
    SO[SessionOrchestrator] --> SD[SessionDispatcher]
    SO --> TSM[TransferSessionManager]
    TSM --> Worker[TransferUploadWorker]
```

## Layers

### 1. Data Layer (`com.rvmeur.transferin.data`)
Handles local persistence, remote networks, and low-level USB client protocols.

- **Local Database (Room):** `db/TransferInDatabase.kt`, `db/DatabaseProvider.kt`, entities (e.g., `SessionEntity.kt`, `TransferQueueItemEntity.kt`), and DAOs (e.g., `SessionDao.kt`).
- **Network Clients:** SFTP clients (`JschSftpClient.kt`, `SftpSessionPool.kt`, `SftpSessionReconnector.kt`, `TwoPhaseUploadHandler.kt`) and WebDAV clients (`WebDavClient.kt`).
- **USB Host Connection:** `usb/` handles low-level USB MTP/PTP camera connectivity, permission management, and camera discovery engine.
- **Repository Implementations:** `repository/` implements domain interfaces (e.g. `CameraMediaRepositoryImpl.kt`, `TransferQueueRepositoryImpl.kt`), decoupling domain logic from libraries.

### 2. Domain Layer (`com.rvmeur.transferin.domain`)
Contains the core business rules, models, and interfaces. This layer is pure Kotlin and independent of Android framework details where possible.

- **Models:** Domain objects representing application states (`CameraMedia.kt`, `TransferQueueItem.kt`, `ProjectSession.kt`).
- **Repository Interfaces:** Decouples data fetching from presentation logic (`SessionRepository.kt`, `NasConnectionRepository.kt`).
- **Use Cases:** Represents business logic actions (e.g., `ListCameraMediaUseCase.kt`).
- **Shoot Mode Business Logic:** `shoot/` package contains critical logic for real-time shoot detection:
  - `ShootModeEngine.kt` - Main engine managing shoot session states.
  - `CtgDatabaseParser.kt` - Parses camera database structures (specifically Canon CTG format).
  - `SmartUploadFilter.kt` / `NewShotDetector.kt` - Detects new photos to trigger automatic uploads.
- **Session Interfaces:** `session/` defines abstract connection/upload orchestrators (`CameraEngine.kt`, `UploadQueueManager.kt`, `TransferSessionManager.kt`).

### 3. Session Runtime Layer (`com.rvmeur.transferin.session`)
Orchestrates background tasks, active uploads, and synchronization lifecycles.

- **Active Session Orchestrator:** `runtime/SessionOrchestrator.kt` and `SessionDispatcher.kt` dispatch active media capture events to the transfer engine.
- **WorkManager Task Runner:** `worker/TransferUploadWorker.kt` schedules queue processing in the background under Android battery and network policies.

### 4. Presentation Layer (`com.rvmeur.transferin.feature` & `com.rvmeur.transferin.ui`)
Implements the user interface using Jetpack Compose and ViewModels.

- **Features:** Clean boundary per feature (e.g., `connectcamera/`, `sessionactive/`, `sessionsummary/`).
- **MVVM Pattern:** Each feature contains:
  - `*Screen.kt` - Compose UI layout.
  - `*ViewModel.kt` - ViewModel exposing UI state (`StateFlow`) and receiving user events.
  - `*UiState.kt` / `*State.kt` - Read-only state container.
- **Service layer:** `com.rvmeur.transferin.service.UsbTetherService.kt` is a foreground service that prevents the OS from killing the app during active background uploads.

---

*Architecture analysis: 2026-08-07*
