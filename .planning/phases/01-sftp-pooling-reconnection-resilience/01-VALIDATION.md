# Phase 1: SFTP Pooling & Reconnection Resilience - Validation Strategy

**Date:** 2026-08-07

## 1. Automated Tests

### Unit Tests
- **SftpSessionPoolTest.kt:**
  - Test session pool initialization, active connection count.
  - Test session acquisition and release.
  - Test marking session unhealthy and destroying it safely.
- **SftpSessionReconnectorTest.kt:**
  - Test exponential backoff delays sequence (1s, 2s, 4s, 8s, 30s).
  - Test circuit breaker transitions (CLOSED -> OPEN -> HALF_OPEN).
- **SftpSessionHealthMonitorTest.kt:**
  - Test keepalive checks evict dead sessions from the pool.

### Instrumented/UI Tests
- **SessionActiveScreenTest.kt:**
  - Test that connection state transitions (e.g. `ReconnectionState.Attempting`) trigger a full-screen loading overlay in the UI.

## 2. Manual Verification

### Connection Drop Recovery (Airplane Mode test)
1. Launch active tethered shooting session.
2. Trigger photo capture to start upload.
3. Toggle Airplane Mode on the Android device.
4. Verify that the UI immediately blocks with a full-screen loading/reconnecting overlay.
5. Verify that `SftpSessionReconnector` logs exponential retry backoffs.
6. Toggle Airplane Mode off.
7. Verify that the session successfully reconnects, the loading overlay disappears, and queue processing resumes.

---

*Validation strategy completed: 2026-08-07*
