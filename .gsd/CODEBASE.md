# Codebase Map

Generated: 2026-08-08T15:13:44Z | Files: 310 | Described: 0/310
<!-- gsd:codebase-meta {"generatedAt":"2026-08-08T15:13:44Z","fingerprint":"ebda54d96f86e3467c3428819e8f860dd8e8fc0a","fileCount":310,"truncated":false} -->

### (root)/
- `.gitignore`
- `build.gradle.kts`
- `DOCUMENTATION.md`
- `gradle.properties`
- `gradlew`
- `gradlew.bat`
- `settings.gradle.kts`

### app/
- `app/.gitignore`
- `app/build.gradle.kts`

### app/schemas/com.reflection.transferin.data.local.db.TransferInDatabase/
- `app/schemas/com.reflection.transferin.data.local.db.TransferInDatabase/8.json`
- `app/schemas/com.reflection.transferin.data.local.db.TransferInDatabase/9.json`

### app/src/androidTest/java/com/reflection/transferin/
- `app/src/androidTest/java/com/reflection/transferin/ExampleInstrumentedTest.kt`

### app/src/androidTest/java/com/reflection/transferin/data/local/db/
- `app/src/androidTest/java/com/reflection/transferin/data/local/db/DatabaseMigrationTest.kt`

### app/src/main/
- `app/src/main/AndroidManifest.xml`

### app/src/main/java/com/reflection/transferin/
- `app/src/main/java/com/reflection/transferin/MainActivity.kt`

### app/src/main/java/com/reflection/transferin/data/local/dao/
- `app/src/main/java/com/reflection/transferin/data/local/dao/CameraMediaDao.kt`
- `app/src/main/java/com/reflection/transferin/data/local/dao/NasConnectionDao.kt`
- `app/src/main/java/com/reflection/transferin/data/local/dao/SessionDao.kt`
- `app/src/main/java/com/reflection/transferin/data/local/dao/TransferQueueDao.kt`
- `app/src/main/java/com/reflection/transferin/data/local/dao/UploadedFingerprintDao.kt`

### app/src/main/java/com/reflection/transferin/data/local/db/
- `app/src/main/java/com/reflection/transferin/data/local/db/DatabaseProvider.kt`
- `app/src/main/java/com/reflection/transferin/data/local/db/Migration_8_9.kt`
- `app/src/main/java/com/reflection/transferin/data/local/db/TransferInDatabase.kt`

### app/src/main/java/com/reflection/transferin/data/local/entity/
- `app/src/main/java/com/reflection/transferin/data/local/entity/CameraMediaItemEntity.kt`
- `app/src/main/java/com/reflection/transferin/data/local/entity/NasConnectionEntity.kt`
- `app/src/main/java/com/reflection/transferin/data/local/entity/SessionEntity.kt`
- `app/src/main/java/com/reflection/transferin/data/local/entity/TransferQueueItemEntity.kt`
- `app/src/main/java/com/reflection/transferin/data/local/entity/UploadedFingerprintEntity.kt`

### app/src/main/java/com/reflection/transferin/data/repository/
- `app/src/main/java/com/reflection/transferin/data/repository/CameraMediaRepositoryImpl.kt`
- `app/src/main/java/com/reflection/transferin/data/repository/NasConnectionRepositoryImpl.kt`
- `app/src/main/java/com/reflection/transferin/data/repository/SessionRepositoryImpl.kt`
- `app/src/main/java/com/reflection/transferin/data/repository/TransferQueueRepositoryImpl.kt`
- `app/src/main/java/com/reflection/transferin/data/repository/UploadedFingerprintRepositoryImpl.kt`

### app/src/main/java/com/reflection/transferin/data/sftp/
- `app/src/main/java/com/reflection/transferin/data/sftp/DelegatingSftpClient.kt`
- `app/src/main/java/com/reflection/transferin/data/sftp/JschSftpClient.kt`
- `app/src/main/java/com/reflection/transferin/data/sftp/SftpClient.kt`
- `app/src/main/java/com/reflection/transferin/data/sftp/SftpConnectionConfig.kt`
- `app/src/main/java/com/reflection/transferin/data/sftp/SftpSessionHealthMonitor.kt`
- `app/src/main/java/com/reflection/transferin/data/sftp/SftpSessionPool.kt`
- `app/src/main/java/com/reflection/transferin/data/sftp/SftpSessionReconnector.kt`
- `app/src/main/java/com/reflection/transferin/data/sftp/TwoPhaseUploadHandler.kt`
- `app/src/main/java/com/reflection/transferin/data/sftp/UploadCheckpointManager.kt`

### app/src/main/java/com/reflection/transferin/data/storage/
- `app/src/main/java/com/reflection/transferin/data/storage/AutoStorageDetector.kt`

### app/src/main/java/com/reflection/transferin/data/usb/
- `app/src/main/java/com/reflection/transferin/data/usb/CameraDiscoveryEngine.kt`
- `app/src/main/java/com/reflection/transferin/data/usb/CameraInitContext.kt`
- `app/src/main/java/com/reflection/transferin/data/usb/CameraInitResult.kt`
- `app/src/main/java/com/reflection/transferin/data/usb/CameraSessionInitializer.kt`
- `app/src/main/java/com/reflection/transferin/data/usb/CameraUnlockOrchestrator.kt`
- `app/src/main/java/com/reflection/transferin/data/usb/CtgWatcher.kt`
- `app/src/main/java/com/reflection/transferin/data/usb/DefaultCameraSessionInitializer.kt`
- `app/src/main/java/com/reflection/transferin/data/usb/DocumentFileScanner.kt`
- `app/src/main/java/com/reflection/transferin/data/usb/FileStabilityChecker.kt`
- `app/src/main/java/com/reflection/transferin/data/usb/MtpContentObserver.kt`
- `app/src/main/java/com/reflection/transferin/data/usb/PhotoWatcher.kt`
- `app/src/main/java/com/reflection/transferin/data/usb/UsbHelper.kt`
- `app/src/main/java/com/reflection/transferin/data/usb/UsbPermissionManager.kt`
- `app/src/main/java/com/reflection/transferin/data/usb/UsbSourceOrchestrator.kt`

### app/src/main/java/com/reflection/transferin/data/webdav/
- `app/src/main/java/com/reflection/transferin/data/webdav/WebDavClient.kt`

### app/src/main/java/com/reflection/transferin/data/worker/
- `app/src/main/java/com/reflection/transferin/data/worker/TransferUploadWorkScheduler.kt`

### app/src/main/java/com/reflection/transferin/domain/model/
- `app/src/main/java/com/reflection/transferin/domain/model/CameraMedia.kt`
- `app/src/main/java/com/reflection/transferin/domain/model/CameraRuntimeState.kt`
- `app/src/main/java/com/reflection/transferin/domain/model/ConnectionMode.kt`
- `app/src/main/java/com/reflection/transferin/domain/model/FileType.kt`
- `app/src/main/java/com/reflection/transferin/domain/model/MediaJob.kt`
- `app/src/main/java/com/reflection/transferin/domain/model/ProjectSession.kt`
- `app/src/main/java/com/reflection/transferin/domain/model/SessionState.kt`
- `app/src/main/java/com/reflection/transferin/domain/model/SourceSelectionMode.kt`
- `app/src/main/java/com/reflection/transferin/domain/model/TransferItemStatus.kt`
- `app/src/main/java/com/reflection/transferin/domain/model/TransferQueueItem.kt`
- `app/src/main/java/com/reflection/transferin/domain/model/WatcherStatus.kt`

### app/src/main/java/com/reflection/transferin/domain/repository/
- `app/src/main/java/com/reflection/transferin/domain/repository/CameraMediaRepository.kt`
- `app/src/main/java/com/reflection/transferin/domain/repository/NasConnectionRepository.kt`
- `app/src/main/java/com/reflection/transferin/domain/repository/SessionRepository.kt`
- `app/src/main/java/com/reflection/transferin/domain/repository/SessionRepositoryAdapter.kt`
- `app/src/main/java/com/reflection/transferin/domain/repository/TransferQueueRepository.kt`
- `app/src/main/java/com/reflection/transferin/domain/repository/UploadedFingerprintRepository.kt`

### app/src/main/java/com/reflection/transferin/domain/session/
- `app/src/main/java/com/reflection/transferin/domain/session/CameraConnectionCoordinator.kt`
- `app/src/main/java/com/reflection/transferin/domain/session/CameraEngine.kt`
- `app/src/main/java/com/reflection/transferin/domain/session/FileClassifier.kt`
- `app/src/main/java/com/reflection/transferin/domain/session/ImportCameraEngine.kt`
- `app/src/main/java/com/reflection/transferin/domain/session/ProjectSessionManager.kt`
- `app/src/main/java/com/reflection/transferin/domain/session/ProjectSessionManagerImpl.kt`
- `app/src/main/java/com/reflection/transferin/domain/session/RemoteFolder.kt`
- `app/src/main/java/com/reflection/transferin/domain/session/SourceWatcher.kt`
- `app/src/main/java/com/reflection/transferin/domain/session/TransferSessionManager.kt`
- `app/src/main/java/com/reflection/transferin/domain/session/UploadQueueManager.kt`

### app/src/main/java/com/reflection/transferin/domain/shoot/
- `app/src/main/java/com/reflection/transferin/domain/shoot/CtgDatabaseParser.kt`
- `app/src/main/java/com/reflection/transferin/domain/shoot/CtgMediaEntry.kt`
- `app/src/main/java/com/reflection/transferin/domain/shoot/CtgMediaSnapshot.kt`
- `app/src/main/java/com/reflection/transferin/domain/shoot/CtgStateDiffer.kt`
- `app/src/main/java/com/reflection/transferin/domain/shoot/Fingerprint.kt`
- `app/src/main/java/com/reflection/transferin/domain/shoot/NewShotDetector.kt`
- `app/src/main/java/com/reflection/transferin/domain/shoot/ShootModeEngine.kt`
- `app/src/main/java/com/reflection/transferin/domain/shoot/SmartUploadFilter.kt`

### app/src/main/java/com/reflection/transferin/domain/usecase/
- `app/src/main/java/com/reflection/transferin/domain/usecase/ListCameraMediaUseCase.kt`

### app/src/main/java/com/reflection/transferin/feature/auth/
- `app/src/main/java/com/reflection/transferin/feature/auth/LoginNasScreen.kt`
- `app/src/main/java/com/reflection/transferin/feature/auth/LoginNasViewModel.kt`
- `app/src/main/java/com/reflection/transferin/feature/auth/LoginNasViewModelFactory.kt`

### app/src/main/java/com/reflection/transferin/feature/connectcamera/
- `app/src/main/java/com/reflection/transferin/feature/connectcamera/ConnectCameraScreen.kt`
- `app/src/main/java/com/reflection/transferin/feature/connectcamera/ConnectCameraViewModel.kt`
- `app/src/main/java/com/reflection/transferin/feature/connectcamera/ConnectCameraViewModelFactory.kt`

### app/src/main/java/com/reflection/transferin/feature/folder/
- `app/src/main/java/com/reflection/transferin/feature/folder/FolderUiState.kt`
- `app/src/main/java/com/reflection/transferin/feature/folder/SelectFolderScreen.kt`
- `app/src/main/java/com/reflection/transferin/feature/folder/SelectFolderViewModel.kt`
- `app/src/main/java/com/reflection/transferin/feature/folder/SelectFolderViewModelFactory.kt`
- `app/src/main/java/com/reflection/transferin/feature/folder/SelectSourceFolderScreen.kt`

### app/src/main/java/com/reflection/transferin/feature/gallery/
- `app/src/main/java/com/reflection/transferin/feature/gallery/CameraGalleryScreen.kt`
- `app/src/main/java/com/reflection/transferin/feature/gallery/CameraGalleryViewModel.kt`
- `app/src/main/java/com/reflection/transferin/feature/gallery/CameraGalleryViewModelFactory.kt`
- `app/src/main/java/com/reflection/transferin/feature/gallery/GalleryUiState.kt`
- `app/src/main/java/com/reflection/transferin/feature/gallery/GalleryViewModel.kt`
- `app/src/main/java/com/reflection/transferin/feature/gallery/GalleryViewModelFactory.kt`
- `app/src/main/java/com/reflection/transferin/feature/gallery/UploadedGalleryScreen.kt`

### app/src/main/java/com/reflection/transferin/feature/sessionactive/
- `app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionActiveScreen.kt`
- `app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionUiState.kt`
- `app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionViewModel.kt`
- `app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionViewModelFactory.kt`

### app/src/main/java/com/reflection/transferin/feature/sessionsummary/
- `app/src/main/java/com/reflection/transferin/feature/sessionsummary/SessionSummaryScreen.kt`
- `app/src/main/java/com/reflection/transferin/feature/sessionsummary/SessionSummaryViewModel.kt`
- `app/src/main/java/com/reflection/transferin/feature/sessionsummary/SummaryState.kt`

### app/src/main/java/com/reflection/transferin/service/
- `app/src/main/java/com/reflection/transferin/service/UsbDisconnectHandler.kt`
- `app/src/main/java/com/reflection/transferin/service/UsbTetherService.kt`

### app/src/main/java/com/reflection/transferin/session/model/
- `app/src/main/java/com/reflection/transferin/session/model/SessionRuntimeState.kt`

### app/src/main/java/com/reflection/transferin/session/runtime/
- `app/src/main/java/com/reflection/transferin/session/runtime/SessionDispatcher.kt`
- `app/src/main/java/com/reflection/transferin/session/runtime/SessionOrchestrator.kt`
- `app/src/main/java/com/reflection/transferin/session/runtime/TransferSessionManagerImpl.kt`

### app/src/main/java/com/reflection/transferin/session/worker/
- `app/src/main/java/com/reflection/transferin/session/worker/TransferUploadWorker.kt`

### app/src/main/java/com/reflection/transferin/ui/theme/
- `app/src/main/java/com/reflection/transferin/ui/theme/Color.kt`
- `app/src/main/java/com/reflection/transferin/ui/theme/Theme.kt`
- `app/src/main/java/com/reflection/transferin/ui/theme/Type.kt`

### app/src/main/keepRules/
- `app/src/main/keepRules/rules.keep`

### app/src/main/res/drawable/
- `app/src/main/res/drawable/ic_launcher_background.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`

### app/src/main/res/mipmap-anydpi/
- `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml`
- `app/src/main/res/mipmap-anydpi/ic_launcher.xml`

### app/src/main/res/mipmap-hdpi/
- `app/src/main/res/mipmap-hdpi/ic_launcher_round.webp`
- `app/src/main/res/mipmap-hdpi/ic_launcher.webp`

### app/src/main/res/mipmap-mdpi/
- `app/src/main/res/mipmap-mdpi/ic_launcher_round.webp`
- `app/src/main/res/mipmap-mdpi/ic_launcher.webp`

### app/src/main/res/mipmap-xhdpi/
- `app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp`
- `app/src/main/res/mipmap-xhdpi/ic_launcher.webp`

### app/src/main/res/mipmap-xxhdpi/
- `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp`
- `app/src/main/res/mipmap-xxhdpi/ic_launcher.webp`

### app/src/main/res/mipmap-xxxhdpi/
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp`
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp`

### app/src/main/res/values/
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/themes.xml`

### app/src/main/res/xml/
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/main/res/xml/device_filter.xml`
- `app/src/main/res/xml/file_paths.xml`

### app/src/test/java/com/reflection/transferin/
- `app/src/test/java/com/reflection/transferin/ExampleUnitTest.kt`

### app/src/test/java/com/reflection/transferin/data/sftp/
- `app/src/test/java/com/reflection/transferin/data/sftp/TwoPhaseUploadHandlerTest.kt`
- `app/src/test/java/com/reflection/transferin/data/sftp/UploadCheckpointManagerTest.kt`

### app/src/test/java/com/reflection/transferin/domain/shoot/
- `app/src/test/java/com/reflection/transferin/domain/shoot/CtgStateDifferTest.kt`

### gradle/
- `gradle/gradle-daemon-jvm.properties`
- `gradle/libs.versions.toml`

### gradle/wrapper/
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`

### graphify-out/
- `graphify-out/.graphify_labels.json`
- `graphify-out/.graphify_python`
- `graphify-out/.graphify_root`
- `graphify-out/cost.json`
- `graphify-out/GRAPH_REPORT.md`
- `graphify-out/graph.html`
- `graphify-out/graph.json`
- `graphify-out/manifest.json`

### graphify-out/cache/
- `graphify-out/cache/last_query_stamp`
- `graphify-out/cache/stat-index.json`

### graphify-out/cache/ast/v0.9.26/
- *(107 files: 107 .json)*

### graphify-out/cache/semantic/pd5fd89c46bb5/
- *(33 files: 33 .json)*
