# Graph Report - TransferInFuji  (2026-10-01)

## Corpus Check
- 907 files · ~6,033,002 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 7178 nodes · 8990 edges · 724 communities (619 shown, 105 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 200 edges (avg confidence: 0.78)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `42d51de4`
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
- .getAllObjectHandlesNoLock
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
- RecorderProcessor
- RecorderProcessor
- R
- KineticButton
- TransitCacheManager
- UsbHardwareSession
- split_configs
- 3. Accessibility & Quality Verification Checklist (WIZARD-01..04)
- 3. Accessibility & Quality Verification Checklist (WIZARD-01..04)
- Deep-Dive Technical Audit
- NikonStorageMapper
- SonyPtpDefragmenterTest
- CullingDecisionGateTest
- CanonPtpAdapterTest
- FujiTetherAdapter
- ExposureScratchBuffer
- AAS Code Review Report: Phase 04 — Zero-Leak Hardware Core & Resource Lifecycle
- CanonCameraInitializer
- NikonRetryPolicy
- FujiTetherAdapterTest
- SonyPtpAdapterTest
- _typeof
- Detailed Task Breakdown
- 4. Work Breakdown Structure & Task List
- 4. Work Breakdown Structure & Atomic Tasks
- CameraSyncStatus
- FujiPtpConstants
- .a
- AAS Code Review Report: Phase 05 — Fujifilm Native Wired Tether Engine & Clean Disconnect Lifecycle
- AAS Code Review Report: Phase 06 — Canon EOS Direct PTP Engine & Fast Preview Extraction
- AAS Code Review Report: Phase 07 — Sony Alpha & Nikon Multi-Vendor Tether Adapters
- Phase 8: 5-Stage Camera-to-Cloud Sync & Direct USB Streaming (PLAN.md)
- Phase 9: Production Hardening, Codebase Pruning & Comprehensive Audits (PLAN.md)
- DefaultCameraSessionInitializer
- CameraTetherAdapterFactoryTest
- NikonPreviewParser
- NEDialog
- enum
- enum
- Phase 3 Visual UI Specification Contract — Live Shoot Dashboard & Gallery Polish
- Phase 3 Visual UI Specification Contract — Live Shoot Dashboard & Gallery Polish
- Detailed Task Breakdown
- v3.0 Requirements
- CameraPictureProfile
- NikonPtpAdapterTest
- ExposureAnalyzerTest
- _slicedToArray
- .sendEvent
- _slicedToArray
- .sendEvent
- 3. Core Component Specifications
- 3. Core Component Specifications
- Phase 4 Verification Report: Zero-Leak Hardware Core & Resource Lifecycle
- Detailed Task Breakdown
- Phase 11: Persistent Multi-Vendor Thumbnail Pipeline & USB Synchronization (11-01-PLAN.md)
- .executePtpCommand
- NikonPtpTransport
- DoppelrandCard
- ExposureSyntheticFixtures
- Phase 10: Zero-Allocation Exposure & Luminance Engine (10-01-PLAN.md)
- Phase 13 Security & Threat Mitigation Review: Milestone v4.0 Hardening Audit
- Pillar-by-Pillar Detailed Audit
- .verifyAndEnforceSafety
- LuminanceHistogram
- UsbHardwareSessionTest
- RafPreviewExtractorTest
- _typeof
- Proposed Changes
- CameraTetherEvent
- NikonCameraInitializer
- SonyCameraInitializer
- SonyPtpTransport
- ExposureClassification
- SmartUploadFilter
- ThumbnailStorageManagerTest
- apk_unpacked/schema/draft-04.json
- unknown/schema/draft-04.json
- extracted/manifest.json
- 2. Technical Component Implementation Patterns
- 2. Technical Component Implementation Patterns
- UploadedFingerprintRepositoryImpl
- UploadedFingerprintRepository
- SyncOrchestratorTest
- FluidWizardScreen
- Cr3PreviewParserTest
- Forensic Report: Android Google OAuth Redirection & Client Type Misconfiguration
- 1. Accomplished Features
- 1. Accomplished Features
- 2. Technical Implementation & Compose Patterns
- 2. Technical Implementation & Compose Patterns
- Assessment Scorecard (Scale 1-4)
- Implementation Decisions Locked
- Implementation Decisions Locked
- Implementation Decisions Locked
- 3. Component Design Specifications
- Implementation Decisions Locked
- 2. Work Packages & Plan Waves
- 2. Key Accomplishments
- .streamWithFallback
- CameraTetherAdapter
- TetherState
- DuplicateVerdict
- CanonCameraInitializerTest
- InstrumentationProxy.java
- definitions
- definitions
- 2. Root Cause Analysis: 3 Masalah Kritis TransferInFuji
- Locked Design Decisions
- Locked Design Decisions
- Tasks
- Tasks
- Locked Design Decisions
- Locked Design Decisions
- Tasks
- Tasks
- Tasks
- 1. Existing Session & Gallery Codebase Analysis
- Tasks
- 1. Existing Session & Gallery Codebase Analysis
- Plan - Phase 6: Camera File Number Baseline Extraction
- Work Accomplished
- Security Audit & OAuth Session Governance — TransferInFuji
- .parseIfdForOrientation
- CameraVendor
- CullingPolicy
- SyncTier
- .processFrame
- CameraSyncDuplicateDetectorTest
- .attachBaseContext
- .a
- d
- Field
- DebugProbesKt
- apk_unpacked/assets/flutter_assets/packages/flutter_image_compress_web/assets/pica.min.js
- WhiteScreenCollector
- apktool_out/assets/flutter_assets/packages/flutter_image_compress_web/assets/pica.min.js
- WhiteScreenCollector
- Utils
- 1. Primary Findings & Anomaly Detection
- Test Cases
- 1. Accomplished Features
- 1. Accomplished Features
- Connection Layers
- Phase 5 Verification Report: Fujifilm Native Wired Tether Engine
- Phase 6 Verification Report: Canon EOS Direct PTP & Fast Preview Engine
- Phase 7 Verification Report: Sony Alpha & Nikon Tether Adapters
- Phase 9 Code Review: Production Hardening, Codebase Pruning & Memory Safety Audit
- Phase 9 UI & Design System Audit: Quiet Luxury Visual & Performance Verification
- Work Accomplished
- Work Accomplished
- additionalProperties
- .a
- o
- Phases Shipped
- Test Cases
- Test Cases
- Phase 3 Research: Google Drive Integration & NAS Replacement
- Phase 04 Summary: Zero-Leak Hardware Core & Resource Lifecycle
- Phase 5 Summary: Fujifilm Native Wired Tether Engine & Clean Disconnect Lifecycle
- Quality Gates Passed
- SEED-001: On-Device AI Exposure & Automated Culling Engine
- Subagent Summoning & Spawning Protocol (No Generic 'self')
- UploadedFingerprintEntity
- .initialize
- ThumbnailImageLoaderProvider
- CompoundFingerprintGeneratorTest
- DualTierSyncRouterTest
- stringArray
- stringArray
- a
- c
- .getAppCtx
- e
- f
- g
- Completed Phases
- Phase 6 Research - Camera File Number Baseline Extraction
- Phase 8: 5-Stage Camera-to-Cloud Sync & Direct USB Streaming (SUMMARY.md)
- Phase 9 Security & Privacy Audit: Android 14/15 Security & Credential Protection
- TmpFileInfo
- .classify
- thumbnailCompoundKey
- b
- Project Handoff: Milestone v3.0 Shipped
- schemaArray
- dependencies
- schemaArray
- d
- Phase 1 Research: Rebranding & Package Rename
- Phase 2 Research: UI Style Refactoring (rvmeur.com Design)
- Milestone v3.0 Audit: Multi-Vendor Production Engine & Zero-Leak Tether Core
- Phase 6 Summary: Canon EOS Direct PTP Engine & Fast Preview Extraction
- Phase 7 Summary: Sony Alpha & Nikon Multi-Vendor Tether Adapters
- EmbeddedPreviewResult
- CompoundFingerprintGenerator
- ThumbnailImageLoaderTest
- enum
- multipleOf
- getPathByURL
- enum
- multipleOf
- h.java
- Session Handoff: Tethered Fuji RAF Gallery Thumbnailing & Live Sync
- app/src/main/java/com/reflection/transferin/data/local/db/DatabaseProvider.kt
- app/src/main/java/com/reflection/transferin/domain/session/SourceWatcher.kt
- app/schemas/com.rvmeur.transferin.data.local.db.TransferInDatabase/8.json
- app/src/main/java/com/rvmeur/transferin/domain/session/ImportCameraEngine.kt
- Phase 1 Summary — Design System & Motion Foundations
- Phase 1 Summary — Design System & Motion Foundations
- Phase 6 Context - Camera File Number Baseline Extraction
- Phase 6 Summary - Camera File Number Baseline Extraction
- Phase 7 Context - Robust RAW+JPG Co-existence & Verification
- Phase 7 Research - Robust RAW+JPG Co-existence & Verification
- Phase 7 Summary - Robust RAW+JPG Co-existence & Verification
- Phase 8 Context - Canon and Fuji Connection Polish
- Phase 8 Research - Canon and Fuji Connection Polish
- Phase 4: Zero-Leak Hardware Core & Resource Lifecycle - Context
- Phase 8 Context: 5-Stage Camera-to-Cloud Sync & Direct USB Streaming
- Phase 9 Context: Production Hardening, Codebase Pruning & Comprehensive Audits
- Phase 9: Production Hardening, Codebase Pruning & Comprehensive Audits (SUMMARY.md)
- Phase 14 Verification: Universal PTP Transport, Storage & AI Culling Pipeline
- Milestone v4.0 Audit: On-Device AI Exposure Analysis, Smart Culling & Reliable Thumbnailing Engine
- addErrorHandlerIfEventEmitter
- additionalItems
- exclusiveMaximum
- exclusiveMinimum
- items
- pattern
- uniqueItems
- getType
- additionalItems
- exclusiveMaximum
- exclusiveMinimum
- items
- pattern
- uniqueItems
- Entry
- p
- Requirements — Milestone v1.0 (Rebranding & Google Drive Migration)
- Milestone v2.0 Requirements (Archived)
- Phase 1 Plan: Rebranding & Package Rename
- Phase 2 Plan: UI Style Refactoring (rvmeur.com Design)
- Phase 3 Plan: Google Drive Integration & NAS Replacement
- Phase 6 Discussion Log - Camera File Number Baseline Extraction
- Phase 7 Discussion Log - Robust RAW+JPG Co-existence & Verification
- Phase 8 Discussion Log - Canon and Fuji Connection Polish
- Phase 5: Fujifilm Native Wired Tether Engine & Clean Disconnect Lifecycle - Context
- Phase 6: Canon EOS Direct PTP Engine & Fast Preview Extraction - Context
- Phase 7: Sony Alpha & Nikon Multi-Vendor Tether Adapters - Context
- Milestone v1 Audit Report
- AGENTS.md
- graphify.md
- Migration_10_11.kt
- Migration_9_10.kt
- CanonPtpConstants.kt
- NikonPtpConstants.kt
- SonyPtpConstants.kt
- format
- maxLength
- minimum
- title
- format
- maxLength
- minProperties
- oneOf
- required
- title
- rflccaf/a.java
- i.java
- j.java
- k.java
- l.java
- m.java
- n.java
- 09-production-hardening-pruning/.continue-here.md

## God Nodes (most connected - your core abstractions)
1. `Communities (448 total, 38 thin omitted)` - 398 edges
2. `MyApplication` - 112 edges
3. `UsbHelper` - 73 edges
4. `Codebase Map` - 52 edges
5. `Communities (66 total, 16 thin omitted)` - 44 edges
6. `permissions` - 43 edges
7. `TransferQueueItemEntity` - 42 edges
8. `CameraMediaItemEntity` - 39 edges
9. `TransferQueueDao` - 37 edges
10. `FujiTetherAdapter` - 36 edges

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

## Communities (724 total, 105 thin omitted)

### Community 0 - "USB Camera & MTP"
Cohesion: 0.01
Nodes (398): Communities (448 total, 38 thin omitted), Community 0 - "USB Camera & MTP", Community 100 - "Phase 1 Plan 02: State Differ and CTG Change Detection", Community 101 - "CanonCameraInitializer", Community 102 - "TransferQueueItemEntity", Community 103 - "ProjectSession", Community 104 - "Implementation Decisions", Community 105 - "SessionActiveScreen" (+390 more)

### Community 1 - "SFTP & Remote Operations"
Cohesion: 0.05
Nodes (44): Communities (66 total, 16 thin omitted), Community 0 - "USB Camera & MTP", Community 10 - "Camera Unlock Orchestrator", Community 11 - "Camera Media DAO", Community 12 - "Camera Media Repository", Community 13 - "Connect Camera Screen", Community 15 - "Community 15", Community 18 - "Community 18" (+36 more)

### Community 2 - "Transfer Session UI"
Cohesion: 0.50
Nodes (4): app/build.gradle.kts, ast_hash, mtime, semantic_hash

### Community 3 - "Camera Initialization"
Cohesion: 0.50
Nodes (4): app/build.gradle.kts, ast_hash, mtime, semantic_hash

### Community 4 - "Database & NAS Connection"
Cohesion: 0.17
Nodes (11): Community Hubs (Navigation), Corpus Check, God Nodes (most connected - your core abstractions), Graph Freshness, Graph Report - TransferInFuji  (2026-08-09), Hyperedges (group relationships), Import Cycles, Knowledge Gaps (+3 more)

### Community 5 - "Gallery UI"
Cohesion: 0.18
Nodes (10): Community Hubs (Navigation), Corpus Check, God Nodes (most connected - your core abstractions), Graph Report - .  (2026-07-31), Hyperedges (group relationships), Import Cycles, Knowledge Gaps, Suggested Questions (+2 more)

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
Cohesion: 0.15
Nodes (12): app/src/main/java/com/reflection/transferin/data/local/entity/CameraMediaItemEntity.kt, ast_hash, mtime, semantic_hash, gradlew, ast_hash, mtime, semantic_hash (+4 more)

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
Cohesion: 0.16
Nodes (9): CompletableJob, Intent, kotlinx, UsbDevice, UsbTetherService, IBinder, Notification, PowerManager (+1 more)

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
Cohesion: 0.15
Nodes (8): CameraInitContext, CameraInitResult, ok(), MtpDevice, UsbDevice, UsbDeviceConnection, CameraVendorInitializer, GenericCameraInitializer

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
Cohesion: 0.05
Nodes (29): UploadedGalleryScreenTest, CullingMetrics, FilterMode, ALL, CULLED, FAILED, OPTIMAL, PHOTOS (+21 more)

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
Cohesion: 0.12
Nodes (11): SessionStats, SessionSummaryState, ShootModeState, TetherState, AndroidViewModel, java, Job, StateFlow (+3 more)

### Community 73 - "CameraMediaItemEntity"
Cohesion: 0.11
Nodes (3): CameraMediaDao, Flow, CameraMediaItemEntity

### Community 75 - "ConnectCameraViewModel"
Cohesion: 0.13
Nodes (4): ConnectCameraUiState, ConnectCameraViewModel, StateFlow, ViewModel

### Community 76 - "TransferQueueDao"
Cohesion: 0.07
Nodes (3): Flow, TransferQueueDao, TransferQueueItemEntity

### Community 77 - "UsbHelper"
Cohesion: 0.10
Nodes (10): CameraSessionState, android, Intent, kotlinx, MtpDevice, StateFlow, UsbDevice, UsbDeviceConnection (+2 more)

### Community 79 - "AutoStorageDetector"
Cohesion: 0.15
Nodes (10): AutoStorageDetector, Uri, StorageLocation, StorageType, DCIM, DOWNLOAD, OTHER, PICTURES (+2 more)

### Community 80 - "SessionEntity"
Cohesion: 0.12
Nodes (3): Flow, SessionDao, SessionEntity

### Community 81 - "UsbDisconnectHandler"
Cohesion: 0.06
Nodes (30): CanonPtpAdapter, CanonPtpResponse, create(), findBulkInEndpoint(), findBulkOutEndpoint(), findPtpInterface(), ByteArray, ByteBuffer (+22 more)

### Community 82 - "DelegatingSftpClient"
Cohesion: 0.19
Nodes (6): DelegatingSftpClient, bytesTransferred, Result, totalBytes, Uri, RemoteFolder

### Community 83 - "SelectFolderViewModel"
Cohesion: 0.13
Nodes (4): StateFlow, Uri, ViewModel, SelectFolderViewModel

### Community 85 - "TwoPhaseUploadHandlerTest"
Cohesion: 0.13
Nodes (8): Exception, Result, Uri, SizeMismatchException, TwoPhaseUploadHandler, com, Uri, TwoPhaseUploadHandlerTest

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
Cohesion: 0.16
Nodes (10): Modifier, LoginNasScreen(), Modifier, TetherState, SessionActiveScreen(), StatMetric, Modifier, RvmFooterBar() (+2 more)

### Community 92 - "Phase 03 Plan 04: USB Disconnect Handler - Summary"
Cohesion: 0.11
Nodes (17): Dependencies, Deviations from Plan, Files Created/Modified, Implementation Summary, Integration Points, Key Links, Known Stubs, Objective (+9 more)

### Community 93 - "MediaStatus"
Cohesion: 0.12
Nodes (7): MediaStatus, CACHED, DISCOVERED, FAILED, QUEUED, UPLOADED, UPLOADING

### Community 94 - "CameraMedia"
Cohesion: 0.16
Nodes (4): CameraMedia, CtgChangeEvent, NewShotDetector, ListCameraMediaUseCase

### Community 95 - "GoogleDriveClient"
Cohesion: 0.26
Nodes (5): GoogleDriveClient, bytesTransferred, Result, totalBytes, Uri

### Community 97 - "CameraGalleryViewModel"
Cohesion: 0.14
Nodes (10): CameraGalleryState, CameraGalleryViewModel, create(), AndroidViewModel, Application, Job, StateFlow, CameraGalleryViewModelFactory (+2 more)

### Community 98 - "TransferIn"
Cohesion: 0.12
Nodes (15): Clean Architecture, MVVM Pattern, Project Structure, Core Value, Evolution, Key Decisions, Shipped Capabilities, Technology (+7 more)

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
Cohesion: 0.07
Nodes (14): CameraRuntimeState, ProjectSession, CameraConnectionCoordinator, StateFlow, CameraEngine, Flow, StateFlow, ImportCameraEngine (+6 more)

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
Cohesion: 0.06
Nodes (17): NasConnectionRepositoryImpl, getAuthorizationUri(), getGoogleSignInClient(), GoogleDriveAuthManager, Context, Result, Uri, launchAuthorizationBrowser() (+9 more)

### Community 112 - "SftpClient"
Cohesion: 0.26
Nodes (5): bytesTransferred, Result, totalBytes, Uri, SftpClient

### Community 113 - "TransferInApp"
Cohesion: 0.14
Nodes (14): Modifier, SelectSourceFolderScreen(), ViewModelProvider, SessionViewModelFactory, Bundle, Intent, MainActivity, PreviewFallbackApp() (+6 more)

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
Cohesion: 0.18
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
Cohesion: 0.04
Nodes (31): addErrorHandlerIfEventEmitter(), adopt(), areInOrder(), calcResourceTimingData(), combineConfig(), delBadMea(), doCover(), doSupplement() (+23 more)

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
Cohesion: 0.04
Nodes (30): adopt(), areInOrder(), calcResourceTimingData(), combineConfig(), delBadMea(), doCover(), doSupplement(), fixRelativeUrl() (+22 more)

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
Cohesion: 0.24
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
Cohesion: 0.42
Nodes (4): DocumentFileScanner, Uri, NodeToScan, ScannedDocument

### Community 152 - "ShootModeEngine"
Cohesion: 0.07
Nodes (20): Corrupted, CtgMetadataSnapshot, CtgStabilityVerifier, CtgVerificationResult, EmptyFile, ByteArray, NotFound, StabilityStage1Result (+12 more)

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
Cohesion: 0.07
Nodes (32): InvalidRafHeaderException, ByteArray, ByteOrder, Exception, java, Result, RafHeaderInfo, RafPreviewExtractor (+24 more)

### Community 157 - "UsbDeviceInfo"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/session/ImportCameraEngine.kt, ast_hash, mtime, semantic_hash

### Community 158 - "TransferInDatabase"
Cohesion: 0.20
Nodes (4): DatabaseProvider, Context, TransferInDatabase, RoomDatabase

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
Cohesion: 0.07
Nodes (18): AiFaceCullingEngine, DefaultAiFaceCullingEngine, FaceQualityDetector, ByteArray, AiFaceQualityMetrics, CullingAction, FLAG_WARNING_ONLY, PROCEED_UPLOAD (+10 more)

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
Cohesion: 0.05
Nodes (42): anyOf, $dynamicRef, allOf, $comment, additionalProperties, $comment, default, deprecated (+34 more)

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
Cohesion: 0.05
Nodes (42): anyOf, $dynamicRef, allOf, $comment, additionalProperties, $comment, default, deprecated (+34 more)

### Community 175 - "Connection Layers"
Cohesion: 0.05
Nodes (43): permissions, android.permission.ACCESS_ADSERVICES_AD_ID, android.permission.ACCESS_ADSERVICES_ATTRIBUTION, android.permission.ACCESS_ADSERVICES_CUSTOM_AUDIENCE, android.permission.ACCESS_ADSERVICES_TOPICS, android.permission.ACCESS_COARSE_LOCATION, android.permission.ACCESS_FINE_LOCATION, android.permission.ACCESS_MEDIA_LOCATION (+35 more)

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
Cohesion: 0.36
Nodes (4): DiscoveredFile, Flow, SourceWatcher, T

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
Cohesion: 0.05
Nodes (41): 1. Executive Summary, 2. Automated Test Execution Evidence, 3. Comprehensive Milestone Requirement Verification Audit, 4. Requirement Traceability Matrix (100% Verified), 5. Final Sign-off & Milestone Acceptance, CANON-01: Canon EOS PTP Remote & Event Draining, CANON-02: Sub-500ms .CR3 ISO-BMFF Box Preview Parsing, CANON-03: Two-Stage CTG Stability Verification (+33 more)

### Community 194 - "Test Cases"
Cohesion: 0.07
Nodes (18): fromInputStream(), fromStreamWriter(), bytesTransferred, totalBytes, UsbStreamRequestBody, BufferBucketType, LARGE, SMALL (+10 more)

### Community 195 - "Phase 3 Research: Google Drive Integration & NAS Replacement"
Cohesion: 0.05
Nodes (36): 1. Executive Summary & Security Posture, 2. Detailed Audit Findings, 3. Resilience Assessment: Adversarial USB OTG Disconnection, 4. Component Export & IPC Security Verification, 5. Prioritized Remediation Roadmap, AAS Security Audit Report: Phase 04 — Zero-Leak Hardware Core & Resource Lifecycle, Assessment & Verification, Defensive Remediation (+28 more)

### Community 196 - "UploadStatus"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/connectcamera/ConnectCameraViewModel.kt, ast_hash, mtime, semantic_hash

### Community 197 - "UploadStatus"
Cohesion: 0.29
Nodes (6): UploadStatus, FAILED, QUEUED, SKIPPED, SUCCESS, UPLOADING

### Community 198 - "SourceSelectionMode"
Cohesion: 0.33
Nodes (3): SourceSelectionMode, AUTO_DETECT, BROWSE_FOLDER

### Community 199 - "SessionSummaryScreen"
Cohesion: 0.53
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
Cohesion: 0.12
Nodes (8): ArwPreviewInfo, ArwPreviewParser, IfdScanResult, ByteArray, ByteOrder, ByteArray, MultiVendorPreviewExtractorTest, ArwPreviewParserTest

### Community 213 - "Research: Target Features & Behaviors"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/connectcamera/ConnectCameraViewModelFactory.kt, ast_hash, mtime, semantic_hash

### Community 214 - "Key Findings"
Cohesion: 0.11
Nodes (18): Architecture Approach, Confidence Assessment, Critical Pitfalls, Executive Summary, Expected Features, Gaps to Address, Implications for Roadmap, Key Findings (+10 more)

### Community 215 - "DefaultCameraSessionInitializer"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/feature/folder/FolderUiState.kt, ast_hash, mtime, semantic_hash

### Community 216 - "MediaFingerprint"
Cohesion: 0.22
Nodes (3): fromStorageKey(), MediaFingerprint, MediaFingerprintTest

### Community 217 - "SelectFolderScreen"
Cohesion: 0.33
Nodes (3): FolderUiState, Modifier, SelectFolderScreen()

### Community 218 - "GalleryViewModelFactory"
Cohesion: 0.06
Nodes (33): Execution Waves & Architecture Dependency Graph, Mandatory Quality Gates, Mandatory Quality Gates, Mandatory Quality Gates, Mandatory Quality Gates, Mandatory Quality Gates, Mandatory Quality Gates, Milestone v1.0: Rebranding & Google Drive Migration (Shipped: 2026-08-12) (+25 more)

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
Cohesion: 0.10
Nodes (3): CameraSyncDao, Flow, CameraSyncItemEntity

### Community 228 - "Phase 2 Research: UI Style Refactoring (rvmeur.com Design)"
Cohesion: 0.06
Nodes (33): $ref, $ref, type, type, type, $ref, $ref, $ref (+25 more)

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
Cohesion: 0.06
Nodes (31): 1. Executive Summary & Security Posture, 2. Detailed Audit Findings, 3. Resilience Assessment: Multi-Vendor Edge Cases, 4. Prioritized Remediation Roadmap, AAS Security Audit Report: Phase 07 — Sony Alpha & Nikon Multi-Vendor Tether Adapters, Additional Hardening Requirement, Defensive Remediation, Defensive Remediation (+23 more)

### Community 236 - "Phase 6 Summary - Camera File Number Baseline Extraction"
Cohesion: 0.07
Nodes (31): default, $ref, $ref, type, type, type, $ref, $ref (+23 more)

### Community 237 - "Phase 7 Context - Robust RAW+JPG Co-existence & Verification"
Cohesion: 0.09
Nodes (28): ApiCollector(), ArmsRum(), Bounds(), BrowserReporter(), _callSuper(), CanvasElementContainer(), CanvasRenderer(), _classCallCheck() (+20 more)

### Community 238 - "Phase 7 Research - Robust RAW+JPG Co-existence & Verification"
Cohesion: 0.09
Nodes (28): ApiCollector(), ArmsRum(), Bounds(), BrowserReporter(), _callSuper(), CanvasElementContainer(), CanvasRenderer(), _classCallCheck() (+20 more)

### Community 239 - "Phase 7 Summary - Robust RAW+JPG Co-existence & Verification"
Cohesion: 0.07
Nodes (28): 1.1 Risk Severity Matrix, 1. Executive Summary & Critical Risk Matrix, 2.1 The Invisible Recursive Ping-Pong Communication, 2.2 Unsynchronized Native `MtpDevice` Concurrency, 2.3 Why Camera Shutdown Fails (The Lockup Sequence), 2.4 `UsbTetherService.onDestroy()` Cancellation Bug, 2. Forensic Analysis: Fuji Device Communication & Shutdown Lock Failure, 3.1 Uncancelled `CoroutineScope` Allocations (+20 more)

### Community 240 - "Phase 8 Context - Canon and Fuji Connection Polish"
Cohesion: 0.07
Nodes (28): 1. Executive Summary & Security Posture, 2. Detailed Audit Findings, 3. Resilience Assessment: Volatile LCD Lockup & OTG Disconnection, 4. Prioritized Remediation Roadmap, AAS Security Audit Report: Phase 05 — Fujifilm Native Wired Tether Engine & Clean Disconnect Lifecycle, Defensive Remediation, Defensive Remediation, Defensive Remediation (+20 more)

### Community 241 - "Phase 8 Research - Canon and Fuji Connection Polish"
Cohesion: 0.07
Nodes (27): 1. Executive Summary & Security Posture, 2. Detailed Audit Findings, 3. Resilience Assessment: Canon DIGIC Event Pump & Shutter Locks, 4. Prioritized Remediation Roadmap, AAS Security Audit Report: Phase 06 — Canon EOS Direct PTP Engine & Fast Preview Extraction, Defensive Remediation, Defensive Remediation, Defensive Remediation (+19 more)

### Community 242 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 248 - "Security Domain"
Cohesion: 0.67
Nodes (3): Applicable ASVS Categories, Known Threat Patterns for SFTP Uploads, Security Domain

### Community 249 - "Standard Stack"
Cohesion: 0.67
Nodes (3): Core, Phase-3 Dependencies (REQUIRED before Phase 4), Standard Stack

### Community 250 - "Phase 1 Plan: Rebranding & Package Rename"
Cohesion: 0.12
Nodes (9): fail(), IOException, UsbTransportClosedException, T, UsbPriorityMutex, UsbTransferPriority, TIER_1_PREVIEW, TIER_2_STREAMING (+1 more)

### Community 251 - "Phase 2 Plan: UI Style Refactoring (rvmeur.com Design)"
Cohesion: 0.12
Nodes (6): Override, Application, ApplicationInfo, ClassLoader, Context, MyJni

### Community 252 - "Phase 3 Plan: Google Drive Integration & NAS Replacement"
Cohesion: 0.08
Nodes (24): 1. Executive Summary, 20 Film Simulation Presets & HUD Styling Tokens:, 2. Multi-Vendor Token & Component Audit Matrix, 3.1 Multi-Vendor Identity & Brand Tokens, 3.2 Fujifilm Film Simulation Badges (`FujiFilmSimulation.kt`), 3.3 Sony Volatile DRAM Safety Warning Banners (`SonyDramSafetyStatus`), 3.4 Dual-Slot Storage Representation (Canon & Nikon), 3.5 Real-Time Connection States (`TetherState`) & Live Studio HUD (+16 more)

### Community 253 - "Phase 6 Discussion Log - Camera File Number Baseline Extraction"
Cohesion: 0.16
Nodes (9): ByteArray, CameraTetherAdapter, CompletableJob, Job, SharedFlow, UsbDevice, UsbEndpoint, UsbInterface (+1 more)

### Community 255 - "Phase 8 Discussion Log - Canon and Fuji Connection Polish"
Cohesion: 0.17
Nodes (9): ByteArray, CameraTetherAdapter, java, Result, StateFlow, UsbDevice, UsbEndpoint, UsbInterface (+1 more)

### Community 256 - "Context"
Cohesion: 0.13
Nodes (11): _addListener(), arrayClone(), checkListener(), Client(), EventEmitter(), _getMaxListeners(), _listeners(), _onceWrap() (+3 more)

### Community 264 - "rvmeur/transferin/session/runtime/SessionOrchestrator.kt"
Cohesion: 0.13
Nodes (11): _addListener(), arrayClone(), checkListener(), Client(), EventEmitter(), _getMaxListeners(), _listeners(), _onceWrap() (+3 more)

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
Cohesion: 0.13
Nodes (5): ThumbnailStorageConfig, ByteArray, ThumbnailStorageManager, ByteArray, ThumbnailPipelineIntegrationTest

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
Cohesion: 0.13
Nodes (8): ByteArray, ByteBuffer, T, UsbEndpoint, SafeUsbTransportManager, UsbDeviceConnection, UsbEndpoint, SafeUsbTransportManagerTest

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
Cohesion: 0.15
Nodes (12): app/src/main/java/com/rvmeur/transferin/data/local/dao/TransferQueueDao.kt, ast_hash, mtime, semantic_hash, .planning/codebase/STRUCTURE.md, ast_hash, mtime, semantic_hash (+4 more)

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
Cohesion: 0.12
Nodes (10): CameraMediaMetadata, CameraTetherAdapter, AutoCloseable, ByteArray, Result, StateFlow, TetherState, UsbDevice (+2 more)

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
Cohesion: 0.12
Nodes (7): CameraSyncMetrics, CameraSyncTracker, AutoCloseable, Job, StateFlow, StatusCounts, CameraSyncTrackerTest

### Community 444 - ".planning/STATE.md"
Cohesion: 0.50
Nodes (4): .planning/STATE.md, ast_hash, mtime, semantic_hash

### Community 445 - "settings.gradle.kts"
Cohesion: 0.50
Nodes (4): settings.gradle.kts, ast_hash, mtime, semantic_hash

### Community 450 - "R"
Cohesion: 0.10
Nodes (20): anim, animator, array, attr, bool, color, dimen, drawable (+12 more)

### Community 451 - "KineticButton"
Cohesion: 0.11
Nodes (14): ExposureInspectionModalTest, CullingStatusBadge(), Modifier, ExposureHistogramCanvas(), IntArray, Modifier, ExifTag(), ExposureInspectionModal() (+6 more)

### Community 452 - "TransitCacheManager"
Cohesion: 0.13
Nodes (4): IOException, TransitCacheExhaustedException, TransitCacheManager, TransitCacheManagerTest

### Community 453 - "UsbHardwareSession"
Cohesion: 0.14
Nodes (13): ActiveStreaming, CleanTerminated, Connected, Disconnecting, Failed, Handshake, HardwareSessionState, Idle (+5 more)

### Community 454 - "split_configs"
Cohesion: 0.10
Nodes (20): split_configs, config.ar, config.arm64_v8a, config.de, config.en, config.es, config.fr, config.hi (+12 more)

### Community 455 - "3. Accessibility & Quality Verification Checklist (WIZARD-01..04)"
Cohesion: 0.10
Nodes (19): 1. Screen Architecture (`FluidWizardScreen`), 2.1 Step 01: Google Drive Auth & Target Folder Card Grid (`01 google drive`), 2.2 Step 02: Camera Tethering & Live Pulse Aura (`02 camera tethering`), 2.3 Step 03: Session Preferences & Launch Action (`03 session rules`), 2. Step Specifications, 3.1 Minimum 44dp Touch Target Contract, 3.2 2-Column Card Grid Scaling & Dynamic Text Contract, 3.3 RVM Microcopy & Lowercase Rule Contract (+11 more)

### Community 456 - "3. Accessibility & Quality Verification Checklist (WIZARD-01..04)"
Cohesion: 0.10
Nodes (19): 1. Screen Architecture (`FluidWizardScreen`), 2.1 Step 01: Google Drive Auth & Target Folder Card Grid (`01 google drive`), 2.2 Step 02: Camera Tethering & Live Pulse Aura (`02 camera tethering`), 2.3 Step 03: Session Preferences & Launch Action (`03 session rules`), 2. Step Specifications, 3.1 Minimum 44dp Touch Target Contract, 3.2 2-Column Card Grid Scaling & Dynamic Text Contract, 3.3 RVM Microcopy & Lowercase Rule Contract (+11 more)

### Community 457 - "Deep-Dive Technical Audit"
Cohesion: 0.10
Nodes (19): 1. Schema Migration (`Migration_10_11.kt`), 1. Sync Coordination & Culling State Machine (`SyncOrchestrator.kt`), 1. Two-Stage Atomic Write & Sync (`ThumbnailStorageManager.kt`), 1. Two-Tier Priority USB Mutex (`UsbPriorityMutex.kt`), 1. Zero-Allocation Integer Fixed-Point Luminance Math (`ExposureAnalyzer.kt`), 2. Unidirectional Data Flow (`GalleryViewModel.kt`), Code Review Findings & Actionable Recommendations, Deep-Dive Technical Audit (+11 more)

### Community 458 - "NikonStorageMapper"
Cohesion: 0.15
Nodes (9): NikonStorageInfo, NikonStorageMapper, NikonStorageSlot, ALL_STORAGES, INTERNAL, SLOT_1, SLOT_2, UNKNOWN (+1 more)

### Community 459 - "SonyPtpDefragmenterTest"
Cohesion: 0.15
Nodes (10): CorruptedPtpPayloadException, Data, ByteArray, IOException, Response, SonyPtpContainer, SonyPtpDefragmenter, UsbDeviceConnection (+2 more)

### Community 461 - "CanonPtpAdapterTest"
Cohesion: 0.17
Nodes (7): CanonPtpAdapterTest, CommandRecord, ByteArray, UsbDevice, UsbDeviceConnection, UsbEndpoint, UsbInterface

### Community 462 - "FujiTetherAdapter"
Cohesion: 0.24
Nodes (9): FujiTetherAdapter, CameraTetherAdapter, Job, StateFlow, TetherState, UsbDevice, UsbDeviceConnection, UsbEndpoint (+1 more)

### Community 463 - "ExposureScratchBuffer"
Cohesion: 0.20
Nodes (8): ExposureAnalyzer, ExposureScratchBuffer, ByteArray, ByteBuffer, IntArray, ExposureAnalysisResult, IntArray, Bitmap

### Community 464 - "AAS Code Review Report: Phase 04 — Zero-Leak Hardware Core & Resource Lifecycle"
Cohesion: 0.11
Nodes (17): 1. Executive Summary, 2. Review Scope & Files Audited, 3.1 Memory Safety & Off-Heap Direct Buffer Pooling (`CORE-03`), 3.2 Thread Safety, JNI Concurrency & IO Latches (`CORE-01`), 3.3 Android 14/15 Foreground Service Compliance (`CORE-02`, `CORE-04`), 3.4 Stream & File Descriptor Resource Hygiene (`CORE-05`), 3. Invariant & Safety Assessment, 4. Findings & Remediation Matrix (+9 more)

### Community 465 - "CanonCameraInitializer"
Cohesion: 0.30
Nodes (6): CanonCameraInitializer, IntArray, UsbDevice, UsbDeviceConnection, UsbEndpoint, UsbInterface

### Community 466 - "NikonRetryPolicy"
Cohesion: 0.23
Nodes (7): IOException, T, NikonDeviceBusyException, NikonDeviceBusyTimeoutException, NikonRetryConfig, NikonRetryPolicy, NikonRetryPolicyTest

### Community 467 - "FujiTetherAdapterTest"
Cohesion: 0.12
Nodes (6): FujiTetherAdapterTest, UsbDevice, UsbDeviceConnection, UsbEndpoint, UsbInterface, SentCommand

### Community 468 - "SonyPtpAdapterTest"
Cohesion: 0.21
Nodes (6): ByteArray, UsbDevice, UsbDeviceConnection, UsbEndpoint, UsbInterface, SonyPtpAdapterTest

### Community 469 - "_typeof"
Cohesion: 0.12
Nodes (17): ajax(), _assertThisInitialized(), _createClass(), _defineProperties(), _defineProperty(), formatParams(), hackFunction(), isBoolean() (+9 more)

### Community 470 - "Detailed Task Breakdown"
Cohesion: 0.12
Nodes (16): 1. Mandatory Automated Verification, 2. Code Review Audit (`/gsd-code-review`), 3. UI & Telemetry Audit (`/gsd-ui-review`), 4. Security & Hardware Safety Audit (`/gsd-secure-phase`), Architecture Dependency Graph, Artifacts Produced & Modified, Detailed Task Breakdown, Fujifilm PTP Protocol Sequence & Clean Disconnect Lifecycle (+8 more)

### Community 471 - "4. Work Breakdown Structure & Task List"
Cohesion: 0.12
Nodes (16): 1. Executive Summary & Architectural Overview, 2. Architecture & Component Interaction, 3. Requirements & Traceability, 4. Work Breakdown Structure & Task List, 6. Testing Strategy & Synthetic Fixtures, 7. Quality Gates & Acceptance Criteria, Key Technical Innovations & Safeguards:, Phase 7: Sony Alpha & Nikon Multi-Vendor Tether Adapters (PLAN.md) (+8 more)

### Community 472 - "4. Work Breakdown Structure & Atomic Tasks"
Cohesion: 0.12
Nodes (16): 1. Executive Summary & Architectural Overview, 2. Architecture & State Machine Sequence Flow, 3. Requirements & Traceability Matrix, 4. Work Breakdown Structure & Atomic Tasks, 5. Directory Structure & Key Files, 6. Testing Strategy & Synthetic Fixtures, 7. Quality Gates & Acceptance Criteria, Phase 12: Smart Culling State Machine, Room Migration & Quiet Luxury Studio UI (PLAN.md) (+8 more)

### Community 473 - "CameraSyncStatus"
Cohesion: 0.12
Nodes (9): CameraSyncStatus, BUFFERED_LOCAL, CONFIRMED_CLOUD, CULLED_SKIPPED, DETECTED, FAILED, FORCE_UPLOAD_PENDING, PURGED_LOCAL (+1 more)

### Community 474 - "FujiPtpConstants"
Cohesion: 0.18
Nodes (6): FujiPtpConstants, ByteArray, IntArray, PtpResponse, PtpEvent, PtpResponse

### Community 475 - ".a"
Cohesion: 0.30
Nodes (10): AppComponentFactory, ContentProvider, g, Activity, Application, ApplicationInfo, ClassLoader, Intent (+2 more)

### Community 476 - "AAS Code Review Report: Phase 05 — Fujifilm Native Wired Tether Engine & Clean Disconnect Lifecycle"
Cohesion: 0.12
Nodes (15): 1. Executive Summary, 2. Review Scope & Files Audited, 3.1 PTP Handshake & Host Control Governance (`FUJI-01`), 3.2 Interrupt Event Ingestion Loop (`FUJI-02`), 3.3 Non-Cancellable 4-Step Clean Teardown (`FUJI-03`), 3.4 High-Performance Binary Header & Recipe Extraction (`FUJI-04`, `FUJI-05`), 3. Invariant & Safety Assessment, 4. Findings & Recommendations (+7 more)

### Community 477 - "AAS Code Review Report: Phase 06 — Canon EOS Direct PTP Engine & Fast Preview Extraction"
Cohesion: 0.12
Nodes (15): 1. Executive Summary, 2. Review Scope & Files Audited, 3.1 Canon EOS Remote Mode & Continuous DIGIC Event Pump (`CANON-01`), 3.2 Sub-500ms `.CR3` ISO-BMFF Box Preview Extraction (`CANON-02`), 3.3 Two-Stage Stability-Verified `.CTG` Fallback (`CANON-03`), 3.4 Dual-Slot Storage ID Isolation (`CANON-04`), 3.5 Deterministic 4-Step Clean Teardown, 3. Invariant & Safety Assessment (+7 more)

### Community 478 - "AAS Code Review Report: Phase 07 — Sony Alpha & Nikon Multi-Vendor Tether Adapters"
Cohesion: 0.12
Nodes (15): 1. Executive Summary, 2. Review Scope & Files Audited, 3.1 Sony Alpha SDIO Handshake & Volatile DRAM Safety (`SONY-01`), 3.2 Sony Multi-Packet Defragmenter & `.ARW` Preview Parser (`SONY-02`), 3.3 Nikon Vendor PTP Engine & Dual-Slot Storage Mapping (`NIKON-01`), 3.4 Adaptive Exponential Backoff Retry Policy (`NIKON-02`), 3. Invariant & Safety Assessment, 4. Findings & Recommendations (+7 more)

### Community 479 - "Phase 8: 5-Stage Camera-to-Cloud Sync & Direct USB Streaming (PLAN.md)"
Cohesion: 0.12
Nodes (15): 1. Executive Summary & Architectural Overview, 2. Architecture & Component Interaction, 3. Requirements & Traceability, 4. Work Breakdown Structure & Task List, 5. Directory Structure & Key Files, 6. Testing Strategy & Synthetic Fixtures, 7. Quality Gates & Acceptance Criteria, Phase 8: 5-Stage Camera-to-Cloud Sync & Direct USB Streaming (PLAN.md) (+7 more)

### Community 480 - "Phase 9: Production Hardening, Codebase Pruning & Comprehensive Audits (PLAN.md)"
Cohesion: 0.12
Nodes (15): 1. Executive Summary & Architectural Overview, 2. Architecture & Quality Gate Dependency Graph, 3. Requirements & Traceability, 4. Work Breakdown Structure & Task List, 5. Directory Structure & Key Files, 6. Testing Strategy & Synthetic Fuzz Fixtures, 7. Quality Gates & Acceptance Criteria, Fuzz Engine Test Matrix: (+7 more)

### Community 482 - "CameraTetherAdapterFactoryTest"
Cohesion: 0.13
Nodes (6): CameraTetherAdapterFactory, CameraTetherAdapter, UsbDevice, UsbDeviceConnection, CameraTetherAdapterFactoryTest, UsbDeviceConnection

### Community 483 - "NikonPreviewParser"
Cohesion: 0.40
Nodes (5): IfdScanResult, ByteArray, ByteOrder, NefPreviewInfo, NikonPreviewParser

### Community 484 - "NEDialog"
Cohesion: 0.19
Nodes (5): Handler, Context, NEDialog, a, Context

### Community 485 - "enum"
Cohesion: 0.13
Nodes (15): definitions, positiveInteger, positiveIntegerDefault0, simpleTypes, array, boolean, integer, null (+7 more)

### Community 486 - "enum"
Cohesion: 0.13
Nodes (15): definitions, positiveInteger, positiveIntegerDefault0, simpleTypes, array, boolean, integer, null (+7 more)

### Community 487 - "Phase 3 Visual UI Specification Contract — Live Shoot Dashboard & Gallery Polish"
Cohesion: 0.13
Nodes (14): 1. Executive Summary, 2.1 Live Session Hub (`SessionActiveScreen`), 2.2 Uploaded Gallery & Preview (`UploadedGalleryScreen`), 2.3 Session Summary Screen (`SessionSummaryScreen`), 2. Component & Screen Architecture, 3. RVM Design System Tokens, 4-Stat Metric Grid Design, 4. Verification Gate Checklist (DASHBOARD-01..02) (+6 more)

### Community 488 - "Phase 3 Visual UI Specification Contract — Live Shoot Dashboard & Gallery Polish"
Cohesion: 0.13
Nodes (14): 1. Executive Summary, 2.1 Live Session Hub (`SessionActiveScreen`), 2.2 Uploaded Gallery & Preview (`UploadedGalleryScreen`), 2.3 Session Summary Screen (`SessionSummaryScreen`), 2. Component & Screen Architecture, 3. RVM Design System Tokens, 4-Stat Metric Grid Design, 4. Verification Gate Checklist (DASHBOARD-01..02) (+6 more)

### Community 489 - "Detailed Task Breakdown"
Cohesion: 0.13
Nodes (14): 1. Mandatory Automated Verification, 2. Code Review Audit (`/gsd-code-review`), 3. Security Audit (`/gsd-secure-phase`), Architecture Dependency Graph, Artifacts Produced & Modified, Detailed Task Breakdown, Phase 4 Plan: Zero-Leak Hardware Core & Resource Lifecycle, Quality Gates & Verification Plan (+6 more)

### Community 490 - "v3.0 Requirements"
Cohesion: 0.13
Nodes (14): 1. Hardware Lifecycle & Zero-Leak Core (CORE), 2. Fujifilm Native Wired Tether Engine (FUJI), 3. Canon EOS Direct PTP & CTG Fallback Engine (CANON), 4. Sony Alpha & Nikon Tethering Adapters (SONY / NIKON), 5. 5-Stage Cloud Sync & Storage Management (SYNC), 6. Production Quality, UI & Security Rigor (QUAL), AI Culling & Batch Presets, Out of Scope (+6 more)

### Community 491 - "CameraPictureProfile"
Cohesion: 0.15
Nodes (13): CameraPictureProfile, CANON_MONOCHROME, CANON_PORTRAIT, CUSTOM, FUJI_ACROS, FUJI_CLASSIC_CHROME, FUJI_MONOCHROME, FUJI_VELVIA (+5 more)

### Community 492 - "NikonPtpAdapterTest"
Cohesion: 0.21
Nodes (6): ByteArray, UsbDevice, UsbDeviceConnection, UsbEndpoint, UsbInterface, NikonPtpAdapterTest

### Community 494 - "_slicedToArray"
Cohesion: 0.16
Nodes (14): _arrayLikeToArray(), _arrayWithHoles(), _arrayWithoutHoles(), _createForOfIteratorHelper(), getResourceType(), _iterableToArray(), _iterableToArrayLimit(), matchList() (+6 more)

### Community 495 - ".sendEvent"
Cohesion: 0.23
Nodes (4): ClickCollector(), PerfCollector(), PvCollector(), StaticResourceCollector()

### Community 496 - "_slicedToArray"
Cohesion: 0.16
Nodes (14): _arrayLikeToArray(), _arrayWithHoles(), _arrayWithoutHoles(), _createForOfIteratorHelper(), getResourceType(), _iterableToArray(), _iterableToArrayLimit(), matchList() (+6 more)

### Community 497 - ".sendEvent"
Cohesion: 0.23
Nodes (4): ClickCollector(), PerfCollector(), PvCollector(), StaticResourceCollector()

### Community 498 - "3. Core Component Specifications"
Cohesion: 0.14
Nodes (13): 1. Visual Design Architecture & Brand Identity, 2. Typography & Microcopy System, 3.1 `RvmHeaderBar` (Top Brand App Bar), 3.2 `RvmFooterBar` (Clickable Web Intent Footer), 3.3 `FluidDotNavigationBar` (Morphing Pager Nav Bar), 3.4 `DoppelrandCard` (Double-Bezel Container Component), 3.5 `KineticButton` (Tactile Press Button), 3. Core Component Specifications (+5 more)

### Community 499 - "3. Core Component Specifications"
Cohesion: 0.14
Nodes (13): 1. Visual Design Architecture & Brand Identity, 2. Typography & Microcopy System, 3.1 `RvmHeaderBar` (Top Brand App Bar), 3.2 `RvmFooterBar` (Clickable Web Intent Footer), 3.3 `FluidDotNavigationBar` (Morphing Pager Nav Bar), 3.4 `DoppelrandCard` (Double-Bezel Container Component), 3.5 `KineticButton` (Tactile Press Button), 3. Core Component Specifications (+5 more)

### Community 500 - "Phase 4 Verification Report: Zero-Leak Hardware Core & Resource Lifecycle"
Cohesion: 0.14
Nodes (13): 1. Executive Summary, 2. Success Criteria Verification Matrix, 3. Automated Test Suite Execution Results, 4.1 CORE-01: Deterministic Hardware Lifecycle & Atomic JNI IO Latch, 4.2 CORE-02: Zero Unmanaged Coroutines & usbfs FD Leaks, 4.3 CORE-03: Zero GC Churn via DirectByteBufferPool, 4.4 CORE-04: Android 14/15 Foreground Service Compliance, 4.5 CORE-05: Stream & File Descriptor Resource Hygiene (+5 more)

### Community 501 - "Detailed Task Breakdown"
Cohesion: 0.14
Nodes (13): 1. Mandatory Automated Verification, 2. Code Review Audit (`/gsd-code-review`), 3. Security Audit (`/gsd-secure-phase`), Architecture Dependency Graph, Artifacts Produced & Modified, Detailed Task Breakdown, Phase 6 Plan: Canon EOS Direct PTP Engine & Fast Preview Extraction, Quality Gates & Verification Plan (+5 more)

### Community 502 - "Phase 11: Persistent Multi-Vendor Thumbnail Pipeline & USB Synchronization (11-01-PLAN.md)"
Cohesion: 0.14
Nodes (13): 1. Executive Summary & Architectural Overview, 2. Architecture & Data Flow, 3. Requirements Traceability, 4. Work Breakdown Structure & Task List, 5. Quality Gates & Performance Verification Targets, 6. Implementation Sequence & Commit Strategy, Core Capabilities Delivered:, Phase 11: Persistent Multi-Vendor Thumbnail Pipeline & USB Synchronization (11-01-PLAN.md) (+5 more)

### Community 503 - ".executePtpCommand"
Cohesion: 0.26
Nodes (4): ByteArray, IntArray, PtpResponse, Result

### Community 504 - "NikonPtpTransport"
Cohesion: 0.33
Nodes (5): ByteArray, IntArray, NikonPtpEvent, NikonPtpResponse, NikonPtpTransport

### Community 505 - "DoppelrandCard"
Cohesion: 0.18
Nodes (10): Modifier, UploadedGalleryScreen(), Modifier, Step1DriveFolder(), DoppelrandCard(), Color, Modifier, Dp (+2 more)

### Community 506 - "ExposureSyntheticFixtures"
Cohesion: 0.26
Nodes (4): ExposureSyntheticFixtures, ByteArray, ByteBuffer, IntArray

### Community 507 - "Phase 10: Zero-Allocation Exposure & Luminance Engine (10-01-PLAN.md)"
Cohesion: 0.15
Nodes (12): 1. Executive Summary & Architectural Overview, 2. Architecture & Data Flow, 3. Requirements Traceability, 4. Work Breakdown Structure & Task List, 5. Quality Gates & Microbenchmark Verification Targets, 6. Implementation Sequence & Commit Strategy, Core Capabilities Delivered:, Phase 10: Zero-Allocation Exposure & Luminance Engine (10-01-PLAN.md) (+4 more)

### Community 508 - "Phase 13 Security & Threat Mitigation Review: Milestone v4.0 Hardening Audit"
Cohesion: 0.15
Nodes (12): 1. Executive Summary, 2. Threat Model Matrix (STRIDE & OWASP Mobile Top 10), 3.1 File System Containment & Storage Boundary Safety, 3.2 USB Transport Concurrency & Mutex Arbitration, 3.3 Database Migration & Transactional SQL Safety, 3.4 Image Decoding & Memory Boundary Safety, 3.5 Cryptographic Hashing & Cloud Token Lifecycles, 3. Detailed Security & Architectural Analysis (+4 more)

### Community 509 - "Pillar-by-Pillar Detailed Audit"
Cohesion: 0.15
Nodes (12): Component Matrix Verification, Executive Summary, Final Certification, GSD UI & UX Design Audit: Phase 12 — Smart Culling State Machine & Quiet Luxury Studio UI, Minor Observations & Recommendations, Pillar 1: Color Tokens & Quiet Luxury Palette Harmony (Score: 20.0 / 20.0), Pillar 2: Typography Hierarchy & Brand Styling (Score: 19.5 / 20.0), Pillar 3: Concentric Geometry & Double-Bezel Architecture (Score: 20.0 / 20.0) (+4 more)

### Community 510 - ".verifyAndEnforceSafety"
Cohesion: 0.29
Nodes (6): ByteArray, Remediated, Safe, SonyDramSafetyGuard, SonyDramSafetyStatus, Warning

### Community 512 - "UsbHardwareSessionTest"
Cohesion: 0.17
Nodes (4): UsbDevice, UsbDeviceConnection, UsbInterface, UsbHardwareSessionTest

### Community 514 - "_typeof"
Cohesion: 0.18
Nodes (12): ajax(), _assertThisInitialized(), _createClass(), _defineProperties(), _defineProperty(), formatParams(), hackFunction(), _possibleConstructorReturn() (+4 more)

### Community 515 - "Proposed Changes"
Cohesion: 0.17
Nodes (11): Automated Verification, Manual Verification, [MODIFY] [PhotoWatcher.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/usb/PhotoWatcher.kt), [MODIFY] [SessionViewModel.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionViewModel.kt), [MODIFY] [SftpConnectionConfig.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/sftp/SftpConnectionConfig.kt), PhotoWatcher, Plan - Phase 7: Robust RAW+JPG Co-existence & Verification, Proposed Changes (+3 more)

### Community 516 - "CameraTetherEvent"
Cohesion: 0.18
Nodes (8): Flow, Flow, CameraStatusChanged, CameraTetherEvent, DevicePropChanged, Error, ObjectAdded, ObjectRemoved

### Community 517 - "NikonCameraInitializer"
Cohesion: 0.27
Nodes (3): UsbDevice, UsbDeviceConnection, NikonCameraInitializer

### Community 518 - "SonyCameraInitializer"
Cohesion: 0.27
Nodes (3): UsbDevice, UsbDeviceConnection, SonyCameraInitializer

### Community 519 - "SonyPtpTransport"
Cohesion: 0.45
Nodes (4): ByteArray, IntArray, SonyPtpResponse, SonyPtpTransport

### Community 520 - "ExposureClassification"
Cohesion: 0.20
Nodes (7): ExposureClassification, OPTIMAL, OVEREXPOSED_BLOWN, UNDEREXPOSED_CRITICAL, compute(), ExposureMetrics, IntArray

### Community 521 - "SmartUploadFilter"
Cohesion: 0.25
Nodes (3): FilterResult, FingerprintSource, SmartUploadFilter

### Community 523 - "apk_unpacked/schema/draft-04.json"
Cohesion: 0.18
Nodes (10): default, dependencies, exclusiveMaximum, exclusiveMinimum, description, id, maximum, minimum (+2 more)

### Community 524 - "unknown/schema/draft-04.json"
Cohesion: 0.18
Nodes (10): default, dependencies, exclusiveMaximum, exclusiveMinimum, description, id, maximum, minimum (+2 more)

### Community 525 - "extracted/manifest.json"
Cohesion: 0.18
Nodes (10): icon, min_sdk_version, name, package_name, split_apks, target_sdk_version, total_size, version_code (+2 more)

### Community 526 - "2. Technical Component Implementation Patterns"
Cohesion: 0.18
Nodes (10): 1. Codebase Architecture & File Mapping, 2.1 Double-Bezel Container (`DoppelrandCard.kt`), 2.2 Kinetic Button (`KineticButton.kt`), 2.3 Fluid Dynamic Dot Navigation Bar (`FluidDotNavigationBar.kt`), 2.4 Clickable Web Intent Footer (`RvmFooterBar.kt`), 2. Technical Component Implementation Patterns, 3. Verification & Build Constraints, Existing Theme Files (`app/src/main/java/com/rvmeur/transferin/ui/theme/`) (+2 more)

### Community 527 - "2. Technical Component Implementation Patterns"
Cohesion: 0.18
Nodes (10): 1. Codebase Architecture & File Mapping, 2.1 Double-Bezel Container (`DoppelrandCard.kt`), 2.2 Kinetic Button (`KineticButton.kt`), 2.3 Fluid Dynamic Dot Navigation Bar (`FluidDotNavigationBar.kt`), 2.4 Clickable Web Intent Footer (`RvmFooterBar.kt`), 2. Technical Component Implementation Patterns, 3. Verification & Build Constraints, Existing Theme Files (`app/src/main/java/com/rvmeur/transferin/ui/theme/`) (+2 more)

### Community 531 - "FluidWizardScreen"
Cohesion: 0.20
Nodes (7): FluidWizardScreen(), Modifier, Modifier, Step2CameraTether(), FluidDotNavigationBar(), Modifier, PagerState

### Community 533 - "Forensic Report: Android Google OAuth Redirection & Client Type Misconfiguration"
Cohesion: 0.20
Nodes (9): 1. Executive Summary & Anomaly Investigation, 2. Evidence Gathering & Technical Root Cause Analysis, 3. Recommended Remediation Strategies, Evidence A: Client Type Mismatch (WEB Client vs Android Native Client), Evidence B: Android 12+ (API 31+) App Link Verification Behavior, Forensic Report: Android Google OAuth Redirection & Client Type Misconfiguration, Primary Symptom Reported, Solution 1: Create an Android OAuth Client ID in Google Cloud / Firebase Console (RECOMMENDED & CLEANEST) (+1 more)

### Community 534 - "1. Accomplished Features"
Cohesion: 0.20
Nodes (9): 1.1 `FluidWizardScreen.kt` (Unified Pager Container), 1.2 `Step1DriveFolder.kt` (OAuth Status & 2-Column Card Grid Folder Browser), 1.3 `Step2CameraTether.kt` (Camera Tethering & Live Pulse Aura), 1.4 `Step3SessionRules.kt` (Session Preferences & Launch Action), 1.5 Navigation Architecture Wiring (`MainActivity.kt`), 1. Accomplished Features, 2. Verification Results, 3. Files Created & Modified (+1 more)

### Community 535 - "1. Accomplished Features"
Cohesion: 0.20
Nodes (9): 1.1 `FluidWizardScreen.kt` (Unified Pager Container), 1.2 `Step1DriveFolder.kt` (OAuth Status & 2-Column Card Grid Folder Browser), 1.3 `Step2CameraTether.kt` (Camera Tethering & Live Pulse Aura), 1.4 `Step3SessionRules.kt` (Session Preferences & Launch Action), 1.5 Navigation Architecture Wiring (`MainActivity.kt`), 1. Accomplished Features, 2. Verification Results, 3. Files Created & Modified (+1 more)

### Community 536 - "2. Technical Implementation & Compose Patterns"
Cohesion: 0.20
Nodes (9): 1. Existing Setup Architecture & Screen Consolidation, 2.1 Forward Swipe Lock Logic, 2.2 2-Column Card Grid Folder Browser, 2.3 Camera Live Status Pulse Aura Card, 2. Technical Implementation & Compose Patterns, 3. Build & Compilation Verification Strategy, Current Screen Structure (Legacy Separate Routes), Phase 2 Consolidated Architecture (`FluidWizardScreen.kt`) (+1 more)

### Community 537 - "2. Technical Implementation & Compose Patterns"
Cohesion: 0.20
Nodes (9): 1. Existing Setup Architecture & Screen Consolidation, 2.1 Forward Swipe Lock Logic, 2.2 2-Column Card Grid Folder Browser, 2.3 Camera Live Status Pulse Aura Card, 2. Technical Implementation & Compose Patterns, 3. Build & Compilation Verification Strategy, Current Screen Structure (Legacy Separate Routes), Phase 2 Consolidated Architecture (`FluidWizardScreen.kt`) (+1 more)

### Community 538 - "Assessment Scorecard (Scale 1-4)"
Cohesion: 0.20
Nodes (9): 1. Typography & Font Families, 2. Color Palette & Brand Consistency, 3. Spatial Rhythm & Layout, 4. Soft Depth & Elevation, 5. Responsive Layout, 6. Microinteractions & Touch Target States, Action Items, Assessment Scorecard (Scale 1-4) (+1 more)

### Community 539 - "Implementation Decisions Locked"
Cohesion: 0.20
Nodes (9): 1. Luminance Calculation Math, 2. Flash Misfire & Underexposure Classification (`UNDEREXPOSED_CRITICAL`), 3. Highlight Blowout Classification (`OVEREXPOSED_BLOWN`), 4. Color Profile & Film Simulation Standardization, 5. Verification & Performance Gate (`QUAL-01`), Canonical References, Domain Boundary, Implementation Decisions Locked (+1 more)

### Community 540 - "Implementation Decisions Locked"
Cohesion: 0.20
Nodes (9): 1. Storage Location & Lifecycle Policy, 2. Atomic Disk Commit & Zero-Byte Prevention, 3. USB Transport Mutex Priority Arbitration, 4. Coil Compose ImageLoader Architecture, 5. EXIF Orientation Normalization, Canonical References, Domain Boundary, Implementation Decisions Locked (+1 more)

### Community 541 - "Implementation Decisions Locked"
Cohesion: 0.20
Nodes (9): 1. Database Schema & Migration Strategy, 2. Default Session Culling Policy, 3. Quiet Luxury Visual Badges on Media Cards, 4. 1-Tap Kinetic Force-Upload Override Interaction, 5. Full-Screen Dark Inspection Modal & Live Histogram, Canonical References, Domain Boundary, Implementation Decisions Locked (+1 more)

### Community 542 - "3. Component Design Specifications"
Cohesion: 0.20
Nodes (9): 1. Visual Token Architecture, 2. Typography Hierarchy, 3. Component Design Specifications, 4. Accessibility & Microinteractions, A. Concentric `DoppelrandCard` Status Badges (`UI-01`), B. Responsive Gallery Filter Row (`UI-04`), C. 1-Tap Kinetic Force-Upload Override Button (`UI-02`), D. Full-Screen Dark Modal Inspection Viewer (`UI-03`) (+1 more)

### Community 543 - "Implementation Decisions Locked"
Cohesion: 0.20
Nodes (9): 1. Multi-Vendor Stress Testing Standards, 2. Memory Leak Profiling & Allocation Budget, 3. Code Review Quality Gate (`QUAL-02`), 4. UI Contract & Design Review Gate (`QUAL-03`), 5. Final Release Protocol, Canonical References, Domain Boundary, Implementation Decisions Locked (+1 more)

### Community 544 - "2. Work Packages & Plan Waves"
Cohesion: 0.20
Nodes (9): 1. Context & Architectural Insights (Forensic Lessons Learned), 2. Work Packages & Plan Waves, 3. Verification & Acceptance Criteria, Phase 14 Plan: Universal PTP Transport, Dual-Engine Storage, On-Device AI Culling & Resilient Pipeline, Wave 1: Direct USB PTP Transport & Hardware Buffer Drain (Priority 1), Wave 2: Dual-Engine Storage Architecture (PTP vs MSC Card Reader), Wave 3: Lossless Filename & Deduplication State Machine, Wave 4: On-Device AI Face/Eye Detection & Smart Culling Engine (+1 more)

### Community 545 - "2. Key Accomplishments"
Cohesion: 0.20
Nodes (9): 1. Executive Summary, 2.1 Wave 1: Direct PTP Buffer Drain & Anti-Lockout Handshake, 2.2 Wave 2: Dual-Engine Storage & Recursive DCIM Discovery, 2.3 Wave 3: Exact Case-Preserving Filename Deduplication, 2.4 Wave 4: On-Device AI Face/Blink & Quality Culling Engine, 2.5 Wave 5: Background Keep-Alive & Android 14/15 Compliance, 2. Key Accomplishments, 3. Test & Verification Coverage (+1 more)

### Community 546 - ".streamWithFallback"
Cohesion: 0.33
Nodes (7): DirectStreamingEngine, DirectStreamingResult, DirectSuccess, Failed, FallbackBuffered, bytesTransferred, totalBytes

### Community 548 - "CameraTetherAdapter"
Cohesion: 0.22
Nodes (3): CameraTetherAdapter, AutoCloseable, ByteArray

### Community 549 - "TetherState"
Cohesion: 0.22
Nodes (8): ActiveStreaming, CleanTerminated, Connecting, Disconnecting, Failed, Handshake, Idle, TetherState

### Community 550 - "DuplicateVerdict"
Cohesion: 0.22
Nodes (6): CameraSyncDuplicateDetector, AlreadyConfirmed, DuplicateVerdict, FailedRetryable, InProgress, NewMedia

### Community 551 - "CanonCameraInitializerTest"
Cohesion: 0.22
Nodes (5): CanonCameraInitializerTest, UsbDevice, UsbDeviceConnection, UsbEndpoint, UsbInterface

### Community 552 - "InstrumentationProxy.java"
Cohesion: 0.36
Nodes (5): Instrumentation, InstrumentationProxy, Activity, Bundle, Override

### Community 553 - "definitions"
Cohesion: 0.22
Nodes (9): $ref, additionalProperties, default, type, additionalProperties, default, type, definitions (+1 more)

### Community 554 - "definitions"
Cohesion: 0.22
Nodes (9): $ref, additionalProperties, default, type, additionalProperties, default, type, definitions (+1 more)

### Community 555 - "2. Root Cause Analysis: 3 Masalah Kritis TransferInFuji"
Cohesion: 0.22
Nodes (8): 1.1 Apa yang Sebenarnya Terbongkar vs Terproteksi, 1. Decompilation Authenticity & Limitations (Anti-Tamper / Packer Audit), 2. Root Cause Analysis: 3 Masalah Kritis TransferInFuji, 3. Rencana Perbaikan Sistematis (Remediation Roadmap), Bug 1: Gagal Membaca Data Existing dari Memori Kamera, Bug 2: Transformasi Nama File Menjadi Lowercase, Bug 3: Kamera Fuji Berkedip Setelah Jepretan Pertama & Gagal Dimatikan (Firmware Deadlock), Forensic Report: Decompilation Authenticity Assessment & Root Cause Analysis for TransferInFuji

### Community 556 - "Locked Design Decisions"
Cohesion: 0.22
Nodes (8): 1. Header Brand Identity (`RvmHeaderBar`), 2. Clickable Elegant Footer (`RvmFooterBar`), 3. Fluid Dynamic Dot Navigation Bar (`FluidDotNavigationBar`), 4. Double-Bezel Container (`DoppelrandCard`), 5. Kinetic Button Physics (`KineticButton`), Locked Design Decisions, Phase 1 Context & Decisions — Design System & Motion Foundations, Technical Constraints & Guidelines

### Community 557 - "Locked Design Decisions"
Cohesion: 0.22
Nodes (8): 1. Header Brand Identity (`RvmHeaderBar`), 2. Clickable Elegant Footer (`RvmFooterBar`), 3. Fluid Dynamic Dot Navigation Bar (`FluidDotNavigationBar`), 4. Double-Bezel Container (`DoppelrandCard`), 5. Kinetic Button Physics (`KineticButton`), Locked Design Decisions, Phase 1 Context & Decisions — Design System & Motion Foundations, Technical Constraints & Guidelines

### Community 558 - "Tasks"
Cohesion: 0.22
Nodes (8): Executive Summary, Phase 1 Execution Plan — Design System & Motion Foundations, Task 1: Refactor Brand Color Tokens & Typography System, Task 2: Build Double-Bezel Card & Kinetic Press Button, Task 3: Build Navigation & Branding Components, Task 4: Compilation & Quality Verification, Tasks, Verification Criteria

### Community 559 - "Tasks"
Cohesion: 0.22
Nodes (8): Executive Summary, Phase 1 Execution Plan — Design System & Motion Foundations, Task 1: Refactor Brand Color Tokens & Typography System, Task 2: Build Double-Bezel Card & Kinetic Press Button, Task 3: Build Navigation & Branding Components, Task 4: Compilation & Quality Verification, Tasks, Verification Criteria

### Community 560 - "Locked Design Decisions"
Cohesion: 0.22
Nodes (8): 1. Wizard Layout & Navigation Architecture (`FluidWizardScreen`), 2. Step Validation & Gesture Rules, 3. Step 1: Google Drive Auth & Folder Card Grid Browser, 4. Step 2: Camera Tethering & Real-Time Discovery Card, 5. Step 3: Session Preferences & Kinetic Launch Action, Locked Design Decisions, Phase 2 Context & Decisions — Fluid Swipeable Setup Wizard, Technical Constraints & Guidelines

### Community 561 - "Locked Design Decisions"
Cohesion: 0.22
Nodes (8): 1. Wizard Layout & Navigation Architecture (`FluidWizardScreen`), 2. Step Validation & Gesture Rules, 3. Step 1: Google Drive Auth & Folder Card Grid Browser, 4. Step 2: Camera Tethering & Real-Time Discovery Card, 5. Step 3: Session Preferences & Kinetic Launch Action, Locked Design Decisions, Phase 2 Context & Decisions — Fluid Swipeable Setup Wizard, Technical Constraints & Guidelines

### Community 562 - "Tasks"
Cohesion: 0.22
Nodes (8): Executive Summary, Phase 2 Execution Plan — Fluid Swipeable Setup Wizard, Task 1: Build Step 1 Components (OAuth Card & Card Grid Folder Browser), Task 2: Build Step 2 & Step 3 Wizard Screens, Task 3: Build Root `FluidWizardScreen` Container & Navigation Wiring, Task 4: Compilation & Quality Verification, Tasks, Verification Criteria

### Community 563 - "Tasks"
Cohesion: 0.22
Nodes (8): Executive Summary, Phase 2 Execution Plan — Fluid Swipeable Setup Wizard, Task 1: Build Step 1 Components (OAuth Card & Card Grid Folder Browser), Task 2: Build Step 2 & Step 3 Wizard Screens, Task 3: Build Root `FluidWizardScreen` Container & Navigation Wiring, Task 4: Compilation & Quality Verification, Tasks, Verification Criteria

### Community 564 - "Tasks"
Cohesion: 0.22
Nodes (8): Executive Summary, Phase 3 Execution Plan — Live Shoot Dashboard & Gallery Polish, Task 1: Refactor `SessionActiveScreen.kt` (Live Shoot Dashboard), Task 2: Refactor `UploadedGalleryScreen.kt` & Full-Screen Image Viewer Modal, Task 3: Refactor `SessionSummaryScreen.kt`, Task 4: Compilation Verification, Tasks, Verification Criteria

### Community 565 - "1. Existing Session & Gallery Codebase Analysis"
Cohesion: 0.22
Nodes (8): 1.1 `SessionActiveScreen.kt`, 1.2 `UploadedGalleryScreen.kt` & Full-Screen Image Viewer Modal, 1.3 `SessionSummaryScreen.kt`, 1. Existing Session & Gallery Codebase Analysis, 2. Technical Implementation Details, 3. Build & Compilation Verification Strategy, 4-Stat Metric Grid Component Snippet, Phase 3 Technical Research — Live Shoot Dashboard & Gallery Polish

### Community 566 - "Tasks"
Cohesion: 0.22
Nodes (8): Executive Summary, Phase 3 Execution Plan — Live Shoot Dashboard & Gallery Polish, Task 1: Refactor `SessionActiveScreen.kt` (Live Shoot Dashboard), Task 2: Refactor `UploadedGalleryScreen.kt` & Full-Screen Image Viewer Modal, Task 3: Refactor `SessionSummaryScreen.kt`, Task 4: Compilation Verification, Tasks, Verification Criteria

### Community 567 - "1. Existing Session & Gallery Codebase Analysis"
Cohesion: 0.22
Nodes (8): 1.1 `SessionActiveScreen.kt`, 1.2 `UploadedGalleryScreen.kt` & Full-Screen Image Viewer Modal, 1.3 `SessionSummaryScreen.kt`, 1. Existing Session & Gallery Codebase Analysis, 2. Technical Implementation Details, 3. Build & Compilation Verification Strategy, 4-Stat Metric Grid Component Snippet, Phase 3 Technical Research — Live Shoot Dashboard & Gallery Polish

### Community 568 - "Plan - Phase 6: Camera File Number Baseline Extraction"
Cohesion: 0.22
Nodes (8): Manual Verification, [MODIFY] [PhotoWatcher.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/usb/PhotoWatcher.kt), [MODIFY] [SessionViewModel.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionViewModel.kt), PhotoWatcher, Plan - Phase 6: Camera File Number Baseline Extraction, Proposed Changes, Session Presentation, Verification Plan

### Community 569 - "Work Accomplished"
Cohesion: 0.22
Nodes (8): 1. Room Database Schema Migration v10 $\to$ v11 (`CULL-05`), 2. Smart Culling Gate & Cloud Quota Protection (`CULL-05`), 3. Quiet Luxury Session Rules & Visual Badges (`UI-01`, `UI-04`), 4. 1-Tap Kinetic Force Upload Manual Override (`UI-02`), 5. Full-Screen Dark Inspection Modal & Live Histogram (`UI-03`), Phase 12 Summary: Smart Culling State Machine, Room Migration & Quiet Luxury Studio UI, Verification Results, Work Accomplished

### Community 570 - "Security Audit & OAuth Session Governance — TransferInFuji"
Cohesion: 0.22
Nodes (8): 1.1 Persistent OAuth Credential Storage (STRIDE Analysis), 1.2 Hardcoded / Manual Credential Entry vs. Dynamic OAuth Browser Authorization, 1. Threat Model & Vulnerability Analysis, 2.1 Authenticated State (`isAuthenticated == true`), 2.2 Unauthenticated State (`isAuthenticated == false`), 2. UI/UX & Security Architecture for Step 1 (`01 google drive`), 3. Verification & Compliance Checklist, Security Audit & OAuth Session Governance — TransferInFuji

### Community 571 - ".parseIfdForOrientation"
Cohesion: 0.39
Nodes (4): ExifOrientationHelper, ByteArray, ByteBuffer, ByteOrder

### Community 572 - "CameraVendor"
Cohesion: 0.29
Nodes (7): CameraVendor, CANON, FUJIFILM, GENERIC, NIKON, SONY, fromVendorId()

### Community 573 - "CullingPolicy"
Cohesion: 0.25
Nodes (6): CullingPolicy, AUTO_SKIP_UPLOAD, DISABLED, WARN_ONLY, Modifier, Step3SessionRules()

### Community 574 - "SyncTier"
Cohesion: 0.29
Nodes (6): DualTierSyncRouter, SyncPlan, SyncTier, SINGLE_TIER_DIRECT, TIER_1_PREVIEW, TIER_2_ARCHIVE

### Community 575 - ".processFrame"
Cohesion: 0.43
Nodes (7): BufferedLocal, CulledSkipped, Failed, FrameProcessingResult, ByteArray, ProceededWithoutUpload, Uploaded

### Community 578 - ".a"
Cohesion: 0.46
Nodes (4): BufferedInputStream, c, ClassLoader, Context

### Community 579 - "d"
Cohesion: 0.32
Nodes (5): c, DialogInterface, OnClickListener, d, Override

### Community 580 - "Field"
Cohesion: 0.39
Nodes (5): Constructor, Field, b, Context, Method

### Community 581 - "DebugProbesKt"
Cohesion: 0.43
Nodes (4): Continuation, Metadata, NotNull, DebugProbesKt

### Community 582 - "apk_unpacked/assets/flutter_assets/packages/flutter_image_compress_web/assets/pica.min.js"
Cohesion: 0.50
Nodes (6): a(), b(), i(), n(), o(), r()

### Community 583 - "WhiteScreenCollector"
Cohesion: 0.43
Nodes (5): Context(), delay(), fetchFinally(), _regeneratorRuntime(), WhiteScreenCollector()

### Community 584 - "apktool_out/assets/flutter_assets/packages/flutter_image_compress_web/assets/pica.min.js"
Cohesion: 0.50
Nodes (6): a(), b(), i(), n(), o(), r()

### Community 585 - "WhiteScreenCollector"
Cohesion: 0.43
Nodes (5): Context(), delay(), fetchFinally(), _regeneratorRuntime(), WhiteScreenCollector()

### Community 586 - "Utils"
Cohesion: 0.29
Nodes (3): integer, Method, Utils

### Community 587 - "1. Primary Findings & Anomaly Detection"
Cohesion: 0.25
Nodes (7): 1. Primary Findings & Anomaly Detection, 2. Complete Resolution Roadmap, Forensic Diagnostic Report: Google Chrome OAuth "Akses Diblokir / Access Blocked", Scientific Root Cause Analysis, Solution Option 1: Register `https://rvmeur.com/oauth2callback` in Web Client ID (Web OAuth Flow), Solution Option 2: Add Google Play Services Auth / Credential Manager (Native Android Dialog Flow - Best UX), Symptom

### Community 588 - "Test Cases"
Cohesion: 0.25
Nodes (7): Phase 3 UAT: Google Drive Integration & NAS Replacement, Summary, Test Case 1: Database Credentials Adaptor, Test Case 2: Google Drive API Client implementation, Test Case 3: Connection Setup and Folder Resolution, Test Case 4: Complete Compilation, Test Cases

### Community 589 - "1. Accomplished Features"
Cohesion: 0.25
Nodes (7): 1.1 `SessionActiveScreen.kt` (Live Shoot Dashboard), 1.2 `UploadedGalleryScreen.kt` & Full-Screen Image Viewer Modal, 1.3 `SessionSummaryScreen.kt` (End-of-Session Summary), 1. Accomplished Features, 2. Verification Results, 3. Files Modified, Phase 3 Summary — Live Shoot Dashboard & Gallery Polish

### Community 590 - "1. Accomplished Features"
Cohesion: 0.25
Nodes (7): 1.1 `SessionActiveScreen.kt` (Live Shoot Dashboard), 1.2 `UploadedGalleryScreen.kt` & Full-Screen Image Viewer Modal, 1.3 `SessionSummaryScreen.kt` (End-of-Session Summary), 1. Accomplished Features, 2. Verification Results, 3. Files Modified, Phase 3 Summary — Live Shoot Dashboard & Gallery Polish

### Community 591 - "Connection Layers"
Cohesion: 0.25
Nodes (7): Connection Layers, Manual Verification, [MODIFY] [CameraUnlockOrchestrator.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/usb/CameraUnlockOrchestrator.kt), [MODIFY] [UsbHelper.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/usb/UsbHelper.kt), Plan - Phase 8: Canon and Fuji Connection Polish, Proposed Changes, Verification Plan

### Community 592 - "Phase 5 Verification Report: Fujifilm Native Wired Tether Engine"
Cohesion: 0.25
Nodes (7): 1. Executive Summary, 2. Success Criteria Verification Matrix, 3. Automated Test Suite Execution Results, 4. Protocol Simulation & Binary Parsing Integrity Verification, 5. Verification Conclusion & Sign-Off, Phase 5 Verification Report: Fujifilm Native Wired Tether Engine, Test Breakdown

### Community 593 - "Phase 6 Verification Report: Canon EOS Direct PTP & Fast Preview Engine"
Cohesion: 0.25
Nodes (7): 1. Executive Summary, 2. Success Criteria Verification Matrix, 3. Automated Test Suite Execution Results, 4. Protocol Simulation & Binary Parsing Integrity Verification, 5. Verification Conclusion & Sign-Off, Phase 6 Verification Report: Canon EOS Direct PTP & Fast Preview Engine, Test Breakdown

### Community 594 - "Phase 7 Verification Report: Sony Alpha & Nikon Tether Adapters"
Cohesion: 0.25
Nodes (7): 1. Executive Summary, 2. Success Criteria Verification Matrix, 3. Automated Test Suite Execution Results, 4. Protocol Simulation & Binary Parsing Integrity Verification, 5. Verification Conclusion & Sign-Off, Phase 7 Verification Report: Sony Alpha & Nikon Tether Adapters, Test Breakdown

### Community 595 - "Phase 9 Code Review: Production Hardening, Codebase Pruning & Memory Safety Audit"
Cohesion: 0.25
Nodes (7): 1. Executive Summary, 2. Checkpoint Verification Matrix, 3.1 Concurrency & Mutex Discipline, 3.2 File Descriptor & Stream Management, 3. Detailed Architectural Findings, 4. Final Sign-off, Phase 9 Code Review: Production Hardening, Codebase Pruning & Memory Safety Audit

### Community 596 - "Phase 9 UI & Design System Audit: Quiet Luxury Visual & Performance Verification"
Cohesion: 0.25
Nodes (7): 1. Executive Summary, 2. Design Token & Visual Language Compliance, 3.1 Live Session HUD & Gallery (`SessionActiveScreen.kt`), 3.2 Cloud Sync & Transit Storage Indicators (`CameraSyncTracker.kt`), 3. Screen-Level Audit Results, 4. Final Sign-off, Phase 9 UI & Design System Audit: Quiet Luxury Visual & Performance Verification

### Community 597 - "Work Accomplished"
Cohesion: 0.25
Nodes (7): 1. Zero-Allocation Integer Fixed-Point Luminance Math (`CULL-01`), 2. Multi-Feature Exposure Classification (`CULL-02`, `CULL-03`), 3. Camera Picture Profile & Dynamic Calibration (`CULL-04`), 4. Verification Suite (`QUAL-01`), Phase 10 Summary: Zero-Allocation Exposure & Luminance Engine, Verification Results, Work Accomplished

### Community 598 - "Work Accomplished"
Cohesion: 0.25
Nodes (7): 1. Multi-Vendor Preview Extraction & Orientation Preservation (`THUMB-01`), 2. Atomic Disk Persistence & Lifecycle Pruning (`THUMB-02`), 3. Two-Tier Priority USB Mutex (`THUMB-03`), 4. Coil ImageLoader & Deterministic Caching (`THUMB-04`), Phase 11 Summary: Persistent Multi-Vendor Thumbnail Pipeline & USB Synchronization, Verification Results, Work Accomplished

### Community 599 - "additionalProperties"
Cohesion: 0.29
Nodes (7): anyOf, default, additionalProperties, type, type, additionalProperties, dependencies

### Community 600 - ".a"
Cohesion: 0.52
Nodes (4): f, ApplicationInfo, ClassLoader, ZipFile

### Community 602 - "Phases Shipped"
Cohesion: 0.29
Nodes (6): Accomplishments, Milestone v2.0: Fluid Wizard & Agency-Grade UI/UX Overhaul (Archived), Phase 1: Design System & Motion Foundations, Phase 2: Fluid Swipeable Setup Wizard, Phase 3: Live Shoot Dashboard & Gallery Polish, Phases Shipped

### Community 603 - "Test Cases"
Cohesion: 0.29
Nodes (6): Phase 1 UAT: Rebranding & Package Rename, Summary, Test Case 1: Package Directory Structure, Test Case 2: Codebase Package References, Test Case 3: Compilation and Build, Test Cases

### Community 604 - "Test Cases"
Cohesion: 0.29
Nodes (6): Phase 2 UAT: UI Style Refactoring (rvmeur.com Design), Summary, Test Case 1: Color Token Refactoring, Test Case 2: Typography Style & Custom Fonts, Test Case 3: Compilation Check, Test Cases

### Community 605 - "Phase 3 Research: Google Drive Integration & NAS Replacement"
Cohesion: 0.29
Nodes (6): 1. Google Drive OAuth2 flow:, 2. Google Drive Multipart Upload REST API:, Codebase Modification Impact, Context & Objective, Phase 3 Research: Google Drive Integration & NAS Replacement, Technical Design & API Specification

### Community 606 - "Phase 04 Summary: Zero-Leak Hardware Core & Resource Lifecycle"
Cohesion: 0.29
Nodes (6): 1. Executive Summary, 2. Artifacts Produced & Modified, 3. Verification Results, 4. Key Architectural Invariants Enforced, Phase 04 Summary: Zero-Leak Hardware Core & Resource Lifecycle, Unit Test Execution (`./gradlew test --rerun-tasks`)

### Community 607 - "Phase 5 Summary: Fujifilm Native Wired Tether Engine & Clean Disconnect Lifecycle"
Cohesion: 0.29
Nodes (6): 1. Overview & Architecture Delivery, 2. Automated Test Verification, 3. Git Changes, Delivered Components & Symbols:, Phase 5 Summary: Fujifilm Native Wired Tether Engine & Clean Disconnect Lifecycle, Test Suites:

### Community 608 - "Quality Gates Passed"
Cohesion: 0.29
Nodes (6): 1. Mandatory Code Review (`QUAL-02`), 2. Mandatory UI & Design Review (`QUAL-03`), 3. Mandatory Security & Threat Mitigation Audit, Final Verification Result, Phase 13 Summary: Production Hardening, Code Review & Quiet Luxury UI Verification, Quality Gates Passed

### Community 609 - "SEED-001: On-Device AI Exposure & Automated Culling Engine"
Cohesion: 0.29
Nodes (6): Breadcrumbs, Quality & Review Gates, Scope Estimate, SEED-001: On-Device AI Exposure & Automated Culling Engine, When to Surface, Why This Matters

### Community 610 - "Subagent Summoning & Spawning Protocol (No Generic 'self')"
Cohesion: 0.33
Nodes (5): 1. Strict Prohibition on Generic `self` Spawning, 2. Mandatory Pre-Planning & Milestone Research Subagents, 3. Subagent Definition & Invocation Protocol, 4. Mandatory Quality Gate & Lifecycle Skill Injections, Subagent Summoning & Spawning Protocol (No Generic 'self')

### Community 612 - ".initialize"
Cohesion: 0.33
Nodes (4): CameraSessionInitializer, MtpDevice, UsbDevice, UsbDeviceConnection

### Community 613 - "ThumbnailImageLoaderProvider"
Cohesion: 0.60
Nodes (3): Context, ThumbnailImageLoaderProvider, ImageLoader

### Community 616 - "stringArray"
Cohesion: 0.33
Nodes (6): stringArray, type, items, minItems, type, uniqueItems

### Community 617 - "stringArray"
Cohesion: 0.33
Nodes (6): stringArray, type, items, minItems, type, uniqueItems

### Community 619 - "c"
Cohesion: 0.47
Nodes (3): c, Context, Override

### Community 624 - "Completed Phases"
Cohesion: 0.33
Nodes (5): Completed Phases, Milestone v1.0 Archive — Rebranding & Google Drive Migration, Phase 1: Rebranding & Package Rename, Phase 2: UI Style Refactoring (rvmeur.com Design), Phase 3: Google Drive Integration & NAS Replacement

### Community 625 - "Phase 6 Research - Camera File Number Baseline Extraction"
Cohesion: 0.33
Nodes (5): 1. Directory Traversal & Storage Structure, 2. Filename Number Extraction, 3. Reset Prevention Anchors, 4. DB Query for Highest File Number, Phase 6 Research - Camera File Number Baseline Extraction

### Community 626 - "Phase 8: 5-Stage Camera-to-Cloud Sync & Direct USB Streaming (SUMMARY.md)"
Cohesion: 0.33
Nodes (5): 1. Summary of Accomplishments, 2. Verification & Testing Evidence, 3. Artifact Map & Key Files, Implemented Capabilities, Phase 8: 5-Stage Camera-to-Cloud Sync & Direct USB Streaming (SUMMARY.md)

### Community 627 - "Phase 9 Security & Privacy Audit: Android 14/15 Security & Credential Protection"
Cohesion: 0.33
Nodes (5): 1. Executive Summary, 2. Security Checkpoint Matrix, 3. Threat Model & Data Flow Verification, 4. Final Sign-off, Phase 9 Security & Privacy Audit: Android 14/15 Security & Credential Protection

### Community 630 - "thumbnailCompoundKey"
Cohesion: 0.50
Nodes (3): ThumbnailCacheKey, thumbnailCompoundKey(), ImageRequest

### Community 632 - "Project Handoff: Milestone v3.0 Shipped"
Cohesion: 0.40
Nodes (4): Infrastructure State, Milestone v3.0 Shipped Capabilities:, Project Handoff: Milestone v3.0 Shipped, Required Reading (in order)

### Community 633 - "schemaArray"
Cohesion: 0.40
Nodes (5): schemaArray, $ref, items, minItems, type

### Community 634 - "dependencies"
Cohesion: 0.40
Nodes (5): anyOf, additionalProperties, type, type, dependencies

### Community 635 - "schemaArray"
Cohesion: 0.40
Nodes (5): schemaArray, $ref, items, minItems, type

### Community 637 - "Phase 1 Research: Rebranding & Package Rename"
Cohesion: 0.40
Nodes (4): Codebase Analysis & Occurrences of "Reflection", Context & Objective, Migration Strategy, Phase 1 Research: Rebranding & Package Rename

### Community 638 - "Phase 2 Research: UI Style Refactoring (rvmeur.com Design)"
Cohesion: 0.40
Nodes (4): Context & Objective, Extracted Design Tokens, Implementation Plan, Phase 2 Research: UI Style Refactoring (rvmeur.com Design)

### Community 639 - "Milestone v3.0 Audit: Multi-Vendor Production Engine & Zero-Leak Tether Core"
Cohesion: 0.40
Nodes (4): 1. Requirements Traceability Matrix (25 / 25 Satisfied), 2. Cross-Phase Integration & Multi-Vendor E2E Verification, 3. Test Suite Audit, Milestone v3.0 Audit: Multi-Vendor Production Engine & Zero-Leak Tether Core

### Community 640 - "Phase 6 Summary: Canon EOS Direct PTP Engine & Fast Preview Extraction"
Cohesion: 0.40
Nodes (4): 1. Overview & Objectives Delivered, 2. Artifacts Created & Modified, 3. Verification & Test Results, Phase 6 Summary: Canon EOS Direct PTP Engine & Fast Preview Extraction

### Community 641 - "Phase 7 Summary: Sony Alpha & Nikon Multi-Vendor Tether Adapters"
Cohesion: 0.40
Nodes (4): 1. Executive Summary, 2. Requirements Verification Matrix, 3. Test Suite Results, Phase 7 Summary: Sony Alpha & Nikon Multi-Vendor Tether Adapters

### Community 645 - "enum"
Cohesion: 0.50
Nodes (4): minItems, type, uniqueItems, enum

### Community 646 - "multipleOf"
Cohesion: 0.50
Nodes (4): exclusiveMinimum, minimum, type, multipleOf

### Community 647 - "getPathByURL"
Cohesion: 0.50
Nodes (4): fixRelativeUrl(), getHashPath(), getPathByURL(), getResourceName()

### Community 648 - "enum"
Cohesion: 0.50
Nodes (4): minItems, type, uniqueItems, enum

### Community 649 - "multipleOf"
Cohesion: 0.50
Nodes (4): exclusiveMinimum, minimum, type, multipleOf

### Community 651 - "Session Handoff: Tethered Fuji RAF Gallery Thumbnailing & Live Sync"
Cohesion: 0.50
Nodes (3): Infrastructure State, Required Reading (in order), Session Handoff: Tethered Fuji RAF Gallery Thumbnailing & Live Sync

### Community 652 - "app/src/main/java/com/reflection/transferin/data/local/db/DatabaseProvider.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/data/local/db/DatabaseProvider.kt, ast_hash, mtime, semantic_hash

### Community 653 - "app/src/main/java/com/reflection/transferin/domain/session/SourceWatcher.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/reflection/transferin/domain/session/SourceWatcher.kt, ast_hash, mtime, semantic_hash

### Community 654 - "app/schemas/com.rvmeur.transferin.data.local.db.TransferInDatabase/8.json"
Cohesion: 0.50
Nodes (4): app/schemas/com.rvmeur.transferin.data.local.db.TransferInDatabase/8.json, ast_hash, mtime, semantic_hash

### Community 655 - "app/src/main/java/com/rvmeur/transferin/domain/session/ImportCameraEngine.kt"
Cohesion: 0.50
Nodes (4): app/src/main/java/com/rvmeur/transferin/domain/session/ImportCameraEngine.kt, ast_hash, mtime, semantic_hash

### Community 656 - "Phase 1 Summary — Design System & Motion Foundations"
Cohesion: 0.50
Nodes (3): Deliverables Completed, Phase 1 Summary — Design System & Motion Foundations, Verification

### Community 657 - "Phase 1 Summary — Design System & Motion Foundations"
Cohesion: 0.50
Nodes (3): Deliverables Completed, Phase 1 Summary — Design System & Motion Foundations, Verification

### Community 658 - "Phase 6 Context - Camera File Number Baseline Extraction"
Cohesion: 0.50
Nodes (3): Decisions, Open Questions, Phase 6 Context - Camera File Number Baseline Extraction

### Community 659 - "Phase 6 Summary - Camera File Number Baseline Extraction"
Cohesion: 0.50
Nodes (3): Accomplishments, Phase 6 Summary - Camera File Number Baseline Extraction, Verification Results

### Community 660 - "Phase 7 Context - Robust RAW+JPG Co-existence & Verification"
Cohesion: 0.50
Nodes (3): Decisions, Open Questions, Phase 7 Context - Robust RAW+JPG Co-existence & Verification

### Community 661 - "Phase 7 Research - Robust RAW+JPG Co-existence & Verification"
Cohesion: 0.50
Nodes (3): 1. Parallel Upload Feasibility & SFTP Pool Capacity, 2. Filename-Level Verification for Reconnection, Phase 7 Research - Robust RAW+JPG Co-existence & Verification

### Community 662 - "Phase 7 Summary - Robust RAW+JPG Co-existence & Verification"
Cohesion: 0.50
Nodes (3): Accomplishments, Phase 7 Summary - Robust RAW+JPG Co-existence & Verification, Verification Results

### Community 663 - "Phase 8 Context - Canon and Fuji Connection Polish"
Cohesion: 0.50
Nodes (3): Decisions, Open Questions, Phase 8 Context - Canon and Fuji Connection Polish

### Community 664 - "Phase 8 Research - Canon and Fuji Connection Polish"
Cohesion: 0.50
Nodes (3): 1. Close Session Order on Android MTP, 2. Syncing Override States to UsbHelper, Phase 8 Research - Canon and Fuji Connection Polish

### Community 665 - "Phase 4: Zero-Leak Hardware Core & Resource Lifecycle - Context"
Cohesion: 0.50
Nodes (3): Implementation Decisions, Phase 4: Zero-Leak Hardware Core & Resource Lifecycle - Context, Phase Boundary

### Community 666 - "Phase 8 Context: 5-Stage Camera-to-Cloud Sync & Direct USB Streaming"
Cohesion: 0.50
Nodes (3): Key Architectural Decisions, Phase 8 Context: 5-Stage Camera-to-Cloud Sync & Direct USB Streaming, Purpose & Scope

### Community 667 - "Phase 9 Context: Production Hardening, Codebase Pruning & Comprehensive Audits"
Cohesion: 0.50
Nodes (3): Key Tasks & Objectives, Phase 9 Context: Production Hardening, Codebase Pruning & Comprehensive Audits, Purpose & Scope

### Community 668 - "Phase 9: Production Hardening, Codebase Pruning & Comprehensive Audits (SUMMARY.md)"
Cohesion: 0.50
Nodes (3): 1. Summary of Accomplishments, 2. Test Execution Evidence, Phase 9: Production Hardening, Codebase Pruning & Comprehensive Audits (SUMMARY.md)

### Community 669 - "Phase 14 Verification: Universal PTP Transport, Storage & AI Culling Pipeline"
Cohesion: 0.50
Nodes (3): 1. Verification Checklist & Outcomes, 2. Quality Gate Verification, Phase 14 Verification: Universal PTP Transport, Storage & AI Culling Pipeline

### Community 670 - "Milestone v4.0 Audit: On-Device AI Exposure Analysis, Smart Culling & Reliable Thumbnailing Engine"
Cohesion: 0.50
Nodes (3): 1. Requirements Traceability Matrix, 2. Overall Milestone Verdict, Milestone v4.0 Audit: On-Device AI Exposure Analysis, Smart Culling & Reliable Thumbnailing Engine

### Community 671 - "addErrorHandlerIfEventEmitter"
Cohesion: 1.00
Nodes (3): addErrorHandlerIfEventEmitter(), eventTargetAgnosticAddListener(), once()

### Community 672 - "additionalItems"
Cohesion: 0.67
Nodes (3): anyOf, default, additionalItems

### Community 673 - "exclusiveMaximum"
Cohesion: 0.67
Nodes (3): default, type, exclusiveMaximum

### Community 674 - "exclusiveMinimum"
Cohesion: 0.67
Nodes (3): default, type, exclusiveMinimum

### Community 675 - "items"
Cohesion: 0.67
Nodes (3): anyOf, default, items

### Community 676 - "pattern"
Cohesion: 0.67
Nodes (3): format, type, pattern

### Community 677 - "uniqueItems"
Cohesion: 0.67
Nodes (3): uniqueItems, default, type

### Community 678 - "getType"
Cohesion: 1.00
Nodes (3): getType(), toRegFormat(), transStrToReg()

### Community 679 - "additionalItems"
Cohesion: 0.67
Nodes (3): anyOf, default, additionalItems

### Community 680 - "exclusiveMaximum"
Cohesion: 0.67
Nodes (3): default, type, exclusiveMaximum

### Community 681 - "exclusiveMinimum"
Cohesion: 0.67
Nodes (3): default, type, exclusiveMinimum

### Community 682 - "items"
Cohesion: 0.67
Nodes (3): anyOf, default, items

### Community 683 - "pattern"
Cohesion: 0.67
Nodes (3): format, type, pattern

### Community 684 - "uniqueItems"
Cohesion: 0.67
Nodes (3): uniqueItems, default, type

## Knowledge Gaps
- **3063 isolated node(s):** `mtime`, `ast_hash`, `semantic_hash`, `mtime`, `ast_hash` (+3058 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **105 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `UsbHelper` connect `UsbHelper` to `CameraGalleryViewModel`, `.requestPermission`, `.listMedia`, `.initialize`, `.getAllObjectHandlesNoLock`, `CanonCameraInitializer`, `.planning/codebase/STRUCTURE.md`, `TransferInApp`, `.buildMonitorState`, `DiscoveredFile`, `UsbTetherService`, `CameraMedia`?**
  _High betweenness centrality (0.037) - this node is a cross-community bridge._
- **Why does `TransferInApp()` connect `TransferInApp` to `CameraGalleryViewModel`, `SelectFolderScreen`, `SessionRepositoryAdapter`, `GalleryViewModel`, `ProjectSession`, `rvmeur/transferin/feature/gallery/CameraGalleryScreen.kt`, `SessionSummaryScreen`, `UsbHelper`, `DoppelrandCard`, `NasConnectionRepositoryImpl`, `DelegatingSftpClient`, `FluidWizardScreen`, `DocumentFileScanner`, `DiscoveredFile`, `.create`, `UsbSourceOrchestrator`, `CameraRuntimeState`, `ConnectCameraScreen`?**
  _High betweenness centrality (0.031) - this node is a cross-community bridge._
- **Why does `Step3SessionRules()` connect `CullingPolicy` to `DoppelrandCard`, `FluidWizardScreen`, `KineticButton`?**
  _High betweenness centrality (0.019) - this node is a cross-community bridge._
- **What connects `mtime`, `ast_hash`, `semantic_hash` to the rest of the system?**
  _3063 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `USB Camera & MTP` be split into smaller, more focused modules?**
  _Cohesion score 0.005025125628140704 - nodes in this community are weakly interconnected._
- **Should `SFTP & Remote Operations` be split into smaller, more focused modules?**
  _Cohesion score 0.045454545454545456 - nodes in this community are weakly interconnected._
- **Should `Document Scanner` be split into smaller, more focused modules?**
  _Cohesion score 0.02040816326530612 - nodes in this community are weakly interconnected._