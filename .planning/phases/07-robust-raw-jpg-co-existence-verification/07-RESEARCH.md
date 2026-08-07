# Phase 7 Research - Robust RAW+JPG Co-existence & Verification

## 1. Parallel Upload Feasibility & SFTP Pool Capacity
- Enqueuing multiple upload jobs to WorkManager triggers parallel execution across the worker thread pool.
- However, our `SftpSessionPool` in `SftpConnectionConfig` currently defaults to `poolSize = 1`.
- When multiple workers try to acquire a session concurrently, the second worker throws `IllegalStateException` due to pool capacity exhaustion.
- **Solution**: We will update the default `poolSize` in `SftpConnectionConfig.kt` to `2` (or read it dynamically). This allows both RAW and JPG upload workers to acquire dedicated healthy channels concurrently.

## 2. Filename-Level Verification for Reconnection
- If RAW and JPG share the same number (e.g. `DSCF0005.RAF` and `DSCF0005.JPG`), a purely numeric-based baseline check (`num <= dbHighestNumber`) will skip the RAW file if the JPG file was successfully uploaded but the RAW file failed before disconnection.
- **Solution**: Instead of numeric baseline filtering for `seenHandles`, we will fetch the set of all successfully uploaded filenames for the active session from Room Database:
  ```kotlin
  val uploadedFilenames = items.filter { it.status == "SUCCESS" }.map { it.targetFileName }.toSet()
  ```
- During `PhotoWatcher.initialize(uploadedFilenames)`, we add the handle to `seenHandles` if and only if `uploadedFilenames.contains(name)`.
- This ensures that if `DSCF0005.JPG` is in the DB but `DSCF0005.RAF` is not, only the JPG handle is marked as seen, while the RAW handle is correctly left out to trigger upload upon reconnecting.
