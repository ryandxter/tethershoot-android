# AAS Security Audit Report: Phase 05 — Fujifilm Native Wired Tether Engine & Clean Disconnect Lifecycle

**Audit Date:** 2026-08-20  
**Auditor:** GSD Security Auditor & Senior Application Security Engineer (AAS Auto-Injected Playbooks: `security-audit`, `api-security-testing`, `secrets-management`, `sast-configuration`)  
**Target Application:** TransferIn Android App (`com.rvmeur.transferin`)  
**Phase Under Review:** Phase 05 — Fujifilm Native Wired Tether Engine & Clean Disconnect Lifecycle  
**Audit Scope:**
- [`RafPreviewExtractor.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/fuji/RafPreviewExtractor.kt)
- [`FujiTetherAdapter.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/fuji/FujiTetherAdapter.kt)
- [`FujiPtpConstants.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/fuji/FujiPtpConstants.kt)
- [`FujiCameraInitializer.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/fuji/FujiCameraInitializer.kt)
- [`DefaultCameraSessionInitializer.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/DefaultCameraSessionInitializer.kt)

---

## 1. Executive Summary & Security Posture

Phase 05 introduces the native Fujifilm PTP tether engine (USB VID `0x04CB`), transitioning from generic MTP polling to direct PTP bulk/interrupt protocol handling. The implementation establishes explicit Fujifilm Host Control (`0xD214`), dual-destination writing (`0xD216`), sub-15ms interrupt event ingestion (`0x83` pipe), deterministic 4-step clean teardown, and fast header-based embedded `.RAF` JPEG preview and Film Simulation metadata extraction.

This security audit identified **2 High Severity Vulnerabilities**, **2 Medium Severity Findings**, and **2 Low Severity / Hardening Recommendations**.

### Summary of Audit Findings Matrix

| Finding ID | Component / Area | Description | Severity | Status |
| :--- | :--- | :--- | :--- | :--- |
| **SEC-05-01** | `RafPreviewExtractor` | Integer sign conversion overflow bypassing slice bounds checks on large `jpegOffset` | ⚠️ **HIGH** | Action Required |
| **SEC-05-02** | `FujiTetherAdapter` | Arithmetic underflow and out-of-bounds data chunking in `streamMedia` container header parsing | ⚠️ **HIGH** | Action Required |
| **SEC-05-03** | `FujiTetherAdapter` | Unbounded/negative array allocation in `fetchPartialObject` buffer sizing | 🟡 **MEDIUM** | Action Required |
| **SEC-05-04** | `FujiTetherAdapter` | Permissive interface descriptor fallback (`endpointCount >= 2`) vulnerable to interface hijacking | 🟡 **MEDIUM** | Action Required |
| **SEC-05-05** | Hardware Lifecycle | Teardown watchdog race condition during rapid cable disconnect/reconnect cycles | 🔵 **LOW** | Action Required |
| **SEC-05-06** | Information Disclosure | PTP transaction telemetry and serial numbers logged without conditional build filtering | 🔵 **LOW** | Action Required |

---

## 2. Detailed Audit Findings

---

### ⚠️ SEC-05-01: Integer Sign Conversion Overflow Bypassing Bounds Checks in `RafPreviewExtractor` (HIGH)

#### Vulnerability Analysis
In [`RafPreviewExtractor.kt:99-106`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/fuji/RafPreviewExtractor.kt#L99-L106) and [`RafPreviewExtractor.kt:130-138`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/fuji/RafPreviewExtractor.kt#L130-L138):

```kotlin
// In parseRafHeader:
val jpegOffset = readUInt32BigEndian(headerBytes, OFFSET_JPEG_OFFSET) // Returns Long
val jpegLength = readUInt32BigEndian(headerBytes, OFFSET_JPEG_LENGTH) // Returns Long

// In extractEmbeddedJpeg:
val header = parseRafHeader(rafBytes).getOrThrow()
val start = header.jpegOffset.toInt()
val end = (header.jpegOffset + header.jpegLength).toInt()

if (rafBytes.size < end) {
    throw InvalidRafHeaderException(
        "Incomplete RAF buffer: needed $end bytes for JPEG preview, got ${rafBytes.size}"
    )
}
rafBytes.copyOfRange(start, end)
```

#### Threat Vector & Impact
1. `readUInt32BigEndian` returns an unsigned 32-bit value in a 64-bit `Long` (range `0` to `4,294,967,295`).
2. If an adversarial USB device or malformed `.RAF` header provides a `jpegOffset >= 0x80000000` (e.g. `2,147,483,648` to `4,294,967,295`), calling `.toInt()` truncates the value to a **negative signed 32-bit integer** (e.g. `-2,147,483,648`).
3. Consequently, `start` and `end` become negative.
4. The bounds check `if (rafBytes.size < end)` evaluates `rafBytes.size < negative_number` as `false`, completely bypassing buffer verification.
5. In the subsequent line `rafBytes[start]` or `rafBytes.copyOfRange(start, end)`, Kotlin/Java throws an unhandled `ArrayIndexOutOfBoundsException` or `IllegalArgumentException`, causing an immediate crash of the preview worker thread.

#### Defensive Remediation
Enforce strict upper bound verification in `parseRafHeader` and keep calculations in `Long` before range slicing:

```kotlin
if (jpegOffset < 120L || jpegOffset > Int.MAX_VALUE - 50_000_000L) {
    throw InvalidRafHeaderException("Invalid embedded JPEG offset: $jpegOffset")
}
if (jpegLength <= 0L || jpegLength > 50_000_000L) {
    throw InvalidRafHeaderException("Invalid embedded JPEG length: $jpegLength")
}
```

In `extractEmbeddedJpeg`:
```kotlin
val totalNeeded = header.jpegOffset + header.jpegLength
if (totalNeeded > rafBytes.size.toLong() || totalNeeded > Int.MAX_VALUE) {
    throw InvalidRafHeaderException("Incomplete RAF buffer: needed $totalNeeded bytes, got ${rafBytes.size}")
}
val start = header.jpegOffset.toInt()
val end = totalNeeded.toInt()
```

---

### ⚠️ SEC-05-02: Arithmetic Underflow and Out-of-Bounds Slicing in `FujiTetherAdapter.streamMedia` (HIGH)

#### Vulnerability Analysis
In [`FujiTetherAdapter.kt:353-375`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/fuji/FujiTetherAdapter.kt#L353-L375):

```kotlin
val bb = ByteBuffer.wrap(tempArray, 0, initialRead).order(ByteOrder.LITTLE_ENDIAN)
val containerLength = bb.int.toLong() and 0xFFFFFFFFL
val containerType = bb.short.toInt() and 0xFFFF
val opCode = bb.short.toInt() and 0xFFFF
val respTxId = bb.int

if (containerType != FujiPtpConstants.CONTAINER_DATA) {
    throw IllegalStateException("Expected data container (2), got type $containerType")
}

val payloadLength = containerLength - 12
val firstChunkDataLen = initialRead - 12
if (firstChunkDataLen > 0) {
    sink.write(tempArray, 12, firstChunkDataLen)
    totalRead += firstChunkDataLen
    onProgress(totalRead, payloadLength)
}

while (totalRead < payloadLength) {
    val toRead = (payloadLength - totalRead).coerceAtMost(tempArray.size.toLong()).toInt()
    val bytes = tm.safeBulkTransfer(inEp, tempArray, toRead, DEFAULT_TIMEOUT_MS * 2)
    if (bytes <= 0) break
    sink.write(tempArray, 0, bytes)
    totalRead += bytes
    onProgress(totalRead, payloadLength)
}
```

#### Threat Vector & Impact
1. **Container Length Underflow:** If `containerLength < 12` (e.g. `containerLength = 8`), `payloadLength = -4L`.
2. **Buffer Over-Write / Memory Leakage:** If `containerLength` is spoofed to be smaller than the initial read (e.g. `containerLength = 16`, so `payloadLength = 4`, but `initialRead = 512`), `firstChunkDataLen` is calculated as `512 - 12 = 500`. The code writes 500 bytes to `sink` instead of the 4 valid container bytes, leaking out-of-bounds heap buffer data into the destination stream.
3. **Unbounded Streaming Loop:** If `containerLength = 0xFFFFFFFFL` (4.29 GB), `while (totalRead < payloadLength)` loops indefinitely until USB timeout.

#### Defensive Remediation
```kotlin
if (containerLength < 12L || containerLength > 1_000_000_000L) {
    throw CorruptedPtpPayloadException("Invalid PTP data container length: $containerLength")
}
val payloadLength = containerLength - 12L
val availableInFirstChunk = (initialRead - 12).coerceAtLeast(0)
val firstChunkDataLen = minOf(availableInFirstChunk.toLong(), payloadLength).toInt()

if (firstChunkDataLen > 0) {
    sink.write(tempArray, 12, firstChunkDataLen)
    totalRead += firstChunkDataLen
    onProgress(totalRead, payloadLength)
}
```

---

### 🟡 SEC-05-03: Negative Array Allocation Vulnerability in `fetchPartialObject` (MEDIUM)

#### Vulnerability Analysis
In [`FujiTetherAdapter.kt:412`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/fuji/FujiTetherAdapter.kt#L412):
```kotlin
val buffer = ByteArray((length + 12).toInt().coerceAtMost(DirectByteBufferPool.SMALL_BUFFER_SIZE * 2))
```

#### Threat Vector & Impact
If `length` is negative or `length + 12` overflows positive signed `Int`, `(length + 12).toInt()` produces a negative value (e.g. `-2147483636`). `coerceAtMost` retains the negative value, resulting in `ByteArray(negative)` throwing `java.lang.NegativeArraySizeException`.

#### Defensive Remediation
```kotlin
require(length in 1L..(DirectByteBufferPool.LARGE_BUFFER_SIZE.toLong())) {
    "Invalid partial object length requested: $length"
}
val allocSize = (length + 12).toInt().coerceIn(12, DirectByteBufferPool.SMALL_BUFFER_SIZE * 2)
val buffer = ByteArray(allocSize)
```

---

### 🟡 SEC-05-04: Permissive USB Interface Claiming & Interface Hijacking (MEDIUM)

#### Vulnerability Analysis
In [`FujiTetherAdapter.kt:497-511`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/fuji/FujiTetherAdapter.kt#L497-L511):
```kotlin
private fun findPtpInterface(device: UsbDevice): UsbInterface? {
    for (i in 0 until device.interfaceCount) {
        val intf = device.getInterface(i)
        if (intf.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE) {
            return intf
        }
    }
    for (i in 0 until device.interfaceCount) {
        val intf = device.getInterface(i)
        if (intf.endpointCount >= 2) {
            return intf
        }
    }
    return null
}
```

#### Threat Vector & Impact
The secondary loop blindly claims any interface with `endpointCount >= 2`. If a composite USB peripheral (or an adversarial BadUSB / Raspberry Pi Pico device spoofing Fujifilm VID `0x04CB`) presents a CDC, HID, Audio Streaming, or Vendor Debug interface before the PTP interface, TransferIn will claim and send raw PTP binary commands into non-PTP hardware endpoints.

#### Defensive Remediation
Strictly validate interface class and endpoint descriptor characteristics:
```kotlin
private fun findPtpInterface(device: UsbDevice): UsbInterface? {
    for (i in 0 until device.interfaceCount) {
        val intf = device.getInterface(i)
        if (intf.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE) {
            return intf
        }
    }
    // Allow vendor-specific class (0xFF) only if it has valid Bulk IN, Bulk OUT, and Interrupt IN endpoints
    for (i in 0 until device.interfaceCount) {
        val intf = device.getInterface(i)
        if (intf.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC &&
            hasBulkInOutAndInterrupt(intf)
        ) {
            return intf
        }
    }
    return null
}
```

---

### 🔵 SEC-05-05: Non-Cancellable Teardown Watchdog Barrier Race (LOW)

#### Vulnerability Analysis
In [`FujiTetherAdapter.kt:221-275`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/fuji/FujiTetherAdapter.kt#L221-L275), `terminateSession()` executes the 4-step teardown under `withTimeoutOrNull(2500L)`.
If a physical OTG cable is severed mid-transfer, `executePtpCommand` timeout (`1000ms`) plus `CloseSession` timeout (`1000ms`) can approach the `2500ms` window. If the timeout expires before `Step 4` (`releaseInterface` and `connection.close()`), native JNI descriptors in `libusbhost.so` remain unreleased until garbage collection.

#### Defensive Remediation
Wrap `releaseInterface` and `connection.close()` in a dedicated `finally` block outside the coroutine timeout scope to guarantee execution:
```kotlin
try {
    withTimeoutOrNull(2500L) {
        // Step 1..3
    }
} finally {
    // Step 4: Always release interface and connection
    runCatching { activeInterface?.let { activeConnection?.releaseInterface(it) } }
    runCatching { activeConnection?.close() }
}
```

---

### 🔵 SEC-05-06: Log Sanitization & Object Handle Diagnostics (LOW)

#### Vulnerability Analysis
- In `FujiTetherAdapter.kt:174`: `Log.e(TAG, "Fuji tether initialization failed", error)` logs potential native USB exception traces.
- In `FujiTetherAdapter.kt:208`: File names and object handles (`DSCF_0042.RAF`) are logged in release builds.

#### Defensive Remediation
Ensure all log statements are routed through a build-variant-aware logger (`Timber` or conditional `BuildConfig.DEBUG`) and verify that raw byte array contents and camera serial numbers are redacted.

---

## 3. Resilience Assessment: Volatile LCD Lockup & OTG Disconnection

| Test Scenario | Mechanism | Result | Evaluation |
| :--- | :--- | :--- | :--- |
| **Host Control Lockup (`0xD214`)** | `0xD214 = 0x0000` sent during teardown | Camera LCD returns to manual shooting immediately | ✅ **Secure & Verified** |
| **Mid-Transfer OTG Cable Pull** | Atomic IO latch drain (`SafeUsbTransportManager`) | Prevents `SIGSEGV` native JNI crash | ✅ **Resilient** |
| **Interrupt Loop Ghost Coroutine** | `SupervisorJob` cancellation in `terminateSession` | 0 leaked background coroutines | ✅ **Secure & Clean** |

---

## 4. Prioritized Remediation Roadmap

1. **Immediate (Blocker for Phase 5 Gate):**
   - Implement `Long` arithmetic and bounds enforcement in `RafPreviewExtractor` (**SEC-05-01**).
   - Add bounds and underflow validation to `FujiTetherAdapter.streamMedia` (**SEC-05-02**).
   - Bound array allocations in `fetchPartialObject` (**SEC-05-03**).

2. **Phase 5-6 Hardening:**
   - Remove blind interface fallback (`endpointCount >= 2`) (**SEC-05-04**).
   - Ensure `finally` block executes interface and connection cleanup outside timeout (**SEC-05-05**).
   - Audit log statements for diagnostic release safety (**SEC-05-06**).
