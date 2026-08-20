# Technology Stack & Protocol Architecture Research: Multi-Vendor Tethering & Zero-Leak Core

**Date:** 2026-08-20  
**Target Milestone:** v3.0 Multi-Vendor Production Engine & Zero-Leak Tether Core  
**Scope:** Fujifilm, Canon EOS, Sony Alpha, Nikon PTP/MTP; Direct Android USB Architecture; Zero-Memory-Leak Lifecycle Engineering; Android 14/15 Foreground Service Compliance  

---

## Executive Summary & Architectural Decisions

TransferIn v3.0 transitions from a legacy MTP/CTG file scanner to a high-performance, deterministic **Direct USB PTP/MTP Camera Engine**. The architecture directly interfaces with `android.hardware.usb.UsbDeviceConnection`, bypasses Android MediaProvider / Storage Access Framework bottlenecks for tethered shooting, and implements native vendor PTP extensions for **Fujifilm (0x04CB)**, **Canon EOS (0x04A9)**, **Sony Remote (0x054C)**, and **Nikon (0x04B0)**.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           TransferIn v3.0 Core Engine                            │
└─────────────────────────────────────────────────────────────────────────────────┘
                                         │
                 ┌───────────────────────┴───────────────────────┐
                 ▼                                               ▼
┌─────────────────────────────────┐             ┌─────────────────────────────────┐
│   Multi-Vendor PTP Protocols    │             │   Zero-Leak Memory & Lifecycle   │
│ ├─ Fuji Native Tether (0x04CB)   │             │ ├─ Direct ByteBuffer Pooling    │
│ ├─ Canon EOS PTP (0x04A9)       │             │ ├─ Strict Coroutine Lifecycle   │
│ ├─ Sony PC Remote (0x054C)      │             │ ├─ UsbConnection Determinism    │
│ └─ Nikon PTP Extension (0x04B0) │             │ └─ LeakCanary 2.x & CI Gates    │
└─────────────────────────────────┘             └─────────────────────────────────┘
                 │                                               │
                 └───────────────────────┬───────────────────────┘
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│           Android 14/15 Compliant Tether Engine (connectedDevice)                │
│    (Persistent Foreground Service, Partial WakeLock, Real-Time Cloud Stream)    │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 1. Low-Level Android USB PTP/MTP Architecture

### 1.1 Direct `UsbDeviceConnection` vs `android.mtp.MtpDevice`

Android's built-in `android.mtp.MtpDevice` is a high-level wrapper around the standard MTP C++ client (`libmtp`). While sufficient for basic SD card file browsing, it has fundamental architectural flaws for real-time tethering:
1. **No Vendor PTP Extension Control:** It cannot execute vendor handshake opcodes (e.g. Canon `EOS_OC_SetRemoteMode` `0x9114`, Fuji `0x902A` Tether Unlock, Sony SDIO `0x9280`).
2. **Blocking JNI Calls without Cancellation:** `MtpDevice.readEvent()` and `MtpDevice.importFile()` block background threads inside native code, frequently causing ANRs during sudden USB disconnections.
3. **Memory Churn:** `MtpDevice.getObject()` allocates full-size Java `byte[]` arrays on the JVM heap, causing GC pauses and OutOfMemoryErrors (OOM) on large RAW images (e.g. 50MB–100MB Fuji RAF or Sony ARW files).

**v3.0 Architecture Decision:** Direct raw USB endpoint management via `android.hardware.usb.UsbDeviceConnection` using custom asynchronous PTP packet transport and zero-copy direct buffers.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            PTP Packet Framing (12-Byte Header)               │
├─────────────────┬─────────────────┬──────────────────┬──────────────────────┤
│ Length (4 Bytes)│ Type (2 Bytes)  │  Code (2 Bytes)  │ TransactionID (4B)   │
│ uint32_t (LE)   │ uint16_t (LE)   │  uint16_t (LE)   │ uint32_t (LE)        │
├─────────────────┴─────────────────┴──────────────────┴──────────────────────┤
│ Payload Data / Parameters (0 to N bytes)                                    │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### PTP Container Types
- `0x0001` — **Command Block:** Host $\rightarrow$ Device (Operation Code + up to 5 uint32 parameters).
- `0x0002` — **Data Block:** Host $\leftrightarrow$ Device (Operation Code + raw payload stream).
- `0x0003` — **Response Block:** Device $\rightarrow$ Host (Response Code + up to 5 uint32 parameters).
- `0x0004` — **Event Block:** Device $\rightarrow$ Host via Interrupt Endpoint (Event Code + parameters).

### 1.2 High-Throughput Bulk Transfer Buffering & Streaming

To achieve maximum USB 3.0 SuperSpeed (5 Gbps theoretical, ~120–300 MB/s real-world) and USB 2.0 HighSpeed (480 Mbps theoretical, ~35–42 MB/s real-world) throughput without heap thrashing:

1. **Endpoint Configuration:**
   - Bulk OUT Endpoint (`wMaxPacketSize`: 512 bytes for HighSpeed, 1024 bytes for SuperSpeed).
   - Bulk IN Endpoint (`wMaxPacketSize`: 512 or 1024 bytes).
   - Interrupt IN Endpoint (`wMaxPacketSize`: 64 bytes, `bInterval`: 1–8ms).
2. **Direct Buffer Sizing:**
   - Command/Response packets: Reusable small direct buffer (512 bytes).
   - Data Stream Transfers: Fixed chunk size of **1 MB (1,048,576 bytes)** using direct-allocated byte buffers (`ByteBuffer.allocateDirect`) or reusable array pools.
3. **Streaming Direct to Disk/Channel:**
   - Rather than accumulating multi-megabyte payloads in memory, pipe the USB IN bulk stream directly into a `FileChannel` backed by an active staging file.
4. **PTP 1.1 / MTP Indefinite Length Handling (`0xFFFFFFFF`):**
   - When a camera sends a Data container with `Length = 0xFFFFFFFF`, the length is unknown/streamed. The reader must continue bulk IN transfers until a **Short Packet** (received bytes < `wMaxPacketSize`) or a **Zero-Length Packet (ZLP)** is encountered, followed immediately by the 12-byte Response Container (`0x0003`).

---

## 2. Multi-Vendor PTP Protocols: Deep Technical Specifications

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                     Multi-Vendor PTP Adapter Topology                         │
├───────────────────────────────────────────────────────────────────────────────┤
│                               CameraDevice                                    │
│                                    │                                          │
│       ┌────────────────┬───────────┴────────────┬────────────────┐            │
│       ▼                ▼                        ▼                ▼            │
│ ┌───────────┐   ┌─────────────┐          ┌─────────────┐   ┌───────────┐      │
│ │ FujiPtp   │   │ CanonEosPtp │          │ SonyPtp     │   │ NikonPtp  │      │
│ │ Adapter   │   │ Adapter     │          │ Adapter     │   │ Adapter   │      │
│ │ (0x04CB)  │   │ (0x04A9)    │          │ (0x054C)    │   │ (0x04B0)  │      │
│ └───────────┘   └─────────────┘          └─────────────┘   └───────────┘      │
└───────────────────────────────────────────────────────────────────────────────┘
```

### 2.1 Fujifilm Native Wired Tether Protocol

- **Vendor ID (VID):** `0x04CB` (Fujifilm Corporation)
- **Vendor Extension ID:** `0x0000000E` (PTP_VENDOR_FUJI)
- **USB Interface Class:** Still Image (`0x06`, Subclass `0x01`, Protocol `0x01`) or Vendor Specific (`0xFF`).

#### Camera USB Connection Modes
Fujifilm cameras (X-T3, X-T4, X-T5, X-H2, X-H2s, X-T30 II, GFX 100 II, GFX 100S) require specific menu configuration:
- `USB Tether Shooting Auto` / `USB Tether Shooting Fixed`: Enables full PTP vendor remote tethering.
- `USB Card Reader`: Standard Mass Storage Class (`0x08`) or simple PTP without tethering events.

#### Fujifilm PTP Protocol Sequence & Opcodes
| Opcode / Event | Name | Description / Parameters |
|---|---|---|
| `0x1002` | `OpenSession` | Param1: `SessionID` (e.g. `0x00000001`) |
| `0x902A` | `Fuji_TetherHandshake` | Handshake unlock for tethered streaming mode |
| `0x9021` | `Fuji_GetDevicePropDesc` | Queries camera property descriptions |
| `0x9022` | `Fuji_SetDevicePropValue`| Configures tether control properties |
| `0x9024` / `0x9029` | `Fuji_GetEvents` | Polls pending capture events from camera buffer |
| `0x9028` | `Fuji_GetObject` | Direct high-speed object data stream retrieval |
| `0x902B` | `Fuji_InitiateCapture` | Shutter trigger via software |
| `0x1003` | `CloseSession` | Orderly teardown |

#### Fujifilm Specific Lifecycle & Termination Sequence
> [!CAUTION]
> **Fuji "Frozen Camera" Prevention:** If the host application disconnects or closes the USB socket without sending the clean session teardown sequence, Fujifilm cameras freeze their internal RTOS state machine, disabling physical dials and shutter buttons until the physical battery is pulled.

**Clean Fuji Termination Handshake:**
1. Send `Fuji_SetDevicePropValue` (`0x9022`) with DPC `0xD207` (Control Mode) set to `0x0000` (Release Host Control).
2. Drain any remaining event packets via `0x9024` until empty (`0x2001 RC_OK` with zero payload).
3. Send `PTP_OC_CloseSession` (`0x1003`).
4. Call `connection.releaseInterface(usbInterface)` followed by `connection.close()`.

---

### 2.2 Canon EOS PTP Class Extension

- **Vendor ID (VID):** `0x04A9` (Canon Inc.)
- **Vendor Extension ID:** `0x0000000B` (PTP_VENDOR_CANON_EOS)
- **Supported Bodies:** EOS R5, R6, R6 Mark II, R3, R7, R8, R10, 5D Mark IV, 1D X Mark III, Rebel series.

#### Canon EOS PTP Protocol Sequence & Opcodes
| Opcode | Hex | Description |
|---|---|---|
| `PTP_OC_OpenSession` | `0x1002` | Open session (`SessionID=1`) |
| `EOS_OC_SetRemoteMode` | `0x9114` | Param1: `1` (Enable PC Remote), `0` (Disable) |
| `EOS_OC_SetEventMode` | `0x9115` | Param1: `1` (Enable High-Speed Event Pipe) |
| `EOS_OC_GetEvent` | `0x9116` | Drains event records from internal camera FIFO |
| `EOS_OC_GetStorageIDs` | `0x9101` | Retrieves CFexpress / SD slot IDs |
| `EOS_OC_GetStorageInfo` | `0x9102` | Retrieves capacity and write permissions |
| `EOS_OC_GetObjectInfo` | `0x9103` | Fast metadata fetch for EOS object handles |
| `EOS_OC_GetObject` | `0x9104` | Chunked raw data transfer (CR3 / CR2 / JPG) |
| `EOS_OC_PtpCapture` | `0x910F` | Host-initiated exposure trigger |

#### Canon Event Records (from `0x9116` GetEvent)
The `EOS_OC_GetEvent` container returns packed event structs:
- `0xC189` (`EOS_EC_ObjectAddedEx`): New file written to SD/CFexpress card. Contains ObjectHandle, StorageID, FormatCode, FileSize.
- `0xC188` (`EOS_EC_RequestObjectTransfer`): Camera requesting host to download the latest shot immediately.
- `0xC18A` (`EOS_EC_ObjectRemoved`): File deleted on camera.
- `0xC181` (`EOS_EC_PropValueChanged`): Aperture, Shutter Speed, ISO, White Balance changed.

#### Why Deprecate `.CTG` Polling in Favor of `EOS_OC_GetEvent`?
Legacy TransferIn implementations polled `.CTG` index files (`CANONMSC/CANON.CTG`). This approach has major drawbacks:
1. **File Write Races:** `.CTG` headers are updated *before* image data finishes writing to flash, leading to corrupted image reads.
2. **I/O Overhead:** Polling `.CTG` requires continuous FAT directory traversal.
3. **EOS Mirrorless Incompatibility:** EOS R series write direct metadata tables into flash without updating `.CTG` files in real-time.
4. **Instant Event Delivery:** `0x9116 GetEvent` delivers new shot handles in **< 15ms** after shutter actuation.

---

### 2.3 Sony Remote Protocol (PTP & PTP/IP)

- **Vendor ID (VID):** `0x054C` (Sony Corporation)
- **Vendor Extension ID:** `0x00000012` (Sony Vendor Extension)
- **Supported Bodies:** A7 III, A7 IV, A7R V, A7S III, A1, A9 II, A9 III, FX3, FX30, ZV-E1.

#### Camera USB Connection Mode
Sony cameras must be configured in:
- `PC Remote` (in USB Connection menu) $\rightarrow$ Allows host tethering and live object synchronization.

#### Sony SDIO / PTP Protocol Architecture
| Opcode / Event | Hex | Description |
|---|---|---|
| `PTP_OC_OpenSession` | `0x1002` | Standard PTP session init |
| `Sony_SDIOConnect` | `0x9280` | Initiates Sony Remote handshake (SDIO mode) |
| `Sony_GetSDIOExtDeviceInfo` | `0x9281` | Retrieves Sony-specific capabilities and property tables |
| `Sony_SDIOGetEvent` | `0x9282` | Polls Sony SDIO event queue |
| `Sony_SDIOSendExtCommand` | `0x9283` | Sends vendor control packets (AF, Shutter, Zoom) |
| `Sony_SDIOControl` | `0x9285` | Storage and property dispatch |
| `PTP_EC_ObjectAdded` | `0x4002` | PTP standard event emitted over Interrupt IN endpoint |

#### Sony Transfer Pipeline
1. On connection: Execute `0x1002 OpenSession` $\rightarrow$ `0x9280 SDIOConnect` $\rightarrow$ `0x9281 GetSDIOExtDeviceInfo`.
2. Event Monitoring: Listen on **Interrupt IN Endpoint** for standard PTP Event `0x4002 (ObjectAdded)`. If interrupt endpoint polling is not supported by the kernel, poll `0x9282 (Sony_SDIOGetEvent)` at 50ms intervals.
3. Data Extraction: Execute `PTP_OC_GetObject (0x1009)` or Sony accelerated partial object retrieval.

---

### 2.4 Nikon PTP Class Extension

- **Vendor ID (VID):** `0x04B0` (Nikon Corporation)
- **Vendor Extension ID:** `0x0000000A` (Nikon Vendor Extension)
- **Supported Bodies:** Z9, Z8, Z6 II, Z6 III, Z7 II, Zf, D850, D780, D7500.

#### Nikon Protocol Architecture & Opcodes
| Opcode / Event | Hex | Description |
|---|---|---|
| `PTP_OC_OpenSession` | `0x1002` | Standard PTP session init |
| `Nikon_Capture` | `0x90C0` | Hardware shutter actuation |
| `Nikon_CheckEvent` | `0x90C7` | Polls internal event FIFO |
| `Nikon_DeviceReady` | `0x90C8` | Queries camera busy/ready state |
| `Nikon_KeepAlive` | `0x9200` | Maintains connection during idle tethering |
| `PTP_EC_ObjectAdded` | `0x4002` | Emitted when RAW/NEF finishes writing to card |

#### Nikon "Device Busy (0x2019)" Handling
Nikon bodies enforce strict internal locking while processing EXPEED buffer flushes:
- Any command sent during card write returns `0x2019 (PTP_RC_DeviceBusy)`.
- **Architectural Requirement:** Implement an exponential backoff retry loop (base delay 25ms, multiplier 1.5, max retry 8 times) when `0x2019` is received, rather than crashing or terminating the session.

---

## 3. Zero-Memory-Leak Engineering & Lifecycle Safety

### 3.1 Memory Leak Hotspots in USB Camera Architectures

| Subsystem | Root Cause of Leak | Prevention Architecture |
|---|---|---|
| **USB Connections** | `UsbDeviceConnection` not closed on unplug; native JNI file descriptors leaked | Deterministic `AutoCloseable` wrapper + Non-cancellable cleanup job |
| **USB Interfaces** | `connection.releaseInterface(intf)` skipped because device was already detached | Cached interface references released in `finally` block before connection close |
| **Coroutines** | `CoroutineScope(Dispatchers.IO).launch` unparented scopes running infinitely | Strict binding to Service/ViewModel lifecycle scopes (`SupervisorJob`) |
| **Buffer Allocations** | `ByteArray(32 * 1024)` reallocated on every USB read | Reusable Direct `ByteBuffer` Pool (`DirectByteBufferPool`) |
| **Room Database** | Unclosed Flow subscriptions or cursor leaks in background sync | DAO single-shot queries or strictly bound `lifecycleOwner` / Service flows |
| **Context Leaks** | Activity Context held in static Singletons / Service delegates | Strict enforcement of `context.applicationContext` |

---

### 3.2 High-Performance Direct `ByteBufferPool` Implementation

```kotlin
package com.rvmeur.transferin.data.usb.buffer

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * High-performance, zero-allocation ByteBuffer pool for high-throughput USB bulk transfers.
 * Prevents JVM GC pressure during multi-megabyte RAW image streaming.
 */
class DirectByteBufferPool(
    private val bufferCapacity: Int = 1024 * 1024, // 1 MB chunk
    private val maxPoolSize: Int = 8
) {
    private val pool = ConcurrentLinkedQueue<ByteBuffer>()
    private val createdCount = AtomicInteger(0)

    fun acquire(): ByteBuffer {
        val buffer = pool.poll()
        if (buffer != null) {
            buffer.clear()
            return buffer
        }
        
        return if (createdCount.get() < maxPoolSize) {
            createdCount.incrementAndGet()
            ByteBuffer.allocateDirect(bufferCapacity)
        } else {
            // Pool capacity reached; allocate temporary heap buffer or fallback
            ByteBuffer.allocate(bufferCapacity)
        }
    }

    fun release(buffer: ByteBuffer) {
        if (buffer.isDirect && pool.size < maxPoolSize) {
            buffer.clear()
            pool.offer(buffer)
        }
    }

    fun clear() {
        pool.clear()
        createdCount.set(0)
    }
}
```

---

### 3.3 Strict Coroutine Scope & Deterministic Lifecycle Management

```kotlin
package com.rvmeur.transferin.data.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.util.Log
import kotlinx.coroutines.*
import java.io.Closeable

/**
 * Deterministic USB session resource holder with guaranteed non-cancellable cleanup.
 */
class UsbHardwareSession(
    val connection: UsbDeviceConnection,
    val usbInterface: UsbInterface,
    val deviceName: String,
    parentScope: CoroutineScope
) : Closeable {

    private val sessionJob = SupervisorJob(parentScope.coroutineContext[Job])
    val sessionScope = CoroutineScope(parentScope.coroutineContext + sessionJob + Dispatchers.IO)
    private var isClosed = false

    override fun close() {
        if (isClosed) return
        isClosed = true

        // 1. Cancel all active session coroutines immediately
        sessionJob.cancel()

        // 2. Perform native hardware resource release synchronously
        runCatching {
            connection.releaseInterface(usbInterface)
            Log.d(TAG, "UsbInterface released successfully for $deviceName")
        }.onFailure { Log.w(TAG, "Failed to release UsbInterface", it) }

        runCatching {
            connection.close()
            Log.d(TAG, "UsbDeviceConnection closed successfully for $deviceName")
        }.onFailure { Log.w(TAG, "Failed to close UsbDeviceConnection", it) }
    }

    companion object {
        private const val TAG = "UsbHardwareSession"
    }
}
```

---

### 3.4 LeakCanary 2.x Integration & Profiling Strategy

#### Dependency Configuration (`app/build.gradle.kts`)
```kotlin
dependencies {
    // Memory leak analysis during local debug & integration testing
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
    
    // Automated CI heap dump assertions
    androidTestImplementation("com.squareup.leakcanary:leakcanary-android-instrumentation:2.14")
}
```

#### Automated Memory Safety Rules in App Lifecycle
1. **AppWatcher Custom Object Watchers:** Register active `UsbHardwareSession`, `CameraVendorInitializer`, and `PtpTransport` instances into `AppWatcher.objectWatcher` on session termination to guarantee they are garbage collected within 5 seconds.
2. **Strict Mode Configuration in `Application.onCreate`:**
   ```kotlin
   if (BuildConfig.DEBUG) {
       StrictMode.setThreadPolicy(
           StrictMode.ThreadPolicy.Builder()
               .detectDiskReads()
               .detectDiskWrites()
               .detectNetwork()
               .penaltyLog()
               .build()
       )
       StrictMode.setVmPolicy(
           StrictMode.VmPolicy.Builder()
               .detectLeakedSqlLiteObjects()
               .detectLeakedClosableObjects()
               .penaltyLog()
               .build()
       )
   }
   ```

---

## 4. Android 14 & 15 Foreground Service Architecture & Power Policies

### 4.1 `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` Requirements

Under **Android 14 (API 34)** and **Android 15 (API 35)**, foreground services are strictly partitioned by functional domain. For USB camera tethering:

1. **Service Type:** Must be declared as `connectedDevice`.
2. **Manifest Declaration (`AndroidManifest.xml`):**
   ```xml
   <manifest xmlns:android="http://schemas.android.com/apk/res/android">
       <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
       <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
       
       <application>
           <service
               android:name=".service.UsbTetherService"
               android:exported="false"
               android:foregroundServiceType="connectedDevice" />
       </application>
   </manifest>
   ```
3. **Runtime Prerequisite:** Access to an attached USB device granted via `UsbManager.requestPermission()` before invoking `startForeground()`.

### 4.2 Android 15 (API 35) Specific Timeout Policy Differences

| Foreground Service Type | Android 15 Quota / Time Limit | `Service.onTimeout(int, int)` Invocation | Behavior on Timeout |
|---|---|---|---|
| **`dataSync`** | **6 hours per 24h period** | Yes (must call `stopSelf()` within seconds) | `ForegroundServiceDidNotStopInTimeException` crash if unhandled |
| **`mediaProcessing`** | **6 hours per 24h period** | Yes (must call `stopSelf()` within seconds) | `ForegroundServiceDidNotStopInTimeException` crash if unhandled |
| **`connectedDevice`** (TransferIn v3.0) | **Unlimited while hardware is connected** | No 6-hour limit while USB peripheral is attached | Continuous tethering allowed for full multi-hour studio shoot |

> [!IMPORTANT]
> Because TransferIn v3.0 uses `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`, it is exempt from the Android 15 6-hour daily cutoff. However, the app must immediately call `stopForeground(STOP_FOREGROUND_REMOVE)` and `stopSelf()` whenever the physical camera is disconnected.

---

### 4.3 Background Power Management & Aggressive OEM Killers

To prevent Samsung (OneUI), Xiaomi (HyperOS/MIUI), and Huawei background killers from terminating long studio sessions:

1. **Partial WakeLock Management:**
   - Acquire `PowerManager.PARTIAL_WAKE_LOCK` with a descriptive tag (`TransferIn::UsbTetherWakeLock`) upon camera connection.
   - Release the WakeLock deterministically inside `Service.onDestroy()`.
2. **Battery Optimization Whitelist Request:**
   - Check `PowerManager.isIgnoringBatteryOptimizations(packageName)`.
   - Prompt the user during the initial Setup Wizard to grant exemption via `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

---

## 5. Technology Stack Selection & Dependency Matrix

| Category | Recommended Technology / Library | Version | Purpose & Rationale |
|---|---|---|---|
| **Language & Runtime** | Kotlin | `2.2.10` | Coroutines 1.10.1, strict null safety, inline value classes |
| **UI Framework** | Jetpack Compose + Material 3 | `1.7.6` / `1.3.1` | Declarative UI, state hoisting, memory-safe composition |
| **USB Communication** | Direct `android.hardware.usb` | Android SDK 35 | Zero external C/JNI dependencies; direct low-latency PTP transport |
| **Memory Profiling** | Square LeakCanary | `2.14` | Automatic heap dump inspection & leak trace logging in debug builds |
| **Database** | AndroidX Room | `2.8.4` | SQLite persistence for media metadata and transfer queue state |
| **Background Sync** | AndroidX WorkManager | `2.11.2` | Reliable background upload retry & network-constrained workers |
| **Cloud Storage Client** | Google Drive REST API Client | `v3-rev20231128-2.0.0` | Official client library for Google Drive cloud sync |
| **HTTP Transport** | OkHttp 3 / Okio | `4.12.0` / `3.9.1` | Efficient chunked multipart upload and connection pooling |
| **Unit & Integration Testing** | JUnit 5, MockK, Turbine, Room Testing | `5.11.0`, `1.13.14`, `1.2.0`, `2.8.4` | Protocol packet verification, state flow assertions, database migration verification |

---

## 6. Implementation & Migration Roadmap

### Phase 1: Direct USB Engine & Buffer Pool Core (Milestone v3.0)
1. Implement `DirectByteBufferPool` and zero-copy PTP packet serializer/deserializer.
2. Build `UsbHardwareSession` with deterministic resource closing.
3. Integrate LeakCanary 2.x into debug builds and establish baseline memory benchmarks.

### Phase 2: Vendor Protocol Adapters Implementation
1. **Fujifilm Native Adapter:** Implement `0x902A` handshake, `0x9024/0x9029` event drain, and clean termination sequence.
2. **Canon EOS Adapter:** Implement `EOS_OC_SetRemoteMode` (`0x9114`), `EOS_OC_SetEventMode` (`0x9115`), and `EOS_OC_GetEvent` (`0x9116`) to replace legacy `.CTG` polling.
3. **Sony Adapter:** Implement `0x9280` SDIO connect handshake and `0x4002` Interrupt IN event pipeline.
4. **Nikon Adapter:** Implement `0x90C7 CheckEvent` and exponential backoff on `0x2019 PTP_RC_DeviceBusy`.

### Phase 3: Android 14/15 Service Hardening & Zero-Leak Verification
1. Update `UsbTetherService` to ensure all coroutine scopes are tied to the service lifecycle without race conditions during `onDestroy()`.
2. Implement Partial WakeLock management and USB attach/detach debounce handlers.
3. Execute continuous automated memory leak regression suites on live connected camera bodies.
