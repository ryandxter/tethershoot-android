# Panduan Setup Firebase Cloud Messaging (FCM) — TransferIn Fuji

Dokumen ini adalah panduan langkah demi langkah untuk mengintegrasikan Firebase Cloud Messaging (FCM) ke aplikasi Android **@rvmeur Tether** (`com.rvmeur.transfer`).

---

## 1. Ringkasan Arsitektur

- **Pusat Notifikasi Tunggal (`InfoNotifier`):**
  Aplikasi ini sudah memiliki kanal notifikasi lokal yang hidup dan berfungsi lewat `InfoNotifier` (`com.rvmeur.transfer.InfoNotifier`). FCM dirancang langsung menyambung ke pusat notifikasi ini begitu pesan cloud masuk — **tidak ada rombak ulang kode notifikasi**.
- **Pengirim via Cloudflare Worker:**
  Pengiriman pesan push dirancang melalui Cloudflare Worker (infrastruktur serverless yang sudah kita miliki) menggunakan **FCM HTTP v1 API** dengan autentikasi Google Service Account.
- **Keamanan Kunci:**
  Kunci privat Service Account **TIDAK PERNAH** ditaruh di dalam repositori Git atau di obrolan chat. Kunci disimpan aman sebagai rahasia terenkripsi di vault/secret Cloudflare Worker (misal via `wrangler secret put`).
- **Status `google-services.json`:**
  File `google-services.json` di sisi Android hanyalah berkas konfigurasi identitas klien (App ID, Project ID, Client API Key). File ini aman dan lazim di-commit ke repositori.

---

## 2. Langkah Konkret User di Mac

Prasyarat di Mac kamu:
- Firebase CLI sudah terpasang (`firebase-tools`).
- Project Google Cloud Platform (GCP) **HappyFamily** (`happyfamily-ryndxtr`) sudah ada.

Jalankan langkah-langkah berikut di Terminal Mac:

### Langkah 1: Konfirmasi Project Terlihat
Pastikan Firebase CLI terhubung ke akun Google kamu dan melihat project GCP yang sudah ada:
```bash
firebase projects:list
```
Pastikan `happyfamily-ryndxtr` muncul di daftar project.

### Langkah 2: Aktifkan Firebase di Project GCP
Bila Firebase belum aktif di project `happyfamily-ryndxtr`, tautkan Firebase:
```bash
firebase projects:addfirebase happyfamily-ryndxtr
```
> **Catatan (Cadangan via Console):** Bila Firebase CLI meminta konfirmasi interaktif atau menolak via CLI, buka [Firebase Console](https://console.firebase.google.com/) → klik **"Add project" / "Tambahkan project"** → pilih project GCP `happyfamily-ryndxtr` yang sudah ada → ikuti panduan singkat sampai selesai.

### Langkah 3: Daftarkan Aplikasi Android
Daftarkan package `com.rvmeur.transfer` ke dalam Firebase project:
```bash
firebase apps:create android "@rvmeur Tether" --project=happyfamily-ryndxtr
```
Bila perintah CLI menanyakan rincian secara interaktif:
- **Package name:** `com.rvmeur.transfer`
- **App nickname:** `@rvmeur Tether`

> **Jalur Firebase Console (Alternatif/Cadangan):**
> 1. Buka **Project Settings** (ikon gerigi) di Firebase Console.
> 2. Di bagian **Your apps**, klik **Add app** → pilih ikon **Android**.
> 3. Masukkan:
>    - **Android package name:** `com.rvmeur.transfer`
>    - **App nickname:** `@rvmeur Tether`
>    - **Debug signing certificate SHA-1:** `a43b1ecd3df99ca3e0e728c956c92c26d0b54dd8`
> 4. Klik **Register app**.

Catat `App ID` yang dihasilkan (formatnya biasanya `1:xxxxxxxxxxxx:android:xxxxxxxxxxxxxxxx`).

### Langkah 4: Unduh Konfigurasi `google-services.json`
Unduh file konfigurasi SDK langsung ke repositori:
```bash
firebase apps:sdkconfig android <APP_ID_DARI_LANGKAH_3> --project=happyfamily-ryndxtr
```
Simpan keluarannya sebagai:
```
foundation/app/google-services.json
```
*(Atau bila unduh dari Console, download `google-services.json` lalu letakkan di path `foundation/app/google-services.json` di root repo ini).*

Itulah titik pasang utamanya. File ini yang akan dibaca oleh gelombang SDK berikutnya.

---

## 3. Gelombang SDK (Dilakukan Asisten setelah `google-services.json` Ada)

Setelah kamu meletakkan `foundation/app/google-services.json`, asisten akan mengeksekusi otomatis:

1. **Ekstraksi String Konfigurasi:**
   Mengekstrak nilai-nilai identitas dari JSON dan menghasilkan `res/values/google-services-strings.xml`:
   - `google_app_id`
   - `gcm_defaultSenderId` (project number)
   - `google_api_key`
   - `firebase_database_url`
   - `google_project_id` / `project_id`
2. **Vendoring Library AAR TANPA Gradle:**
   Menyediakan AAR `firebase-messaging` beserta dependensi runtime-nya ke dalam folder `foundation/vendor/`, lalu mengekstrak `classes.jar` agar bisa langsung masuk ke classpath kompilasi `kotlinc` dan dexing `d8`.
3. **Entri Manifest:**
   Menambahkan `FirebaseInitProvider` dan service penerima push `TetherMessagingService` (dengan intent-filter action `com.google.firebase.MESSAGING_EVENT`) ke dalam `AndroidManifest.xml`.
4. **Pencatatan Token & Integrasi Notifikasi:**
   - Token registrasi FCM perangkat dicatat rapi ke log aplikasi dengan tag `[FCM]`.
   - Method `onMessageReceived` langsung mem-forward payload ke `InfoNotifier` untuk menyajikan notifikasi sistem.

---

## 4. Uji Kirim Notifikasi

Setelah APK terpasang di perangkat Android:

### Cara 1: Uji Cepat via Firebase Console
1. Buka aplikasi di HP, cek logcat / runtime log untuk melihat token FCM perangkat (cari tag `[FCM] Token:`). Salin token registrasi tersebut.
2. Di Firebase Console, navigasi ke menu **Build** → **Cloud Messaging**.
3. Klik **Send your first message** atau buat pesan uji coba (*Test message*).
4. Masukkan Title & Body pesan.
5. Klik **Send test message**, tempel token perangkat kamu, lalu kirim.
6. Notifikasi akan langsung muncul di notification tray lewat `InfoNotifier`.

### Cara 2: Kirim Programatis via FCM HTTP v1 API (Cloudflare Worker)
Worker membuat token OAuth 2.0 menggunakan Service Account (Google Auth JWT), lalu melakukan request `POST`:
```bash
curl -X POST "https://fcm.googleapis.com/v1/projects/happyfamily-ryndxtr/messages:send" \
  -H "Authorization: Bearer <ACCESS_TOKEN_OAUTH2>" \
  -H "Content-Type: application/json; UTF-8" \
  -d '{
    "message": {
      "token": "<FCM_DEVICE_TOKEN>",
      "notification": {
        "title": "@rvmeur Tether",
        "body": "Pembaruan sistem OTA tersedia!"
      },
      "data": {
        "type": "ota_available",
        "version": "1.3.0"
      }
    }
  }'
```
Ketika pesan diterima perangkat, `TetherMessagingService` memanggil `InfoNotifier` untuk memunculkan notifikasi pembaruan secara instan.
