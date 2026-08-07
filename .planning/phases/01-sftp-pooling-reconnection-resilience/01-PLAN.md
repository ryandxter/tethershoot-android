# Plan: Phase 1 - SFTP Pooling & Reconnection Resilience

This plan aims to establish a leak-free pool of SFTP sessions with keepalives, exponential backoff, and circuit breakers, fully integrated with UI/UX blocking overlays.

## User Review Required

> [!IMPORTANT]
> **Dynamic Health Check Polling:** The polling frequency dynamically scales based on connection type (10s on Wi-Fi, 30s on Cellular data) to prevent high battery/bandwidth usage.
> 
> **UI Block during Reconnection:** During reconnection attempts, a full-screen blocking overlay is displayed, informing the user of the process and preventing any double-triggers or menu inputs until the recovery completes or fails.

## Proposed Changes

### SFTP Client & Pooling

#### [MODIFY] [SftpSessionPool.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/sftp/SftpSessionPool.kt)
- Add `getActiveSessions()` to expose all active sessions currently in the pool.
```kotlin
fun getActiveSessions(): List<PooledSftpSession> {
    return pool.toList()
}
```

#### [MODIFY] [SftpSessionHealthMonitor.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/sftp/SftpSessionHealthMonitor.kt)
- Update constructor to accept `context: Context`.
- Add `getDynamicCheckInterval()` using `ConnectivityManager` to retrieve current network transport type.
- Update the check coroutine loop to delay based on dynamic interval.
- Update `checkAllSessions()` to fetch `pool.getActiveSessions()` and invoke `checkSession(session)`. If it returns false, call `pool.handleSessionFailure(session)` to clean it up.

#### [MODIFY] [JschSftpClient.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/sftp/JschSftpClient.kt)
- Expose the pool's reconnector or instantiate it dynamically inside `withPooledSession()`.
- On connection failure catch block, invoke `reconnector.reconnectWithBackoff(pool)` to attempt exponential recovery before declaring the block a failure.

---

### UI & ViewModel Integration

#### [MODIFY] [SessionUiState.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionUiState.kt)
- Add `reconnectionStatusText: String? = null` parameter to `ShootModeState`.

#### [MODIFY] [SessionViewModel.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionViewModel.kt)
- Inject or access `SftpSessionReconnector` and collect from its `reconnectionState` stream inside `init`.
- Update `_shootModeState` to reflect the reconnection status:
  - If state is `ReconnectionState.Attempting`, set `reconnectionStatusText` to `"Mencoba menghubungkan kembali... (Percobaan ${attempt}/${maxRetries})"`
  - If state is `ReconnectionState.CircuitOpen`, set `reconnectionStatusText` to `"Sirkuit terbuka - Batas kegagalan terlampaui. Menunggu cooldown..."`
  - If state is `ReconnectionState.Success` or `ReconnectionState.Failed` or null, set `reconnectionStatusText` back to `null`.

#### [MODIFY] [SessionActiveScreen.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionActiveScreen.kt)
- Add a full-screen overlay component rendered on top of everything when `shootModeState?.reconnectionStatusText != null`.
- The overlay displays a loading spinner, the reconnection status message, and absorbs touch events so the user cannot click buttons behind it.

## Verification Plan

### Automated Tests
Run unit tests for pooling and reconnection:
```bash
./gradlew test
```

### Manual Verification
1. Start a live shoot mode session.
2. Unplug/disconnect the network (or toggle Airplane mode).
3. Verify that the UI displays a full-screen loading overlay containing the reconnection message in Indonesian.
4. Re-enable network and check that the overlay disappears and transfer queue completes.
