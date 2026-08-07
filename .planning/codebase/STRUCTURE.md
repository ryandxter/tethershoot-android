# Project Structure

**Analysis Date:** 2026-08-07

The main source code resides in `app/src/main/java/com/reflection/transferin/`. Below is the logical file and directory organization:

```text
com/reflection/transferin/
│
├── MainActivity.kt                # App launcher Activity & navigation host
│
├── data/                          # Data Layer implementations
│   ├── local/                     # Room local database
│   │   ├── dao/                   # Room DAOs (e.g., SessionDao.kt)
│   │   ├── db/                    # DB configuration (DatabaseProvider.kt, Migration_8_9.kt)
│   │   └── entity/                # Room entities (e.g., SessionEntity.kt)
│   │
│   ├── repository/                # Implementations of domain repository interfaces
│   │
│   ├── sftp/                      # SFTP client pool, resilience, and checkpoints
│   │   ├── JschSftpClient.kt
│   │   ├── SftpSessionPool.kt
│   │   ├── SftpSessionReconnector.kt
│   │   ├── SftpSessionHealthMonitor.kt
│   │   └── TwoPhaseUploadHandler.kt
│   │
│   ├── storage/                   # External local OTG storage detector
│   │
│   ├── usb/                       # USB Host, permissions, and PTP/MTP initializations
│   │   ├── UsbHelper.kt
│   │   ├── CameraDiscoveryEngine.kt
│   │   └── vendor/                # Vendor-specific handshake (CannonPtpSession.kt)
│   │
│   ├── webdav/                    # WebDAV client
│   └── worker/                    # WorkManager background task scheduler
│
├── domain/                        # Domain Layer (Pure Kotlin logic & models)
│   ├── model/                     # Core business entities (CameraMedia.kt, ProjectSession.kt)
│   ├── repository/                # Interfaces for data injection (SessionRepository.kt)
│   │
│   ├── session/                   # Core watcher & coordinator interfaces
│   │   ├── CameraEngine.kt
│   │   ├── UploadQueueManager.kt
│   │   └── TransferSessionManager.kt
│   │
│   ├── shoot/                     # Real-time shoot & CTG parser engines
│   │   ├── ShootModeEngine.kt
│   │   ├── CtgDatabaseParser.kt
│   │   └── SmartUploadFilter.kt
│   │
│   └── usecase/                   # Functional domain interactors (ListCameraMediaUseCase.kt)
│
├── feature/                       # Presentation Layer - Feature modules
│   ├── connectcamera/             # Camera connection setup & VM
│   ├── sessionactive/             # Live upload UI, VM & states
│   └── sessionsummary/            # Transfer logs and session statistics
│
├── service/                       # Android Services & Receivers
│   ├── UsbTetherService.kt        # Foreground service keeping the connection alive
│   └── UsbDisconnectHandler.kt    # Graceful recovery helper for USB disconnects
│
├── session/                       # Session Runtime & Queue Runners
│   ├── model/                     # Runtime states (SessionRuntimeState.kt)
│   ├── runtime/                   # SessionOrchestrator & SessionDispatcher
│   └── worker/                    # TransferUploadWorker (WorkManager background execution)
│
└── ui/                            # Common Theme & UI styles (Theme.kt, Color.kt)
```

---

*Structure analysis: 2026-08-07*
