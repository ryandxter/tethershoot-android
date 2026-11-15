<div align="center">

<img src="https://cdn.jsdelivr.net/npm/lucide-static/icons/globe.svg" width="20" alt=""> [Bahasa Indonesia](#bahasa-indonesia) | [English](#english)

</div>

---

<a id="bahasa-indonesia"></a>
# <img src="https://cdn.jsdelivr.net/npm/lucide-static/icons/camera.svg" width="20" alt=""> @rvmeur Tether

[![Versi](https://img.shields.io/badge/versi-1.4.1-blue.svg)](https://github.com/ryandxter/tethershoot-android/releases)
[![Platform](https://img.shields.io/badge/platform-Android%20(API%2035)-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.10-purple.svg)](https://kotlinlang.org)

@rvmeur Tether (`com.rvmeur.transfer`) adalah aplikasi Android mandiri untuk kebutuhan pemotretan tether kabel USB dari kamera Fujifilm langsung ke tablet Android. Berkas foto diunduh otomatis dari buffer kamera tanpa mengunci pengoperasian kamera, lalu diunggah ke folder Google Drive pilihan pengguna melalui layanan latar depan. Proyek utilitas satu orang ini dibangun secara terarah dan teruji pada kamera Fujifilm X-T5 serta tablet Xiaomi Mi Pad 6S Pro.

- [Fitur Utama](#fitur-utama)
- [Cara Kerja](#cara-kerja)
- [Teknologi](#teknologi)
- [Instalasi dan Build](#instalasi-dan-build)
- [Struktur Repositori](#struktur-repositori)
- [Privasi dan Keamanan](#privasi-dan-keamanan)
- [Lisensi dan Kredit](#lisensi-dan-kredit)

---

<a id="fitur-utama"></a>
## <img src="https://cdn.jsdelivr.net/npm/lucide-static/icons/layers.svg" width="20" alt=""> Fitur Utama

- **Tether USB Per Berkas:** Mengambil foto dari buffer kamera Fujifilm secara berurutan. Kamera dapat dimatikan secara normal kapan saja tanpa perlu mencabut kabel USB.
- **Unggah Otomatis ke Google Drive:** Berkas dikirim langsung ke folder Google Drive yang ditentukan melalui alur Google Picker resmi.
- **Gerbang Kurasi Lokal:** Menilai ketajaman dan pencahayaan tiap pasangan foto secara lokal. Foto yang memenuhi standar langsung masuk antrean unggah; foto yang meragukan ditahan untuk peninjauan manual dan tidak pernah dihapus otomatis.
- **Isolasi Sesi & Pasangan Berkas:** Foto yang telah ada di kartu memori sebelum sesi dimulai diabaikan secara otomatis (baseline). Berkas RAW dan JPG diperlakukan sebagai satu kesatuan pasangan dengan format penamaan `IMG_<tanggal>_<nomor urut>`.
- **Unggahan Paralel Tangguh:** Mendukung hingga 3 proses unggah bersamaan dengan metode resumable upload dan verifikasi integritas MD5. Layanan latar depan menjaga antrean tetap berjalan sampai tuntas meskipun koneksi kabel kamera terputus.
- **Pembaruan OTA Terverifikasi:** Pemeriksaan pembaruan berkala dan manual via GitHub Releases. Unduhan berkas APK diverifikasi menggunakan hash SHA-256 dan ditolak jika manifes rilis tidak lengkap (fail-closed).
- **Sistem Notifikasi:** Menyediakan notifikasi status lokal (penyelesaian sesi, progres antrean unggah, pembaruan aplikasi) dan fondasi Firebase Cloud Messaging (FCM) untuk kebutuhan notifikasi jarak jauh.

---

<a id="cara-kerja"></a>
## <img src="https://cdn.jsdelivr.net/npm/lucide-static/icons/play.svg" width="20" alt=""> Cara Kerja

Pengoperasian aplikasi berjalan melalui wizard 3 langkah:

1. **Masuk Akun Google & Pilih Folder:** Pengguna melakukan otentikasi Google dan memilih folder penyimpanan tujuan via Google Picker dengan izin akses terbatas `drive.file`.
2. **Sambungkan Kamera:** Hubungkan kamera Fujifilm ke tablet Android menggunakan kabel USB.
3. **Konfigurasi Aturan Sesi & Mulai:** Tetapkan preferensi sesi pemotretan dan mulai proses tethering.

<details>
<summary>Detail Teknis Alur Sesi Pemotretan</summary>

- **Penetapan Baseline:** Saat sesi dimulai, berkas yang sudah tersimpan di kartu memori kamera didata sebagai baseline agar tidak ikut terproses. Hanya jepretan baru yang diambil ke dalam antrean.
- **Penanganan RAW+JPG:** Berkas RAW dan JPG dengan nomor urut yang sama dipasangkan dan diproses bersamaan.
- **Standarisasi Penamaan:** Penamaan berkas diubah mengikuti pola konsisten `IMG_<tanggal>_<nomor urut>`.
- **Evaluasi Kurasi:** Algoritma lokal mengevaluasi tingkat ketajaman dan eksposur. Berkas yang lolos langsung dialihkan ke antrean unggah, sedangkan berkas yang memerlukan konfirmasi fotografer diberi status ditahan tanpa penghapusan otomatis.
- **Ketahanan Layanan Latar Depan:** Pengunggahan dijalankan oleh Android Foreground Service dengan 3 saluran paralel. Jika kabel terlepas di tengah sesi, seluruh berkas yang telah tersimpan di tablet tetap diunggah hingga selesai.
- **Validasi Integritas:** Setiap potongan unggahan resumable dicocokkan dengan nilai MD5 untuk memastikan tidak ada data yang korup di penyimpanan awan.

</details>

---

<a id="teknologi"></a>
## <img src="https://cdn.jsdelivr.net/npm/lucide-static/icons/cpu.svg" width="20" alt=""> Teknologi

- **Bahasa & Antarmuka:** Kotlin dengan Android Views murni.
- **Bebas Gradle & Bebas AndroidX:** Sistem tidak bergantung pada Gradle build wrapper maupun dependensi pustaka AndroidX.
- **Pipeline Build Mandiri:** Proses kompilasi manual menggunakan rantai alat `kotlinc` -> `aapt2` -> `d8` -> `apksigner`. Skrip yang sama digunakan untuk pembangunan lokal (`foundation/build-foundation.sh`) dan continuous integration (`foundation/build-ci.sh`).
- **Vendor SDK:** Pustaka Firebase SDK di-vendor secara lokal di direktori `foundation/vendor/`.

---

<a id="instalasi-dan-build"></a>
## <img src="https://cdn.jsdelivr.net/npm/lucide-static/icons/download.svg" width="20" alt=""> Instalasi dan Build

### Pemasangan APK
1. Unduh paket APK dari halaman [Releases](https://github.com/ryandxter/tethershoot-android/releases).
2. Pasang APK pada tablet Android yang mendukung USB Host / OTG.
3. Buka aplikasi dan ikuti panduan wizard 3 langkah.

### Kompilasi dari Kode Sumber
Kebutuhan lingkungan:
- JDK 21
- Kotlin Compiler (`kotlinc`) 2.2.10
- Android SDK Platform 35 dan Build-Tools

Jalankan perintah pembangunan:
```bash
bash foundation/build-foundation.sh
```

---

<a id="struktur-repositori"></a>
## <img src="https://cdn.jsdelivr.net/npm/lucide-static/icons/folder-open.svg" width="20" alt=""> Struktur Repositori

| Jalur | Deskripsi |
| :--- | :--- |
| `foundation/` | Basis kode aplikasi aktif saat ini (@rvmeur Tether), skrip pembangunan mandiri, dan dependensi vendor. |
| `foundation/build-foundation.sh` | Skrip pembangunan lokal menggunakan kompilator `kotlinc`, `aapt2`, dan `d8`. |
| `foundation/build-ci.sh` | Skrip pembangunan otomatis untuk lingkungan CI GitHub Actions. |
| `foundation/vendor/` | Kumpulan pustaka SDK Firebase yang di-vendor secara manual. |
| `app/` | Kode sumber aplikasi legacy, dipertahankan sebagai referensi historis tanpa modifikasi. |
| `docs/FIREBASE-FCM-SETUP.md` | Dokumentasi pengaturan dan konfigurasi Firebase Cloud Messaging. |
| `.github/workflows/release.yml` | Alur kerja otomatisasi rilis pada tag `v*` (menghasilkan paket APK dan `version.json`). |

---

<a id="privasi-dan-keamanan"></a>
## <img src="https://cdn.jsdelivr.net/npm/lucide-static/icons/shield-check.svg" width="20" alt=""> Privasi dan Keamanan

- **Izin Google Drive Terbatas:** Menggunakan scope `https://www.googleapis.com/auth/drive.file`. Aplikasi hanya dapat membaca dan menulis ke folder yang secara spesifik dipilih oleh pengguna serta berkas yang dibuat oleh aplikasi ini sendiri.
- **Penyimpanan Kredensial Lokal:** Token otentikasi Google disimpan dalam penyimpanan terenkripsi di dalam perangkat pengguna.
- **Bebas Rahasia Klien:** Menggunakan alur Android OAuth publik tanpa menyematkan kredensial `client_secret` di dalam aplikasi.
- **Pembaruan OTA Fail-Closed:** Berkas unduhan dari GitHub Releases wajib lolos verifikasi hash SHA-256 berdasarkan manifes rilis. Pembaruan otomatis ditolak jika manifes rilis tidak lengkap atau tidak valid.

---

<a id="lisensi-dan-kredit"></a>
## <img src="https://cdn.jsdelivr.net/npm/lucide-static/icons/book-open.svg" width="20" alt=""> Lisensi dan Kredit

- **Lisensi:** Proyek pribadi rvmeur. Seluruh hak cipta dilindungi undang-undang (All Rights Reserved).
- **Kredit:** Dirancang dan dikembangkan oleh Riansyah Rizky Poetra ([rvmeur.com](https://rvmeur.com)).

---
---

<a id="english"></a>
# <img src="https://cdn.jsdelivr.net/npm/lucide-static/icons/camera.svg" width="20" alt=""> @rvmeur Tether

[![Version](https://img.shields.io/badge/version-1.4.1-blue.svg)](https://github.com/ryandxter/tethershoot-android/releases)
[![Platform](https://img.shields.io/badge/platform-Android%20(API%2035)-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.10-purple.svg)](https://kotlinlang.org)

@rvmeur Tether (`com.rvmeur.transfer`) is a standalone Android utility for USB-tethered photography from Fujifilm cameras directly to Android tablets. Captured photos are automatically downloaded from the camera buffer per file without locking the camera hardware, then uploaded to a user-designated Google Drive folder via a foreground service. Built as a focused artisan utility, tested on a Fujifilm X-T5 and a Xiaomi Mi Pad 6S Pro.

- [Features](#features)
- [How It Works](#how-it-works)
- [Technology](#technology)
- [Installation and Build](#installation-and-build)
- [Repository Structure](#repository-structure)
- [Privacy and Security](#privacy-and-security)
- [License and Credits](#license-and-credits)

---

<a id="features"></a>
## <img src="https://cdn.jsdelivr.net/npm/lucide-static/icons/layers.svg" width="20" alt=""> Features

- **Per-File USB Tethering:** Sequentially downloads images from the Fujifilm camera buffer. The camera can be powered off normally at any point without unplugging the USB cable.
- **Direct Google Drive Upload:** Syncs files straight to the destination folder chosen via the official Google Picker flow.
- **Local Curation Gate:** Automatically scores sharpness and exposure for each image pair on-device. Passing pairs enter the upload queue immediately, while borderline files are HELD for manual inspection and never deleted automatically.
- **Session Baseline & Pairing:** Existing card files present before the session starts are automatically ignored (baseline). RAW and JPG files are treated as a single pair following the `IMG_<date>_<sequence>` naming convention.
- **Resilient Parallel Uploads:** Manages up to 3 parallel uploads using resumable upload chunks verified with MD5 checksums. An Android foreground service ensures uploads continue until completion even if the camera cable is detached.
- **Verified OTA Updates:** Checks GitHub Releases both periodically and manually. APK downloads are verified against SHA-256 hashes and rejected if the release manifest is incomplete (fail-closed).
- **Notification Subsystem:** Delivers local notifications for session completion, queue status, and app updates, with a Firebase Cloud Messaging (FCM) foundation for remote notifications.

---

<a id="how-it-works"></a>
## <img src="https://cdn.jsdelivr.net/npm/lucide-static/icons/play.svg" width="20" alt=""> How It Works

The application workflow operates through a 3-step wizard:

1. **Google Sign-In & Folder Selection:** Authenticate with Google and select the target destination folder via Google Picker under the restricted `drive.file` scope.
2. **Connect Camera:** Attach the Fujifilm camera to the Android tablet using a USB-C cable.
3. **Session Rules & Start:** Configure session parameters and initiate tethering.

<details>
<summary>Technical Session Details</summary>

- **Baseline Initialization:** When a session begins, all files already stored on the camera memory card are indexed as a baseline. Only new captures taken during the active session are ingested.
- **RAW+JPG Pairing:** RAW and JPG files sharing the same sequence number are grouped and managed as a single record.
- **Standardized Naming:** Ingested files are renamed using the uniform pattern `IMG_<date>_<sequence>`.
- **Curation Evaluation:** A local scoring routine examines focus sharpness and exposure balance. Passing pairs queue for upload immediately; uncertain pairs are held for photographer review without automatic file deletion.
- **Foreground Service Persistence:** Cloud transfers are managed by a dedicated Android Foreground Service with 3 concurrent upload threads. If the camera disconnects, files already checked out to the tablet continue uploading to completion.
- **Integrity Validation:** Each resumable upload chunk is validated with MD5 checksums to ensure file fidelity on cloud storage.

</details>

---

<a id="technology"></a>
## <img src="https://cdn.jsdelivr.net/npm/lucide-static/icons/cpu.svg" width="20" alt=""> Technology

- **Language & UI:** Pure Kotlin with Android Views.
- **No Gradle & No AndroidX:** Operates completely without Gradle build wrappers and without AndroidX library dependencies.
- **Manual Toolchain Pipeline:** Compiled through direct toolchain commands (`kotlinc` -> `aapt2` -> `d8` -> `apksigner`). The exact same build pipeline runs locally (`foundation/build-foundation.sh`) and on GitHub Actions CI (`foundation/build-ci.sh`).
- **Vendored SDKs:** Firebase SDK components are vendored directly inside `foundation/vendor/`.

---

<a id="installation-and-build"></a>
## <img src="https://cdn.jsdelivr.net/npm/lucide-static/icons/download.svg" width="20" alt=""> Installation and Build

### Installing the APK
1. Download the latest APK release from the [GitHub Releases](https://github.com/ryandxter/tethershoot-android/releases) section.
2. Install the APK on an Android tablet supporting USB Host / OTG.
3. Launch the app and follow the 3-step setup wizard.

### Building from Source
Prerequisites:
- JDK 21
- Kotlin Compiler (`kotlinc`) 2.2.10
- Android SDK Platform 35 and Build-Tools

Execute the build script:
```bash
bash foundation/build-foundation.sh
```

---

<a id="repository-structure"></a>
## <img src="https://cdn.jsdelivr.net/npm/lucide-static/icons/folder-open.svg" width="20" alt=""> Repository Structure

| Path | Description |
| :--- | :--- |
| `foundation/` | Active application codebase (@rvmeur Tether), standalone build scripts, and vendored dependencies. |
| `foundation/build-foundation.sh` | Local build script using `kotlinc`, `aapt2`, and `d8`. |
| `foundation/build-ci.sh` | Continuous integration build script for GitHub Actions. |
| `foundation/vendor/` | Manually vendored Firebase SDK libraries. |
| `app/` | Legacy application codebase, retained for historical reference without modifications. |
| `docs/FIREBASE-FCM-SETUP.md` | Configuration documentation for Firebase Cloud Messaging credentials. |
| `.github/workflows/release.yml` | GitHub Actions workflow for tag-triggered releases (`v*` tag producing APK and `version.json`). |

---

<a id="privacy-and-security"></a>
## <img src="https://cdn.jsdelivr.net/npm/lucide-static/icons/shield-check.svg" width="20" alt=""> Privacy and Security

- **Restricted Google Drive Scope:** Operates strictly under `https://www.googleapis.com/auth/drive.file`. The application only has access to the user-selected folder and files created by this application.
- **Encrypted Local Storage:** OAuth tokens and session data are stored securely on the local device.
- **No Embedded Client Secret:** Uses standard Android public OAuth client flow with no `client_secret` compiled into the application.
- **Fail-Closed OTA Verification:** APK downloads from GitHub Releases require SHA-256 verification against the release manifest. Updates fail closed if the manifest is missing or verification does not pass.

---

<a id="license-and-credits"></a>
## <img src="https://cdn.jsdelivr.net/npm/lucide-static/icons/book-open.svg" width="20" alt=""> License and Credits

- **License:** Personal project of rvmeur. All rights reserved.
- **Credits:** Created and maintained by Riansyah Rizky Poetra ([rvmeur.com](https://rvmeur.com)).
