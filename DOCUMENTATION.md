# Dokumentasi Teknis Project: Transfer In

## 1. Identitas & Tujuan Utama
**Transfer In** adalah aplikasi Android "Bridge" yang berfungsi sebagai jembatan otomatis untuk memindahkan data media dari perangkat USB/Kamera (via MTP atau Storage Access Framework) langsung ke NAS melalui protokol SFTP. Aplikasi ini dirancang untuk alur kerja fotografer profesional di mana file harus segera masuk ke penyimpanan terpusat saat pengambilan gambar berlangsung.

---

## 2. Struktur Paket (Package Structure) Terperinci
Berikut adalah hierarki direktori dan tanggung jawab masing-masing folder:

**com.rvmeur.transferin**
*   **data**
    *   **local**
        *   **db**: Konfigurasi Room Database (`DatabaseProvider`, `TransferInDatabase`)
        *   **dao**: Antarmuka query (`NasConnectionDao`, `SessionDao`, `TransferQueueDao`)
        *   **entity**: Tabel Database (`NasConnectionEntity`, `SessionEntity`, `TransferQueueItemEntity`)
    *   **repository**: Implementasi Repository (Concrete Classes: `CameraMediaRepositoryImpl.kt`, `NasConnectionRepositoryImpl.kt`, `SessionRepositoryImpl.kt`)
    *   **sftp**: Logika Network SFTP (`SftpClient.kt` interface, `JschSftpClient.kt` implementasi)
    *   **usb**: Low-level USB & File Handling (`UsbHelper.kt`, `DocumentFileScanner.kt`, `FileStabilityChecker.kt`, `UsbSourceOrchestrator.kt`)
*   **domain**
    *   **model**: Data class murni (`ConnectionMode`, `CameraRuntimeState`, `RemoteFolder`)
    *   **repository**: Interface Repository (Abstraksi untuk UI/UseCase)
    *   **session**: Logika Bisnis Sesi (`ImportCameraEngine.kt`, `SourceWatcher.kt`, `ProjectSessionManager.kt`)
    *   **usecase**: Logika bisnis spesifik (`ListCameraMediaUseCase.kt`, dll)
*   **feature**: Layer Presentasi (UI) berdasarkan Fitur
    *   **auth**: Login NAS & Setting SFTP
    *   **connectcamera**: Deteksi USB & Persiapan Sesi
    *   **folder**: Browser folder NAS & Pemilih Folder Sumber
    *   **sessionactive**: Monitoring Progress Upload (Real-time)
    *   **sessionsummary**: Ringkasan hasil transfer setelah selesai
*   **service**: Background Components (`UsbTetherService.kt` - Foreground service)
*   **ui**: Konfigurasi Material3 Theme

---

## 3. Komponen Utama & Tanggung Jawab

### A. Mesin Deteksi & Stabilitas (Data Layer - USB)
*   **`UsbHelper`**: Bertanggung jawab atas siklus hidup USB, menangani `UsbManager`, izin, dan komunikasi MTP.
*   **`FileStabilityChecker`**: Memantau ukuran file dalam interval waktu tertentu untuk memastikan file "siap" (tidak sedang ditulis oleh kamera).

### B. Mesin Pemindah (Domain Layer - Engine)
*   **`ImportCameraEngine`**: Otak aplikasi yang menghubungkan status hardware dengan instruksi user via Coroutines background loop.
*   **`SourceWatcher`**: Menggunakan `SharedFlow` untuk notifikasi penemuan file secara asinkron (reactive event bus).

### C. Layer Komunikasi (Data Layer - SFTP)
*   **`JschSftpClient`**: Menangani SSH/SFTP, pembuatan direktori, dan streaming upload file dari `Uri` ke NAS.

---

## 4. Alur Kerja & Koneksi Antar Bagian (Data Flow)

### Alur 1: Deteksi dan Inisialisasi
1.  `MainActivity` inisialisasi `UsbHelper`.
2.  USB dicolok -> `UsbHelper` kirim `StateFlow` ke `ConnectCameraViewModel`.
3.  User pilih folder sumber -> simpan `treeUri`.

### Alur 2: Proses "Auto-Import" (The Core Loop)
1.  **Scanning**: `UsbSourceOrchestrator` scan folder sumber secara berkala.
2.  **Discovery**: File baru dikirim ke `SourceWatcher`.
3.  **Stability Check**: `ImportCameraEngine` minta `FileStabilityChecker` validasi stabilitas file.
4.  **Enqueuing**: Simpan ke database Room status `QUEUED`.
5.  **Uploading**: `SessionViewModel` deteksi `QUEUED` -> panggil `JschSftpClient` -> Update status `SUCCESS`.

---

## 5. Skema Data (Persistence - Room)
1.  **`nas_connections`**: Detail Host, Port, Credential, Default Folder.
2.  **`sessions`**: Log start/end sesi, customer name, stats.
3.  **`transfer_queue_items`**: Antrean upload (sourceUri, targetPath, size, status: QUEUED/UPLOADING/SUCCESS/FAILED).

---

## 6. Teknologi Utama
*   Jetpack Compose, Kotlin Coroutines & Flow.
*   Room Persistence, JSch (SFTP), USB Host API.

---

## 7. Penanganan Resiliensi
*   **Retry Logic**: Otomatis mencoba kembali upload yang gagal.
*   **MTP Safety**: Penanganan timeout/busy session pada hardware kamera.
*   **Reactive UI**: UI mengamati database sebagai *Single Source of Truth*.
