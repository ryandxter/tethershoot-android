# AAS Security Audit Report: Phase 04 — Zero-Leak Hardware Core & Resource Lifecycle

**Audit Date:** 2026-08-20  
**Auditor:** GSD Security Auditor & Application Security Engineering (AAS Auto-Injected Playbooks)  
**Target Application:** TransferIn Android App (`com.rvmeur.transferin`)  
**Phase Under Review:** Phase 04 — Zero-Leak Hardware Core & Resource Lifecycle  
**Audit Scope:**
- [`UsbPermissionManager.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/UsbPermissionManager.kt)
- [`UsbTetherService.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/service/UsbTetherService.kt)
- [`SafeUsbTransportManager.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/core/SafeUsbTransportManager.kt)
- [`UsbHardwareSession.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/core/UsbHardwareSession.kt)
- [`DirectByteBufferPool.kt`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/core/DirectByteBufferPool.kt)
- [`AndroidManifest.xml`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/AndroidManifest.xml)
- Project-wide Credential & Token Storage Pipeline

---

## 1. Executive Summary & Security Posture

Phase 04 establishes the core hardware access layer, USB tethering lifecycle, and off-heap memory management. The architectural improvements successfully solve low-level stability issues (such as native JNI page faults during sudden OTG cable disconnects and GC spikes).

This audit identified **1 Critical Vulnerability**, **1 High Severity Vulnerability**, **3 Medium Severity Findings**, and **2 Low Severity / Hardening Recommendations**.

### Summary of Audit Findings Matrix

| Finding ID | Component / Area | Description | Severity | Status |
| :--- | :--- | :--- | :--- | :--- |
| **SEC-04-01** | Token Storage / SQLite | Plaintext OAuth refresh tokens and secrets stored in Room SQLite (`nas_connection`) with `allowBackup="true"` | 🚨 **CRITICAL** | Action Required |
| **SEC-04-02** | Auth / Secrets Management | Hardcoded Google OAuth client secret (`WEB_CLIENT_SECRET`) in decompilable source code | ⚠️ **HIGH** | Action Required |
| **SEC-04-03** | Android Manifest / Notifications | Missing `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` declaration | 🟡 **MEDIUM** | Action Required |
| **SEC-04-04** | USB Buffer Management | Potential `BufferOverflowException` / `BufferUnderflowException` & redundant heap allocation in `SafeUsbTransportManager.safeBulkTransfer(ByteBuffer)` | 🟡 **MEDIUM** | Action Required |
| **SEC-04-05** | Android Manifest / Cleartext | Global `android:usesCleartextTraffic="true"` configured without domain-specific network security config | 🟡 **MEDIUM** | Action Required |
| **SEC-04-06** | USB Permission Lifecycle | `PendingIntent.FLAG_MUTABLE` missing `FLAG_UPDATE_CURRENT` in `UsbPermissionManager` | 🔵 **LOW** | Action Required |
| **SEC-04-07** | Information Disclosure / Logging | Sensitive tokens & auth codes partially exposed in Logcat diagnostics | 🔵 **LOW** | Action Required |

---

## 2. Detailed Audit Findings

---

### 🚨 SEC-04-01: Plaintext OAuth Token & Secret Storage in Room Database (CRITICAL)

#### Vulnerability Analysis
In [`GoogleDriveAuthManager.kt:104-116`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/sftp/GoogleDriveAuthManager.kt#L104-L116) and [`NasConnectionEntity.kt:7-20`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/local/entity/NasConnectionEntity.kt#L7-L20), Google Drive OAuth2 `refresh_token`, `access_token`, and passwords are persisted as unencrypted plaintext strings in Room SQLite table `nas_connection`. Furthermore, [`AndroidManifest.xml:17`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/AndroidManifest.xml#L17) enables `android:allowBackup="true"`.

#### Threat Vector & Impact
- Any unprivileged backup mechanism (e.g. `adb backup`), physical device extraction, or backup restoration can extract long-lived Google Drive OAuth refresh tokens.
- Violates **AAS Secrets Management Playbook** and Project Requirement **QUAL-04** (mandatory EncryptedSharedPreferences/Keystore storage).

#### Defensive Remediation
1. Store all OAuth refresh tokens, access tokens, and NAS credentials in Android Jetpack Security [`EncryptedSharedPreferences`](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences) backed by the hardware Android Keystore (`MasterKey.DEFAULT_AES256_GCM_SPEC`).
2. Do not store credentials in Room database entities.
3. Set `android:allowBackup="false"` or configure `dataExtractionRules` to explicitly exclude credential stores and databases.

---

### ⚠️ SEC-04-02: Hardcoded OAuth Client Secret in Source Code (HIGH)

#### Vulnerability Analysis
In [`GoogleDriveAuthManager.kt:28`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/sftp/GoogleDriveAuthManager.kt#L28):
```kotlin
const val WEB_CLIENT_SECRET = "[REDACTED-rotasi-15-Nov-2026]"
```
Hardcoding client secrets in a public or distributed mobile binary provides zero cryptographic protection. Client secrets can be recovered instantly with JADX/dex2jar.

#### Threat Vector & Impact
- Rogue third parties can impersonate the web client application, bypass quota restrictions, and initiate unauthorized token exchange flows.

#### Defensive Remediation
- Migrate Google Drive authentication to the OAuth 2.0 PKCE (RFC 7636) authorization code flow using the Android Native Client ID (`ANDROID_CLIENT_ID = "343575435197-ug3en8ich4l1grgae2m6kbpcub0glu77.apps.googleusercontent.com"`).
- Native client flows utilize package name + SHA-1 certificate fingerprint verification in Google Cloud Console and require **no client secret**.

---

### 🟡 SEC-04-03: Missing Manifest Declaration for `POST_NOTIFICATIONS` (MEDIUM)

#### Vulnerability Analysis
In [`MainActivity.kt:95`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/MainActivity.kt#L95) and [`UsbTetherService.kt:253`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/service/UsbTetherService.kt#L253), the code checks and requests `Manifest.permission.POST_NOTIFICATIONS`. However, [`AndroidManifest.xml`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/AndroidManifest.xml) does **not** declare `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`.

#### Impact
- On Android 13+ (API 33+), runtime permission requests for undeclared permissions fail silently.
- Foreground service tether notification updates and warning banners will be suppressed by the OS.

#### Defensive Remediation
Add the permission to [`AndroidManifest.xml`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/AndroidManifest.xml):
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

### 🟡 SEC-04-04: `BufferOverflowException` / `BufferUnderflowException` & Heap Allocation Churn in `SafeUsbTransportManager` (MEDIUM)

#### Vulnerability Analysis
In [`SafeUsbTransportManager.kt:74-99`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/core/SafeUsbTransportManager.kt#L74-L99):
```kotlin
fun safeBulkTransfer(
    endpoint: UsbEndpoint,
    byteBuffer: ByteBuffer,
    length: Int,
    timeoutMs: Int
): Int {
    ensureActive()
    _activeIoCount.incrementAndGet()
    try {
        ensureActive()
        val temp = ByteArray(length) // Heap allocation on every transfer
        val isOut = endpoint.direction == android.hardware.usb.UsbConstants.USB_DIR_OUT
        if (isOut && byteBuffer.remaining() >= length) {
            val initialPos = byteBuffer.position()
            byteBuffer.get(temp, 0, length)
            byteBuffer.position(initialPos)
        }
        val transferred = connection.bulkTransfer(endpoint, temp, 0, length, timeoutMs)
        if (transferred > 0 && !isOut) {
            byteBuffer.put(temp, 0, transferred) // Crashes if byteBuffer.remaining() < transferred
        }
        return transferred
    } finally {
        _activeIoCount.decrementAndGet()
    }
}
```

#### Vulnerability Mechanics & Impact
1. **Buffer Overflow:** If an adversarial or noisy USB device returns `transferred` bytes greater than `byteBuffer.remaining()`, `byteBuffer.put(...)` throws an unhandled `java.nio.BufferOverflowException`, causing an app crash during streaming.
2. **Buffer Underflow / Corrupted Outgoing Data:** If `isOut == true` and `byteBuffer.remaining() < length`, the `if` condition evaluates to false; `temp` remains all-zeroes and transfers uninitialized zero bytes over USB.
3. **GC Overhead:** Allocating `val temp = ByteArray(length)` on every chunk undermines the zero-GC off-heap architecture of [`DirectByteBufferPool`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/core/DirectByteBufferPool.kt).

#### Defensive Remediation
```kotlin
val toWrite = minOf(transferred, byteBuffer.remaining())
if (toWrite > 0 && !isOut) {
    byteBuffer.put(temp, 0, toWrite)
}
```
For API 28+, directly utilize native `connection.bulkTransfer(endpoint, byteBuffer, length, timeoutMs)` or copy safely with strict bounds verification.

---

### 🟡 SEC-04-05: Unrestricted Cleartext Traffic Policy (MEDIUM)

#### Vulnerability Analysis
In [`AndroidManifest.xml:25`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/AndroidManifest.xml#L25):
```xml
android:usesCleartextTraffic="true"
```

#### Impact
Enabling global cleartext traffic permits non-TLS HTTP communication across all network domains, allowing MitM interception if any network client inadvertently connects over HTTP.

#### Defensive Remediation
Create `res/xml/network_security_config.xml` to limit cleartext traffic exclusively to local IP subnets (e.g. `192.168.0.0/16`, `10.0.0.0/8`, `172.16.0.0/12`) for local NAS servers, while enforcing HTTPS across all public cloud APIs.

---

### 🔵 SEC-04-06: `PendingIntent.FLAG_MUTABLE` Missing `FLAG_UPDATE_CURRENT` (LOW)

#### Vulnerability Analysis
In [`UsbPermissionManager.kt:160-171`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/UsbPermissionManager.kt#L160-L171):
```kotlin
val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    PendingIntent.FLAG_MUTABLE
} else {
    0
}
val permissionIntent = PendingIntent.getBroadcast(
    appContext,
    device.deviceId,
    intent,
    flags
)
```

#### Assessment & Verification
- `FLAG_MUTABLE` is **strictly required** by Android OS `UsbManager.requestPermission()` because the framework needs to populate `UsbManager.EXTRA_DEVICE` and `UsbManager.EXTRA_PERMISSION_GRANTED` via `Intent.fillIn()`.
- The intent is safely package-bound with `intent.setPackage(appContext.packageName)`, and the receiver is registered with `ContextCompat.RECEIVER_NOT_EXPORTED`.
- **Improvement:** Without `FLAG_UPDATE_CURRENT`, requesting permission for the same `deviceId` multiple times may return a cached `PendingIntent` with stale metadata.

#### Defensive Remediation
```kotlin
val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
} else {
    PendingIntent.FLAG_UPDATE_CURRENT
}
```

---

### 🔵 SEC-04-07: Log Sanitization & Sensitive Token Diagnostics (LOW)

#### Vulnerability Analysis
- In [`GoogleDriveAuthManager.kt:94`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/sftp/GoogleDriveAuthManager.kt#L94): `Log.e(TAG, "Token exchange error: ${response.code} $bodyString")`
- In [`GoogleDriveAuthManager.kt:102`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/sftp/GoogleDriveAuthManager.kt#L102): `Log.d(TAG, "OAuth Token exchange successful! Refresh Token retrieved: ${refreshToken.take(10)}...")`
- In [`MainActivity.kt:282`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/MainActivity.kt#L282): `Log.d(TAG_TRANSFER_APP, "Native Google Sign-In success! AuthCode: ${authCode?.take(10)}...")`

#### Impact
Partial authorization tokens and potential server reflection payloads are logged to the Android system Logcat, accessible to any process with Logcat read permissions or via bug reports.

#### Defensive Remediation
Ensure all authentication tokens, authorization codes, and credentials are completely redacted (`[REDACTED]`) before logging in debug and release builds.

---

## 3. Resilience Assessment: Adversarial USB OTG Disconnection

### Native JNI Page Fault Resilience (`libusbhost.so`)
- **Evaluation:** High
- [`SafeUsbTransportManager`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/core/SafeUsbTransportManager.kt) and [`UsbHardwareSession`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/core/UsbHardwareSession.kt) correctly implement atomic transfer counting (`_activeIoCount`) and a non-cancellable 5-step teardown barrier:
  1. Session coroutine cancellation
  2. IO latch drainage (`beginDrainAndClose` with 800ms barrier)
  3. Vendor teardown hook (`onVendorTeardown`)
  4. Interface release (`connection.releaseInterface`)
  5. Connection closure (`connection.close`)
- **Result:** Effectively prevents native JNI `SIGSEGV` page faults when physical USB OTG cables are disconnected mid-transfer.

---

## 4. Component Export & IPC Security Verification

| Component | Exported | Intent Filters / Permissions | Security Evaluation |
| :--- | :--- | :--- | :--- |
| `UsbTetherService` | `false` | None (Local only), `foregroundServiceType="connectedDevice"` | ✅ **Secure** — External apps cannot invoke or bind. |
| `FileProvider` | `false` | `grantUriPermissions="true"` | ✅ **Secure** — Explicit URI grants only. |
| `UsbPermissionManager` Receiver | `false` (Dynamic) | `ContextCompat.RECEIVER_NOT_EXPORTED` | ✅ **Secure** — External apps cannot spoof USB permission broadcasts. |
| `MainActivity` | `true` | `MAIN/LAUNCHER`, `USB_DEVICE_ATTACHED`, OAuth deep links | ⚠️ Deep link `code` parameter should be validated with PKCE / `state` token. |

---

## 5. Prioritized Remediation Roadmap

1. **Immediate (Blocker for Production):**
   - Implement `EncryptedSharedPreferences` for all OAuth refresh tokens and NAS credentials (**SEC-04-01**).
   - Add `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` to `AndroidManifest.xml` (**SEC-04-03**).
   - Implement bound-safe buffer copy in `SafeUsbTransportManager.safeBulkTransfer` (**SEC-04-04**).

2. **Phase 5-6 Hardening:**
   - Migrate OAuth2 to PKCE Native Client flow and remove `WEB_CLIENT_SECRET` (**SEC-04-02**).
   - Add `network_security_config.xml` to limit cleartext traffic to local LAN subnets (**SEC-04-05**).
   - Sanitize all Logcat logging for tokens and credentials (**SEC-04-07**).
