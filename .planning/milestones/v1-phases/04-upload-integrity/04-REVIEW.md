# Phase 04 Code Review: Upload Integrity & Checkpoints

Conducting a quick depth code review for Phase 4 changes.

## Summary of Findings

| Severity | Count | Description |
| :--- | :--- | :--- |
| **Critical** | 0 | No critical safety or crash risks identified. |
| **Warning** | 1 | Potential thread blocking in progress monitor callback. |
| **Info** | 1 | Enhancement for clean skip threshold on very small files. |

---

## Detailed Findings

### [Warning] `runBlocking` in progress callback (TwoPhaseUploadHandler.kt)
* **Location**: `TwoPhaseUploadHandler.kt` (lines 38-42)
* **Issue**: The JSch SFTP progress monitor runs on JSch's network writing thread. Using `runBlocking` blocks this thread while waiting for the database write inside `checkpointManager.maybeSaveCheckpoint`.
* **Impact**: Slight latency in network transfers during database write.
* **Mitigation**: The database write is throttled to 1MB/30s inside `maybeSaveCheckpoint`, which minimizes blocking frequency. A future enhancement could offload the database write to an asynchronous coroutine scope (e.g. `handlerScope.launch`) rather than using `runBlocking`.

### [Info] Minimum file size for checkpoints (UploadCheckpointManager.kt)
* **Location**: `UploadCheckpointManager.kt`
* **Issue**: Checkpoint interval is fixed at `1_048_576` bytes (1MB) or 30 seconds.
* **Impact**: For files smaller than 1MB, checkpoints will only trigger on the 30-second time boundary (or not at all if the upload finishes quickly).
* **Mitigation**: This is correct behavior, as tiny files do not benefit from partial resume and should simply be uploaded from scratch if they fail.
