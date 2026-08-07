# Research: Architecture Integration

**Date:** 2026-08-07

## 1. Resilience & SFTP Pooling Architecture
- **Delegating Client Pattern:** `JschSftpClient` delegates connection acquisition to `SftpSessionPool`.
- **Observer Pattern for Reconnection:** `SftpSessionReconnector` publishes state changes (e.g. `ReconnectionState`) using a `SharedFlow` so UI ViewModels can reflect the status in the top bar.
- **Circuit Breaker Placement:** Placed at the entry point of session acquisition in the pool.

## 2. USB Handler & Service Architecture
- **Event Debouncing Pipeline:**
  ```text
  USB Broadcast Receiver ──> UsbDisconnectHandler (2s debounce) ──> SessionOrchestrator (Pause/Resume Queue)
  ```
- **Foreground Lifecycle Manager:** `UsbTetherService` binds to the `UsbSourceOrchestrator` and listens for active transfers to control foreground notification actions.

## 3. Large File Checkpoint Flow
- **Two-Phase Upload Integration:**
  - `TwoPhaseUploadHandler` queries `UploadCheckpointManager` prior to file transfer.
  - If a valid checkpoint exists, start the SFTP output stream at the specified offset using `ChannelSftp.put(src, dst, monitor, ChannelSftp.RESUME)`.
  - Clear checkpoint on successful completion.

## 4. Test Framework Integration
- **Migration Test Suite:** Set up migration tests under `app/src/androidTest/java/com/reflection/transferin/data/local/db/MigrationTest.kt` using Room's `MigrationTestHelper`.

---

*Research: 2026-08-07*
