# Phase 2: USB Debounce & Service Compliance - Validation Strategy

**Date:** 2026-08-07

## 1. Automated Tests

### Unit Tests
- **UsbDisconnectHandlerTest.kt:**
  - Test that receiving multiple `ACTION_USB_DEVICE_ATTACHED` events in rapid succession (< 2 seconds) only triggers the `onReconnect` callback once.
  - Test that receiving `ACTION_USB_DEVICE_DETACHED` immediately cancels any pending reconnection job.

### Instrumented/UI Tests
- **MainActivityPermissionTest.kt:**
  - Verify that the app requests `POST_NOTIFICATIONS` runtime permission on launch on Android 13+ (API 33+) devices.

## 2. Manual Verification

### Rapid Plug-Unplug Cycle Test
1. Launch TransferIn app.
2. Plug the USB tether cable into the camera.
3. Unplug and replug the USB cable quickly 3 to 4 times within a 2-second window.
4. Verify from the logcat that the previous stabilization delay jobs are cancelled.
5. Verify that only one `onReconnect` callback and session initialization is triggered after the cable is left plugged in for 2 seconds.
6. Verify that no duplicate active connections are registered on the remote server.

---

*Validation strategy completed: 2026-08-07*
