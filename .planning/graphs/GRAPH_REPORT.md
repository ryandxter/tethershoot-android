# Graph Report - TransferInFuji  (2026-08-09)

## Corpus Check
- 622 files · ~767,261 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 3516 nodes · 4243 edges · 448 communities (410 shown, 38 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 73 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `54fc3985`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- USB Camera & MTP
- SFTP & Remote Operations
- Transfer Session UI
- Camera Initialization
- Database & NAS Connection
- Gallery UI
- Document Scanner
- Transfer Queue
- Architecture & Phase Planning
- File Stability Checker
- Camera Unlock Orchestrator
- Camera Media DAO
- Camera Media Repository
- Connect Camera Screen
- Transfer Queue DAO
- SessionEntity
- CameraMediaRepositoryImpl
- TransferQueueRepositoryImpl
- AutoStorageDetector
- research/STACK.md
- PhotoWatcher
- TransferQueueItemEntity
- SessionRepositoryImpl
- UsbPermissionManager
- CameraDiscoveryEngine
- CameraGalleryViewModel
- UsbTetherService
- MediaStatus
- CtgWatcher
- MtpContentObserver
- CanonPtpEnumerator
- SessionState
- ConnectionMode
- TransferItemStatus
- .create
- TransferUploadWorker
- WatcherStatus
- CameraRuntimeState
- CtgWatcher
- UploadQueueManager
- Codebase Map
- ShootModeEngine
- CanonPtpTransport
- TransferUploadWorkScheduler
- FileType
- SessionRepository
- CanonCameraInitializer
- UploadedFingerprintDao
- gradlew
- ExampleInstrumentedTest
- ExampleUnitTest
- Exponential Backoff
- reflection/transferin/feature/sessionsummary/SessionSummaryViewModel.kt
- reflection/transferin/feature/sessionsummary/SummaryState.kt
- reflection/transferin/session/runtime/SessionDispatcher.kt
- reflection/transferin/session/runtime/SessionOrchestrator.kt
- Stale Part File Cleanup
- reflection/transferin/domain/repository/TransferQueueRepository.kt
- reflection/transferin/domain/session/FileClassifier.kt
- reflection/transferin/ui/theme/Color.kt
- reflection/transferin/ui/theme/Type.kt
- build.gradle.kts
- v1.0 Requirements
- v1.0 Roadmap
- settings.gradle.kts
- Phase 1: CTG Foundation - Research
- TransferSessionManagerImpl
- GalleryViewModel
- Phase 2: Smart Upload Detection - Research
- CameraUnlockOrchestrator
- CameraMediaRepository
- SessionViewModel
- CameraMediaItemEntity
- TransferQueueRepositoryImpl
- ConnectCameraViewModel
- TransferQueueDao
- UsbHelper
- CameraMediaRepositoryImpl
- AutoStorageDetector
- SessionEntity
- UsbDisconnectHandler
- DelegatingSftpClient
- SelectFolderViewModel
- SessionRepositoryImpl
- TwoPhaseUploadHandlerTest
- PhotoWatcher
- ConnectionMode
- CameraDiscoveryEngine
- UsbSourceOrchestrator
- Technical Approaches
- CameraRuntimeState
- Phase 03 Plan 04: USB Disconnect Handler - Summary
- MediaStatus
- CameraMedia
- GoogleDriveClient
- UploadCheckpointManagerTest
- CameraGalleryViewModel
- TransferIn
- Phase 1 Plan 01: CTG Foundation - Tracer (End-to-End CTG Parsing)
- Phase 1 Plan 02: State Differ and CTG Change Detection
- CanonCameraInitializer
- TransferQueueItemEntity
- ProjectSession
- Implementation Decisions
- SessionActiveScreen
- Phase 01: CTG Foundation Summary
- Plan: Phase 1 - SFTP Pooling & Reconnection Resilience
- Phase 02 Plan 01: Fingerprint Persistence Layer Summary
- Phase 2 Plan 02: Smart Upload Detection Domain Layer Summary
- Phase 03 Plan 01: SFTP Session Pool Infrastructure
- NasConnectionRepositoryImpl
- SftpClient
- TransferInApp
- Phase 1 Plan 2: State Differ and CTG Change Detection Summary
- Phase 3, Plan 2: SFTP Session Health Monitoring
- Phase 03 Plan 03: SFTP Session Reconnection Summary
- CameraMedia
- LoginNasViewModel
- SessionState
- .create
- UploadQueueManager
- Dokumentasi Teknis Project: Transfer In
- Goal Achievement
- 1. Technical Domain Analysis
- Phase 2: USB Debounce & Service Compliance - Context
- Phase 3: Multi-Vendor MTP Fallback - Context
- Phase 4: Resumable Transfers & Server Cleanup - Context
- Phase 4: Upload Integrity - Research
- Research: WebDAV Protocol Support & Starting Shared Folder
- .requestPermission
- TransferItemStatus
- SessionRepositoryAdapter
- ImportCameraEngine
- LoginNasViewModel
- rvmeur/transferin/feature/gallery/CameraGalleryScreen.kt
- Plan: Phase 2 - USB Debounce & Service Compliance
- Identified Gaps & Modifications
- 2. Tethered Mode CTG Integration & Deduplication
- Phase 5: Room Migration Validation Suite - Context
- Milestone v1 Active Requirements
- Proposed Changes
- JschSftpClient
- reflection/transferin/feature/gallery/CameraGalleryScreen.kt
- WatcherStatus
- TransferUploadWorker
- TransferInDatabase
- SftpClient
- DocumentFileScanner
- DocumentFileScanner
- .buildMonitorState
- ShootModeEngine
- Phase 3 Session Resilience
- 1. Technical Domain Analysis
- Phase 04 Plan 01: End-to-end two-phase upload tracer Summary
- Assessment Scorecard (Scale 1-4)
- UsbDeviceInfo
- TransferInDatabase
- Research: Common Pitfalls & Prevention
- Plan: Phase 3 - Multi-Vendor MTP Fallback
- Phase 03: Session Resilience - COMPLETE
- Phase 04 Plan 02: Stale .part file cleanup Summary
- Plan - Phase 6: Camera File Number Baseline Extraction
- Milestone v1: Rebranding & Google Drive Migration (Active)
- STATE.md
- NasConnectionDao
- NasConnectionDao
- .transact
- .create
- Technology Stack
- Phase 1: SFTP Pooling & Reconnection Resilience - Discussion Log
- Architecture Patterns
- Milestone v1 Phases
- Test Cases
- Connection Layers
- Milestone v2 Active Requirements
- FileStabilityChecker
- .getAllObjectHandlesNoLock
- DiscoveredFile
- SourceSelectionMode
- FileType
- SessionRepository
- DiscoveredFile
- ConnectCameraScreen
- Research: Architecture Integration
- Layers
- Technical Concerns & Debt
- Coding Conventions
- Testing Practices
- 1. Automated Tests
- 1. Automated Tests
- 2. Manual Verification
- Test Cases
- Test Cases
- Phase 3 Research: Google Drive Integration & NAS Replacement
- UploadStatus
- UploadStatus
- SourceSelectionMode
- SessionSummaryScreen
- research/SUMMARY.md
- JSch MWiede Fork 0.2.26
- Integrations
- Phase 1: SFTP Pooling & Reconnection Resilience - Summary
- Items Discussed
- Phase 2: USB Debounce & Service Compliance - Summary
- Items Discussed
- Items Discussed
- Detailed Findings
- Common Pitfalls
- Items Discussed
- Onboarding Summary
- Phase 6 Research - Camera File Number Baseline Extraction
- Research: Target Features & Behaviors
- Key Findings
- DefaultCameraSessionInitializer
- MediaFingerprint
- SelectFolderScreen
- GalleryViewModelFactory
- Phase 3: Multi-Vendor MTP Fallback - Summary
- Phase 4: Resumable Transfers & Server Cleanup - Summary
- 04-01-PLAN.md
- 04-02-PLAN.md
- Phase 04 UI Review: Retroactive Visual Audit
- User Constraints (from CONTEXT.md)
- Validation Architecture
- Phase 5: Room Migration Validation Suite - Summary
- Phase 1 Research: Rebranding & Package Rename
- Phase 2 Research: UI Style Refactoring (rvmeur.com Design)
- DatabaseMigrationTest
- SessionSummaryScreen
- rvmeur/transferin/session/worker/TransferUploadWorker.kt
- Milestones
- Code Examples
- Sources
- Phase 6 Context - Camera File Number Baseline Extraction
- Phase 6 Summary - Camera File Number Baseline Extraction
- Phase 7 Context - Robust RAW+JPG Co-existence & Verification
- Phase 7 Research - Robust RAW+JPG Co-existence & Verification
- Phase 7 Summary - Robust RAW+JPG Co-existence & Verification
- Phase 8 Context - Canon and Fuji Connection Polish
- Phase 8 Research - Canon and Fuji Connection Polish
- gradlew
- ExampleInstrumentedTest
- ExampleUnitTest
- Milestone v1 Audit Report
- 02-01-PLAN.md
- 02-02-PLAN.md
- Security Domain
- Standard Stack
- Phase 1 Plan: Rebranding & Package Rename
- Phase 2 Plan: UI Style Refactoring (rvmeur.com Design)
- Phase 3 Plan: Google Drive Integration & NAS Replacement
- Phase 6 Discussion Log - Camera File Number Baseline Extraction
- Phase 7 Discussion Log - Robust RAW+JPG Co-existence & Verification
- Phase 8 Discussion Log - Canon and Fuji Connection Polish
- Context
- Milestone v2 Audit Report
- aas-gsd-auto-integration.md
- Migration_8_9.kt
- SftpConnectionConfig.kt
- rvmeur/transferin/feature/sessionsummary/SessionSummaryViewModel.kt
- rvmeur/transferin/feature/sessionsummary/SummaryState.kt
- rvmeur/transferin/session/runtime/SessionDispatcher.kt
- rvmeur/transferin/session/runtime/SessionOrchestrator.kt
- last-snapshot.md
- app/src/main/java/com/reflection/transferin/feature/folder/SelectSourceFolderScreen.kt
- app/src/main/java/com/reflection/transferin/feature/gallery/CameraGalleryScreen.kt
- app/src/main/java/com/reflection/transferin/feature/gallery/CameraGalleryViewModel.kt
- app/src/main/java/com/reflection/transferin/feature/gallery/CameraGalleryViewModelFactory.kt
- app/src/main/java/com/reflection/transferin/feature/gallery/GalleryUiState.kt
- app/src/main/java/com/reflection/transferin/feature/gallery/GalleryViewModel.kt
- app/src/main/java/com/reflection/transferin/feature/gallery/GalleryViewModelFactory.kt
- app/src/main/java/com/reflection/transferin/feature/gallery/UploadedGalleryScreen.kt
- app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionActiveScreen.kt
- app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionUiState.kt
- app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionViewModel.kt
- app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionViewModelFactory.kt
- app/src/main/java/com/reflection/transferin/feature/sessionsummary/SessionSummaryScreen.kt
- app/src/main/java/com/reflection/transferin/feature/sessionsummary/SessionSummaryViewModel.kt
- app/src/main/java/com/reflection/transferin/feature/sessionsummary/SummaryState.kt
- app/src/main/java/com/reflection/transferin/MainActivity.kt
- app/src/main/java/com/reflection/transferin/service/UsbTetherService.kt
- app/src/main/java/com/reflection/transferin/session/model/SessionRuntimeState.kt
- app/src/main/java/com/reflection/transferin/session/runtime/SessionDispatcher.kt
- app/src/main/java/com/reflection/transferin/session/runtime/SessionOrchestrator.kt
- app/src/main/java/com/reflection/transferin/session/runtime/TransferSessionManagerImpl.kt
- app/src/main/java/com/reflection/transferin/session/worker/TransferUploadWorker.kt
- app/src/main/java/com/reflection/transferin/ui/theme/Color.kt
- app/src/main/java/com/reflection/transferin/ui/theme/Theme.kt
- app/src/main/java/com/reflection/transferin/ui/theme/Type.kt
- app/src/test/java/com/reflection/transferin/ExampleUnitTest.kt
- .bg-shell/manifest.json
- build.gradle.kts
- .claude/settings.local.json
- gradlew
- .gsd/runtime/write-gate-state.json
- .planning/codebase/ARCHITECTURE.md
- .planning/codebase/CONCERNS.md
- .planning/codebase/CONVENTIONS.md
- .planning/codebase/INTEGRATIONS.md
- .planning/codebase/STACK.md
- .planning/codebase/STRUCTURE.md
- .planning/codebase/TESTING.md
- .planning/MILESTONES.md
- .planning/onboarding/SUMMARY.md
- .planning/phases/01-CTG-Foundation/01-01-PLAN.md
- .planning/phases/01-CTG-Foundation/01-02-PLAN.md
- .planning/PROJECT.md
- .planning/REQUIREMENTS.md
- .planning/ROADMAP.md
- .planning/STATE.md
- settings.gradle.kts
- app/client_secret_2_343575435197-q6tnos0o1cgt8c0ir8alne21gitioj7i.apps.googleusercontent.com.json
- app/schemas/com.rvmeur.transferin.data.local.db.TransferInDatabase/9.json
- app/src/androidTest/java/com/rvmeur/transferin/data/local/db/DatabaseMigrationTest.kt
- app/src/androidTest/java/com/rvmeur/transferin/ExampleInstrumentedTest.kt
- app/src/google-services.json
- app/src/main/java/com/rvmeur/transferin/data/local/dao/CameraMediaDao.kt
- app/src/main/java/com/rvmeur/transferin/data/local/dao/NasConnectionDao.kt
- app/src/main/java/com/rvmeur/transferin/data/local/dao/SessionDao.kt
- app/src/main/java/com/rvmeur/transferin/data/local/dao/TransferQueueDao.kt
- app/src/main/java/com/rvmeur/transferin/data/local/dao/UploadedFingerprintDao.kt
- app/src/main/java/com/rvmeur/transferin/data/local/db/DatabaseProvider.kt
- app/src/main/java/com/rvmeur/transferin/data/local/db/Migration_8_9.kt
- app/src/main/java/com/rvmeur/transferin/data/local/db/TransferInDatabase.kt
- app/src/main/java/com/rvmeur/transferin/data/local/entity/CameraMediaItemEntity.kt
- app/src/main/java/com/rvmeur/transferin/data/local/entity/NasConnectionEntity.kt
- app/src/main/java/com/rvmeur/transferin/data/local/entity/SessionEntity.kt
- app/src/main/java/com/rvmeur/transferin/data/local/entity/TransferQueueItemEntity.kt
- app/src/main/java/com/rvmeur/transferin/data/local/entity/UploadedFingerprintEntity.kt
- app/src/main/java/com/rvmeur/transferin/data/repository/CameraMediaRepositoryImpl.kt
- app/src/main/java/com/rvmeur/transferin/data/repository/NasConnectionRepositoryImpl.kt
- app/src/main/java/com/rvmeur/transferin/data/repository/SessionRepositoryImpl.kt
- app/src/main/java/com/rvmeur/transferin/data/repository/TransferQueueRepositoryImpl.kt
- app/src/main/java/com/rvmeur/transferin/data/repository/UploadedFingerprintRepositoryImpl.kt
- app/src/main/java/com/rvmeur/transferin/data/sftp/DelegatingSftpClient.kt
- app/src/main/java/com/rvmeur/transferin/data/sftp/GoogleDriveClient.kt
- app/src/main/java/com/rvmeur/transferin/data/sftp/SftpClient.kt
- app/src/main/java/com/rvmeur/transferin/data/sftp/SftpConnectionConfig.kt
- app/src/main/java/com/rvmeur/transferin/data/sftp/TwoPhaseUploadHandler.kt
- app/src/main/java/com/rvmeur/transferin/data/sftp/UploadCheckpointManager.kt
- app/src/main/java/com/rvmeur/transferin/data/storage/AutoStorageDetector.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/CameraDiscoveryEngine.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/CameraInitContext.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/CameraInitResult.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/CameraSessionInitializer.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/CameraUnlockOrchestrator.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/CtgWatcher.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/DefaultCameraSessionInitializer.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/DocumentFileScanner.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/FileStabilityChecker.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/MtpContentObserver.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/PhotoWatcher.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/UsbHelper.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/UsbPermissionManager.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/UsbSourceOrchestrator.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CameraVendorInitializer.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CannonCameraInitializer.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CannonPtpEnumerator.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CannonPtpSession.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CannonPtpTransport.kt
- app/src/main/java/com/rvmeur/transferin/data/usb/vendor/GenericCameraInitializer.kt
- app/src/main/java/com/rvmeur/transferin/data/worker/TransferUploadWorkScheduler.kt
- app/src/main/java/com/rvmeur/transferin/domain/model/CameraMedia.kt
- app/src/main/java/com/rvmeur/transferin/domain/model/CameraRuntimeState.kt
- app/src/main/java/com/rvmeur/transferin/domain/model/ConnectionMode.kt
- app/src/main/java/com/rvmeur/transferin/domain/model/FileType.kt
- app/src/main/java/com/rvmeur/transferin/domain/model/MediaJob.kt
- app/src/main/java/com/rvmeur/transferin/domain/model/ProjectSession.kt
- app/src/main/java/com/rvmeur/transferin/domain/model/SessionState.kt
- app/src/main/java/com/rvmeur/transferin/domain/model/SourceSelectionMode.kt
- app/src/main/java/com/rvmeur/transferin/domain/model/TransferItemStatus.kt
- app/src/main/java/com/rvmeur/transferin/domain/model/TransferQueueItem.kt
- app/src/main/java/com/rvmeur/transferin/domain/model/WatcherStatus.kt
- app/src/main/java/com/rvmeur/transferin/domain/repository/CameraMediaRepository.kt
- app/src/main/java/com/rvmeur/transferin/domain/repository/NasConnectionRepository.kt
- app/src/main/java/com/rvmeur/transferin/domain/repository/SessionRepository.kt
- app/src/main/java/com/rvmeur/transferin/domain/repository/SessionRepositoryAdapter.kt
- app/src/main/java/com/rvmeur/transferin/domain/repository/TransferQueueRepository.kt
- app/src/main/java/com/rvmeur/transferin/domain/repository/UploadedFingerprintRepository.kt
- app/src/main/java/com/rvmeur/transferin/domain/session/CameraConnectionCoordinator.kt
- app/src/main/java/com/rvmeur/transferin/domain/session/CameraEngine.kt
- app/src/main/java/com/rvmeur/transferin/domain/session/FileClassifier.kt
- app/src/main/java/com/rvmeur/transferin/domain/session/ProjectSessionManager.kt
- app/src/main/java/com/rvmeur/transferin/domain/session/ProjectSessionManagerImpl.kt
- app/src/main/java/com/rvmeur/transferin/domain/session/RemoteFolder.kt
- app/src/main/java/com/rvmeur/transferin/domain/session/SourceWatcher.kt
- app/src/main/java/com/rvmeur/transferin/domain/session/TransferSessionManager.kt
- app/src/main/java/com/rvmeur/transferin/domain/session/UploadQueueManager.kt
- app/src/main/java/com/rvmeur/transferin/domain/shoot/CtgDatabaseParser.kt
- app/src/main/java/com/rvmeur/transferin/domain/shoot/CtgMediaEntry.kt
- app/src/main/java/com/rvmeur/transferin/domain/shoot/CtgMediaSnapshot.kt
- app/src/main/java/com/rvmeur/transferin/domain/shoot/CtgStateDiffer.kt
- app/src/main/java/com/rvmeur/transferin/domain/shoot/Fingerprint.kt
- app/src/main/java/com/rvmeur/transferin/domain/shoot/NewShotDetector.kt
- app/src/main/java/com/rvmeur/transferin/domain/shoot/ShootModeEngine.kt
- app/src/main/java/com/rvmeur/transferin/domain/shoot/SmartUploadFilter.kt
- app/src/main/java/com/rvmeur/transferin/domain/usecase/ListCameraMediaUseCase.kt
- app/src/main/java/com/rvmeur/transferin/feature/auth/LoginNasScreen.kt
- app/src/main/java/com/rvmeur/transferin/feature/auth/LoginNasViewModel.kt
- app/src/main/java/com/rvmeur/transferin/feature/auth/LoginNasViewModelFactory.kt
- app/src/main/java/com/rvmeur/transferin/feature/connectcamera/ConnectCameraScreen.kt
- app/src/main/java/com/rvmeur/transferin/feature/connectcamera/ConnectCameraViewModel.kt
- app/src/main/java/com/rvmeur/transferin/feature/connectcamera/ConnectCameraViewModelFactory.kt
- app/src/main/java/com/rvmeur/transferin/feature/folder/FolderUiState.kt
- app/src/main/java/com/rvmeur/transferin/feature/folder/SelectFolderScreen.kt
- app/src/main/java/com/rvmeur/transferin/MainActivity.kt
- .bg-shell/manifest.json
- build.gradle.kts
- .claude/settings.local.json
- DOCUMENTATION.md
- gradlew
- .gsd/graphs/graph.json
- .gsd/runtime/write-gate-state.json
- .planning/codebase/ARCHITECTURE.md
- .planning/codebase/CONCERNS.md
- .planning/codebase/CONVENTIONS.md
- .planning/codebase/INTEGRATIONS.md
- .planning/codebase/STACK.md
- .planning/codebase/STRUCTURE.md
- .planning/codebase/TESTING.md
- .planning/config.json
- .planning/MILESTONES.md
- .planning/onboarding/SUMMARY.md
- .planning/PROJECT.md
- .planning/REQUIREMENTS.md
- .planning/research/ARCHITECTURE.md
- .planning/research/FEATURES.md
- .planning/research/PITFALLS.md
- .planning/research/STACK.md
- .planning/research/SUMMARY.md
- .planning/ROADMAP.md
- .planning/STATE.md
- settings.gradle.kts
- MTP Handle Staleness
- WorkManager 2.11.2

## God Nodes (most connected - your core abstractions)
1. `Communities (277 total, 63 thin omitted)` - 194 edges
2. `UsbHelper` - 65 edges
3. `Codebase Map` - 52 edges
4. `Communities (66 total, 16 thin omitted)` - 44 edges
5. `TransferQueueItemEntity` - 42 edges
6. `CameraMediaItemEntity` - 39 edges
7. `TransferQueueDao` - 37 edges
8. `SessionViewModel` - 35 edges
9. `TransferQueueRepositoryImpl` - 34 edges
10. `CameraMediaRepositoryImpl` - 33 edges

## Surprising Connections (you probably didn't know these)
- `TransferInApp()` --calls--> `NasConnectionRepositoryImpl`  [INFERRED]
  app/src/main/java/com/rvmeur/transferin/MainActivity.kt → app/src/main/java/com/rvmeur/transferin/data/repository/NasConnectionRepositoryImpl.kt
- `TransferInApp()` --calls--> `DelegatingSftpClient`  [INFERRED]
  app/src/main/java/com/rvmeur/transferin/MainActivity.kt → app/src/main/java/com/rvmeur/transferin/data/sftp/DelegatingSftpClient.kt
- `TransferInApp()` --calls--> `DocumentFileScanner`  [INFERRED]
  app/src/main/java/com/rvmeur/transferin/MainActivity.kt → app/src/main/java/com/rvmeur/transferin/data/usb/DocumentFileScanner.kt
- `TransferInApp()` --calls--> `FileStabilityChecker`  [INFERRED]
  app/src/main/java/com/rvmeur/transferin/MainActivity.kt → app/src/main/java/com/rvmeur/transferin/data/usb/FileStabilityChecker.kt
- `TransferInApp()` --calls--> `UsbSourceOrchestrator`  [INFERRED]
  app/src/main/java/com/rvmeur/transferin/MainActivity.kt → app/src/main/java/com/rvmeur/transferin/data/usb/UsbSourceOrchestrator.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **v1.0 Complete Roadmap Structure** — project_transferin_milestone_v1_0, phase_01_ctg_foundation, phase_02_smart_upload_detection, phase_03_session_resilience, phase_04_upload_integrity, requirement_ctg_01, requirement_ctg_02, requirement_ctg_03, requirement_upload_01, requirement_upload_02, requirement_upload_03, requirement_resilience_01, requirement_resilience_02, requirement_resilience_03, requirement_resilience_04 [EXTRACTED 1.00]
- **Complete Technology Stack** — tech_kotlin, tech_jetpack_compose, tech_room, tech_workmanager, tech_jsch [EXTRACTED 1.00]
- **Core Technology Stack** — library_jsch_mwiede, library_room, library_workmanager [EXTRACTED 1.00]

## Communities (448 total, 38 thin omitted)

### Community 0 - "USB Camera & MTP"
Cohesion: 0.01
Nodes (194): Communities (277 total, 63 thin omitted), Community 100 - "Phase 1 Plan 02: State Differ and CTG Change Detection", Community 103 - "ProjectSession", Community 104 - "Implementation Decisions", Community 105 - "SessionActiveScreen", Community 106 - "Phase 01: CTG Foundation Summary", Community 107 - "Plan: Phase 1 - SFTP Pooling & Reconnection Resilience", Community 108 - "Phase 02 Plan 01: Fingerprint Persistence Layer Summary" (+186 more)

### Community 1 - "SFTP & Remote Operations"
Cohesion: 0.05
Nodes (44): Communities (66 total, 16 thin omitted), Community 0 - "USB Camera & MTP", Community 10 - "Camera Unlock Orchestrator", Community 11 - "Camera Media DAO", Community 12 - "Camera Media Repository", Community 13 - "Connect Camera Screen", Community 15 - "Community 15", Community 18 - "Community 18" (+36 more)

### Community 2 - "Transfer Session UI"
Cohesion: 0.15
Nodes (12): app/build.gradle.kts, ast_hash, mtime, semantic_hash, app/src/main/java/com/reflection/transferin/data/local/db/DatabaseProvider.kt, ast_hash, mtime, semantic_hash (+4 more)

### Community 3 - "Camera Initialization"
Cohesion: 0.15
Nodes (12): app/build.gradle.kts, ast_hash, mtime, semantic_hash, app/schemas/com.rvmeur.transferin.data.local.db.TransferInDatabase/8.json, ast_hash, mtime, semantic_hash (+4 more)

### Community 4 - "Database & NAS Connection"
Cohesion: 0.17
Nodes (11): Community Hubs (Navigation), Corpus Check, God Nodes (most connected - your core abstractions), Graph Freshness, Graph Report - TransferInFuji  (2026-08-09), Hyperedges (group relationships), Import Cycles, Knowledge Gaps (+3 more)

### Community 5 - "Gallery UI"
Cohesion: 0.18
Nodes (10): Community Hubs (Navigation), Corpus Check, God Nodes (most connected - your core abstractions), Graph Report - .  (2026-07-31), Hyperedges (group relationships), Import Cycles, Knowledge Gaps, Suggested Questions (+2 more)

### Community 6 - "Document Scanner"
Cohesion: 0.40
Nodes (3): T, ViewModelProvider, SessionViewModelFactory

### Community 7 - "Transfer Queue"
Cohesion: 0.50
Nodes (4): app/src/androidTest/java/com/reflection/transferin/ExampleInstrumentedTest.kt, ast_hash, mtime, semantic_hash

### Community 8 - "Architecture & Phase Planning"
Cohesion: 0.16
Nodes (15): Phase 1 CTG Foundation, Phase 2 Smart Upload Detection, Phase 3 Session Resilience, Phase 4 Upload Integrity, Milestone v1.0 Shoot Mode Optimization, CTG-01 Parse Canon CTG files, CTG-02 Compare CTG against upload queue, CTG-03 Detect CTG file changes (+7 more)

### Community 9 - "File Stability Checker"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/local/dao/CameraMediaDao.kt, ast_hash, mtime, semantic_hash

### Community 10 - "Camera Unlock Orchestrator"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/local/dao/NasConnectionDao.kt, ast_hash, mtime, semantic_hash

### Community 11 - "Camera Media DAO"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/local/dao/SessionDao.kt, ast_hash, mtime, semantic_hash

### Community 12 - "Camera Media Repository"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/local/dao/TransferQueueDao.kt, ast_hash, mtime, semantic_hash

### Community 13 - "Connect Camera Screen"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/local/db/TransferInDatabase.kt, ast_hash, mtime, semantic_hash

### Community 14 - "Transfer Queue DAO"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/local/entity/CameraMediaItemEntity.kt, ast_hash, mtime, semantic_hash

### Community 15 - "SessionEntity"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/local/entity/NasConnectionEntity.kt, ast_hash, mtime, semantic_hash

### Community 16 - "CameraMediaRepositoryImpl"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/local/entity/SessionEntity.kt, ast_hash, mtime, semantic_hash

### Community 17 - "TransferQueueRepositoryImpl"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/local/entity/TransferQueueItemEntity.kt, ast_hash, mtime, semantic_hash

### Community 18 - "AutoStorageDetector"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/repository/CameraMediaRepositoryImpl.kt, ast_hash, mtime, semantic_hash

### Community 20 - "PhotoWatcher"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/repository/NasConnectionRepositoryImpl.kt, ast_hash, mtime, semantic_hash

### Community 21 - "TransferQueueItemEntity"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/repository/SessionRepositoryImpl.kt, ast_hash, mtime, semantic_hash

### Community 22 - "SessionRepositoryImpl"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/repository/TransferQueueRepositoryImpl.kt, ast_hash, mtime, semantic_hash

### Community 23 - "UsbPermissionManager"
Cohesion: 0.18
Nodes (10): AlreadyHasPermission, Denied, Granted, Intent, StateFlow, UsbDevice, UsbManager, PermissionResult (+2 more)

### Community 24 - "CameraDiscoveryEngine"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/sftp/JschSftpClient.kt, ast_hash, mtime, semantic_hash

### Community 25 - "CameraGalleryViewModel"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/sftp/SftpClient.kt, ast_hash, mtime, semantic_hash

### Community 26 - "UsbTetherService"
Cohesion: 0.18
Nodes (7): Intent, Job, UsbDevice, UsbTetherService, IBinder, Notification, Service

### Community 27 - "MediaStatus"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/storage/AutoStorageDetector.kt, ast_hash, mtime, semantic_hash

### Community 28 - "CtgWatcher"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/CameraDiscoveryEngine.kt, ast_hash, mtime, semantic_hash

### Community 29 - "MtpContentObserver"
Cohesion: 0.15
Nodes (8): ChangeEvent, Error, Flow, Uri, MtpContentObserver, NewFileDetected, SystemNotified, ContentObserver

### Community 30 - "CanonPtpEnumerator"
Cohesion: 0.29
Nodes (4): CanonPtpEnumerator, CanonPtpObjectInfo, ByteArray, ByteBuffer

### Community 31 - "SessionState"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/CameraInitContext.kt, ast_hash, mtime, semantic_hash

### Community 32 - "ConnectionMode"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/CameraInitResult.kt, ast_hash, mtime, semantic_hash

### Community 33 - "TransferItemStatus"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/CameraSessionInitializer.kt, ast_hash, mtime, semantic_hash

### Community 34 - ".create"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/CameraUnlockOrchestrator.kt, ast_hash, mtime, semantic_hash

### Community 35 - "TransferUploadWorker"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/CtgWatcher.kt, ast_hash, mtime, semantic_hash

### Community 36 - "WatcherStatus"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/DefaultCameraSessionInitializer.kt, ast_hash, mtime, semantic_hash

### Community 37 - "CameraRuntimeState"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/DocumentFileScanner.kt, ast_hash, mtime, semantic_hash

### Community 38 - "CtgWatcher"
Cohesion: 0.06
Nodes (22): CtgChangeEvent, CtgFileMeta, CtgWatcher, CtgWatcherState, Job, SharedFlow, StateFlow, CtgDatabase (+14 more)

### Community 39 - "UploadQueueManager"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/FileStabilityChecker.kt, ast_hash, mtime, semantic_hash

### Community 40 - "Codebase Map"
Cohesion: 0.04
Nodes (52): app/, app/schemas/com.reflection.transferin.data.local.db.TransferInDatabase/, app/src/androidTest/java/com/reflection/transferin/, app/src/androidTest/java/com/reflection/transferin/data/local/db/, app/src/main/, app/src/main/java/com/reflection/transferin/, app/src/main/java/com/reflection/transferin/data/local/dao/, app/src/main/java/com/reflection/transferin/data/local/db/ (+44 more)

### Community 41 - "ShootModeEngine"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/MtpContentObserver.kt, ast_hash, mtime, semantic_hash

### Community 42 - "CanonPtpTransport"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/PhotoWatcher.kt, ast_hash, mtime, semantic_hash

### Community 44 - "FileType"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/UsbHelper.kt, ast_hash, mtime, semantic_hash

### Community 45 - "SessionRepository"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/UsbPermissionManager.kt, ast_hash, mtime, semantic_hash

### Community 46 - "CanonCameraInitializer"
Cohesion: 0.07
Nodes (21): CameraInitContext, CameraInitResult, fail(), ok(), CameraSessionInitializer, MtpDevice, UsbDevice, UsbDeviceConnection (+13 more)

### Community 47 - "UploadedFingerprintDao"
Cohesion: 0.05
Nodes (10): Flow, UploadedFingerprintDao, UploadedFingerprintEntity, Flow, UploadedFingerprintRepositoryImpl, Flow, UploadedFingerprintRepository, FilterResult (+2 more)

### Community 48 - "gradlew"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/UsbSourceOrchestrator.kt, ast_hash, mtime, semantic_hash

### Community 49 - "ExampleInstrumentedTest"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/vendor/CameraVendorInitializer.kt, ast_hash, mtime, semantic_hash

### Community 50 - "ExampleUnitTest"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/vendor/CannonCameraInitializer.kt, ast_hash, mtime, semantic_hash

### Community 51 - "Exponential Backoff"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/vendor/CannonPtpEnumerator.kt, ast_hash, mtime, semantic_hash

### Community 52 - "reflection/transferin/feature/sessionsummary/SessionSummaryViewModel.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/vendor/CannonPtpSession.kt, ast_hash, mtime, semantic_hash

### Community 53 - "reflection/transferin/feature/sessionsummary/SummaryState.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/vendor/CannonPtpTransport.kt, ast_hash, mtime, semantic_hash

### Community 54 - "reflection/transferin/session/runtime/SessionDispatcher.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/usb/vendor/GenericCameraInitializer.kt, ast_hash, mtime, semantic_hash

### Community 55 - "reflection/transferin/session/runtime/SessionOrchestrator.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/worker/TransferUploadWorkScheduler.kt, ast_hash, mtime, semantic_hash

### Community 56 - "Stale Part File Cleanup"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/model/CameraMedia.kt, ast_hash, mtime, semantic_hash

### Community 58 - "reflection/transferin/domain/repository/TransferQueueRepository.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/model/CameraRuntimeState.kt, ast_hash, mtime, semantic_hash

### Community 59 - "reflection/transferin/domain/session/FileClassifier.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/model/ConnectionMode.kt, ast_hash, mtime, semantic_hash

### Community 60 - "reflection/transferin/ui/theme/Color.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/model/FileType.kt, ast_hash, mtime, semantic_hash

### Community 61 - "reflection/transferin/ui/theme/Type.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/model/MediaJob.kt, ast_hash, mtime, semantic_hash

### Community 62 - "build.gradle.kts"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/model/ProjectSession.kt, ast_hash, mtime, semantic_hash

### Community 65 - "settings.gradle.kts"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/model/SessionState.kt, ast_hash, mtime, semantic_hash

### Community 66 - "Phase 1: CTG Foundation - Research"
Cohesion: 0.04
Nodes (48): Applicable ASVS Categories, Architectural Responsibility Map, Architecture Patterns, Assumptions Log, Canon CTG File Format, Common Pitfalls, Core Technologies, Design Pattern (+40 more)

### Community 67 - "TransferSessionManagerImpl"
Cohesion: 0.07
Nodes (8): newDetected(), TransferQueueItem, Flow, TransferSessionManager, SessionRuntimeState, Flow, StateFlow, TransferSessionManagerImpl

### Community 68 - "GalleryViewModel"
Cohesion: 0.08
Nodes (27): FilterMode, ALL, FAILED, PHOTOS, UPLOADED, VIDEOS, GalleryItem, GalleryUiState (+19 more)

### Community 69 - "Phase 2: Smart Upload Detection - Research"
Cohesion: 0.05
Nodes (40): 10. Assumptions Log, 11. Open Questions, 12. References, 1. Executive Summary, 2.1 Composite Fingerprint Design, 2.2 Why Composite Over Hash?, 2.3 Handle Name Encoding Considerations, 2.4 Size Edge Cases (+32 more)

### Community 70 - "CameraUnlockOrchestrator"
Cohesion: 0.11
Nodes (15): CameraUnlockOrchestrator, CameraUnlockState, MtpDevice, StateFlow, UsbDevice, UsbDeviceConnection, UsbManager, UnlockStatus (+7 more)

### Community 71 - "CameraMediaRepository"
Cohesion: 0.08
Nodes (4): CameraMediaRepository, com, Flow, java

### Community 72 - "SessionViewModel"
Cohesion: 0.13
Nodes (8): SessionStats, SessionSummaryState, AndroidViewModel, java, Job, StateFlow, Uri, SessionViewModel

### Community 73 - "CameraMediaItemEntity"
Cohesion: 0.10
Nodes (4): CameraMediaDao, Flow, CameraMediaItemEntity, Flow

### Community 75 - "ConnectCameraViewModel"
Cohesion: 0.12
Nodes (5): ConnectCameraUiState, ConnectCameraViewModel, StateFlow, ViewModel, UsbDeviceInfo

### Community 76 - "TransferQueueDao"
Cohesion: 0.07
Nodes (3): Flow, TransferQueueDao, TransferQueueItemEntity

### Community 77 - "UsbHelper"
Cohesion: 0.14
Nodes (8): android, CameraSessionState, MtpDevice, StateFlow, UsbDeviceConnection, UsbManager, UsbHelper, kotlinx

### Community 79 - "AutoStorageDetector"
Cohesion: 0.15
Nodes (10): AutoStorageDetector, Uri, StorageLocation, StorageType, DCIM, DOWNLOAD, OTHER, PICTURES (+2 more)

### Community 80 - "SessionEntity"
Cohesion: 0.11
Nodes (3): Flow, SessionDao, SessionEntity

### Community 81 - "UsbDisconnectHandler"
Cohesion: 0.11
Nodes (13): ConnectionState, Disconnected, Error, Intent, Job, StateFlow, UsbDevice, SftpConnected (+5 more)

### Community 82 - "DelegatingSftpClient"
Cohesion: 0.21
Nodes (6): DelegatingSftpClient, bytesTransferred, Result, totalBytes, Uri, RemoteFolder

### Community 83 - "SelectFolderViewModel"
Cohesion: 0.12
Nodes (4): StateFlow, Uri, ViewModel, SelectFolderViewModel

### Community 85 - "TwoPhaseUploadHandlerTest"
Cohesion: 0.13
Nodes (7): Result, Uri, SizeMismatchException, TwoPhaseUploadHandler, com, Uri, TwoPhaseUploadHandlerTest

### Community 86 - "PhotoWatcher"
Cohesion: 0.17
Nodes (8): Job, SharedFlow, StateFlow, Uri, NewPhotoEvent, PhotoMeta, PhotoWatcher, PhotoWatcherState

### Community 87 - "ConnectionMode"
Cohesion: 0.13
Nodes (7): ConnectionMode, IMPORT_FALLBACK, TETHERED, Flow, ProjectSessionManager, Flow, ProjectSessionManagerImpl

### Community 88 - "CameraDiscoveryEngine"
Cohesion: 0.21
Nodes (4): CameraDiscoveryEngine, CtgState, DiscoveryResult, Uri

### Community 89 - "UsbSourceOrchestrator"
Cohesion: 0.21
Nodes (5): CameraPickItem, Uri, PollResult, SnapshotSignature, UsbSourceOrchestrator

### Community 90 - "Technical Approaches"
Cohesion: 0.11
Nodes (18): 1. JSch Session Pooling (RESILIENCE-01), 2. Keepalive Implementation (RESILIENCE-02), 3. Exponential Backoff Reconnection (RESILIENCE-03), 4. USB Disconnect Handling (RESILIENCE-04), 5. Thread Safety and Coroutines Integration, 6. Error Handling Patterns, Architecture Recommendations, File Structure (+10 more)

### Community 91 - "CameraRuntimeState"
Cohesion: 0.32
Nodes (15): ActionButtonsArea(), ActiveIndicator(), CameraStatusHeader(), Color, Modifier, MiniStatPill(), QuickStatsCard(), SessionActiveScreen() (+7 more)

### Community 92 - "Phase 03 Plan 04: USB Disconnect Handler - Summary"
Cohesion: 0.11
Nodes (17): Dependencies, Deviations from Plan, Files Created/Modified, Implementation Summary, Integration Points, Key Links, Known Stubs, Objective (+9 more)

### Community 93 - "MediaStatus"
Cohesion: 0.12
Nodes (7): MediaStatus, CACHED, DISCOVERED, FAILED, QUEUED, UPLOADED, UPLOADING

### Community 94 - "CameraMedia"
Cohesion: 0.12
Nodes (4): CameraMedia, CtgChangeEvent, NewShotDetector, ListCameraMediaUseCase

### Community 95 - "GoogleDriveClient"
Cohesion: 0.26
Nodes (6): GoogleDriveClient, bytesTransferred, Result, totalBytes, Uri, Exception

### Community 97 - "CameraGalleryViewModel"
Cohesion: 0.14
Nodes (10): CameraGalleryState, CameraGalleryViewModel, create(), AndroidViewModel, Job, StateFlow, CameraGalleryViewModelFactory, T (+2 more)

### Community 98 - "TransferIn"
Cohesion: 0.12
Nodes (15): Clean Architecture, MVVM Pattern, Project Structure, Core Value, Current Milestone: v2 Camera File Numbering & Traversal Alignment, Evolution, Existing Capabilities, Technology (+7 more)

### Community 99 - "Phase 1 Plan 01: CTG Foundation - Tracer (End-to-End CTG Parsing)"
Cohesion: 0.12
Nodes (16): Architecture, Automated Verification, Context, Objective, Output, Output, Phase 1 Plan 01: CTG Foundation - Tracer (End-to-End CTG Parsing), Purpose (+8 more)

### Community 100 - "Phase 1 Plan 02: State Differ and CTG Change Detection"
Cohesion: 0.12
Nodes (16): Automated Tests, Context, Dependencies, Integration Check, Objective, Output, Output, Phase 1 Plan 02: State Differ and CTG Change Detection (+8 more)

### Community 101 - "CanonCameraInitializer"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/model/SourceSelectionMode.kt, ast_hash, mtime, semantic_hash

### Community 102 - "TransferQueueItemEntity"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/model/TransferItemStatus.kt, ast_hash, mtime, semantic_hash

### Community 103 - "ProjectSession"
Cohesion: 0.09
Nodes (11): CameraRuntimeState, ProjectSession, CameraConnectionCoordinator, StateFlow, CameraEngine, Flow, StateFlow, ImportCameraEngine (+3 more)

### Community 104 - "Implementation Decisions"
Cohesion: 0.12
Nodes (15): Canonical References, Circuit Breaker Thresholds, Deferred Ideas, Established Patterns, Existing Code Insights, Health Monitor Check Interval, Idle Connection Eviction Policy, Implementation Decisions (+7 more)

### Community 105 - "SessionActiveScreen"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/model/TransferQueueItem.kt, ast_hash, mtime, semantic_hash

### Community 106 - "Phase 01: CTG Foundation Summary"
Cohesion: 0.13
Nodes (14): Accomplishments, Actuals (#2632), Coverage metadata, Decisions Made, Dependency graph, Deviations from Plan, Files Created/Modified, Issues Encountered (+6 more)

### Community 107 - "Plan: Phase 1 - SFTP Pooling & Reconnection Resilience"
Cohesion: 0.13
Nodes (14): Automated Tests, Manual Verification, [MODIFY] [JschSftpClient.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/sftp/JschSftpClient.kt), [MODIFY] [SessionActiveScreen.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionActiveScreen.kt), [MODIFY] [SessionUiState.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionUiState.kt), [MODIFY] [SessionViewModel.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionViewModel.kt), [MODIFY] [SftpSessionHealthMonitor.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/sftp/SftpSessionHealthMonitor.kt), [MODIFY] [SftpSessionPool.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/sftp/SftpSessionPool.kt) (+6 more)

### Community 108 - "Phase 02 Plan 01: Fingerprint Persistence Layer Summary"
Cohesion: 0.13
Nodes (14): Created, Decisions Made, Deviations from Plan, Files Created/Modified, Modified, Objective, Overview, Phase 02 Plan 01: Fingerprint Persistence Layer Summary (+6 more)

### Community 109 - "Phase 2 Plan 02: Smart Upload Detection Domain Layer Summary"
Cohesion: 0.13
Nodes (14): Artifacts Created, Completed Tasks, Deviations from Plan, Fingerprint.kt, Integration Points, NewShotDetector, One-liner, Phase 2 Plan 02: Smart Upload Detection Domain Layer Summary (+6 more)

### Community 110 - "Phase 03 Plan 01: SFTP Session Pool Infrastructure"
Cohesion: 0.13
Nodes (14): Completed Tasks, Deviations from Plan, Implementation Details, JschSftpClient Integration, Known Stubs, One-liner, Phase 03 Plan 01: SFTP Session Pool Infrastructure, Self-Check: PASSED (+6 more)

### Community 111 - "NasConnectionRepositoryImpl"
Cohesion: 0.22
Nodes (3): NasConnectionRepositoryImpl, NasConnection, NasConnectionRepository

### Community 112 - "SftpClient"
Cohesion: 0.18
Nodes (6): bytesTransferred, Result, totalBytes, Uri, SftpClient, TmpFileInfo

### Community 113 - "TransferInApp"
Cohesion: 0.19
Nodes (9): Modifier, SelectSourceFolderScreen(), Intent, MainActivity, PreviewFallbackApp(), TransferInApp(), TransferInPreview(), TransferInTheme() (+1 more)

### Community 114 - "Phase 1 Plan 2: State Differ and CTG Change Detection Summary"
Cohesion: 0.14
Nodes (13): Automated Checks, Completed Tasks, CtgStateDiffer (domain/shoot/CtgStateDiffer.kt), CtgWatcher Enhancements (data/usb/CtgWatcher.kt), Deliverables, Deviations from Plan, Next Steps, Objective (+5 more)

### Community 115 - "Phase 3, Plan 2: SFTP Session Health Monitoring"
Cohesion: 0.14
Nodes (13): Accomplishments, Actuals, Decisions Made, Dependency graph, Deviations from Plan, Files Created/Modified, Issues Encountered, Metrics (+5 more)

### Community 116 - "Phase 03 Plan 03: SFTP Session Reconnection Summary"
Cohesion: 0.14
Nodes (13): Completed Tasks, Dependencies Satisfied, Deviations from Plan, Files Created/Modified, Key Implementation Details, Next Steps, Objective, Phase 03 Plan 03: SFTP Session Reconnection Summary (+5 more)

### Community 117 - "CameraMedia"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/model/WatcherStatus.kt, ast_hash, mtime, semantic_hash

### Community 118 - "LoginNasViewModel"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/repository/CameraMediaRepository.kt, ast_hash, mtime, semantic_hash

### Community 119 - "SessionState"
Cohesion: 0.17
Nodes (9): SessionState, CANCELLED, COMPLETED, FAILED, IDLE, RUNNING, STARTING, STOPPING_INTAKE (+1 more)

### Community 120 - ".create"
Cohesion: 0.39
Nodes (4): FileStabilityChecker, Uri, UsbFileInfo, UsbFileInfoProvider

### Community 121 - "UploadQueueManager"
Cohesion: 0.28
Nodes (4): MediaJob, Uri, SourceMetadata, UploadQueueManager

### Community 122 - "Dokumentasi Teknis Project: Transfer In"
Cohesion: 0.14
Nodes (13): 1. Identitas & Tujuan Utama, 2. Struktur Paket (Package Structure) Terperinci, 3. Komponen Utama & Tanggung Jawab, 4. Alur Kerja & Koneksi Antar Bagian (Data Flow), 5. Skema Data (Persistence - Room), 6. Teknologi Utama, 7. Penanganan Resiliensi, A. Mesin Deteksi & Stabilitas (Data Layer - USB) (+5 more)

### Community 123 - "Goal Achievement"
Cohesion: 0.15
Nodes (12): Anti-Patterns Found, Behavioral Spot-Checks, Gaps Summary, Goal Achievement, Key Link Verification, Observable Truths, Phase 1: CTG Foundation Verification Report, Phase metadata (+4 more)

### Community 124 - "1. Technical Domain Analysis"
Cohesion: 0.15
Nodes (12): 1. Technical Domain Analysis, 2. Codebase Scouting & Integration Points, 3. Validation Architecture, A. SftpSessionPool Exposing Sessions, B. SftpSessionHealthMonitor Verification Loop, C. JschSftpClient Error Handling & Retry Integration, Circuit Breaker & Exponential Backoff, Health & Keepalive Telemetry (+4 more)

### Community 125 - "Phase 2: USB Debounce & Service Compliance - Context"
Cohesion: 0.15
Nodes (12): Android 14+ connectedDevice Compliance, Canonical References, Deferred Ideas, Established Patterns, Existing Code Insights, Implementation Decisions, Phase 2: USB Debounce & Service Compliance - Context, Phase Boundary (+4 more)

### Community 126 - "Phase 3: Multi-Vendor MTP Fallback - Context"
Cohesion: 0.15
Nodes (12): Canonical References, Deferred Ideas, Established Patterns, Existing Code Insights, Implementation Decisions, Non-Canon Fallback & Database Detection, Phase 3: Multi-Vendor MTP Fallback - Context, Phase Boundary (+4 more)

### Community 127 - "Phase 4: Resumable Transfers & Server Cleanup - Context"
Cohesion: 0.15
Nodes (12): Canonical References, Deferred Ideas, Established Patterns, Existing Code Insights, Implementation Decisions, Pembersihan Berkas Yatim Piatu (Orphaned File Cleanup & Age), Phase 4: Resumable Transfers & Server Cleanup - Context, Phase Boundary (+4 more)

### Community 128 - "Phase 4: Upload Integrity - Research"
Cohesion: 0.15
Nodes (12): Architectural Responsibility Map, Assumptions Log, Don't Hand-Roll, Environment Availability, Key Implementation Decisions for Planner, Metadata, Open Questions, Package Legitimacy Audit (+4 more)

### Community 129 - "Research: WebDAV Protocol Support & Starting Shared Folder"
Cohesion: 0.15
Nodes (12): 1. Starting Shared Folder for SFTP/WebDAV, 2. WebDAV Client Implementation, 3. UI and Database Integration, Automated Tests, Connection Setup Screen, Database Changes, Findings, Manual Verification (+4 more)

### Community 130 - ".requestPermission"
Cohesion: 0.18
Nodes (4): Intent, UsbDevice, UsbEndpoint, UsbInterface

### Community 132 - "TransferItemStatus"
Cohesion: 0.17
Nodes (11): TransferItemStatus, CANCELLED, DETECTED, DISPATCHED, FAILED, QUEUED, RETRY_SCHEDULED, SKIPPED_DUPLICATE (+3 more)

### Community 133 - "SessionRepositoryAdapter"
Cohesion: 0.12
Nodes (8): Flow, SessionRepositoryAdapter, ConnectCameraViewModelFactory, T, ViewModelProvider, T, ViewModelProvider, SelectFolderViewModelFactory

### Community 134 - "ImportCameraEngine"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/repository/NasConnectionRepository.kt, ast_hash, mtime, semantic_hash

### Community 135 - "LoginNasViewModel"
Cohesion: 0.17
Nodes (3): ViewModel, LoginNasUiState, LoginNasViewModel

### Community 136 - "rvmeur/transferin/feature/gallery/CameraGalleryScreen.kt"
Cohesion: 0.30
Nodes (11): CameraGalleryScreen(), EmptyState(), GalleryGrid(), GalleryHeader(), GalleryItem(), Modifier, LoadingState(), NewBadge() (+3 more)

### Community 137 - "Plan: Phase 2 - USB Debounce & Service Compliance"
Cohesion: 0.17
Nodes (11): Automated Tests, Manual Verification, [MODIFY] [MainActivity.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/MainActivity.kt), [MODIFY] [UsbDisconnectHandler.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/service/UsbDisconnectHandler.kt), [MODIFY] [UsbTetherService.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/service/UsbTetherService.kt), Plan: Phase 2 - USB Debounce & Service Compliance, Proposed Changes, Service Compliance & Permissions (+3 more)

### Community 138 - "Identified Gaps & Modifications"
Cohesion: 0.17
Nodes (11): 1. Technical Domain Analysis, 2. Codebase Scouting & Integration Points, 3. Validation Architecture, A. UsbDisconnectHandler Job Tracking, Android 14+ Foreground Service, B. MainActivity Permission Request, C. UsbTetherService Exception Handling, Identified Gaps & Modifications (+3 more)

### Community 139 - "2. Tethered Mode CTG Integration & Deduplication"
Cohesion: 0.17
Nodes (11): 1. NAS Read/Write Permission Validation, 2. Tethered Mode CTG Integration & Deduplication, Findings, Findings, Manual Verification, Optimizing `CtgWatcher`, Proposed Architecture, Research: CTG Deduplication & NAS Write Check (+3 more)

### Community 140 - "Phase 5: Room Migration Validation Suite - Context"
Cohesion: 0.17
Nodes (11): Canonical References, Deferred Ideas, Existing Code Insights, Implementation Decisions, Lokasi Penyimpanan Ekspor Schema (Schema Location), Phase 5: Room Migration Validation Suite - Context, Phase Boundary, Project Requirements & Roadmap (+3 more)

### Community 141 - "Milestone v1 Active Requirements"
Cohesion: 0.17
Nodes (11): 1. SFTP Connection Resilience (SFTP), 2. USB Device Stability (USB), 3. Service Compliance (SVC), 4. Large File Resumability (FILE), 5. Room Migration Verification (DB), Future Requirements, Milestone v1 Active Requirements, Out of Scope (+3 more)

### Community 142 - "Proposed Changes"
Cohesion: 0.17
Nodes (11): Automated Verification, Manual Verification, [MODIFY] [PhotoWatcher.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/usb/PhotoWatcher.kt), [MODIFY] [SessionViewModel.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionViewModel.kt), [MODIFY] [SftpConnectionConfig.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/sftp/SftpConnectionConfig.kt), PhotoWatcher, Plan - Phase 7: Robust RAW+JPG Co-existence & Verification, Proposed Changes (+3 more)

### Community 143 - "JschSftpClient"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/repository/SessionRepository.kt, ast_hash, mtime, semantic_hash

### Community 144 - "reflection/transferin/feature/gallery/CameraGalleryScreen.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/repository/SessionRepositoryAdapter.kt, ast_hash, mtime, semantic_hash

### Community 145 - "WatcherStatus"
Cohesion: 0.18
Nodes (9): WatcherStatus, ENQUEUEING, ERROR, IDLE, PAUSED, SCANNING, STARTING, STOPPED (+1 more)

### Community 146 - "TransferUploadWorker"
Cohesion: 0.23
Nodes (4): Result, Uri, TransferUploadWorker, CoroutineWorker

### Community 147 - "TransferInDatabase"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/repository/TransferQueueRepository.kt, ast_hash, mtime, semantic_hash

### Community 148 - "SftpClient"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/session/CameraConnectionCoordinator.kt, ast_hash, mtime, semantic_hash

### Community 149 - "DocumentFileScanner"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/session/CameraEngine.kt, ast_hash, mtime, semantic_hash

### Community 150 - "DocumentFileScanner"
Cohesion: 0.40
Nodes (5): DocumentFileScanner, Uri, NodeToScan, ScannedDocument, Bundle

### Community 152 - "ShootModeEngine"
Cohesion: 0.27
Nodes (3): Job, StateFlow, ShootModeEngine

### Community 153 - "Phase 3 Session Resilience"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/session/FileClassifier.kt, ast_hash, mtime, semantic_hash

### Community 154 - "1. Technical Domain Analysis"
Cohesion: 0.20
Nodes (9): 1. Technical Domain Analysis, 2. Codebase Scouting & Integration Points, A. CameraDiscoveryEngine Database File Recognition, B. CameraDiscoveryEngine Fallback Sync Logic, Identified Gaps & Modifications, Multi-Vendor Database Directory & File Patterns, Phase 3: Multi-Vendor MTP Fallback - Research, Reusable Assets (+1 more)

### Community 155 - "Phase 04 Plan 01: End-to-end two-phase upload tracer Summary"
Cohesion: 0.20
Nodes (9): Created, Files Created/Modified, Modified, Objective, Overview, Phase 04 Plan 01: End-to-end two-phase upload tracer Summary, Requirements Addressed, Tasks Completed (+1 more)

### Community 156 - "Assessment Scorecard (Scale 1-4)"
Cohesion: 0.20
Nodes (9): 1. Typography & Font Families, 2. Color Palette & Brand Consistency, 3. Spatial Rhythm & Layout, 4. Soft Depth & Elevation, 5. Responsive Layout, 6. Microinteractions & Touch Target States, Action Items, Assessment Scorecard (Scale 1-4) (+1 more)

### Community 157 - "UsbDeviceInfo"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/session/ImportCameraEngine.kt, ast_hash, mtime, semantic_hash

### Community 158 - "TransferInDatabase"
Cohesion: 0.20
Nodes (4): DatabaseProvider, TransferInDatabase, Context, RoomDatabase

### Community 160 - "Plan: Phase 3 - Multi-Vendor MTP Fallback"
Cohesion: 0.22
Nodes (8): Automated Tests, Database Detection, Manual Verification, [MODIFY] [CameraDiscoveryEngine.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/usb/CameraDiscoveryEngine.kt), Plan: Phase 3 - Multi-Vendor MTP Fallback, Proposed Changes, User Review Required, Verification Plan

### Community 161 - "Phase 03: Session Resilience - COMPLETE"
Cohesion: 0.22
Nodes (8): Architecture, Artifacts Created, Dependencies for Next Phase, Key Features Implemented, Phase 03: Session Resilience - COMPLETE, Plans Executed, Summary, Verification

### Community 162 - "Phase 04 Plan 02: Stale .part file cleanup Summary"
Cohesion: 0.22
Nodes (8): Files Created/Modified, Modified, Objective, Overview, Phase 04 Plan 02: Stale .part file cleanup Summary, Requirements Addressed, Tasks Completed, Verification Results

### Community 163 - "Plan - Phase 6: Camera File Number Baseline Extraction"
Cohesion: 0.22
Nodes (8): Manual Verification, [MODIFY] [PhotoWatcher.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/usb/PhotoWatcher.kt), [MODIFY] [SessionViewModel.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionViewModel.kt), PhotoWatcher, Plan - Phase 6: Camera File Number Baseline Extraction, Proposed Changes, Session Presentation, Verification Plan

### Community 164 - "Milestone v1: Rebranding & Google Drive Migration (Active)"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/session/ProjectSessionManager.kt, ast_hash, mtime, semantic_hash

### Community 165 - "STATE.md"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/session/ProjectSessionManagerImpl.kt, ast_hash, mtime, semantic_hash

### Community 166 - "NasConnectionDao"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/session/RemoteFolder.kt, ast_hash, mtime, semantic_hash

### Community 168 - ".transact"
Cohesion: 0.43
Nodes (4): CanonPtpTransport, ByteArray, IntArray, PtpResponse

### Community 169 - ".create"
Cohesion: 0.25
Nodes (5): Modifier, LoginNasScreen(), T, ViewModelProvider, LoginNasViewModelFactory

### Community 170 - "Technology Stack"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/session/TransferSessionManager.kt, ast_hash, mtime, semantic_hash

### Community 171 - "Phase 1: SFTP Pooling & Reconnection Resilience - Discussion Log"
Cohesion: 0.25
Nodes (7): Circuit Breaker Thresholds, Deferred Ideas, Health Monitor Check Interval, Idle Connection Eviction Policy, Phase 1: SFTP Pooling & Reconnection Resilience - Discussion Log, Reconnection UI/UX Behavior, the agent's Discretion

### Community 172 - "Architecture Patterns"
Cohesion: 0.25
Nodes (8): Architecture Patterns, Pattern 1: Two-Phase Commit, Pattern 2: Checkpoint Persistence, Pattern 3: Part File Cleanup, Pattern 4: JSch RESUME Mode, Recommended Project Structure, System Architecture Diagram, Two-Phase Upload Flow

### Community 173 - "Milestone v1 Phases"
Cohesion: 0.25
Nodes (7): Milestone v1 Phases, Phase 1: SFTP Pooling & Reconnection Resilience, Phase 2: USB Debounce & Service Compliance, Phase 3: Multi-Vendor MTP Fallback, Phase 4: Resumable Transfers & Server Cleanup, Phase 5: Room Migration Validation Suite, Roadmap

### Community 174 - "Test Cases"
Cohesion: 0.25
Nodes (7): Phase 3 UAT: Google Drive Integration & NAS Replacement, Summary, Test Case 1: Database Credentials Adaptor, Test Case 2: Google Drive API Client implementation, Test Case 3: Connection Setup and Folder Resolution, Test Case 4: Complete Compilation, Test Cases

### Community 175 - "Connection Layers"
Cohesion: 0.25
Nodes (7): Connection Layers, Manual Verification, [MODIFY] [CameraUnlockOrchestrator.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/usb/CameraUnlockOrchestrator.kt), [MODIFY] [UsbHelper.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/usb/UsbHelper.kt), Plan - Phase 8: Canon and Fuji Connection Polish, Proposed Changes, Verification Plan

### Community 176 - "Milestone v2 Active Requirements"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/session/UploadQueueManager.kt, ast_hash, mtime, semantic_hash

### Community 177 - "FileStabilityChecker"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/shoot/ShootModeEngine.kt, ast_hash, mtime, semantic_hash

### Community 178 - ".getAllObjectHandlesNoLock"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/usecase/ListCameraMediaUseCase.kt, ast_hash, mtime, semantic_hash

### Community 179 - "DiscoveredFile"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/auth/LoginNasScreen.kt, ast_hash, mtime, semantic_hash

### Community 180 - "SourceSelectionMode"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/auth/LoginNasViewModel.kt, ast_hash, mtime, semantic_hash

### Community 181 - "FileType"
Cohesion: 0.38
Nodes (6): FileType, JPEG, OTHER, RAW, fromFileName(), fromMimeType()

### Community 183 - "DiscoveredFile"
Cohesion: 0.52
Nodes (3): DiscoveredFile, Flow, SourceWatcher

### Community 184 - "ConnectCameraScreen"
Cohesion: 0.48
Nodes (6): CameraStatusCard(), ConnectCameraScreen(), ImportModeCard(), Modifier, TetheredReadyCard(), UsbPermissionCard()

### Community 185 - "Research: Architecture Integration"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/auth/LoginNasViewModelFactory.kt, ast_hash, mtime, semantic_hash

### Community 186 - "Layers"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/connectcamera/ConnectCameraScreen.kt, ast_hash, mtime, semantic_hash

### Community 187 - "Technical Concerns & Debt"
Cohesion: 0.29
Nodes (6): 1. Connection Stability & Pool Resource Leaks, 2. USB Camera Compatibility & Vendor Quirks, 3. Foreground Service & USB Detach Cycles, 4. Large Media Transfers, 5. Room Database Migrations, Technical Concerns & Debt

### Community 188 - "Coding Conventions"
Cohesion: 0.29
Nodes (6): 1. Asynchronous Programming & Concurrency, 2. Architecture & MVVM Pattern, 3. Database & Local Persistence, 4. Error Handling & Resilience, 5. Coding Style & Formatting, Coding Conventions

### Community 189 - "Testing Practices"
Cohesion: 0.29
Nodes (6): 1. Test Architecture, 2. Typical Testing Scenarios, 3. Commands, Instrumented Tests (`app/src/androidTest/`), Local Unit Tests (`app/src/test/`), Testing Practices

### Community 190 - "1. Automated Tests"
Cohesion: 0.29
Nodes (6): 1. Automated Tests, 2. Manual Verification, Connection Drop Recovery (Airplane Mode test), Instrumented/UI Tests, Phase 1: SFTP Pooling & Reconnection Resilience - Validation Strategy, Unit Tests

### Community 191 - "1. Automated Tests"
Cohesion: 0.29
Nodes (6): 1. Automated Tests, 2. Manual Verification, Instrumented/UI Tests, Phase 2: USB Debounce & Service Compliance - Validation Strategy, Rapid Plug-Unplug Cycle Test, Unit Tests

### Community 192 - "2. Manual Verification"
Cohesion: 0.29
Nodes (6): 1. Automated Tests, 2. Manual Verification, Phase 3: Multi-Vendor MTP Fallback - Validation Strategy, Size & Date Diff Fallback Test, Unit Tests, Vendor Database Detection Test

### Community 193 - "Test Cases"
Cohesion: 0.29
Nodes (6): Phase 1 UAT: Rebranding & Package Rename, Summary, Test Case 1: Package Directory Structure, Test Case 2: Codebase Package References, Test Case 3: Compilation and Build, Test Cases

### Community 194 - "Test Cases"
Cohesion: 0.29
Nodes (6): Phase 2 UAT: UI Style Refactoring (rvmeur.com Design), Summary, Test Case 1: Color Token Refactoring, Test Case 2: Typography Style & Custom Fonts, Test Case 3: Compilation Check, Test Cases

### Community 195 - "Phase 3 Research: Google Drive Integration & NAS Replacement"
Cohesion: 0.29
Nodes (6): 1. Google Drive OAuth2 flow:, 2. Google Drive Multipart Upload REST API:, Codebase Modification Impact, Context & Objective, Phase 3 Research: Google Drive Integration & NAS Replacement, Technical Design & API Specification

### Community 196 - "UploadStatus"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/connectcamera/ConnectCameraViewModel.kt, ast_hash, mtime, semantic_hash

### Community 197 - "UploadStatus"
Cohesion: 0.33
Nodes (5): UploadStatus, FAILED, QUEUED, SUCCESS, UPLOADING

### Community 198 - "SourceSelectionMode"
Cohesion: 0.33
Nodes (3): SourceSelectionMode, AUTO_DETECT, BROWSE_FOLDER

### Community 199 - "SessionSummaryScreen"
Cohesion: 0.60
Nodes (5): Color, Modifier, SessionSummaryScreen(), SummaryRow(), SummaryStatItem()

### Community 202 - "Integrations"
Cohesion: 0.33
Nodes (5): 1. SFTP Client (NAS Integration), 2. USB Camera Connectivity (MTP / PTP Protocol), 3. WebDAV Integration, 4. Local Storage / OTG Backup (Storage Access Framework), Integrations

### Community 203 - "Phase 1: SFTP Pooling & Reconnection Resilience - Summary"
Cohesion: 0.33
Nodes (5): 1. Deliverables Completed, 2. Bug Fixes, 3. Verification Results, Automated Tests, Phase 1: SFTP Pooling & Reconnection Resilience - Summary

### Community 204 - "Items Discussed"
Cohesion: 0.33
Nodes (5): 1. USB Event Debounce Strategy, 2. Android 14+ connectedDevice Compliance, Discussion Summary, Items Discussed, Phase 2: USB Debounce & Service Compliance - Discussion Log

### Community 205 - "Phase 2: USB Debounce & Service Compliance - Summary"
Cohesion: 0.33
Nodes (5): 1. Deliverables Completed, 2. Bug Fixes, 3. Verification Results, Automated Tests, Phase 2: USB Debounce & Service Compliance - Summary

### Community 206 - "Items Discussed"
Cohesion: 0.33
Nodes (5): 1. Fallback & Database Detection, 2. Polling & Sync Strategy, Discussion Summary, Items Discussed, Phase 3: Multi-Vendor MTP Fallback - Discussion Log

### Community 207 - "Items Discussed"
Cohesion: 0.33
Nodes (5): 1. Temporary Suffix Alignment, 2. Orphaned File Cleanup & Age, Discussion Summary, Items Discussed, Phase 4: Resumable Transfers & Server Cleanup - Discussion Log

### Community 208 - "Detailed Findings"
Cohesion: 0.33
Nodes (5): Detailed Findings, [Info] Minimum file size for checkpoints (UploadCheckpointManager.kt), Phase 04 Code Review: Upload Integrity & Checkpoints, Summary of Findings, [Warning] `runBlocking` in progress callback (TwoPhaseUploadHandler.kt)

### Community 209 - "Common Pitfalls"
Cohesion: 0.33
Nodes (6): Common Pitfalls, Pitfall 1: Race Condition on Atomic Rename, Pitfall 2: Checkpoint Drift, Pitfall 3: Orphaned .part Files, Pitfall 4: Stream Position Drift on Resume, Pitfall 5: Phase 3 Dependency Not Ready

### Community 210 - "Items Discussed"
Cohesion: 0.33
Nodes (5): 1. Schema Location & Integration, 2. Runtime Migration Registration, Discussion Summary, Items Discussed, Phase 5: Room Migration Validation Suite - Discussion Log

### Community 211 - "Onboarding Summary"
Cohesion: 0.33
Nodes (5): Codebase Context, Docs Context, Onboarding Summary, Project State, Recommended Next Step

### Community 212 - "Phase 6 Research - Camera File Number Baseline Extraction"
Cohesion: 0.33
Nodes (5): 1. Directory Traversal & Storage Structure, 2. Filename Number Extraction, 3. Reset Prevention Anchors, 4. DB Query for Highest File Number, Phase 6 Research - Camera File Number Baseline Extraction

### Community 213 - "Research: Target Features & Behaviors"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/connectcamera/ConnectCameraViewModelFactory.kt, ast_hash, mtime, semantic_hash

### Community 214 - "Key Findings"
Cohesion: 0.20
Nodes (9): 1. Connection & SFTP Resilience, 2. Multi-Vendor Camera & USB Support, 3. Foreground Service Lifecycle, 4. Large Media transfers & Checkpoints, 5. Migration Safety, Implications for Roadmap, Key Findings, Project Research Summary (+1 more)

### Community 215 - "DefaultCameraSessionInitializer"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/folder/FolderUiState.kt, ast_hash, mtime, semantic_hash

### Community 217 - "SelectFolderScreen"
Cohesion: 0.40
Nodes (3): FolderUiState, Modifier, SelectFolderScreen()

### Community 218 - "GalleryViewModelFactory"
Cohesion: 0.40
Nodes (3): GalleryViewModelFactory, T, ViewModelProvider

### Community 219 - "Phase 3: Multi-Vendor MTP Fallback - Summary"
Cohesion: 0.40
Nodes (4): 1. Deliverables Completed, 2. Verification Results, Automated Tests, Phase 3: Multi-Vendor MTP Fallback - Summary

### Community 220 - "Phase 4: Resumable Transfers & Server Cleanup - Summary"
Cohesion: 0.40
Nodes (4): 1. Deliverables Completed, 2. Verification Results, Automated Tests, Phase 4: Resumable Transfers & Server Cleanup - Summary

### Community 221 - "04-01-PLAN.md"
Cohesion: 0.40
Nodes (4): Artifacts this phase modifies, Artifacts this phase produces, STRIDE Threat Register, Trust Boundaries

### Community 222 - "04-02-PLAN.md"
Cohesion: 0.40
Nodes (4): Artifacts this phase modifies, Artifacts this phase produces, STRIDE Threat Register, Trust Boundaries

### Community 223 - "Phase 04 UI Review: Retroactive Visual Audit"
Cohesion: 0.40
Nodes (4): 6-Pillar Graded Assessment, Audit Scope, Phase 04 UI Review: Retroactive Visual Audit, Verdict

### Community 224 - "User Constraints (from CONTEXT.md)"
Cohesion: 0.40
Nodes (5): Deliverables, Dependencies, Phase Requirements, User Constraints (from CONTEXT.md), Verification Criteria

### Community 225 - "Validation Architecture"
Cohesion: 0.40
Nodes (5): Phase Requirements Test Map, Sampling Rate, Test Framework, Validation Architecture, Wave 0 Gaps

### Community 226 - "Phase 5: Room Migration Validation Suite - Summary"
Cohesion: 0.40
Nodes (4): 1. Deliverables Completed, 2. Verification Results, Instrumented Test Build Verification, Phase 5: Room Migration Validation Suite - Summary

### Community 227 - "Phase 1 Research: Rebranding & Package Rename"
Cohesion: 0.40
Nodes (4): Codebase Analysis & Occurrences of "Reflection", Context & Objective, Migration Strategy, Phase 1 Research: Rebranding & Package Rename

### Community 228 - "Phase 2 Research: UI Style Refactoring (rvmeur.com Design)"
Cohesion: 0.40
Nodes (4): Context & Objective, Extracted Design Tokens, Implementation Plan, Phase 2 Research: UI Style Refactoring (rvmeur.com Design)

### Community 230 - "SessionSummaryScreen"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/folder/SelectFolderScreen.kt, ast_hash, mtime, semantic_hash

### Community 231 - "rvmeur/transferin/session/worker/TransferUploadWorker.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/folder/SelectFolderViewModel.kt, ast_hash, mtime, semantic_hash

### Community 232 - "Milestones"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/folder/SelectFolderViewModelFactory.kt, ast_hash, mtime, semantic_hash

### Community 233 - "Code Examples"
Cohesion: 0.50
Nodes (4): Code Examples, Enhanced SftpClient Interface (add resumeUpload), TwoPhaseUploadHandler Implementation, UploadCheckpointManager Implementation

### Community 234 - "Sources"
Cohesion: 0.50
Nodes (4): Primary (HIGH confidence), Secondary (MEDIUM confidence), Sources, Tertiary (LOW confidence)

### Community 235 - "Phase 6 Context - Camera File Number Baseline Extraction"
Cohesion: 0.50
Nodes (3): Decisions, Open Questions, Phase 6 Context - Camera File Number Baseline Extraction

### Community 236 - "Phase 6 Summary - Camera File Number Baseline Extraction"
Cohesion: 0.50
Nodes (3): Accomplishments, Phase 6 Summary - Camera File Number Baseline Extraction, Verification Results

### Community 237 - "Phase 7 Context - Robust RAW+JPG Co-existence & Verification"
Cohesion: 0.50
Nodes (3): Decisions, Open Questions, Phase 7 Context - Robust RAW+JPG Co-existence & Verification

### Community 238 - "Phase 7 Research - Robust RAW+JPG Co-existence & Verification"
Cohesion: 0.50
Nodes (3): 1. Parallel Upload Feasibility & SFTP Pool Capacity, 2. Filename-Level Verification for Reconnection, Phase 7 Research - Robust RAW+JPG Co-existence & Verification

### Community 239 - "Phase 7 Summary - Robust RAW+JPG Co-existence & Verification"
Cohesion: 0.50
Nodes (3): Accomplishments, Phase 7 Summary - Robust RAW+JPG Co-existence & Verification, Verification Results

### Community 240 - "Phase 8 Context - Canon and Fuji Connection Polish"
Cohesion: 0.50
Nodes (3): Decisions, Open Questions, Phase 8 Context - Canon and Fuji Connection Polish

### Community 241 - "Phase 8 Research - Canon and Fuji Connection Polish"
Cohesion: 0.50
Nodes (3): 1. Close Session Order on Android MTP, 2. Syncing Override States to UsbHelper, Phase 8 Research - Canon and Fuji Connection Polish

### Community 242 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 248 - "Security Domain"
Cohesion: 0.67
Nodes (3): Applicable ASVS Categories, Known Threat Patterns for SFTP Uploads, Security Domain

### Community 249 - "Standard Stack"
Cohesion: 0.67
Nodes (3): Core, Phase-3 Dependencies (REQUIRED before Phase 4), Standard Stack

### Community 256 - "Context"
Cohesion: 0.67
Nodes (3): Context, Problem, Users

### Community 277 - "app/src/main/java/com/reflection/transferin/feature/folder/SelectSourceFolderScreen.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/folder/SelectSourceFolderScreen.kt, ast_hash, mtime, semantic_hash

### Community 278 - "app/src/main/java/com/reflection/transferin/feature/gallery/CameraGalleryScreen.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/gallery/CameraGalleryScreen.kt, ast_hash, mtime, semantic_hash

### Community 279 - "app/src/main/java/com/reflection/transferin/feature/gallery/CameraGalleryViewModel.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/gallery/CameraGalleryViewModel.kt, ast_hash, mtime, semantic_hash

### Community 280 - "app/src/main/java/com/reflection/transferin/feature/gallery/CameraGalleryViewModelFactory.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/gallery/CameraGalleryViewModelFactory.kt, ast_hash, mtime, semantic_hash

### Community 281 - "app/src/main/java/com/reflection/transferin/feature/gallery/GalleryUiState.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/gallery/GalleryUiState.kt, ast_hash, mtime, semantic_hash

### Community 282 - "app/src/main/java/com/reflection/transferin/feature/gallery/GalleryViewModel.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/gallery/GalleryViewModel.kt, ast_hash, mtime, semantic_hash

### Community 283 - "app/src/main/java/com/reflection/transferin/feature/gallery/GalleryViewModelFactory.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/gallery/GalleryViewModelFactory.kt, ast_hash, mtime, semantic_hash

### Community 284 - "app/src/main/java/com/reflection/transferin/feature/gallery/UploadedGalleryScreen.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/gallery/UploadedGalleryScreen.kt, ast_hash, mtime, semantic_hash

### Community 285 - "app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionActiveScreen.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionActiveScreen.kt, ast_hash, mtime, semantic_hash

### Community 286 - "app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionUiState.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionUiState.kt, ast_hash, mtime, semantic_hash

### Community 287 - "app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionViewModel.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionViewModel.kt, ast_hash, mtime, semantic_hash

### Community 288 - "app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionViewModelFactory.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionViewModelFactory.kt, ast_hash, mtime, semantic_hash

### Community 289 - "app/src/main/java/com/reflection/transferin/feature/sessionsummary/SessionSummaryScreen.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/sessionsummary/SessionSummaryScreen.kt, ast_hash, mtime, semantic_hash

### Community 290 - "app/src/main/java/com/reflection/transferin/feature/sessionsummary/SessionSummaryViewModel.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/sessionsummary/SessionSummaryViewModel.kt, ast_hash, mtime, semantic_hash

### Community 291 - "app/src/main/java/com/reflection/transferin/feature/sessionsummary/SummaryState.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/sessionsummary/SummaryState.kt, ast_hash, mtime, semantic_hash

### Community 292 - "app/src/main/java/com/reflection/transferin/MainActivity.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/MainActivity.kt, ast_hash, mtime, semantic_hash

### Community 293 - "app/src/main/java/com/reflection/transferin/service/UsbTetherService.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/service/UsbTetherService.kt, ast_hash, mtime, semantic_hash

### Community 294 - "app/src/main/java/com/reflection/transferin/session/model/SessionRuntimeState.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/session/model/SessionRuntimeState.kt, ast_hash, mtime, semantic_hash

### Community 295 - "app/src/main/java/com/reflection/transferin/session/runtime/SessionDispatcher.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/session/runtime/SessionDispatcher.kt, ast_hash, mtime, semantic_hash

### Community 296 - "app/src/main/java/com/reflection/transferin/session/runtime/SessionOrchestrator.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/session/runtime/SessionOrchestrator.kt, ast_hash, mtime, semantic_hash

### Community 297 - "app/src/main/java/com/reflection/transferin/session/runtime/TransferSessionManagerImpl.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/session/runtime/TransferSessionManagerImpl.kt, ast_hash, mtime, semantic_hash

### Community 298 - "app/src/main/java/com/reflection/transferin/session/worker/TransferUploadWorker.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/session/worker/TransferUploadWorker.kt, ast_hash, mtime, semantic_hash

### Community 299 - "app/src/main/java/com/reflection/transferin/ui/theme/Color.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/ui/theme/Color.kt, ast_hash, mtime, semantic_hash

### Community 300 - "app/src/main/java/com/reflection/transferin/ui/theme/Theme.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/ui/theme/Theme.kt, ast_hash, mtime, semantic_hash

### Community 301 - "app/src/main/java/com/reflection/transferin/ui/theme/Type.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/ui/theme/Type.kt, ast_hash, mtime, semantic_hash

### Community 302 - "app/src/test/java/com/reflection/transferin/ExampleUnitTest.kt"
Cohesion: 0.50
Nodes (4): app/src/test/java/com/reflection/transferin/ExampleUnitTest.kt, ast_hash, mtime, semantic_hash

### Community 303 - ".bg-shell/manifest.json"
Cohesion: 0.50
Nodes (4): .bg-shell/manifest.json, ast_hash, mtime, semantic_hash

### Community 304 - "build.gradle.kts"
Cohesion: 0.50
Nodes (4): build.gradle.kts, ast_hash, mtime, semantic_hash

### Community 305 - ".claude/settings.local.json"
Cohesion: 0.50
Nodes (4): .claude/settings.local.json, ast_hash, mtime, semantic_hash

### Community 306 - "gradlew"
Cohesion: 0.50
Nodes (4): gradlew, ast_hash, mtime, semantic_hash

### Community 307 - ".gsd/runtime/write-gate-state.json"
Cohesion: 0.50
Nodes (4): .gsd/runtime/write-gate-state.json, ast_hash, mtime, semantic_hash

### Community 308 - ".planning/codebase/ARCHITECTURE.md"
Cohesion: 0.50
Nodes (4): .planning/codebase/ARCHITECTURE.md, ast_hash, mtime, semantic_hash

### Community 309 - ".planning/codebase/CONCERNS.md"
Cohesion: 0.50
Nodes (4): .planning/codebase/CONCERNS.md, ast_hash, mtime, semantic_hash

### Community 310 - ".planning/codebase/CONVENTIONS.md"
Cohesion: 0.50
Nodes (4): .planning/codebase/CONVENTIONS.md, ast_hash, mtime, semantic_hash

### Community 311 - ".planning/codebase/INTEGRATIONS.md"
Cohesion: 0.50
Nodes (4): .planning/codebase/INTEGRATIONS.md, ast_hash, mtime, semantic_hash

### Community 312 - ".planning/codebase/STACK.md"
Cohesion: 0.50
Nodes (4): .planning/codebase/STACK.md, ast_hash, mtime, semantic_hash

### Community 313 - ".planning/codebase/STRUCTURE.md"
Cohesion: 0.50
Nodes (4): .planning/codebase/STRUCTURE.md, ast_hash, mtime, semantic_hash

### Community 314 - ".planning/codebase/TESTING.md"
Cohesion: 0.50
Nodes (4): .planning/codebase/TESTING.md, ast_hash, mtime, semantic_hash

### Community 315 - ".planning/MILESTONES.md"
Cohesion: 0.50
Nodes (4): .planning/MILESTONES.md, ast_hash, mtime, semantic_hash

### Community 316 - ".planning/onboarding/SUMMARY.md"
Cohesion: 0.50
Nodes (4): .planning/onboarding/SUMMARY.md, ast_hash, mtime, semantic_hash

### Community 317 - ".planning/phases/01-CTG-Foundation/01-01-PLAN.md"
Cohesion: 0.50
Nodes (4): .planning/phases/01-CTG-Foundation/01-01-PLAN.md, ast_hash, mtime, semantic_hash

### Community 318 - ".planning/phases/01-CTG-Foundation/01-02-PLAN.md"
Cohesion: 0.50
Nodes (4): .planning/phases/01-CTG-Foundation/01-02-PLAN.md, ast_hash, mtime, semantic_hash

### Community 319 - ".planning/PROJECT.md"
Cohesion: 0.50
Nodes (4): .planning/PROJECT.md, ast_hash, mtime, semantic_hash

### Community 320 - ".planning/REQUIREMENTS.md"
Cohesion: 0.50
Nodes (4): .planning/REQUIREMENTS.md, ast_hash, mtime, semantic_hash

### Community 321 - ".planning/ROADMAP.md"
Cohesion: 0.50
Nodes (4): .planning/ROADMAP.md, ast_hash, mtime, semantic_hash

### Community 322 - ".planning/STATE.md"
Cohesion: 0.50
Nodes (4): .planning/STATE.md, ast_hash, mtime, semantic_hash

### Community 323 - "settings.gradle.kts"
Cohesion: 0.50
Nodes (4): settings.gradle.kts, ast_hash, mtime, semantic_hash

### Community 324 - "app/client_secret_2_343575435197-q6tnos0o1cgt8c0ir8alne21gitioj7i.apps.googleusercontent.com.json"
Cohesion: 0.50
Nodes (4): app/client_secret_2_343575435197-q6tnos0o1cgt8c0ir8alne21gitioj7i.apps.googleusercontent.com.json, ast_hash, mtime, semantic_hash

### Community 325 - "app/schemas/com.rvmeur.transferin.data.local.db.TransferInDatabase/9.json"
Cohesion: 0.50
Nodes (4): app/schemas/com.rvmeur.transferin.data.local.db.TransferInDatabase/9.json, ast_hash, mtime, semantic_hash

### Community 326 - "app/src/androidTest/java/com/rvmeur/transferin/data/local/db/DatabaseMigrationTest.kt"
Cohesion: 0.50
Nodes (4): app/src/androidTest/java/com/rvmeur/transferin/data/local/db/DatabaseMigrationTest.kt, ast_hash, mtime, semantic_hash

### Community 327 - "app/src/androidTest/java/com/rvmeur/transferin/ExampleInstrumentedTest.kt"
Cohesion: 0.50
Nodes (4): app/src/androidTest/java/com/rvmeur/transferin/ExampleInstrumentedTest.kt, ast_hash, mtime, semantic_hash

### Community 328 - "app/src/google-services.json"
Cohesion: 0.50
Nodes (4): app/src/google-services.json, ast_hash, mtime, semantic_hash

### Community 329 - "app/src/main/java/com/rvmeur/transferin/data/local/dao/CameraMediaDao.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/local/dao/CameraMediaDao.kt, ast_hash, mtime, semantic_hash

### Community 330 - "app/src/main/java/com/rvmeur/transferin/data/local/dao/NasConnectionDao.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/local/dao/NasConnectionDao.kt, ast_hash, mtime, semantic_hash

### Community 331 - "app/src/main/java/com/rvmeur/transferin/data/local/dao/SessionDao.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/local/dao/SessionDao.kt, ast_hash, mtime, semantic_hash

### Community 332 - "app/src/main/java/com/rvmeur/transferin/data/local/dao/TransferQueueDao.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/local/dao/TransferQueueDao.kt, ast_hash, mtime, semantic_hash

### Community 333 - "app/src/main/java/com/rvmeur/transferin/data/local/dao/UploadedFingerprintDao.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/local/dao/UploadedFingerprintDao.kt, ast_hash, mtime, semantic_hash

### Community 334 - "app/src/main/java/com/rvmeur/transferin/data/local/db/DatabaseProvider.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/local/db/DatabaseProvider.kt, ast_hash, mtime, semantic_hash

### Community 335 - "app/src/main/java/com/rvmeur/transferin/data/local/db/Migration_8_9.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/local/db/Migration_8_9.kt, ast_hash, mtime, semantic_hash

### Community 336 - "app/src/main/java/com/rvmeur/transferin/data/local/db/TransferInDatabase.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/local/db/TransferInDatabase.kt, ast_hash, mtime, semantic_hash

### Community 337 - "app/src/main/java/com/rvmeur/transferin/data/local/entity/CameraMediaItemEntity.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/local/entity/CameraMediaItemEntity.kt, ast_hash, mtime, semantic_hash

### Community 338 - "app/src/main/java/com/rvmeur/transferin/data/local/entity/NasConnectionEntity.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/local/entity/NasConnectionEntity.kt, ast_hash, mtime, semantic_hash

### Community 339 - "app/src/main/java/com/rvmeur/transferin/data/local/entity/SessionEntity.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/local/entity/SessionEntity.kt, ast_hash, mtime, semantic_hash

### Community 340 - "app/src/main/java/com/rvmeur/transferin/data/local/entity/TransferQueueItemEntity.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/local/entity/TransferQueueItemEntity.kt, ast_hash, mtime, semantic_hash

### Community 341 - "app/src/main/java/com/rvmeur/transferin/data/local/entity/UploadedFingerprintEntity.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/local/entity/UploadedFingerprintEntity.kt, ast_hash, mtime, semantic_hash

### Community 342 - "app/src/main/java/com/rvmeur/transferin/data/repository/CameraMediaRepositoryImpl.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/repository/CameraMediaRepositoryImpl.kt, ast_hash, mtime, semantic_hash

### Community 343 - "app/src/main/java/com/rvmeur/transferin/data/repository/NasConnectionRepositoryImpl.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/repository/NasConnectionRepositoryImpl.kt, ast_hash, mtime, semantic_hash

### Community 344 - "app/src/main/java/com/rvmeur/transferin/data/repository/SessionRepositoryImpl.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/repository/SessionRepositoryImpl.kt, ast_hash, mtime, semantic_hash

### Community 345 - "app/src/main/java/com/rvmeur/transferin/data/repository/TransferQueueRepositoryImpl.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/repository/TransferQueueRepositoryImpl.kt, ast_hash, mtime, semantic_hash

### Community 346 - "app/src/main/java/com/rvmeur/transferin/data/repository/UploadedFingerprintRepositoryImpl.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/repository/UploadedFingerprintRepositoryImpl.kt, ast_hash, mtime, semantic_hash

### Community 347 - "app/src/main/java/com/rvmeur/transferin/data/sftp/DelegatingSftpClient.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/sftp/DelegatingSftpClient.kt, ast_hash, mtime, semantic_hash

### Community 348 - "app/src/main/java/com/rvmeur/transferin/data/sftp/GoogleDriveClient.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/sftp/GoogleDriveClient.kt, ast_hash, mtime, semantic_hash

### Community 349 - "app/src/main/java/com/rvmeur/transferin/data/sftp/SftpClient.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/sftp/SftpClient.kt, ast_hash, mtime, semantic_hash

### Community 350 - "app/src/main/java/com/rvmeur/transferin/data/sftp/SftpConnectionConfig.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/sftp/SftpConnectionConfig.kt, ast_hash, mtime, semantic_hash

### Community 351 - "app/src/main/java/com/rvmeur/transferin/data/sftp/TwoPhaseUploadHandler.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/sftp/TwoPhaseUploadHandler.kt, ast_hash, mtime, semantic_hash

### Community 352 - "app/src/main/java/com/rvmeur/transferin/data/sftp/UploadCheckpointManager.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/sftp/UploadCheckpointManager.kt, ast_hash, mtime, semantic_hash

### Community 353 - "app/src/main/java/com/rvmeur/transferin/data/storage/AutoStorageDetector.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/storage/AutoStorageDetector.kt, ast_hash, mtime, semantic_hash

### Community 354 - "app/src/main/java/com/rvmeur/transferin/data/usb/CameraDiscoveryEngine.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/CameraDiscoveryEngine.kt, ast_hash, mtime, semantic_hash

### Community 355 - "app/src/main/java/com/rvmeur/transferin/data/usb/CameraInitContext.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/CameraInitContext.kt, ast_hash, mtime, semantic_hash

### Community 356 - "app/src/main/java/com/rvmeur/transferin/data/usb/CameraInitResult.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/CameraInitResult.kt, ast_hash, mtime, semantic_hash

### Community 357 - "app/src/main/java/com/rvmeur/transferin/data/usb/CameraSessionInitializer.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/CameraSessionInitializer.kt, ast_hash, mtime, semantic_hash

### Community 358 - "app/src/main/java/com/rvmeur/transferin/data/usb/CameraUnlockOrchestrator.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/CameraUnlockOrchestrator.kt, ast_hash, mtime, semantic_hash

### Community 359 - "app/src/main/java/com/rvmeur/transferin/data/usb/CtgWatcher.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/CtgWatcher.kt, ast_hash, mtime, semantic_hash

### Community 360 - "app/src/main/java/com/rvmeur/transferin/data/usb/DefaultCameraSessionInitializer.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/DefaultCameraSessionInitializer.kt, ast_hash, mtime, semantic_hash

### Community 361 - "app/src/main/java/com/rvmeur/transferin/data/usb/DocumentFileScanner.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/DocumentFileScanner.kt, ast_hash, mtime, semantic_hash

### Community 362 - "app/src/main/java/com/rvmeur/transferin/data/usb/FileStabilityChecker.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/FileStabilityChecker.kt, ast_hash, mtime, semantic_hash

### Community 363 - "app/src/main/java/com/rvmeur/transferin/data/usb/MtpContentObserver.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/MtpContentObserver.kt, ast_hash, mtime, semantic_hash

### Community 364 - "app/src/main/java/com/rvmeur/transferin/data/usb/PhotoWatcher.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/PhotoWatcher.kt, ast_hash, mtime, semantic_hash

### Community 365 - "app/src/main/java/com/rvmeur/transferin/data/usb/UsbHelper.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/UsbHelper.kt, ast_hash, mtime, semantic_hash

### Community 366 - "app/src/main/java/com/rvmeur/transferin/data/usb/UsbPermissionManager.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/UsbPermissionManager.kt, ast_hash, mtime, semantic_hash

### Community 367 - "app/src/main/java/com/rvmeur/transferin/data/usb/UsbSourceOrchestrator.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/UsbSourceOrchestrator.kt, ast_hash, mtime, semantic_hash

### Community 368 - "app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CameraVendorInitializer.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CameraVendorInitializer.kt, ast_hash, mtime, semantic_hash

### Community 369 - "app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CannonCameraInitializer.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CannonCameraInitializer.kt, ast_hash, mtime, semantic_hash

### Community 370 - "app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CannonPtpEnumerator.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CannonPtpEnumerator.kt, ast_hash, mtime, semantic_hash

### Community 371 - "app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CannonPtpSession.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CannonPtpSession.kt, ast_hash, mtime, semantic_hash

### Community 372 - "app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CannonPtpTransport.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CannonPtpTransport.kt, ast_hash, mtime, semantic_hash

### Community 373 - "app/src/main/java/com/rvmeur/transferin/data/usb/vendor/GenericCameraInitializer.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/usb/vendor/GenericCameraInitializer.kt, ast_hash, mtime, semantic_hash

### Community 374 - "app/src/main/java/com/rvmeur/transferin/data/worker/TransferUploadWorkScheduler.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/data/worker/TransferUploadWorkScheduler.kt, ast_hash, mtime, semantic_hash

### Community 375 - "app/src/main/java/com/rvmeur/transferin/domain/model/CameraMedia.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/model/CameraMedia.kt, ast_hash, mtime, semantic_hash

### Community 376 - "app/src/main/java/com/rvmeur/transferin/domain/model/CameraRuntimeState.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/model/CameraRuntimeState.kt, ast_hash, mtime, semantic_hash

### Community 377 - "app/src/main/java/com/rvmeur/transferin/domain/model/ConnectionMode.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/model/ConnectionMode.kt, ast_hash, mtime, semantic_hash

### Community 378 - "app/src/main/java/com/rvmeur/transferin/domain/model/FileType.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/model/FileType.kt, ast_hash, mtime, semantic_hash

### Community 379 - "app/src/main/java/com/rvmeur/transferin/domain/model/MediaJob.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/model/MediaJob.kt, ast_hash, mtime, semantic_hash

### Community 380 - "app/src/main/java/com/rvmeur/transferin/domain/model/ProjectSession.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/model/ProjectSession.kt, ast_hash, mtime, semantic_hash

### Community 381 - "app/src/main/java/com/rvmeur/transferin/domain/model/SessionState.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/model/SessionState.kt, ast_hash, mtime, semantic_hash

### Community 382 - "app/src/main/java/com/rvmeur/transferin/domain/model/SourceSelectionMode.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/model/SourceSelectionMode.kt, ast_hash, mtime, semantic_hash

### Community 383 - "app/src/main/java/com/rvmeur/transferin/domain/model/TransferItemStatus.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/model/TransferItemStatus.kt, ast_hash, mtime, semantic_hash

### Community 384 - "app/src/main/java/com/rvmeur/transferin/domain/model/TransferQueueItem.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/model/TransferQueueItem.kt, ast_hash, mtime, semantic_hash

### Community 385 - "app/src/main/java/com/rvmeur/transferin/domain/model/WatcherStatus.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/model/WatcherStatus.kt, ast_hash, mtime, semantic_hash

### Community 386 - "app/src/main/java/com/rvmeur/transferin/domain/repository/CameraMediaRepository.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/repository/CameraMediaRepository.kt, ast_hash, mtime, semantic_hash

### Community 387 - "app/src/main/java/com/rvmeur/transferin/domain/repository/NasConnectionRepository.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/repository/NasConnectionRepository.kt, ast_hash, mtime, semantic_hash

### Community 388 - "app/src/main/java/com/rvmeur/transferin/domain/repository/SessionRepository.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/repository/SessionRepository.kt, ast_hash, mtime, semantic_hash

### Community 389 - "app/src/main/java/com/rvmeur/transferin/domain/repository/SessionRepositoryAdapter.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/repository/SessionRepositoryAdapter.kt, ast_hash, mtime, semantic_hash

### Community 390 - "app/src/main/java/com/rvmeur/transferin/domain/repository/TransferQueueRepository.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/repository/TransferQueueRepository.kt, ast_hash, mtime, semantic_hash

### Community 391 - "app/src/main/java/com/rvmeur/transferin/domain/repository/UploadedFingerprintRepository.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/repository/UploadedFingerprintRepository.kt, ast_hash, mtime, semantic_hash

### Community 392 - "app/src/main/java/com/rvmeur/transferin/domain/session/CameraConnectionCoordinator.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/session/CameraConnectionCoordinator.kt, ast_hash, mtime, semantic_hash

### Community 393 - "app/src/main/java/com/rvmeur/transferin/domain/session/CameraEngine.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/session/CameraEngine.kt, ast_hash, mtime, semantic_hash

### Community 394 - "app/src/main/java/com/rvmeur/transferin/domain/session/FileClassifier.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/session/FileClassifier.kt, ast_hash, mtime, semantic_hash

### Community 395 - "app/src/main/java/com/rvmeur/transferin/domain/session/ProjectSessionManager.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/session/ProjectSessionManager.kt, ast_hash, mtime, semantic_hash

### Community 396 - "app/src/main/java/com/rvmeur/transferin/domain/session/ProjectSessionManagerImpl.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/session/ProjectSessionManagerImpl.kt, ast_hash, mtime, semantic_hash

### Community 397 - "app/src/main/java/com/rvmeur/transferin/domain/session/RemoteFolder.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/session/RemoteFolder.kt, ast_hash, mtime, semantic_hash

### Community 398 - "app/src/main/java/com/rvmeur/transferin/domain/session/SourceWatcher.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/session/SourceWatcher.kt, ast_hash, mtime, semantic_hash

### Community 399 - "app/src/main/java/com/rvmeur/transferin/domain/session/TransferSessionManager.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/session/TransferSessionManager.kt, ast_hash, mtime, semantic_hash

### Community 400 - "app/src/main/java/com/rvmeur/transferin/domain/session/UploadQueueManager.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/session/UploadQueueManager.kt, ast_hash, mtime, semantic_hash

### Community 401 - "app/src/main/java/com/rvmeur/transferin/domain/shoot/CtgDatabaseParser.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/shoot/CtgDatabaseParser.kt, ast_hash, mtime, semantic_hash

### Community 402 - "app/src/main/java/com/rvmeur/transferin/domain/shoot/CtgMediaEntry.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/shoot/CtgMediaEntry.kt, ast_hash, mtime, semantic_hash

### Community 403 - "app/src/main/java/com/rvmeur/transferin/domain/shoot/CtgMediaSnapshot.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/shoot/CtgMediaSnapshot.kt, ast_hash, mtime, semantic_hash

### Community 404 - "app/src/main/java/com/rvmeur/transferin/domain/shoot/CtgStateDiffer.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/shoot/CtgStateDiffer.kt, ast_hash, mtime, semantic_hash

### Community 405 - "app/src/main/java/com/rvmeur/transferin/domain/shoot/Fingerprint.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/shoot/Fingerprint.kt, ast_hash, mtime, semantic_hash

### Community 406 - "app/src/main/java/com/rvmeur/transferin/domain/shoot/NewShotDetector.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/shoot/NewShotDetector.kt, ast_hash, mtime, semantic_hash

### Community 407 - "app/src/main/java/com/rvmeur/transferin/domain/shoot/ShootModeEngine.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/shoot/ShootModeEngine.kt, ast_hash, mtime, semantic_hash

### Community 408 - "app/src/main/java/com/rvmeur/transferin/domain/shoot/SmartUploadFilter.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/shoot/SmartUploadFilter.kt, ast_hash, mtime, semantic_hash

### Community 409 - "app/src/main/java/com/rvmeur/transferin/domain/usecase/ListCameraMediaUseCase.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/usecase/ListCameraMediaUseCase.kt, ast_hash, mtime, semantic_hash

### Community 410 - "app/src/main/java/com/rvmeur/transferin/feature/auth/LoginNasScreen.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/feature/auth/LoginNasScreen.kt, ast_hash, mtime, semantic_hash

### Community 411 - "app/src/main/java/com/rvmeur/transferin/feature/auth/LoginNasViewModel.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/feature/auth/LoginNasViewModel.kt, ast_hash, mtime, semantic_hash

### Community 412 - "app/src/main/java/com/rvmeur/transferin/feature/auth/LoginNasViewModelFactory.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/feature/auth/LoginNasViewModelFactory.kt, ast_hash, mtime, semantic_hash

### Community 413 - "app/src/main/java/com/rvmeur/transferin/feature/connectcamera/ConnectCameraScreen.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/feature/connectcamera/ConnectCameraScreen.kt, ast_hash, mtime, semantic_hash

### Community 414 - "app/src/main/java/com/rvmeur/transferin/feature/connectcamera/ConnectCameraViewModel.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/feature/connectcamera/ConnectCameraViewModel.kt, ast_hash, mtime, semantic_hash

### Community 415 - "app/src/main/java/com/rvmeur/transferin/feature/connectcamera/ConnectCameraViewModelFactory.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/feature/connectcamera/ConnectCameraViewModelFactory.kt, ast_hash, mtime, semantic_hash

### Community 416 - "app/src/main/java/com/rvmeur/transferin/feature/folder/FolderUiState.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/feature/folder/FolderUiState.kt, ast_hash, mtime, semantic_hash

### Community 417 - "app/src/main/java/com/rvmeur/transferin/feature/folder/SelectFolderScreen.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/feature/folder/SelectFolderScreen.kt, ast_hash, mtime, semantic_hash

### Community 418 - "app/src/main/java/com/rvmeur/transferin/MainActivity.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/MainActivity.kt, ast_hash, mtime, semantic_hash

### Community 419 - ".bg-shell/manifest.json"
Cohesion: 0.50
Nodes (4): .bg-shell/manifest.json, ast_hash, mtime, semantic_hash

### Community 420 - "build.gradle.kts"
Cohesion: 0.50
Nodes (4): build.gradle.kts, ast_hash, mtime, semantic_hash

### Community 421 - ".claude/settings.local.json"
Cohesion: 0.50
Nodes (4): .claude/settings.local.json, ast_hash, mtime, semantic_hash

### Community 422 - "DOCUMENTATION.md"
Cohesion: 0.50
Nodes (4): DOCUMENTATION.md, ast_hash, mtime, semantic_hash

### Community 423 - "gradlew"
Cohesion: 0.50
Nodes (4): gradlew, ast_hash, mtime, semantic_hash

### Community 424 - ".gsd/graphs/graph.json"
Cohesion: 0.50
Nodes (4): .gsd/graphs/graph.json, ast_hash, mtime, semantic_hash

### Community 425 - ".gsd/runtime/write-gate-state.json"
Cohesion: 0.50
Nodes (4): .gsd/runtime/write-gate-state.json, ast_hash, mtime, semantic_hash

### Community 426 - ".planning/codebase/ARCHITECTURE.md"
Cohesion: 0.50
Nodes (4): .planning/codebase/ARCHITECTURE.md, ast_hash, mtime, semantic_hash

### Community 427 - ".planning/codebase/CONCERNS.md"
Cohesion: 0.50
Nodes (4): .planning/codebase/CONCERNS.md, ast_hash, mtime, semantic_hash

### Community 428 - ".planning/codebase/CONVENTIONS.md"
Cohesion: 0.50
Nodes (4): .planning/codebase/CONVENTIONS.md, ast_hash, mtime, semantic_hash

### Community 429 - ".planning/codebase/INTEGRATIONS.md"
Cohesion: 0.50
Nodes (4): .planning/codebase/INTEGRATIONS.md, ast_hash, mtime, semantic_hash

### Community 430 - ".planning/codebase/STACK.md"
Cohesion: 0.50
Nodes (4): .planning/codebase/STACK.md, ast_hash, mtime, semantic_hash

### Community 431 - ".planning/codebase/STRUCTURE.md"
Cohesion: 0.50
Nodes (4): .planning/codebase/STRUCTURE.md, ast_hash, mtime, semantic_hash

### Community 432 - ".planning/codebase/TESTING.md"
Cohesion: 0.50
Nodes (4): .planning/codebase/TESTING.md, ast_hash, mtime, semantic_hash

### Community 433 - ".planning/config.json"
Cohesion: 0.50
Nodes (4): .planning/config.json, ast_hash, mtime, semantic_hash

### Community 434 - ".planning/MILESTONES.md"
Cohesion: 0.50
Nodes (4): .planning/MILESTONES.md, ast_hash, mtime, semantic_hash

### Community 435 - ".planning/onboarding/SUMMARY.md"
Cohesion: 0.50
Nodes (4): .planning/onboarding/SUMMARY.md, ast_hash, mtime, semantic_hash

### Community 436 - ".planning/PROJECT.md"
Cohesion: 0.50
Nodes (4): .planning/PROJECT.md, ast_hash, mtime, semantic_hash

### Community 437 - ".planning/REQUIREMENTS.md"
Cohesion: 0.50
Nodes (4): .planning/REQUIREMENTS.md, ast_hash, mtime, semantic_hash

### Community 438 - ".planning/research/ARCHITECTURE.md"
Cohesion: 0.50
Nodes (4): .planning/research/ARCHITECTURE.md, ast_hash, mtime, semantic_hash

### Community 439 - ".planning/research/FEATURES.md"
Cohesion: 0.50
Nodes (4): .planning/research/FEATURES.md, ast_hash, mtime, semantic_hash

### Community 440 - ".planning/research/PITFALLS.md"
Cohesion: 0.50
Nodes (4): .planning/research/PITFALLS.md, ast_hash, mtime, semantic_hash

### Community 441 - ".planning/research/STACK.md"
Cohesion: 0.50
Nodes (4): .planning/research/STACK.md, ast_hash, mtime, semantic_hash

### Community 442 - ".planning/research/SUMMARY.md"
Cohesion: 0.50
Nodes (4): .planning/research/SUMMARY.md, ast_hash, mtime, semantic_hash

### Community 443 - ".planning/ROADMAP.md"
Cohesion: 0.50
Nodes (4): .planning/ROADMAP.md, ast_hash, mtime, semantic_hash

### Community 444 - ".planning/STATE.md"
Cohesion: 0.50
Nodes (4): .planning/STATE.md, ast_hash, mtime, semantic_hash

### Community 445 - "settings.gradle.kts"
Cohesion: 0.50
Nodes (4): settings.gradle.kts, ast_hash, mtime, semantic_hash

## Knowledge Gaps
- **1722 isolated node(s):** `mtime`, `ast_hash`, `semantic_hash`, `mtime`, `ast_hash` (+1717 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **38 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `UsbHelper` connect `UsbHelper` to `CameraGalleryViewModel`, `.requestPermission`, `.getAllObjectHandlesNoLock`, `Document Scanner`, `CanonCameraInitializer`, `TransferInApp`, `.buildMonitorState`, `UsbTetherService`, `CameraMedia`?**
  _High betweenness centrality (0.030) - this node is a cross-community bridge._
- **Why does `TransferInApp()` connect `TransferInApp` to `SessionRepositoryAdapter`, `Document Scanner`, `rvmeur/transferin/feature/gallery/CameraGalleryScreen.kt`, `DocumentFileScanner`, `.create`, `DiscoveredFile`, `ConnectCameraScreen`, `GalleryViewModel`, `SessionSummaryScreen`, `UsbHelper`, `DelegatingSftpClient`, `SelectFolderScreen`, `UsbSourceOrchestrator`, `GalleryViewModelFactory`, `CameraRuntimeState`, `CameraGalleryViewModel`, `ProjectSession`, `NasConnectionRepositoryImpl`, `.create`?**
  _High betweenness centrality (0.021) - this node is a cross-community bridge._
- **Why does `SessionRepositoryImpl` connect `SessionRepositoryImpl` to `SessionRepositoryAdapter`, `Document Scanner`, `SessionEntity`, `WatcherStatus`, `ConnectionMode`, `SessionState`?**
  _High betweenness centrality (0.017) - this node is a cross-community bridge._
- **What connects `mtime`, `ast_hash`, `semantic_hash` to the rest of the system?**
  _1722 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `USB Camera & MTP` be split into smaller, more focused modules?**
  _Cohesion score 0.010309278350515464 - nodes in this community are weakly interconnected._
- **Should `SFTP & Remote Operations` be split into smaller, more focused modules?**
  _Cohesion score 0.045454545454545456 - nodes in this community are weakly interconnected._
- **Should `CtgWatcher` be split into smaller, more focused modules?**
  _Cohesion score 0.05961538461538462 - nodes in this community are weakly interconnected._