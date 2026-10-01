# Forensic Report: Decompilation Authenticity Assessment & Root Cause Analysis for TransferInFuji

**Timestamp:** 2026-10-01T22:58:00+07:00  
**Target Project:** TransferInFuji (`/Users/chiio/VSCode/TransferInFuji`)  
**Analyzed Binary:** `Piufoto_v3.46.29_APKPure.xapk` (`com.alltuu.android.piufoto`)  
**Methodology:** AAS Multi-Agent Debate, Codebase Forensic Review, and Security Reverse-Engineering Perspective.

---

## 1. Decompilation Authenticity & Limitations (Anti-Tamper / Packer Audit)

### 1.1 Apa yang Sebenarnya Terbongkar vs Terproteksi
Pemeriksaan binary security pentest mengonfirmasi:
1. **DEX Layer (TERPROTEKSI KETAT)**:
   - APK utama diproteksi oleh **NetEase EasyShield / Yidun (网易易盾)** (`com.xaqalea.xye.rflccaf.*` + stub `classes.dex` 58 KB + encrypted payload `assets/nedata.db` 16.9 MB).
   - Bytecode Java asli (`com.alltuu.android.godson.*`) dienkripsi dan di-load secara dinamis ke memory saat runtime via JNI `MyJni`.
   - **Batas Kejujuran Forensik**: Siapa pun atau model AI mana pun yang mengklaim telah membaca source code Java `com.alltuu.android.godson` 100% tanpa runtime memory dump (Frida hook pada rooted device) adalah **keliru / berhalusinasi**.
2. **Native Shared Objects (`.so`) (TERBONGKAR SECARA AKURAT)**:
   - `libportal0.1.so` dan `libusb-lib.so` **TIDAK** diproteksi dengan virtualisasi VM-obfuscator. Dynamic symbol table (`nm -D`) dan strings ASCII/UTF-8 terbuka penuh.
   - Terbukti secara valid: Piufoto membajak Android USB descriptor menggunakan `libusb_wrap_sys_device(fd)` (`AlltuuUsbPort`) untuk melewati batasan `MtpDevice` Android OS, serta memanfaatkan library `libaums` (`com.github.mjdev.libaums`) untuk pembacaan raw SCSI/FAT32 pada Card Reader.
3. **Flutter Dart AOT Snapshot (`libapp.so`) (TERBONGKAR)**:
   - Dart VM snapshot string table dan dispatch metadata tidak dienkripsi oleh packer.
   - Skema tabel SQLite `AlltuuWaitUploadFile` dan `AlltuuFailUploadFile`, event channel model (`CardReaderReportEvent`, `IosPtpReportEvent`), dan state flow `alltuu_local_upload` berhasil diekstraksi secara faktual.

---

## 2. Root Cause Analysis: 3 Masalah Kritis TransferInFuji

### Bug 1: Gagal Membaca Data Existing dari Memori Kamera
- **Lokasi Codebase**:
  - [`UsbHelper.kt:302-327`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/UsbHelper.kt#L302-L327)
  - [`UsbHelper.kt:472-487`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/UsbHelper.kt#L472-L487)
- **Akar Masalah**:
  1. **Parent ID Flat Query**: `UsbHelper.kt` memanggil `mtp.getObjectHandles(storageId, 0, 0)` dengan `parent = 0`. Dalam standar protokol PTP/MTP, `parent = 0` hanya me-list object di root directory. Foto kamera selalu berada di dalam subfolder `DCIM/100_FUJI/` atau `DCIM/100CANON/`. Karena tidak melakukan recursive folder walk atau `parent = 0xFFFFFFFF`, query selalu mengembalikan `0` file.
  2. **Interface Claim Collision**: Ketika vendor initializer (seperti `FujiCameraInitializer` atau `SonyCameraInitializer`) mengklaim USB interface via `UsbDeviceConnection.claimInterface()`, objek Android `MtpDevice` gagal dibuka (`activeMtp == null`). Akibatnya `listMedia()` langsung keluar di baris 302: `val mtp = activeMtp ?: return emptyList()`.
  3. **Tether Mode Storage Locking**: Kamera Fujifilm dalam mode *USB Tether Shooting* (PC Shoot) secara default mengunci storage bus dari pembacaan MTP biasa kecuali dipicu via PTP vendor storage command.

---

### Bug 2: Transformasi Nama File Menjadi Lowercase
- **Lokasi Codebase**:
  - [`Fingerprint.kt:48-50`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/domain/shoot/Fingerprint.kt#L48-L50)
  - [`Fingerprint.kt:66-69`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/domain/shoot/Fingerprint.kt#L66-L69)
  - [`NewShotDetector.kt:61`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/domain/shoot/NewShotDetector.kt#L61)
  - [`UsbSourceOrchestrator.kt:192`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/UsbSourceOrchestrator.kt#L192)
  - [`SessionViewModel.kt:298-325`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/feature/sessionactive/SessionViewModel.kt#L298-L325)
- **Akar Masalah**:
  - Di `Fingerprint.kt`, method `toStorageKey()` mengonversi filename menjadi lowercase (`filename.trim().lowercase()`) ke format `{handle}|{normalizedName}|{size}`.
  - Namun pada `fromStorageKey()`, nilai `parts[1]` (yang sudah menjadi lowercase) dikembalikan dan digunakan kembali sebagai `filename` entitas `CtgMediaEntry` / `MediaFingerprint`.
  - Ketika `SessionViewModel` memproses CTG diff (`diff.added`), baris 325 mengisi `originalFileName = entry.filename` (yang berasal dari parsing fingerprint lowercase), sehingga nama file asli dari kamera (`DSCF0001.JPG`) berubah permanen menjadi lowercase (`dscf0001.jpg`) saat diunggah ke NAS/Cloud.

---

### Bug 3: Kamera Fuji Berkedip Setelah Jepretan Pertama & Gagal Dimatikan (Firmware Deadlock)
- **Lokasi Codebase**:
  - [`FujiTetherAdapter.kt:151-164`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/fuji/FujiTetherAdapter.kt#L151-L164)
  - [`FujiTetherAdapter.kt:201-218`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/data/usb/vendor/fuji/FujiTetherAdapter.kt#L201-L218)
  - [`SessionViewModel.kt:220-258`](file:///Users/chiio/VSCode/TransferInFuji/app/src/main/java/com/rvmeur/transferin/feature/sessionactive/SessionViewModel.kt#L220-L258)
- **Akar Masalah**:
  1. **Unconsumed PTP Buffer Phase**: `FujiTetherAdapter` mengatur `PROP_FUJI_SAVE_DESTINATION` (`0xD216 = 0x0002` [PC + SD Card]). Mode ini mewajibkan Host (aplikasi) untuk segera membaca payload buffer foto melalui `GetObject` (`0x1009`) atau mengirim ACK via PTP Bulk Pipe.
  2. **MTP Download Collision**: Ketika event `ObjectAdded` tertangkap, `SessionViewModel` malah memanggil `downloadMtpFile()` yang mencoba menggunakan Android `MtpDevice`. Karena USB interface sedang di-*claim* oleh `FujiTetherAdapter`, panggilan `MtpDevice.importFile()` mengembalikan null/gagal.
  3. **Hanging Hardware Pipe**: Tidak ada komponen yang memanggil `FujiTetherAdapter.streamMedia(handle)` untuk men-drain data dari USB Bulk IN endpoint. Firmware Fujifilm menganggap transfer foto tethering sedang *in-flight*, menahan lampu indikator berkedip (green/orange), dan memblokir power-down sequence kamera untuk melindungi integritas buffer DRAM kamera.

---

## 3. Rencana Perbaikan Sistematis (Remediation Roadmap)

1. **Perbaikan PTP Drain & Handshake Fuji**:
   - Ganti pemanggilan `downloadMtpFile()` di `SessionViewModel` agar menggunakan `tetherAdapter.streamMedia(handle)` langsung melalui endpoint native PTP tanpa melewati `MtpDevice`.
   - Kirim `0x902B` ACK setelah transfer selesai agar firmware Fuji langsung melepaskan buffer internal.
2. **Perbaikan Case-Preserving Filename**:
   - Pisahkan `displayFileName` / `originalFileName` (case-sensitive) dari `fingerprintKey` (case-insensitive) di `Fingerprint.kt` dan `CompoundFingerprintGenerator.kt`.
3. **Perbaikan Enumerasi Storage Kamera Existing**:
   - Implementasikan PTP recursive folder tree traversal di `UsbHelper.kt` dan tambahkan fallback enumerasi langsung via PTP Native Adapter saat `activeMtp` null.
