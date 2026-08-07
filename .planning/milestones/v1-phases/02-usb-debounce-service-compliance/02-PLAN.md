# Plan: Phase 2 - USB Debounce & Service Compliance

This plan aims to debounce rapid USB connection cycles to prevent race conditions and ensure compliance with Android 14+ foreground service constraints.

## User Review Required

> [!IMPORTANT]
> **Cancellation of Prior Attach Jobs:** Rapid plug-unplug cycles will immediately cancel any pending reconnection stabilization coroutines, preventing race conditions that lead to duplicate active sessions on the remote NAS server.
> 
> **POST_NOTIFICATIONS Runtime Permission:** On Android 13+ (API 33+), the app will request the `POST_NOTIFICATIONS` permission during `MainActivity.onCreate()` to ensure that the foreground service notification can be safely shown.

## Proposed Changes

### USB Debounce

#### [MODIFY] [UsbDisconnectHandler.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/service/UsbDisconnectHandler.kt)
- Add `private var attachJob: Job? = null` field.
- In `usbReceiver` callbacks:
  - For `ACTION_USB_DEVICE_ATTACHED`, cancel `attachJob` and re-assign it to the new launch.
  - For `ACTION_USB_DEVICE_DETACHED`, cancel `attachJob`, clear it, and run `handleDetach` immediately.

---

### Service Compliance & Permissions

#### [MODIFY] [MainActivity.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/MainActivity.kt)
- Add import for `android.os.Build` and request the `android.Manifest.permission.POST_NOTIFICATIONS` runtime permission on launch for devices running API level 33+.

#### [MODIFY] [UsbTetherService.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/service/UsbTetherService.kt)
- Add try-catch block wrapping `startForeground(...)` in `onCreate()` to catch `SecurityException` gracefully and handle termination if permission is missing.

## Verification Plan

### Automated Tests
Run unit tests for debounce and service logic:
```bash
./gradlew test
```

### Manual Verification
1. Launch the app.
2. Verify that the notification permission prompt is shown on Android 13+ devices.
3. Plug the camera via USB tether, unplug and plug it back rapidly 3-4 times.
4. Verify that only a single reconnect call is triggered after the 2-second stabilization delay.
