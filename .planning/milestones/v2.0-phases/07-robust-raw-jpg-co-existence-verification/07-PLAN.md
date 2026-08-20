# Plan - Phase 7: Robust RAW+JPG Co-existence & Verification

We will configure parallel SFTP pool capacity and transition to filename-level verification to prevent duplicate or skipped file synchronization during reconnection.

## Proposed Changes

### SFTP Client

#### [MODIFY] [SftpConnectionConfig.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/sftp/SftpConnectionConfig.kt)
- Update default value: `val poolSize: Int = 2` (changed from `1`) to support concurrent parallel uploads over SSH.

### PhotoWatcher

#### [MODIFY] [PhotoWatcher.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/usb/PhotoWatcher.kt)
- Update signature to `suspend fun initialize(uploadedFilenames: Set<String> = emptySet()): Boolean`.
- Update `initializeWithStorages(storageIds: List<Int>, uploadedFilenames: Set<String>)`:
  - Calculate `highestDbNumber` by parsing suffix numbers of filenames in `uploadedFilenames`.
  - Add handles to `seenHandles` if and only if `uploadedFilenames.contains(name)`.
  - Set baseline `baselineFileNumber = maxOf(maxCardNumber, mostLatestImageNumber, highestDbNumber)`.

### Session Presentation

#### [MODIFY] [SessionViewModel.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionViewModel.kt)
- In `observeMtpConnectivity()`, query Room DB for successfully uploaded items:
  ```kotlin
  val uploadedNames = runCatching {
      transferQueueRepository.getBySession(sessionId)
          .filter { it.status == "SUCCESS" }
          .map { it.targetFileName }
          .toSet()
  }.getOrDefault(emptySet())
  ```
- Pass `uploadedNames` to `photoWatcher.initialize(uploadedNames)`.

---

## Verification Plan

### Automated Verification
- Verify successful compilation with parallel WorkManager integration and pool size configuration.

### Manual Verification
1. Enqueue both RAW and JPG concurrently. Verify they are dispatched in parallel.
2. Interrupt connection mid-transfer when `DSCF0005.jpg` completes upload but `DSCF0005.raf` (RAW) fails.
3. Reconnect the camera and verify `DSCF0005.raf` is immediately detected and uploaded, without getting skipped or blocked by the twin JPG.
