# Graph Report - .  (2026-07-31)

## Corpus Check
- 154 files · ~86,787 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1153 nodes · 1843 edges · 66 communities (50 shown, 16 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 67 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

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
- Community 15
- Community 16
- Community 17
- Community 18
- Community 19
- Community 20
- Community 21
- Community 22
- Community 23
- Community 24
- Community 25
- Community 26
- Community 27
- Community 28
- Community 29
- Community 30
- Community 31
- Community 32
- Community 33
- Community 34
- Community 35
- Community 36
- Community 37
- Community 38
- Community 39
- Community 40
- Community 41
- Community 42
- Community 43
- Community 44
- Community 45
- Community 46
- Community 47
- Community 48
- Community 49
- Community 50
- Community 51
- Community 52
- Community 53
- Community 54
- Community 55
- Community 56
- Community 63
- Community 64

## God Nodes (most connected - your core abstractions)
1. `UsbHelper` - 62 edges
2. `CameraMediaItemEntity` - 39 edges
3. `TransferQueueItemEntity` - 37 edges
4. `TransferQueueDao` - 35 edges
5. `SessionViewModel` - 34 edges
6. `CameraMediaRepositoryImpl` - 33 edges
7. `TransferQueueRepositoryImpl` - 33 edges
8. `CameraMediaDao` - 31 edges
9. `SessionEntity` - 30 edges
10. `CameraMediaRepository` - 30 edges

## Surprising Connections (you probably didn't know these)
- `Technical Documentation` --conceptually_related_to--> `Architecture Research`  [INFERRED]
  DOCUMENTATION.md → .planning/research/ARCHITECTURE.md
- `TransferInApp()` --calls--> `NasConnectionRepositoryImpl`  [INFERRED]
  app/src/main/java/com/reflection/transferin/MainActivity.kt → app/src/main/java/com/reflection/transferin/data/repository/NasConnectionRepositoryImpl.kt
- `TransferInApp()` --calls--> `JschSftpClient`  [INFERRED]
  app/src/main/java/com/reflection/transferin/MainActivity.kt → app/src/main/java/com/reflection/transferin/data/sftp/JschSftpClient.kt
- `TransferInApp()` --calls--> `FileStabilityChecker`  [INFERRED]
  app/src/main/java/com/reflection/transferin/MainActivity.kt → app/src/main/java/com/reflection/transferin/data/usb/FileStabilityChecker.kt
- `TransferInApp()` --calls--> `UsbSourceOrchestrator`  [INFERRED]
  app/src/main/java/com/reflection/transferin/MainActivity.kt → app/src/main/java/com/reflection/transferin/data/usb/UsbSourceOrchestrator.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **v1.0 Complete Roadmap Structure** — project_transferin_milestone_v1_0, phase_01_ctg_foundation, phase_02_smart_upload_detection, phase_03_session_resilience, phase_04_upload_integrity, requirement_ctg_01, requirement_ctg_02, requirement_ctg_03, requirement_upload_01, requirement_upload_02, requirement_upload_03, requirement_resilience_01, requirement_resilience_02, requirement_resilience_03, requirement_resilience_04 [EXTRACTED 1.00]
- **Phase 1 CTG Foundation Plans** — plan_01_01, plan_01_02, research_01 [EXTRACTED 1.00]
- **Phase 2 Smart Upload Plans** — plan_02_01, plan_02_02, research_02 [EXTRACTED 1.00]
- **Phase 3 Session Resilience Plans** — plan_03_01, plan_03_02 [EXTRACTED 1.00]
- **Complete Technology Stack** — tech_kotlin, tech_jetpack_compose, tech_room, tech_workmanager, tech_jsch [EXTRACTED 1.00]
- **Complete Codebase Documentation** — planning_codebase_architecture, planning_codebase_concerns, planning_codebase_conventions, planning_codebase_integrations, planning_codebase_stack, planning_codebase_structure, planning_codebase_testing [EXTRACTED 1.00]
- **Architecture Patterns** — arch_clean_architecture, arch_mvvm, pattern_immutable_snapshot, pattern_fingerprint, pattern_session_pool [EXTRACTED 1.00]
- **Phase 03 Session Resilience Research Package** — planning_phases_03_session_resilience_03_research, planning_phases_03_session_resilience_03_03_plan, planning_phases_03_session_resilience_03_04_plan, concept_session_pooling, concept_keepalive, concept_exponential_backoff, concept_sftp_circuit_breaker, concept_usb_disconnect_handling [EXTRACTED 1.00]
- **Phase 04 Upload Integrity Research Package** — planning_phases_04_upload_integrity_research, planning_phases_04_upload_integrity_04_01_plan, planning_phases_04_upload_integrity_04_02_plan, concept_two_phase_commit, concept_checkpoint_persistence, concept_atomic_rename, concept_size_verification, concept_stale_part_file_cleanup [EXTRACTED 1.00]
- **Core Technology Stack** — library_jsch_mwiede, library_room, library_workmanager [EXTRACTED 1.00]
- **Domain Pitfalls** — planning_research_pitfalls, concept_mtp_handle_staleness, concept_db_sftp_state_divergence [EXTRACTED 1.00]

## Communities (66 total, 16 thin omitted)

### Community 0 - "USB Camera & MTP"
Cohesion: 0.06
Nodes (14): CameraSessionState, Intent, MtpDevice, StateFlow, UsbDevice, UsbDeviceConnection, UsbEndpoint, UsbInterface (+6 more)

### Community 1 - "SFTP & Remote Operations"
Cohesion: 0.05
Nodes (19): JschSftpClient, bytesTransferred, Result, T, totalBytes, Uri, bytesTransferred, Result (+11 more)

### Community 2 - "Transfer Session UI"
Cohesion: 0.08
Nodes (28): MediaJob, UploadStatus, FAILED, QUEUED, SUCCESS, UPLOADING, ActionButtonsArea(), ActiveIndicator() (+20 more)

### Community 3 - "Camera Initialization"
Cohesion: 0.07
Nodes (21): CameraInitContext, CameraInitResult, fail(), ok(), CameraSessionInitializer, MtpDevice, UsbDevice, UsbDeviceConnection (+13 more)

### Community 4 - "Database & NAS Connection"
Cohesion: 0.05
Nodes (17): NasConnectionDao, DatabaseProvider, TransferInDatabase, NasConnectionEntity, NasConnectionRepositoryImpl, NasConnection, NasConnectionRepository, Modifier (+9 more)

### Community 5 - "Gallery UI"
Cohesion: 0.07
Nodes (30): FilterMode, ALL, FAILED, PHOTOS, UPLOADED, VIDEOS, GalleryItem, GalleryUiState (+22 more)

### Community 6 - "Document Scanner"
Cohesion: 0.07
Nodes (32): DocumentFileScanner, Uri, NodeToScan, ScannedDocument, Modifier, SelectSourceFolderScreen(), CameraGalleryScreen(), EmptyState() (+24 more)

### Community 7 - "Transfer Queue"
Cohesion: 0.07
Nodes (8): newDetected(), TransferQueueItem, Flow, TransferSessionManager, SessionRuntimeState, Flow, StateFlow, TransferSessionManagerImpl

### Community 8 - "Architecture & Phase Planning"
Cohesion: 0.05
Nodes (43): Clean Architecture, MVVM Pattern, Fingerprint Pattern, Immutable Snapshot Pattern, Session Pooling Pattern, Phase 1 CTG Foundation, Phase 2 Smart Upload Detection, Phase 3 Session Resilience (+35 more)

### Community 9 - "File Stability Checker"
Cohesion: 0.08
Nodes (16): FileStabilityChecker, Uri, UsbFileInfo, UsbFileInfoProvider, CameraPickItem, Uri, PollResult, SnapshotSignature (+8 more)

### Community 10 - "Camera Unlock Orchestrator"
Cohesion: 0.11
Nodes (15): CameraUnlockOrchestrator, CameraUnlockState, MtpDevice, StateFlow, UsbDevice, UsbDeviceConnection, UsbManager, UnlockStatus (+7 more)

### Community 11 - "Camera Media DAO"
Cohesion: 0.09
Nodes (3): CameraMediaDao, Flow, CameraMediaItemEntity

### Community 12 - "Camera Media Repository"
Cohesion: 0.08
Nodes (4): CameraMediaRepository, com, Flow, java

### Community 13 - "Connect Camera Screen"
Cohesion: 0.10
Nodes (10): CameraStatusCard(), ConnectCameraScreen(), ImportModeCard(), Modifier, TetheredReadyCard(), UsbPermissionCard(), ConnectCameraUiState, ConnectCameraViewModel (+2 more)

### Community 15 - "Community 15"
Cohesion: 0.11
Nodes (3): Flow, SessionDao, SessionEntity

### Community 18 - "Community 18"
Cohesion: 0.15
Nodes (10): AutoStorageDetector, Uri, StorageLocation, StorageType, DCIM, DOWNLOAD, OTHER, PICTURES (+2 more)

### Community 19 - "Community 19"
Cohesion: 0.10
Nodes (23): Atomic Rename, Checkpoint Persistence, Canon CTG Database Parsing, Database vs SFTP State Divergence, Keepalive Heartbeat, MTP Handle Staleness, Session Pooling, Remote Size Verification (+15 more)

### Community 20 - "Community 20"
Cohesion: 0.18
Nodes (8): Job, SharedFlow, StateFlow, Uri, NewPhotoEvent, PhotoMeta, PhotoWatcher, PhotoWatcherState

### Community 23 - "Community 23"
Cohesion: 0.18
Nodes (10): AlreadyHasPermission, Denied, Granted, Intent, StateFlow, UsbDevice, UsbManager, PermissionResult (+2 more)

### Community 24 - "Community 24"
Cohesion: 0.21
Nodes (4): CameraDiscoveryEngine, CtgState, DiscoveryResult, Uri

### Community 25 - "Community 25"
Cohesion: 0.14
Nodes (10): CameraGalleryState, CameraGalleryViewModel, create(), AndroidViewModel, Job, StateFlow, CameraGalleryViewModelFactory, T (+2 more)

### Community 26 - "Community 26"
Cohesion: 0.18
Nodes (7): Intent, Job, UsbDevice, UsbTetherService, IBinder, Notification, Service

### Community 27 - "Community 27"
Cohesion: 0.13
Nodes (7): MediaStatus, CACHED, DISCOVERED, FAILED, QUEUED, UPLOADED, UPLOADING

### Community 28 - "Community 28"
Cohesion: 0.23
Nodes (7): CtgChangeEvent, CtgFileMeta, CtgWatcher, CtgWatcherState, Job, SharedFlow, StateFlow

### Community 29 - "Community 29"
Cohesion: 0.15
Nodes (8): ChangeEvent, Error, Flow, Uri, MtpContentObserver, NewFileDetected, SystemNotified, ContentObserver

### Community 30 - "Community 30"
Cohesion: 0.29
Nodes (4): CanonPtpEnumerator, CanonPtpObjectInfo, ByteArray, ByteBuffer

### Community 31 - "Community 31"
Cohesion: 0.17
Nodes (9): SessionState, CANCELLED, COMPLETED, FAILED, IDLE, RUNNING, STARTING, STOPPING_INTAKE (+1 more)

### Community 32 - "Community 32"
Cohesion: 0.18
Nodes (5): ConnectionMode, IMPORT_FALLBACK, TETHERED, Flow, ProjectSessionManager

### Community 33 - "Community 33"
Cohesion: 0.17
Nodes (11): TransferItemStatus, CANCELLED, DETECTED, DISPATCHED, FAILED, QUEUED, RETRY_SCHEDULED, SKIPPED_DUPLICATE (+3 more)

### Community 34 - "Community 34"
Cohesion: 0.17
Nodes (5): Flow, SessionRepositoryAdapter, T, ViewModelProvider, SelectFolderViewModelFactory

### Community 35 - "Community 35"
Cohesion: 0.33
Nodes (4): Result, Uri, TransferUploadWorker, CoroutineWorker

### Community 36 - "Community 36"
Cohesion: 0.18
Nodes (9): WatcherStatus, ENQUEUEING, ERROR, IDLE, PAUSED, SCANNING, STARTING, STOPPED (+1 more)

### Community 37 - "Community 37"
Cohesion: 0.24
Nodes (3): CameraRuntimeState, CameraConnectionCoordinator, StateFlow

### Community 38 - "Community 38"
Cohesion: 0.22
Nodes (3): ProjectSession, Flow, ProjectSessionManagerImpl

### Community 39 - "Community 39"
Cohesion: 0.33
Nodes (3): Uri, SourceMetadata, UploadQueueManager

### Community 40 - "Community 40"
Cohesion: 0.20
Nodes (4): ImportCameraEngine, com, Job, StateFlow

### Community 41 - "Community 41"
Cohesion: 0.27
Nodes (3): Job, StateFlow, ShootModeEngine

### Community 42 - "Community 42"
Cohesion: 0.43
Nodes (4): CanonPtpTransport, ByteArray, IntArray, PtpResponse

### Community 44 - "Community 44"
Cohesion: 0.38
Nodes (6): FileType, JPEG, OTHER, RAW, fromFileName(), fromMimeType()

### Community 46 - "Community 46"
Cohesion: 0.33
Nodes (3): CameraEngine, Flow, StateFlow

### Community 47 - "Community 47"
Cohesion: 0.40
Nodes (3): ConnectCameraViewModelFactory, T, ViewModelProvider

### Community 48 - "Community 48"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 51 - "Community 51"
Cohesion: 1.00
Nodes (3): Exponential Backoff, SftpCircuitBreaker, 03-03-PLAN: SFTP Session Reconnection with Exponential Backoff

## Knowledge Gaps
- **112 isolated node(s):** `DISCOVERED`, `CACHED`, `QUEUED`, `UPLOADING`, `UPLOADED` (+107 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **16 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `UsbHelper` connect `USB Camera & MTP` to `Camera Initialization`, `Document Scanner`, `File Stability Checker`, `Community 25`, `Community 26`?**
  _High betweenness centrality (0.235) - this node is a cross-community bridge._
- **Why does `TransferInApp()` connect `Document Scanner` to `USB Camera & MTP`, `SFTP & Remote Operations`, `Community 34`, `Transfer Session UI`, `Database & NAS Connection`, `Gallery UI`, `Community 40`, `File Stability Checker`, `Connect Camera Screen`, `Community 47`, `Community 25`?**
  _High betweenness centrality (0.230) - this node is a cross-community bridge._
- **Why does `SessionRepositoryImpl` connect `Community 22` to `Community 32`, `Community 34`, `Community 36`, `File Stability Checker`, `Community 47`, `Community 15`, `Community 31`?**
  _High betweenness centrality (0.122) - this node is a cross-community bridge._
- **What connects `DISCOVERED`, `CACHED`, `QUEUED` to the rest of the system?**
  _112 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `USB Camera & MTP` be split into smaller, more focused modules?**
  _Cohesion score 0.06293706293706294 - nodes in this community are weakly interconnected._
- **Should `SFTP & Remote Operations` be split into smaller, more focused modules?**
  _Cohesion score 0.05388471177944862 - nodes in this community are weakly interconnected._
- **Should `Transfer Session UI` be split into smaller, more focused modules?**
  _Cohesion score 0.07993966817496229 - nodes in this community are weakly interconnected._