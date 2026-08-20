# AAS Security Audit Report: Phase 07 — Sony Alpha & Nikon Multi-Vendor Tether Adapters

**Audit Date:** 2026-08-20  
**Auditor:** GSD Security Auditor & Senior Application Security Engineer (AAS Auto-Injected Playbooks: `security-audit`, `api-security-testing`, `secrets-management`, `sast-configuration`)  
**Target Application:** TransferIn Android App (`com.rvmeur.transferin`)  
**Phase Under Review:** Phase 07 — Sony Alpha & Nikon Multi-Vendor Tether Adapters  
**Audit Scope:**
- [`SonyPtpDefragmenter.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/sony/SonyPtpDefragmenter.kt)
- [`ArwPreviewParser.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/sony/ArwPreviewParser.kt)
- [`SonyDramSafetyGuard.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/sony/SonyDramSafetyGuard.kt)
- [`SonyPtpAdapter.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/sony/SonyPtpAdapter.kt)
- [`SonyPtpTransport.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/sony/SonyPtpTransport.kt)
- [`NikonPtpTransport.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/nikon/NikonPtpTransport.kt)
- [`NikonPtpAdapter.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/nikon/NikonPtpAdapter.kt)
- [`NikonStorageMapper.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/nikon/NikonStorageMapper.kt)
- [`NikonRetryPolicy.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/nikon/NikonRetryPolicy.kt)
- [`CameraTetherAdapterFactory.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CameraTetherAdapterFactory.kt)

---

## 1. Executive Summary & Security Posture

Phase 07 completes the multi-vendor tethering suite by introducing native PTP tether engines for **Sony Alpha** (`VID 0x054C`) and **Nikon** (`VID 0x04B0`). The implementation incorporates a 3-phase SDIO handshake (`0x9280`), volatile DRAM loss prevention (`0xD2C1`/`0xD2C2`), multi-packet USB defragmentation, TIFF SubIFD `.ARW` preview extraction, Nikon vendor event polling (`0x90C0`/`0x90C1`), dual-slot card mapping, and an adaptive exponential backoff loop for EXPEED `0x2019 DeviceBusy` handling.

This security audit identified **3 High Severity Vulnerabilities**, **2 Medium Severity Findings**, and **2 Low Severity Findings**.

### Summary of Audit Findings Matrix

| Finding ID | Component / Area | Description | Severity | Status |
| :--- | :--- | :--- | :--- | :--- |
| **SEC-07-01** | `NikonPtpTransport` | Unchecked container length integer underflow leading to `OutOfMemoryError` DoS | ⚠️ **HIGH** | Action Required |
| **SEC-07-02** | `ArwPreviewParser` | Out-of-bounds byte indexing on negative offset values in `readUInt32` / `readUInt16` | ⚠️ **HIGH** | Action Required |
| **SEC-07-03** | `SonyDramSafetyGuard` | Volatile DRAM data destruction risk under `SAVE_DEST_PC_ONLY` (0x0001) mode | ⚠️ **HIGH** | Addressed & Hardening Req. |
| **SEC-07-04** | Sony / Nikon Adapters | Thread stall / CPU DoS in `decodeUInt32Array` caused by unvalidated `count` loop iteration | 🟡 **MEDIUM** | Action Required |
| **SEC-07-05** | Sony / Nikon Adapters | Permissive interface descriptor claiming (`endpointCount > 0`) susceptible to interface hijacking | 🟡 **MEDIUM** | Action Required |
| **SEC-07-06** | `CameraTetherAdapterFactory` | Missing Fujifilm VID (`0x04CB`) mapping in unified adapter factory | 🔵 **LOW** | Action Required |
| **SEC-07-07** | Information Disclosure | Logcat diagnostic verbosity in release builds | 🔵 **LOW** | Action Required |

---

## 2. Detailed Audit Findings

---

### ⚠️ SEC-07-01: Unchecked Container Length Underflow & DoS in `NikonPtpTransport` (HIGH)

#### Vulnerability Analysis
In [`NikonPtpTransport.kt:69-82`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/nikon/NikonPtpTransport.kt#L69-L82):

```kotlin
val length = bb.int
val type = bb.short.toInt() and 0xFFFF
val code = bb.short.toInt() and 0xFFFF
val txId = bb.int

when (type) {
    NikonPtpConstants.CONTAINER_DATA -> {
        val totalPayloadLength = (length - HEADER_SIZE).coerceAtLeast(0)
        dataPayload = readFullPayload(
            initialPacket = packet,
            initialPayloadLength = (packet.size - HEADER_SIZE).coerceAtLeast(0),
            totalPayloadLength = totalPayloadLength,
            timeoutMs = timeoutMs
        )
    }
}
```

#### Threat Vector & Impact
1. Unlike `SonyPtpDefragmenter` (which verifies `length in 12..256 * 1024 * 1024`), `NikonPtpTransport` performs **zero validation** on `length`.
2. If `length` is signed negative (e.g. `0x80000000` = `-2,147,483,648`), `length - HEADER_SIZE` underflows to `2,147,483,636`.
3. In `readFullPayload`: `val result = ByteArray(totalPayloadLength)` attempts to allocate a 2.14 GB byte array on the JVM heap.
4. The Android runtime throws an unhandled `java.lang.OutOfMemoryError`, immediately crashing the app.

#### Defensive Remediation
Apply strict bounds checking before allocating buffers or calculating payload offsets:

```kotlin
if (length < HEADER_SIZE || length > 256 * 1024 * 1024) {
    throw IOException("Invalid Nikon PTP container length: $length (expected $HEADER_SIZE..268435456)")
}
val totalPayloadLength = length - HEADER_SIZE
```

---

### ⚠️ SEC-07-02: Out-of-Bounds Indexing on Negative Offsets in `ArwPreviewParser` (HIGH)

#### Vulnerability Analysis
In [`ArwPreviewParser.kt:180-186`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/sony/ArwPreviewParser.kt#L180-L186) and [`ArwPreviewParser.kt:226-230`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/sony/ArwPreviewParser.kt#L226-L230):

```kotlin
// In scanIfd:
TAG_SUB_IFDS -> {
    if (count == 1) {
        subIfdOffsets.add(valOrOffset)
    } else if (count > 1 && valOrOffset < bytes.size) {
        for (k in 0 until minOf(count, 8)) {
            val off = valOrOffset + k * 4
            if (off + 4 <= bytes.size) {
                subIfdOffsets.add(readUInt32(bytes, off, byteOrder))
            }
        }
    }
}

// In readUInt32:
private fun readUInt32(bytes: ByteArray, offset: Int, byteOrder: ByteOrder): Int {
    if (offset + 4 > bytes.size) return 0
    val bb = ByteBuffer.wrap(bytes, offset, 4).order(byteOrder)
    return bb.int
}
```

#### Threat Vector & Impact
1. `readUInt32` returns signed `Int`. If the TIFF tag offset is malformed or negative (e.g. `valOrOffset = -100`), the condition `valOrOffset < bytes.size` evaluates to `true` (since `-100 < 131072`).
2. Then `val off = valOrOffset + k * 4` is `-100`.
3. In `readUInt32`: `if (offset + 4 > bytes.size)` evaluates `-100 + 4 = -96 > 131072` as `false`.
4. `ByteBuffer.wrap(bytes, -100, 4)` is invoked with a negative offset, throwing `java.lang.IndexOutOfBoundsException` and crashing the ARW parsing pipeline.

#### Defensive Remediation
Update `readUInt32` and `readUInt16` to strictly validate `offset >= 0`:

```kotlin
private fun readUInt16(bytes: ByteArray, offset: Int, byteOrder: ByteOrder): Int {
    if (offset < 0 || offset + 2 > bytes.size) return 0
    val bb = ByteBuffer.wrap(bytes, offset, 2).order(byteOrder)
    return bb.short.toInt() and 0xFFFF
}

private fun readUInt32(bytes: ByteArray, offset: Int, byteOrder: ByteOrder): Int {
    if (offset < 0 || offset + 4 > bytes.size) return 0
    val bb = ByteBuffer.wrap(bytes, offset, 4).order(byteOrder)
    return bb.int
}
```
And in `scanIfd`:
```kotlin
else if (count > 1 && valOrOffset in 0 until bytes.size) {
    for (k in 0 until minOf(count, 8)) {
        val off = valOrOffset + k * 4
        if (off >= 0 && off + 4 <= bytes.size) {
            subIfdOffsets.add(readUInt32(bytes, off, byteOrder))
        }
    }
}
```

---

### ⚠️ SEC-07-03: Volatile DRAM Data Loss on Sony Alpha Cameras (`0xD2C1` / `0xD2C2`) (HIGH)

#### Vulnerability Analysis & Forensic Assessment
On Sony Alpha cameras connected via USB PTP (e.g. A7 IV, A7R V, A1, A9 III), the default PC Remote setting often configures the Save Destination property (`0xD2C1` / `0xD2C2`) to `0x0001` (`SAVE_DEST_PC_ONLY`).

**The Danger of Volatile DRAM Mode:**
- When in `SAVE_DEST_PC_ONLY` (0x0001), captured images reside **exclusively in volatile camera DRAM buffer** and are NOT written to the camera's physical SD / CFexpress card.
- If the USB OTG cable is bumped, if Android OS kills the app under memory pressure, or if battery drops, the images stored in camera DRAM are **permanently and irreversibly destroyed**.

#### Remediation Verification
[`SonyDramSafetyGuard.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/sony/SonyDramSafetyGuard.kt) implements an automated 4-step safety workflow:
1. Queries `0xD2C1` and `0xD2C2` via Sony vendor op `0x9802` and standard PTP op `0x1015`.
2. Detects `SAVE_DEST_PC_ONLY` (0x0001).
3. Executes automated remediation: sends `0x9803 SetDevicePropValue(0xD2C1, 0x0002)` (`SAVE_DEST_PC_AND_CAMERA`).
4. Re-reads and verifies the property value. If verified, emits `SonyDramSafetyStatus.Remediated`. If remediation fails, emits `SonyDramSafetyStatus.Warning`.

#### Additional Hardening Requirement
If `SonyDramSafetyGuard` returns `SonyDramSafetyStatus.Warning` (remediation failed due to locked camera firmware), the tether session must surface a high-visibility modal to the photographer requiring manual camera menu adjustment before allowing shooting.

---

### 🟡 SEC-07-04: Thread Stall & CPU DoS via Unbounded Loop in `decodeUInt32Array` (MEDIUM)

#### Vulnerability Analysis
In [`SonyPtpAdapter.kt:310-316`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/sony/SonyPtpAdapter.kt#L310-L316) and [`NikonPtpAdapter.kt:311-317`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/nikon/NikonPtpAdapter.kt#L311-L317):

```kotlin
val count = bb.int
if (count <= 0) return emptyList()

val list = ArrayList<Int>(minOf(count, 10000))
repeat(count) {
    if (bb.remaining() >= 4) {
        list.add(bb.int)
    }
}
```

#### Threat Vector & Impact
While `ArrayList` allocation is bounded to `10000`, `repeat(count)` executes `count` iterations. If an adversarial packet provides `count = 0x7FFFFFFF` (2.14 billion), the CPU executes 2.14 billion loop iterations on the IO dispatcher thread, stalling the worker coroutine for 5 to 10 seconds.

#### Defensive Remediation
```kotlin
val count = bb.int
if (count <= 0) return emptyList()

val maxPossibleElements = bb.remaining() / 4
val safeIterations = minOf(count, maxPossibleElements, 100_000)
val list = ArrayList<Int>(minOf(safeIterations, 10_000))

repeat(safeIterations) {
    list.add(bb.int)
}
```

---

### 🟡 SEC-07-05: Permissive USB Interface Selection in Sony and Nikon Adapters (MEDIUM)

#### Vulnerability Analysis
In [`SonyPtpAdapter.kt:327-331`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/sony/SonyPtpAdapter.kt#L327-L331) and [`NikonPtpAdapter.kt:328-332`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/nikon/NikonPtpAdapter.kt#L328-L332):
```kotlin
for (i in 0 until device.interfaceCount) {
    val intf = device.getInterface(i)
    if (intf.endpointCount > 0) { // Fallback claims ANY interface!
        return intf
    }
}
```

#### Threat Vector & Impact
If a connected composite device has a non-Still Image interface appearing before the Still Image interface, the fallback claims that interface and attempts to send PTP commands.

#### Defensive Remediation
Enforce `intf.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE` or require verification of Bulk IN and Bulk OUT endpoint types.

---

### 🔵 SEC-07-06: Missing Fujifilm VID in `CameraTetherAdapterFactory` (LOW)

#### Vulnerability Analysis
In [`CameraTetherAdapterFactory.kt:37-69`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/CameraTetherAdapterFactory.kt#L37-L69), `createAdapter` handles Sony (`0x054C`), Nikon (`0x04B0`), and Canon (`0x04A9`), but does not have a branch for Fujifilm (`0x04CB` / `FujiPtpConstants.FUJI_VENDOR_ID`).

#### Defensive Remediation
Add the Fujifilm branch:
```kotlin
FujiPtpConstants.FUJI_VENDOR_ID -> {
    Log.i(TAG, "Instantiating FujiTetherAdapter for ${device.deviceName}")
    FujiTetherAdapter(bufferPool = bufferPool)
}
```

---

### 🔵 SEC-07-07: Logcat Diagnostic Sanitization (LOW)

#### Vulnerability Analysis
Ensure that all opcode transactions, EXPEED backoff retry counts, and storage descriptor labels are sanitized in production release builds.

---

## 3. Resilience Assessment: Multi-Vendor Edge Cases

| Scenario | Component | Security Mechanism | Status |
| :--- | :--- | :--- | :--- |
| **Sony Volatile DRAM Mode** | `SonyDramSafetyGuard` | Auto-remediates `0xD2C1` to `0x0002` (PC+Camera) | ✅ **Protected** |
| **Sony Multi-Packet Framing** | `SonyPtpDefragmenter` | Handles ZLP and short packets with 256MB bounds | ✅ **Resilient** |
| **Nikon EXPEED Card Flush** | `NikonRetryPolicy` | Exponential backoff (25ms..1500ms with jitter) | ✅ **No Thread Lockups** |
| **Nikon Dual-Slot Mirroring** | `NikonStorageMapper` | Discretely partitions Slot 1 vs Slot 2 handles | ✅ **No Collision** |

---

## 4. Prioritized Remediation Roadmap

1. **Immediate (Blocker for Phase 7 Gate):**
   - Add length verification to `NikonPtpTransport.transact` (**SEC-07-01**).
   - Add bounds checks to `ArwPreviewParser.readUInt32` and `readUInt16` (**SEC-07-02**).
   - Bound loop iterations in `decodeUInt32Array` (**SEC-07-04**).

2. **Phase 7 Hardening:**
   - Add hard warning UI modal when DRAM remediation fails (**SEC-07-03**).
   - Remove blind `endpointCount > 0` interface claiming (**SEC-07-05**).
   - Add Fujifilm VID to `CameraTetherAdapterFactory` (**SEC-07-06**).
