# TransferIn Android Codebase — Deep Architectural & Tech Debt Audit

**Audit Date:** 2026-08-20  
**Target Environment:** Android 12 to 14+ (API 31–37)  
**Primary Modules Audited:** `com.rvmeur.transferin` (`service/`, `data/usb/`, `domain/session/`, `domain/shoot/`, `session/runtime/`, `feature/`, `data/sftp/`)

---

## 1. Executive Summary & Critical Risk Matrix

The **TransferIn** codebase is designed to handle high-throughput tethered photography intake from DSLR and mirrorless cameras (Canon, Fujifilm, Sony, Nikon) over USB (PTP/MTP) and upload media to Google Drive / NAS in real time.

While core ingestion mechanics (Room database schema, Compose UI structure, and Two-Phase Google Drive upload) are logically defined, the system suffers from **critical architectural fragmentation** and **severe USB concurrency bugs**. Most notably, connecting Fujifilm cameras triggers an **uncontrolled recursive PTP polling storm** that locks the camera firmware, freezes the UI, and fails to release USB endpoints on shutdown.

### 1.1 Risk Severity Matrix

| Severity | Category | Root Cause / Issue | Impact |
| :--- | :--- | :--- | :--- |
| **CRITICAL** | USB / PTP Protocol | **Fuji MTP Infinite Polling Storm:** `CtgWatcher` & `PhotoWatcher` run concurrent 2000ms polling loops executing full-card `getObjectMetadata` enumerations across thousands of handles because Fuji lacks Canon `.CTG` files. | Camera CPU lockup, "USB COMMUNICATING" freeze, battery drain, dropped shots. |
| **CRITICAL** | USB Lifecycle | **Unclean Shutdown & Missing PTP `CloseSession`:** `closeSessionInternal()` abruptly releases USB interfaces while native JNI MTP reads are blocking in kernel ioctls. `UsbTetherService.onDestroy()` immediately cancels its own cleanup scope. | Camera remains locked after disconnect; requires physical battery pull to reset. |
| **CRITICAL** | Concurrency / JNI | **Unsynchronized Native MTP Concurrency:** `PhotoWatcher`, `CtgWatcher`, `mtpEventDrainJob`, and `downloadMtpFile()` invoke `MtpDevice.importFile()`, `getObjectHandles()`, and `readEvent()` simultaneously on a single non-thread-safe JNI wrapper. | USB endpoint stall, broken pipes (`EPIPE`), corrupted file imports. |
| **HIGH** | Coroutine Leaks | **Orphaned `CoroutineScope` Allocations:** `CtgWatcher`, `ImportCameraEngine`, `ShootModeEngine`, and `TransferSessionManagerImpl` instantiate unmanaged `CoroutineScope(Dispatchers.IO + Job())` that are never cancelled on disconnect. | Continuous background CPU drain, leaked file listeners, memory leaks. |
| **HIGH** | File Descriptor Leaks | **Unclosed Streams in Thumbnail Generators:** `UploadQueueManager.createThumbnail()` and `TransferUploadWorker.createThumbnail()` leak `InputStream` and `ParcelFileDescriptor` instances on RAW files (`.RAF`, `.CR3`, `.NEF`). | Native memory exhaustion, Android `EMFILE` (Too many open files) crash. |
| **HIGH** | Storage Leaks | **Transit Cache Storage Exhaustion:** `camera_transit/` retains temporary downloaded RAW files indefinitely if an upload fails or user cancels; `camera_thumbnails/` has no purge policy. | Device storage filled to capacity over long sessions. |
| **MEDIUM** | Android OS / API 34 | **Foreground Service & Permission Vulnerabilities:** `UsbTetherService` starts `connectedDevice` FGS without guaranteeing attached hardware; `UsbPermissionManager` uses invalid `FLAG_NO_CREATE` on SDK < 33. | `ForegroundServiceStartNotAllowedException` on Android 14+; silent permission request failures on Android 12. |
| **MEDIUM** | Network / Threading | **`runBlocking` inside Streaming Progress Callbacks:** `TwoPhaseUploadHandler` runs `runBlocking` inside `onProgress` callbacks during active OkHttp socket writes. | Dispatcher thread starvation and upload throttling. |

---

## 2. Forensic Analysis: Fuji Device Communication & Shutdown Lock Failure

### 2.1 The Invisible Recursive Ping-Pong Communication

#### Architecture Mismatch (Canon CTG vs. Fuji Standard MTP)
In Canon EOS cameras, the camera maintains a proprietary SQLite index file (`AUTOMRIC.CTG` or `CANON_MDC.CTG`) in the storage root or `CANONMSC` directory. Whenever a photo is captured, Canon updates this single CTG file's timestamp and size. TransferIn's `CtgWatcher.kt` was designed around this assumption.

When a **Fujifilm** camera (Vendor ID `0x04CB`) is connected:
1. `CtgWatcher.kt` calls `findCtgFile(storageId)`.
2. Because Fujifilm cameras do not use CTG files, `findCtgFile()` returns `null`.
3. `CtgWatcher.kt` falls back to `findLatestFile(storageId)` ([`CtgWatcher.kt:422-447`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/CtgWatcher.kt#L422-L447)):
```kotlin
private suspend fun findLatestFile(storageId: Int): CtgFileMeta? = withContext(Dispatchers.IO) {
    val handles = usbHelper.getAllObjectHandles(storageId)
    if (handles.isEmpty()) return@withContext null
    var latest: CtgFileMeta? = null
    for (handle in handles) {
        val meta = usbHelper.getObjectMetadata(handle) ?: continue
        if (meta.name.lowercase().endsWith(".ctg")) continue
        if (latest == null || (meta.modifiedTime ?: 0) > latest.timestamp) {
            latest = CtgFileMeta(handle, meta.name, meta.modifiedTime ?: 0, meta.size)
        }
    }
    latest
}
```

```
+-----------------------------------------------------------------------------------+
|                           THE FUJI POLLING DISASTER                               |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  [ Android Device ]                                           [ Fuji Camera ]     |
|         |                                                            |            |
|         |--- 1. GetObjectHandles(storageId) ------------------------>| (OpCode    |
|         |<-- Returns Array of 3,000 Handles -------------------------|  0x1007)   |
|         |                                                            |            |
|         |--- 2. GetObjectInfo(Handle #1) --------------------------->| (OpCode    |
|         |<-- ObjectInfo Payload (512B) ------------------------------|  0x1008)   |
|         |--- 3. GetObjectInfo(Handle #2) --------------------------->|            |
|         |<-- ObjectInfo Payload (512B) ------------------------------|            |
|         |    ... [REPEATED 3,000 TIMES IN A BLOCKING LOOP] ...       |            |
|         |--- N. GetObjectInfo(Handle #3000) ------------------------>|            |
|         |<-- ObjectInfo Payload (512B) ------------------------------|            |
|         |                                                            |            |
|         | === Takes ~15–30 Seconds ===                               |            |
|         |                                                            |            |
|         | [ delay(2000ms) fires in CtgWatcher ]                      |            |
|         | [ delay(2000ms) fires in PhotoWatcher ]                    |            |
|         |                                                            |            |
|         |--- RESTART FULL 3,000-HANDLE ENUMERATION ----------------->| Camera DSP |
|         |    (CtgWatcher & PhotoWatcher overlapping!)                | Stalls     |
|         |                                                            | UI Freezes |
+-----------------------------------------------------------------------------------+
```

#### The Algorithmic Complexity Trap
- For an SD card with $N$ photos (e.g. 2,000 photos), one scan iteration requires:
  - $1 \times$ `GetObjectHandles`
  - $N \times$ `GetObjectInfo` over USB Bulk IN/OUT endpoints.
- Each USB bulk transaction on Android incurs kernel context switches, JNI boundary crossings, and physical USB bus turnaround latency (avg 5–15ms per transaction).
- $2,000 \times 10\text{ ms} = 20\text{ seconds}$ of nonstop USB saturation.
- **Both `CtgWatcher` AND `PhotoWatcher` run this loop independently every 2,000ms.**
- While loop #1 is still querying handle #1,400, loop #2 starts querying handle #1.
- Furthermore, `SessionViewModel.kt` lines 377–383 also triggers `ctgDatabaseParser.parseCtgFile(storageId)` which performs yet another full iteration of `getAllObjectHandles` + `getObjectMetadata`.

#### The Feedback Ping-Pong Mechanism
1. The Fuji camera's USB controller receives hundreds of consecutive `GetObjectInfo` requests.
2. In Fuji firmware, rapid object info requests update internal access timestamps and trigger MTP interrupt events (e.g., `ObjectInfoChanged` `0x4007` or `DevicePropertyChanged`).
3. These events fill the camera's USB interrupt endpoint buffer.
4. `UsbHelper.kt`'s `mtpEventDrainJob` ([`UsbHelper.kt:563-580`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/UsbHelper.kt#L563-L580)) reads these events, while `MtpContentObserver` detects changes and triggers further scans.
5. When a photo is shot, both `PhotoWatcher` and `CtgWatcher` detect it and simultaneously call `downloadMtpFile()`, colliding on `mtpDevice.importFile()`.
6. The camera remains perpetually trapped in a state of high-load USB processing, making it impossible for the camera DSP to return to idle shooting mode.

---

### 2.2 Unsynchronized Native `MtpDevice` Concurrency

Android's `android.mtp.MtpDevice` is a thin JNI wrapper over `libmtp` / kernel USB device endpoints (`/dev/bus/usb/xxx/yyy`). **`MtpDevice` is NOT thread-safe.**

In `SessionViewModel.kt` and `UsbHelper.kt`:
1. **Thread 1 (PhotoWatcher):** Calls `mtpDevice.getObjectHandles()` and `mtpDevice.getObjectInfo()`.
2. **Thread 2 (CtgWatcher):** Calls `mtpDevice.getObjectHandles()` and `mtpDevice.getObjectInfo()`.
3. **Thread 3 (MtpEventDrain):** Calls `mtpDevice.readEvent(signal)` on the interrupt endpoint.
4. **Thread 4 (Import / Download):** Calls `mtpDevice.importFile(handle, path)`.

When Thread 4 initiates bulk data transfer (`importFile`) while Thread 1 or 2 sends a PTP Command container (`GetObjectInfo`) over the same bulk endpoint:
- The PTP command packet gets interleaved with RAW image payload data.
- The camera firmware receives malformed PTP container headers and enters a protocol stall (`STALL` PID on USB endpoint).
- Android throws `java.io.IOException: MTP transaction failed` or silently truncates the downloaded file.

---

### 2.3 Why Camera Shutdown Fails (The Lockup Sequence)

When the session is closed via `SessionViewModel.closeCameraSession()` or `onCleared()`:

```kotlin
// In SessionViewModel.kt:
fun closeCameraSession() {
    stopPhotoWatching()
    viewModelScope.launch {
        cameraUnlockOrchestrator.closeSessionSuspend()
        usbHelper.closeCameraSession()
    }
}
```

```kotlin
// In CameraUnlockOrchestrator.kt:
private fun closeSessionInternal() {
    val device = activeDevice
    val connection = activeConnection

    if (device != null && connection != null) {
        try {
            vendorInitializer.closeSession(device, connection) // Calls Canon-specific PTP Close
        } catch (t: Throwable) {}

        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            runCatching {
                connection.releaseInterface(usbInterface) // <--- CRITICAL RACE #1
            }
        }
    }

    try {
        activeMtpDevice?.close() // <--- CRITICAL RACE #2
    } finally {
        activeMtpDevice = null
        usbHelper.setMtpDeviceOverride(null, null)
    }

    try {
        connection?.close() // <--- CRITICAL RACE #3
    } catch (t: Throwable) {}
}
```

#### The Fatal Failure Points:

1. **Canon CloseSession sent to Fuji:** `vendorInitializer.closeSession()` checks Canon-specific logic. For Fuji, it does nothing or sends invalid Canon EOS opcodes (`EOS_OC_SET_REMOTE_MODE = 0x9114`), which Fuji rejects with `RESPONSE_INVALID_PARAMETER`.
2. **Interface Released Before `MtpDevice.close()`:** `connection.releaseInterface(usbInterface)` is called **before** `activeMtpDevice?.close()`. Android's `MtpDevice.close()` attempts to send standard PTP `CloseSession` (`0x1003`), but fails with `EBADF` / `EIO` because the interface is already claimed by nothing.
3. **Blocking Native JNI vs. Coroutine Cancellation:** When `stopPhotoWatching()` calls `watchJob?.cancel()`, native C++ calls (`ioctl(USBDEVFS_BULK)`) inside `getObjectInfo` or `importFile` **do not unblock**.
4. **Abrupt Kernel File Descriptor Termination:** `connection?.close()` closes the underlying Linux USB file descriptor while the camera firmware is waiting for the next PTP container.
5. **Result on Fuji Cameras:** Because no valid PTP `CloseSession` or USB Bus Reset was transmitted, the camera's internal OS remains in the `MTP_SESSION_OPEN` state. The camera screen remains frozen on "USB COMMUNICATING" with locked shutter buttons until the battery is removed.

---

### 2.4 `UsbTetherService.onDestroy()` Cancellation Bug

In [`UsbTetherService.kt:134-148`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/service/UsbTetherService.kt#L134-L148):

```kotlin
override fun onDestroy() {
    openSessionJob?.cancel()

    serviceScope.launch(Dispatchers.IO) {
        runCatching {
            usbHelper.closeCameraSession()
        }.onFailure { throwable ->
            Log.w(TAG, "Failed to close camera session on destroy", throwable)
        }
    }

    serviceScope.cancel() // <--- INSTANT CANCEL!
    Log.d(TAG, "UsbTetherService destroyed")
    super.onDestroy()
}
```

**Bug:** `serviceScope.launch` spawns a job on `serviceScope`, and the very next line executes `serviceScope.cancel()`. The coroutine is cancelled **at line 145 before `usbHelper.closeCameraSession()` even starts**. No cleanup is executed when `UsbTetherService` is destroyed by the Android OS.

---

## 3. Comprehensive Memory Leak & Resource Retention Audit

### 3.1 Uncancelled `CoroutineScope` Allocations

| Class / Component | Scope Definition | Leak Mechanism |
| :--- | :--- | :--- |
| [`CtgWatcher.kt:75`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/CtgWatcher.kt#L75) | `private val scope = CoroutineScope(Dispatchers.IO + Job())` | Never cancelled. No `close()` or `shutdown()` lifecycle method exists. Loop jobs accumulate if instances are recreated. |
| [`ImportCameraEngine.kt:38`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/domain/session/ImportCameraEngine.kt#L38) | `private val scope = CoroutineScope(Dispatchers.IO + Job())` | `disconnect()` does not cancel `scope` or `watcherJob`. `sourceWatcher.newFiles.collect` runs forever and emits jobs for dead sessions. |
| [`ShootModeEngine.kt:49`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/domain/shoot/ShootModeEngine.kt#L49) | `private val scope = CoroutineScope(Dispatchers.IO + Job())` | Scope is never cancelled when engine is disconnected. |
| [`TransferSessionManagerImpl.kt:37`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/session/runtime/TransferSessionManagerImpl.kt#L37) | `private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` | Scope is retained indefinitely without cleanup hooks. |
| [`UsbHelper.kt:563`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/UsbHelper.kt#L563) | `mtpEventDrainJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { ... }` | Creates an unmanaged, untracked `CoroutineScope` every time a session is opened. |

---

### 3.2 File Descriptor & Stream Leaks in Thumbnail Generation

In [`UploadQueueManager.kt:242-295`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/domain/session/UploadQueueManager.kt#L242-L295) and [`TransferUploadWorker.kt:420-473`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/session/worker/TransferUploadWorker.kt#L420-L473):

```kotlin
private fun createThumbnail(sourceUriStr: String, itemId: String) {
    try {
        val sourceUri = Uri.parse(sourceUriStr)
        val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return // STREAM #1 OPENED

        val isRaw = sourceUriStr.lowercase().endsWith(".raf") || ...

        var bitmap: Bitmap? = null

        if (isRaw) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(sourceUri, "r")
                if (pfd != null) {
                    val exifInterface = ExifInterface(pfd.fileDescriptor)
                    val thumbBytes = exifInterface.thumbnailBytes
                    if (thumbBytes != null) {
                        bitmap = BitmapFactory.decodeByteArray(thumbBytes, 0, thumbBytes.size)
                    }
                    pfd.close() // NOT in a finally block!
                }
            } catch (e: Exception) {}
            // STREAM #1 IS NEVER CLOSED IN THIS RAW BRANCH! LEAK!
        }

        if (bitmap == null) {
            // ...
            inputStream.close() // Only closed if bitmap was null
            val decodeStream = context.contentResolver.openInputStream(sourceUri) ?: return
            bitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
            decodeStream.close()
        }

        val thumbFile = File(thumbDir, "thumb_$itemId.jpg")
        val outputStream = java.io.FileOutputStream(thumbFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        outputStream.close()
        bitmap.recycle()
    } catch (e: Exception) {
        // Unhandled exceptions leak inputStream, pfd, decodeStream, outputStream, and Bitmap!
    }
}
```

#### Leak Consequences:
- **RAW files:** `inputStream` is opened at line 245 and **never closed** if `isRaw == true` and an EXIF thumbnail is decoded.
- **Exception handling:** None of the file descriptors or streams use Kotlin's `.use { }` construct. A decode failure permanently leaks the file descriptor in the Android process table.
- **Bitmap Recycling:** If `bitmap.compress` throws an I/O error, `bitmap.recycle()` is bypassed, causing native graphics memory leaks.

---

### 3.3 Redundant Double Thumbnail Processing

1. When a photo is discovered, `UploadQueueManager.enqueueMediaJob()` calls `createThumbnail(sourceUri.toString(), entityId)` ([`UploadQueueManager.kt:71`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/domain/session/UploadQueueManager.kt#L71)).
2. Later, when `TransferUploadWorker.doWork()` completes the upload, it executes `createThumbnail(localUri.toString(), queueItem.id)` a **second time** ([`TransferUploadWorker.kt:223`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/session/worker/TransferUploadWorker.kt#L223)).
3. This decodes 30–50MB RAW files (`.RAF` / `.CR3`) twice per photo on mobile CPU cores, generating severe thermal throttling and battery consumption.

---

### 3.4 Transit Storage Exhaustion

- Direct MTP imports download camera files to `context.getExternalFilesDir("camera_transit")`.
- `TransferUploadWorker.kt` deletes `localUri` via `cleanupLocalSource()` **only if upload succeeds**.
- If network connection drops permanently, upload fails repeatedly, or the session is cancelled, all downloaded files in `camera_transit/` remain on disk forever.
- No TTL eviction, LRU cache boundary, or session cleanup hook exists for `camera_transit/` or `camera_thumbnails/`.

---

### 3.5 Duplicate HTTP Client Allocation & Connection Pool Leaks

In [`GoogleDriveClient.kt:22-26`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/sftp/GoogleDriveClient.kt#L22-L26):
```kotlin
class GoogleDriveClient(private val context: Context) : SftpClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
```
- `GoogleDriveClient` is instantiated inside `DelegatingSftpClient`.
- `DelegatingSftpClient` is instantiated anew in `MainActivity`, `SelectFolderViewModelFactory`, and in every execution of `TransferUploadWorker`.
- Each `OkHttpClient` instance allocates its own **ConnectionPool**, **Dispatcher thread pool**, and **DNS resolver**.
- In an active shoot with dozens of WorkManager workers, dozens of OkHttpClient instances and their associated thread pools remain active in memory simultaneously.

---

### 3.6 Android 14+ (API 34) Foreground Service Compliance Risks

1. **Foreground Service Type:** In `AndroidManifest.xml`, `UsbTetherService` specifies `android:foregroundServiceType="connectedDevice"`.
2. **Android 14 Enforcements:**
   - Under Android 14+ (API 34), launching a `connectedDevice` Foreground Service requires that a matching physical device is currently connected and granted permission.
   - In `UsbTetherService.onCreate()`, `startForeground()` is called before `onStartCommand()` extracts and validates the `UsbDevice`.
   - If the OS or an intent restarts the service without an attached USB device, Android 14 throws a runtime `ForegroundServiceStartNotAllowedException` or terminates the app.
3. **Notification Permission:** Target SDK is set to 37. On Android 13+ (API 33), `POST_NOTIFICATIONS` is a runtime permission. In `UsbTetherService.updateNotification()`, if `POST_NOTIFICATIONS` is not granted, `manager.notify()` is skipped, leaving the initial "Starting..." notification static and un-updated.

---

### 3.7 `UsbPermissionManager` Invalid Flag on SDK < 33

In [`UsbPermissionManager.kt:160-171`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/UsbPermissionManager.kt#L160-L171):
```kotlin
val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    PendingIntent.FLAG_MUTABLE
} else {
    PendingIntent.FLAG_NO_CREATE // <--- FATAL BUG ON ANDROID 12 / 12L (API 31/32)
}
```
- `PendingIntent.FLAG_NO_CREATE` specifies: *"If the described PendingIntent does not already exist, return null instead of creating it."*
- On Android 12 (API 31/32), `permissionIntent` evaluates to `null`.
- `usbManager.requestPermission(device, permissionIntent)` fails or throws `IllegalArgumentException`, making it impossible to grant USB permission on Android 12 devices.

---

## 4. Architectural Tech Debt & Redundant Code Inventory

```
+-----------------------------------------------------------------------------------------+
|                               ARCHITECTURAL ENTANGLEMENT                                |
+-----------------------------------------------------------------------------------------+
|                                                                                         |
|       [ MainActivity ]  <---------------------------------------------+                |
|             |                                                         |                 |
|             +---> creates UsbHelper #1                                |                 |
|             +---> creates UsbSourceOrchestrator                       |                 |
|             +---> creates ImportCameraEngine (Scope leaked)           |                 |
|                                                                       |                 |
|       [ ConnectCameraViewModel ]                                      |                 |
|             +---> uses UsbHelper #1                                   |                 |
|             +---> observes UsbMonitorState                            |                 |
|                                                                       |                 |
|       [ SessionViewModel ]                                            |                 |
|             +---> creates CameraUnlockOrchestrator                    |                 |
|             |        +---> opens UsbDeviceConnection #2 (Duplicated!) |                 |
|             |        +---> opens MtpDevice #2 (Duplicated!)           |                 |
|             |        +---> overrides UsbHelper #1 MtpDevice           |                 |
|             +---> creates CtgWatcher (Scope leaked)                   |                 |
|             +---> creates PhotoWatcher                                |                 |
|             +---> creates ShootModeEngine (Scope leaked)              |                 |
|             +---> creates ImportCameraEngine (Scope leaked)           |                 |
|                                                                       |                 |
|       [ UsbTetherService ]                                            |                 |
|             +---> creates UsbHelper #2 (Independent!)                 |                 |
|             +---> opens UsbDeviceConnection #3                        |                 |
|                                                                       |                 |
|       [ UsbDisconnectHandler ] -------- (ORPHANED / UNUSED DEAD CODE)-+                 |
|       [ SessionOrchestrator ] --------- (EMPTY FILE / DEAD CODE)                        |
|                                                                                         |
+-----------------------------------------------------------------------------------------+
```

### 4.1 Dead / Orphaned Components

1. **`UsbDisconnectHandler.kt`:** A 361-line file containing reconnect/disconnect logic, broadcast receivers, and SFTP marking hooks. It is **never referenced or instantiated anywhere** in the entire codebase.
2. **`SessionOrchestrator.kt`:** A completely empty class (`class SessionOrchestrator {}`) in `session/runtime/`.
3. **`ShootModeEngine.kt` Local MediaStore Fallback:** In [`ShootModeEngine.kt:134-196`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/domain/shoot/ShootModeEngine.kt#L134-L196), `checkForNewPhotosViaMediaStore()` queries Android's local device gallery (`MediaStore.Images.Media.EXTERNAL_CONTENT_URI`) with `now - date < 60`. It does not monitor the USB camera at all, and continuously re-emits local phone screenshots and photos every 3 seconds without deduplication.

### 4.2 Dual Session Manager Fragmentation

The codebase maintains two conflicting session management architectures:
1. **Domain Layer:** `ProjectSessionManagerImpl.kt` (updates `SessionEntity` in Room, handles `ConnectionMode.TETHERED` vs `IMPORT_FALLBACK`).
2. **Session Runtime Layer:** `TransferSessionManagerImpl.kt` (manages `SessionRuntimeState`, dispatches WorkManager tasks, tracks session counters).
3. **UI Bridge:** `SessionViewModel.kt` manually synchronizes state between both managers, resulting in race conditions when sessions are started, paused, or finished.

---

## 5. Architectural Remediation Roadmap

```
+-----------------------------------------------------------------------------------------+
|                              PROPOSED TARGET ARCHITECTURE                               |
+-----------------------------------------------------------------------------------------+
|                                                                                         |
|   +---------------------------------------------------------------------------------+   |
|   |                        CameraHardwareManager (Singleton)                        |   |
|   |  - Owns UsbDeviceConnection & UsbInterface claims                               |   |
|   |  - Mutex-serialized MTP / PTP Transaction Channel                               |   |
|   |  - Unified Lifecycle (Open -> Claim -> Init -> Monitor -> CloseSession -> Rel)  |   |
|   +---------------------------------------------------------------------------------+   |
|            |                                                         |                  |
|            v                                                         v                  |
|   +------------------------------------+   +----------------------------------------+   |
|   |     Vendor Transport Strategy      |   |        Camera Event & File Watcher     |   |
|   |  - Canon: PTP Remote Mode + CTG    |   |  - Canon: CTG Differ (O(1))            |   |
|   |  - Fuji: Incremental Handle Diff   |   |  - Fuji/Sony/Nikon: MTP Event /        |   |
|   |  - Sony/Nikon: MTP Standard        |   |    Incremental Handle Tail (O(k))      |   |
|   +------------------------------------+   +----------------------------------------+   |
|                                                              |                          |
|                                                              v                          |
|   +---------------------------------------------------------------------------------+   |
|   |                        Unified Session Intake Pipeline                          |   |
|   |  - Deduplicated MediaJob Emitter (SourceWatcher)                                |   |
|   |  - Single-Pass EXIF Thumbnail Generator (Using try-with-resources)              |   |
|   |  - Transit Storage Manager with Auto-Eviction & LRU Cleanup                     |   |
|   +---------------------------------------------------------------------------------+   |
|                                                                                         |
+-----------------------------------------------------------------------------------------+
```

### Phase 1: Fix Fuji Communication & Clean Shutdown (Immediate Priority)
1. **Eliminate Full-Card Polling for Non-Canon Cameras:**
   - In `CtgWatcher.kt`: If `findCtgFile()` returns `null`, **immediately disable CTG polling**. Do not fall back to `findLatestFile()` full-card handle loops.
   - For Fuji/Sony/Nikon: Implement **Incremental Handle Tracking**:
     - On baseline: Read handles once, save the highest handle ID / count.
     - On poll: Query `getObjectHandles(storageId)`. Filter only `handles.filter { it !in seenHandles }`.
     - Query metadata **only** for the newly discovered handles ($O(k)$ where $k \ll N$), instead of querying all $N$ handles.
2. **Serialize All MTP / PTP Operations Behind a Single Mutex:**
   - Eliminate dual ownership of `UsbDeviceConnection` between `UsbHelper` and `CameraUnlockOrchestrator`.
   - Wrap `getObjectHandles()`, `getObjectInfo()`, `importFile()`, and `readEvent()` inside a shared re-entrant lock/mutex to prevent USB packet collisions.
3. **Implement Strict Clean Shutdown Sequence:**
   - Step 1: Cancel active watcher loops and wait for in-flight JNI calls to complete.
   - Step 2: If MTP session is open, call `mtpDevice.close()` (which sends PTP `CloseSession 0x1003`).
   - Step 3: Release claimed USB interfaces (`connection.releaseInterface(intf)`).
   - Step 4: Close `UsbDeviceConnection`.
4. **Fix `UsbTetherService.onDestroy()`:**
   - Perform synchronous USB cleanup via `runBlocking(Dispatchers.IO)` inside `onDestroy()`, or use `NonCancellable` context:
   ```kotlin
   override fun onDestroy() {
       openSessionJob?.cancel()
       runBlocking(Dispatchers.IO) {
           runCatching { usbHelper.closeCameraSession() }
       }
       serviceScope.cancel()
       super.onDestroy()
   }
   ```

---

### Phase 2: Eliminate Memory Leaks & Resource Bleed
1. **Enforce `.use { }` on All Streams and Descriptors:**
   - Refactor `UploadQueueManager.createThumbnail()` and `TransferUploadWorker.createThumbnail()` to wrap `openInputStream`, `openFileDescriptor`, `FileOutputStream`, and `Bitmap` in `try-finally` blocks.
2. **Eliminate Duplicate Thumbnail Generation:**
   - Generate the thumbnail once in `UploadQueueManager` when creating the queue item. Store the thumbnail path in `TransferQueueItemEntity.thumbnailUri`.
   - Remove thumbnail re-creation from `TransferUploadWorker`.
3. **Implement Transit Storage Auto-Eviction:**
   - Create `TransitStorageManager` that sweeps `camera_transit/` on session start and completion, deleting files older than 1 hour or already marked `SUCCESS` / `FAILED` in Room.
4. **Share Single `OkHttpClient` Instance:**
   - Inject a singleton `OkHttpClient` instance via `DatabaseProvider` or dependency container into `GoogleDriveClient`.
5. **Fix `UsbPermissionManager` PendingIntent Flag:**
   ```kotlin
   val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
       PendingIntent.FLAG_MUTABLE
   } else {
       0
   }
   ```
6. **Tie CoroutineScopes to Lifecycle:**
   - Cancel all watcher scopes in `onCleared()` / `disconnect()` methods.

---

### Phase 3: Architectural Simplification & Tech Debt Pruning
1. **Delete Dead Code:**
   - Remove `UsbDisconnectHandler.kt`.
   - Remove `SessionOrchestrator.kt`.
   - Remove fake MediaStore query in `ShootModeEngine.kt`.
2. **Consolidate Session Management:**
   - Merge `ProjectSessionManagerImpl` and `TransferSessionManagerImpl` into a single reactive repository backed by Room flows.
3. **Android 14+ Foreground Service Hardening:**
   - Delay `startForeground()` in `UsbTetherService` until `UsbDevice` attachment is verified in `onStartCommand()`.
   - Handle `POST_NOTIFICATIONS` permission gracefully.
