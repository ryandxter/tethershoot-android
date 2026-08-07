# Phase 2: USB Debounce & Service Compliance - Research

**Date:** 2026-08-07

## 1. Technical Domain Analysis

### USB Stabilization & Debounce
- **Race Condition:** Rapid plug-unplug cycles trigger consecutive `ACTION_USB_DEVICE_ATTACHED` broadcasts.
- **Coroutines Race:** Multiple `handleAttach` instances launch in parallel on `handlerScope`.
- **Mitigation:**
  - Cancel any prior `attachJob` before launching a new one:
    ```kotlin
    private var attachJob: Job? = null
    ```
  - When `ATTACHED` is received:
    ```kotlin
    attachJob?.cancel()
    attachJob = handlerScope.launch { handleAttach(dev) }
    ```
  - When `DETACHED` is received:
    ```kotlin
    attachJob?.cancel()
    attachJob = null
    handlerScope.launch { handleDetach(dev) }
    ```

### Android 14+ Foreground Service
- **Notification Permission:** Android 13+ (API 33+) requires `POST_NOTIFICATIONS` runtime permission to post notifications.
- **Target SDK Connected Device:** Android 14+ requires `FOREGROUND_SERVICE_CONNECTED_DEVICE` type. Permission is already declared in `AndroidManifest.xml`.
- **Mitigation:**
  - Request `POST_NOTIFICATIONS` at startup in `MainActivity.kt` on API 33+ devices.
  - Handle `SecurityException` inside `startForeground` in `UsbTetherService.kt`.

## 2. Codebase Scouting & Integration Points

### Reusable Assets
- `UsbDisconnectHandler` (`app/src/main/java/com/reflection/transferin/service/UsbDisconnectHandler.kt`)
- `UsbTetherService` (`app/src/main/java/com/reflection/transferin/service/UsbTetherService.kt`)
- `MainActivity` (`app/src/main/java/com/reflection/transferin/MainActivity.kt`)

### Identified Gaps & Modifications

#### A. UsbDisconnectHandler Job Tracking
Modify `UsbDisconnectHandler.kt` to track the active `attachJob` and cancel it on new events:
```kotlin
    private var attachJob: Job? = null
```

#### B. MainActivity Permission Request
Modify `MainActivity.kt` to request `POST_NOTIFICATIONS` on creation for API 33+ devices.

#### C. UsbTetherService Exception Handling
Modify `UsbTetherService.kt` to catch `SecurityException` during `startForeground` invocation.

## 3. Validation Architecture

- **Unit Tests:** Verify that successive calls to `ACTION_USB_DEVICE_ATTACHED` cancel the preceding coroutine and only trigger a single reconnection after 2 seconds.

---

*Phase 2 Research Completed: 2026-08-07*
