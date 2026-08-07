# Research: Stack Additions & Changes

**Date:** 2026-08-07

## 1. Network & SFTP Resilience
- **JSch SSH Keepalive:** Use native session parameters:
  - `session.setServerAliveInterval(30000)` (30 seconds keepalive interval)
  - `session.setServerAliveCountMax(3)` (disconnect after 3 unacknowledged keepalives)
- **Circuit Breaker State Machine:** Implement custom local state tracking (CLOSED, OPEN, HALF_OPEN) to prevent thrashing.

## 2. Android Foreground Service Types (Android 14+)
- **Foreground Service Type:** `FOREGROUND_SERVICE_CONNECTED_DEVICE` is required for USB communication tethering.
- **Manifest Declaration:**
  ```xml
  <service
      android:name=".service.UsbTetherService"
      android:foregroundServiceType="connectedDevice"
      android:exported="false" />
  ```
- **Runtime Permission:** Ensure `Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE` is requested on Android 14 (API 34) and above.

## 3. Room Database Migration Validation
- **Room Testing Tool:** Add `androidx.room:room-testing` to validation stack to run migration verification:
  ```kotlin
  testImplementation("androidx.room:room-testing:2.8.4")
  ```
- **SQLite Schema Exports:** Ensure `room.schemaLocation` is configured in `build.gradle.kts` to enable automatic schema diff verification.

---

*Research: 2026-08-07*
