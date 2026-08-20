# Plan - Phase 8: Canon and Fuji Connection Polish

We will fix close session order and device name synchronization for Canon and Fuji cameras.

## Proposed Changes

### Connection Layers

#### [MODIFY] [CameraUnlockOrchestrator.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/usb/CameraUnlockOrchestrator.kt)
- Update `closeSessionInternal()` to release the Canon handshake (`vendorInitializer.closeSession`) before closing the MtpDevice.
- Update `openCameraSession()` to pass the active device name when setting the override MTP device.
- Call `usbHelper.setMtpDeviceOverride(null, null)` in `closeSessionInternal()`.

#### [MODIFY] [UsbHelper.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/usb/UsbHelper.kt)
- Define `setMtpDeviceOverride(mtpDevice: MtpDevice?, deviceName: String?)` to set `activeMtpDeviceOverride` and `currentSessionDeviceName` at the same time.

---

## Verification Plan

### Manual Verification
1. Connect a Canon camera and enter tethered mode.
2. Stop watching / close the session.
3. Verify that the Canon camera cleanly exits remote mode (screen goes back to normal) and the physical shutter button is working again.
