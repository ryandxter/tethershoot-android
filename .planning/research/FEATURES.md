# Research: Target Features & Behaviors

**Date:** 2026-08-07

## 1. SFTP Connection Pool & Reconnection
- **Session Eviction:** Auto-evict channels/sessions when keepalives fail. When a session is returned to `SftpSessionPool`, check if it's still alive using `session.isConnected()`.
- **Reconnection Engine:** Implement a custom `SftpSessionReconnector` that handles exponential backoff (e.g. 1s, 2s, 4s, 8s, 16s, max 30s) and attempts at most 5 retries.
- **Circuit Breaker:** Block attempts to connect when state is OPEN. Reset circuit breaker after a 60-second cool-down window.

## 2. Multi-Vendor Camera Support
- **PTP Fallback:** When a non-Canon camera is connected, fallback to a standard PTP/MTP directory listing poll. Even without CTG database updates, fall back to file date/size diff comparison.
- **Background Initializer:** Execute MTP initialization in a background coroutine to prevent blocking the Main thread, handling vendor-specific PTP session parameters safely.

## 3. Foreground Service & USB Debounce
- **USB Event Debouncer:** Implement a `UsbDisconnectHandler` using a 2-second stabilization timer (e.g. Kotlin coroutine delay) to absorb rapid plug-unplug triggers.
- **Foreground Service Lifecycle:** Auto-terminate `UsbTetherService` only when the camera session has been idle for more than 5 minutes or explicitly disconnected.

## 4. Large Media Transfers & Cleanup
- **Resume Checkpoint:** Persist local metadata containing file size, name, destination, and bytes transferred. Read checkpoint before launching a new transfer.
- **Dangling Temp Files Garbage Collection:** On connection setup, run a background cleanup task to detect and delete partial temp upload files (e.g. files with `.tmp` prefix) older than 24 hours on the remote SFTP/WebDAV server.

## 5. Migration Safety
- **Dry-run Validation:** Validate Room migrations by running unit tests that build the database from an older schema version (e.g., version 8) and run migrations to version 9.

---

*Research: 2026-08-07*
