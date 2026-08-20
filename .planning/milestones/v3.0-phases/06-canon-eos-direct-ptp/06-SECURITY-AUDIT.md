# AAS Security Audit Report: Phase 06 — Canon EOS Direct PTP Engine & Fast Preview Extraction

**Audit Date:** 2026-08-20  
**Auditor:** GSD Security Auditor & Senior Application Security Engineer (AAS Auto-Injected Playbooks: `security-audit`, `api-security-testing`, `secrets-management`, `sast-configuration`)  
**Target Application:** TransferIn Android App (`com.rvmeur.transferin`)  
**Phase Under Review:** Phase 06 — Canon EOS Direct PTP Engine & Fast Preview Extraction  
**Audit Scope:**
- [`Cr3PreviewParser.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/canon/Cr3PreviewParser.kt)
- [`CanonPtpAdapter.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/canon/CanonPtpAdapter.kt)
- [`CanonPtpConstants.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/canon/CanonPtpConstants.kt)
- [`CanonStorageInfo.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/canon/CanonStorageInfo.kt)
- [`CtgStabilityVerifier.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/canon/CtgStabilityVerifier.kt)
- [`CtgWatcher.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/CtgWatcher.kt)
- [`CannonCameraInitializer.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CannonCameraInitializer.kt)
- [`CannonPtpTransport.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CannonPtpTransport.kt)
- [`CannonPtpEnumerator.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CannonPtpEnumerator.kt)

---

## 1. Executive Summary & Security Posture

Phase 06 implements a direct, high-throughput Canon EOS PTP protocol engine (USB VID `0x04A9`) supporting modern Canon EOS mirrorless (EOS R-series) and DSLR bodies. The engine operates in native EOS Remote (`0x9114 = 1`) and Event (`0x9115 = 1`) modes, drains DIGIC event queues via `0x9116 EOS_GetEvent` to prevent shutter locks, parses ISO-BMFF `.CR3` preview atoms (`PRVW`/`THMB`) in <500ms, performs two-stage stability verification on legacy `.CTG` catalogs, and isolates dual-slot storage volumes (CFexpress + SD).

This audit identified **3 High Severity Vulnerabilities**, **2 Medium Severity Findings**, and **1 Low Severity / Hardening Recommendation**.

### Summary of Audit Findings Matrix

| Finding ID | Component / Area | Description | Severity | Status |
| :--- | :--- | :--- | :--- | :--- |
| **SEC-06-01** | `Cr3PreviewParser` | Integer overflow/truncation in 64-bit extended box size calculations (`boxSizeRaw == 1L`) | ⚠️ **HIGH** | Action Required |
| **SEC-06-02** | `CannonPtpEnumerator` | Denial of Service (OutOfMemoryError) via unvalidated `count` array allocation in `decodeUInt32Array` | ⚠️ **HIGH** | Action Required |
| **SEC-06-03** | `CanonPtpAdapter` / Transport | Integer underflow, heap allocation churn, and stream de-synchronization during data transfers | ⚠️ **HIGH** | Action Required |
| **SEC-06-04** | `CanonCameraInitializer` | Overly permissive interface selection (`endpointCount > 0`) susceptible to interface hijacking | 🟡 **MEDIUM** | Action Required |
| **SEC-06-05** | `CtgWatcher` / Verifier | Partial CTG catalog lock race and TOCTOU retry handling during burst card writes | 🟡 **MEDIUM** | Action Required |
| **SEC-06-06** | Information Disclosure | PTP event codes and internal sequence numbers emitted to unconditioned Logcat | 🔵 **LOW** | Action Required |

---

## 2. Detailed Audit Findings

---

### ⚠️ SEC-06-01: Integer Overflow and Truncation in `Cr3PreviewParser.scanBoxes` (HIGH)

#### Vulnerability Analysis
In [`Cr3PreviewParser.kt:105-121`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/canon/Cr3PreviewParser.kt#L105-L121):

```kotlin
val boxSizeRaw = buffer.getInt(cursor).toLong() and 0xFFFFFFFFL
val boxType = buffer.getInt(cursor + 4)

val (headerSize, boxSize) = when (boxSizeRaw) {
    1L -> {
        if (cursor + 16 > endOffset) return
        val extendedSize = buffer.getLong(cursor + 8)
        16 to extendedSize
    }
    0L -> 8 to (endOffset - cursor).toLong()
    else -> 8 to boxSizeRaw
}

if (boxSize < headerSize) break

val payloadStart = cursor + headerSize
val boxEnd = (cursor + boxSize).toInt().coerceAtMost(endOffset)
```

#### Threat Vector & Impact
1. When `boxSizeRaw == 1L`, the 64-bit `extendedSize` is read from `buffer.getLong(cursor + 8)`.
2. If an adversarial or malformed CR3 header supplies an `extendedSize > Int.MAX_VALUE` (e.g. `0x1_0000_0005L` or `0x7FFF_FFFF_FFFF_FFFFL`), the addition `cursor + boxSize` succeeds as a `Long`.
3. However, calling `.toInt()` truncates the high 32 bits. For example:
   - `cursor = 100`
   - `boxSize = 0x1_0000_0005L`
   - `(cursor + boxSize) = 0x1_0000_006DL` (4,294,967,341)
   - `.toInt()` results in `109`!
4. `val boxEnd = 109.coerceAtMost(endOffset)` produces an invalid end boundary `109`, while `payloadStart = cursor + 16 = 116`.
5. `payloadStart > boxEnd`, causing downstream atom parsers and JPEG search loops to miscalculate boundaries, bypass UUID sub-box traversal, or throw unexpected slice exceptions.

#### Defensive Remediation
Validate `boxSize` against `endOffset` using 64-bit arithmetic prior to `Int` conversion:

```kotlin
val maxAllowedBoxSize = (endOffset - cursor).toLong()
if (boxSize < headerSize || boxSize > maxAllowedBoxSize) {
    break
}
val payloadStart = cursor + headerSize
val boxEnd = (cursor + boxSize.toInt())
```

---

### ⚠️ SEC-06-02: Denial of Service (OOM) via Unchecked `count` in `CannonPtpEnumerator` (HIGH)

#### Vulnerability Analysis
In [`CannonPtpEnumerator.kt:181-191`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CannonPtpEnumerator.kt#L181-L191):

```kotlin
private fun decodeUInt32Array(payload: ByteArray?): List<Int> {
    if (payload == null || payload.size < 4) return emptyList()

    val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
    val count = bb.int
    if (count <= 0) return emptyList()

    val result = ArrayList<Int>(count)
    repeat(count) {
        if (bb.remaining() >= 4) {
            result += bb.int
        }
    }
    return result
}
```

#### Threat Vector & Impact
1. `count` is read directly from the first 4 bytes of the PTP payload.
2. If an adversarial USB device returns `count = 0x7FFFFFFF` (2,147,483,647) with a payload of only 4 bytes, `ArrayList<Int>(2147483647)` attempts to allocate an 8.5 GB contiguous array on the JVM heap.
3. The Android OS immediately throws `java.lang.OutOfMemoryError` or `NegativeArraySizeException`, terminating the process and crashing the background tether service.

#### Defensive Remediation
Bound the initial `ArrayList` capacity to the number of elements actually present in the payload:

```kotlin
private fun decodeUInt32Array(payload: ByteArray?): List<Int> {
    if (payload == null || payload.size < 4) return emptyList()

    val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
    val count = bb.int
    if (count <= 0) return emptyList()

    val availableElements = bb.remaining() / 4
    val actualCapacity = minOf(count, availableElements, 10_000)
    val result = ArrayList<Int>(actualCapacity)

    for (i in 0 until minOf(count, availableElements)) {
        result.add(bb.int)
    }
    return result
}
```

---

### ⚠️ SEC-06-03: Integer Underflow, Memory Spikes, & Stream De-sync in PTP Transports (HIGH)

#### Vulnerability Analysis
1. **In [`CanonPtpAdapter.kt:624-646`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/canon/CanonPtpAdapter.kt#L624-L646):**
   ```kotlin
   val containerLen = bb.int // Signed 32-bit integer
   when (containerType) {
       CanonPtpConstants.CONTAINER_DATA -> {
           val payloadLen = (containerLen - 12).coerceAtLeast(0)
           val totalData = ByteArray(payloadLen)
           // ...
           while (bytesRead < payloadLen) {
               val remainingToRead = payloadLen - bytesRead
               val nextChunk = ByteArray(remainingToRead.coerceAtMost(readBuffer.size)) // Heap allocation per chunk!
               // ...
           }
       }
   }
   ```
   - If `containerLen = 0x80000000` (-2,147,483,648), `containerLen - 12` underflows to `2,147,483,636`, causing an immediate `OutOfMemoryError`.
   - Allocating `val nextChunk = ByteArray(...)` inside the loop causes massive heap allocation churn and GC pauses during 50MB RAW transfers.

2. **In [`CannonPtpTransport.kt:61-67`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CannonPtpTransport.kt#L61-L67):**
   ```kotlin
   CONTAINER_DATA -> {
       val payloadLength = (length - 12).coerceAtLeast(0)
       val payload = ByteArray(payloadLength)
       if (payloadLength > 0) {
           bb.get(payload, 0, minOf(payloadLength, bb.remaining()))
       }
       dataPayload = payload
   }
   ```
   - `readContainer()` only reads up to 32KB. For any data container > 32KB, it leaves unread payload bytes in the USB hardware FIFO pipe.
   - On the next `readContainer()` invocation, raw payload bytes are erroneously parsed as a PTP container header, causing permanent transaction desynchronization.

#### Defensive Remediation
- Validate container length: `if (containerLen < 12 || containerLen > 256 * 1024 * 1024) throw CorruptedPtpPayloadException("Invalid container length: $containerLen")`.
- Reuse pooled read buffers (`DirectByteBufferPool` / shared pre-allocated `ByteArray`) instead of allocating inside the transfer loop.
- Fully drain all remaining container chunks from the endpoint before returning.

---

### 🟡 SEC-06-04: Permissive USB Interface Selection in `CanonCameraInitializer` (MEDIUM)

#### Vulnerability Analysis
In [`CannonCameraInitializer.kt:270-285`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CannonCameraInitializer.kt#L270-L285):
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
        if (intf.endpointCount > 0) { // Overly permissive fallback!
            return intf
        }
    }
    return null
}
```

#### Threat Vector & Impact
If a non-Still Image interface (such as a CDC communications interface, USB HID keyboard interface, or Vendor diagnostic interface) is present on a device with VID `0x04A9`, the fallback claims the first interface with `endpointCount > 0`, causing command packets to be sent into invalid endpoints.

#### Defensive Remediation
Require interfaces to have `USB_CLASS_STILL_IMAGE` or verify that the interface possesses distinct Bulk OUT, Bulk IN, and matching descriptor attributes.

---

### 🟡 SEC-06-05: Partial CTG Catalog SQLite Lock Concurrency & TOCTOU (MEDIUM)

#### Vulnerability Analysis
In [`CtgStabilityVerifier.kt:97-134`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/canon/CtgStabilityVerifier.kt#L97-L134), `verifyInvariance` samples metadata across a 250ms interval. During rapid continuous burst shooting (e.g. 30 fps on Canon R3/R5), the camera firmware can commit SQLite journal updates between Stage 1 verification and the actual SQLite database read.

#### Defensive Remediation
1. Ensure SQLite read transactions on cached `.CTG` files specify `PRAGMA query_only = ON` and `PRAGMA journal_mode = WAL`.
2. Wrap SQLite queries in structured `runCatching` blocks that fall back to timestamp-anchored object enumeration if `sqlite3_busy` or disk I/O errors occur.

---

### 🔵 SEC-06-06: Logcat Event Telemetry Sanitization (LOW)

#### Vulnerability Analysis
In `CanonPtpAdapter.kt:244` and `CanonPtpEnumerator.kt:74`, transaction IDs, storage IDs, and object counts are logged without conditional build checks.

#### Defensive Remediation
Ensure all verbose logging is disabled in production release builds via ProGuard / R8 rules and `Timber.Tree` stripping.

---

## 3. Resilience Assessment: Canon DIGIC Event Pump & Shutter Locks

| Security Scenario | Defense Mechanism | Assessment |
| :--- | :--- | :--- |
| **DIGIC 32-Record Ring Overflow** | Continuous `0x9116 EOS_GetEvent` pump coroutine | ✅ **Eliminates "Busy" shutter lock** |
| **Dual-Slot Card Collisions** | Isolated handle sets (`_slot1Handles`, `_slot2Handles`) | ✅ **Collision-free isolation** |
| **Sudden OTG Cable Severance** | `SafeUsbTransportManager` atomic latch drainage | ✅ **Prevents native `SIGSEGV` faults** |
| **Teardown Remote Mode Release** | Step 2 `0x9114 = 0` in `NonCancellable` context | ✅ **Restores local camera UI** |

---

## 4. Prioritized Remediation Roadmap

1. **Immediate (Blocker for Phase 6 Gate):**
   - Fix 64-bit extended box size calculations in `Cr3PreviewParser` (**SEC-06-01**).
   - Add capacity bounds checking to `decodeUInt32Array` in `CannonPtpEnumerator` (**SEC-06-02**).
   - Fix integer underflow and memory churn in `CanonPtpAdapter.transact` (**SEC-06-03**).

2. **Phase 6-7 Hardening:**
   - Remove blind `endpointCount > 0` interface claiming (**SEC-06-04**).
   - Ensure read-only SQLite pragma on CTG verification (**SEC-06-05**).
   - Sanitize diagnostic logging (**SEC-06-06**).
