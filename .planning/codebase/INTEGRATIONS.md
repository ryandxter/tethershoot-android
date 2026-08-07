# Integrations

**Analysis Date:** 2026-08-07

## 1. SFTP Client (NAS Integration)
Used to upload captured or imported media to a remote NAS or SFTP server.

- **Client Implementation:** `app/src/main/java/com/reflection/transferin/data/sftp/JschSftpClient.kt` implementing `SftpClient.kt`.
- **Session Pooling:** `app/src/main/java/com/reflection/transferin/data/sftp/SftpSessionPool.kt` maintains a pool of reuseable SSH channels/sessions to avoid connection overhead.
- **Health & Keepalive:** `app/src/main/java/com/reflection/transferin/data/sftp/SftpSessionHealthMonitor.kt` monitors connection stability. Emits keepalive packets every 30s (`serverAliveInterval=30000`) and disconnects if 3 packets go unacknowledged (`serverAliveCountMax=3`).
- **Reconnection with Backoff:** `app/src/main/java/com/reflection/transferin/data/sftp/SftpSessionReconnector.kt` uses exponential backoff (1s, 2s, 4s, 8s... up to 30s) to rebuild failed connections. Integrated with `SftpCircuitBreaker` which trips after 5 failures.
- **Chunked Uploads:** `app/src/main/java/com/reflection/transferin/data/sftp/TwoPhaseUploadHandler.kt` handles large file chunk transfers.
- **Upload Progress Checkpoints:** `app/src/main/java/com/reflection/transferin/data/sftp/UploadCheckpointManager.kt` persists upload progress, allowing resumes of interrupted transfers.

## 2. USB Camera Connectivity (MTP / PTP Protocol)
Directly connects to high-end DSLR/Mirrorless cameras over USB to import photos in real-time.

- **USB Host Orchestration:** `app/src/main/java/com/reflection/transferin/data/usb/UsbHelper.kt` and `UsbSourceOrchestrator.kt` manage connection states and lifecycle.
- **Permission Flow:** `app/src/main/java/com/reflection/transferin/data/usb/UsbPermissionManager.kt` requests system USB permissions.
- **Discovery:** `app/src/main/java/com/reflection/transferin/data/usb/CameraDiscoveryEngine.kt` uses intent filters and USB vendor IDs to identify cameras.
- **Protocol Initializers:**
  - `DefaultCameraSessionInitializer.kt` coordinates vendor initializations.
  - `CannonCameraInitializer.kt` specializes PTP handshake for Canon cameras.
  - `GenericCameraInitializer.kt` handles fallback PTP/MTP devices.
- **Canon PTP Protocol Implementation:**
  - `CannonPtpSession.kt` - custom session command handling.
  - `CannonPtpTransport.kt` - low-level USB bulk transfers.
  - `CannonPtpEnumerator.kt` - enumerates camera objects.

## 3. WebDAV Integration
Alternative cloud backup server connection.
- **Client Implementation:** `app/src/main/java/com/reflection/transferin/data/webdav/WebDavClient.kt` handles WebDAV HTTP operations (propfind, put, mkcol) for alternative NAS/server integrations.

## 4. Local Storage / OTG Backup (Storage Access Framework)
Supports backup to OTG flash drives or SD cards.
- **Storage Scanner:** `app/src/main/java/com/reflection/transferin/data/usb/DocumentFileScanner.kt` scans and navigates standard files using `DocumentFile` APIs.
- **Auto-Detection:** `app/src/main/java/com/reflection/transferin/data/storage/AutoStorageDetector.kt` listens for storage mounts.

---

*Integrations analysis: 2026-08-07*
