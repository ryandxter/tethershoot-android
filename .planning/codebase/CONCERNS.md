# Technical Concerns & Debt

**Analysis Date:** 2026-08-07

Below is a list of active concerns, technical debt, and potential fragility points within the codebase.

## 1. Connection Stability & Pool Resource Leaks
- **SftpSessionPool Resource Management:** If SFTP connection sessions are not evicted or closed correctly when a session ends, it can result in SSH channel exhaustion or file descriptor leaks on the NAS server.
- **Network Handover:** Swapping between Wi-Fi and Cellular during an active shoot session triggers reconnection backoffs. High-frequency network flips can exhaust the retry limits and trip the circuit breaker permanently, requiring manual intervention.

## 2. USB Camera Compatibility & Vendor Quirks
- **Vendor-Specific Handshakes:** Real-time shoot detection relies on parsing the Canon CTG database parser (`CtgDatabaseParser.kt`). Cameras from other vendors (Sony, Nikon) lack this structure and fallback to generic MTP polling. This degrades real-time sync capabilities on non-Canon cameras.
- **MTP RAW+JPG Race Condition (Fixed):** Pemantauan MTP pada kamera non-Canon (Fuji dll) sebelumnya menggunakan perbandingan *timestamp* basis satu foto terbaru. Hal ini menyebabkan foto RAW yang diambil bersamaan dengan JPG (pada milidetik yang sama) terlewat. Solusinya kini diatasi dengan pelacakan set `seenHandles` di `PhotoWatcher.kt` yang mengidentifikasi semua *handle* objek MTP baru secara sekuensial tanpa batas waktu atau perbandingan tunggal.
- **System USB Host Failures:** Certain Android manufacturers implement strict battery saving or USB permission security boundaries, causing silent failures during background PTP initialization.

## 3. Foreground Service & USB Detach Cycles
- **Stabilization Timers:** Rapid unplugging and plugging of the USB tether cable can cause service race conditions. `UsbDisconnectHandler.kt` enforces a 2-second stabilization delay, but this requires continuous refinement to prevent crashes or double service allocations.
- **Android OS Restrictions:** Android 14+ introduces strict regulations for foreground services. `UsbTetherService` must strictly adhere to the `FOREGROUND_SERVICE_CONNECTED_DEVICE` type to avoid OS termination.

## 4. Large Media Transfers
- **Two-Phase Upload Resumability:** Uploading large RAW files (.CR3, .NEF) or 4K videos is prone to failures. While `TwoPhaseUploadHandler.kt` uses `UploadCheckpointManager.kt`, partial uploads leave dangling temp files on the SFTP/WebDAV server that must be cleaned up periodically.
- **Penamaan File Target pada MTP Cache (Fixed):** Proses unduhan langsung MTP menggunakan nama cache lokal seperti `${handle}_${filename}` agar unik di perangkat. Prefix angka ini sebelumnya tidak sengaja terbawa ke nama berkas unggahan di NAS. Masalah ini diselesaikan dengan pembersihan *prefix regex* (`^\d+_(.+)$`) pada `UploadQueueManager.kt` sehingga nama target di NAS 1:1 persis seperti nama orisinal di memori kamera.

## 5. Room Database Migrations
- **SQLite Migrations:** Modifying fields in entities (e.g. `UploadedFingerprintEntity.kt`) requires custom SQL migrations. If migrations like `Migration_8_9.kt` are improperly defined, users updating their app can face runtime crashes.

---

*Concerns analysis: 2026-08-07*
