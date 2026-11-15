# Tether Remote FCM Push Sender (Cloudflare Worker)

Pengirim notifikasi push Firebase Cloud Messaging (FCM) v1 jarak jauh untuk aplikasi **@rvmeur Tether** berbasis Cloudflare Worker tanpa dependensi eksternal.

---

## 🏛️ Arsitektur Singkat

- **Aplikasi Android (@rvmeur Tether)** bertindak sebagai **Penerima**:
  - Mendaftarkan perangkat ke Firebase Cloud Messaging via FCM SDK.
  - Menghasilkan *FCM registration token* unik untuk perangkat.
  - Memiliki tombol **"salin token fcm."** di UI aplikasi untuk menyalin token tersebut.
- **Cloudflare Worker (`tools/fcm-sender`)** bertindak sebagai **Pengirim Jarak Jauh (Serverless)**:
  - Berjalan di jaringan Cloudflare Edge tanpa membutuhkan server/VPS mandiri.
  - Mengamankan akses endpoint dengan verifikasi header `Authorization: Bearer <SENDER_SECRET>`.
  - Membuat dan menandatangani Google OAuth2 JWT (RS256) menggunakan WebCrypto API bawaan.
  - Menukar JWT dengan *access token* Google, lalu menembak endpoint resmi **FCM v1 HTTP API** (`https://fcm.googleapis.com/v1/projects/happyfamily-ryndxtr/messages:send`).
  - Menerapkan sensor otomatis (hanya menampilkan 6 karakter awal & 6 karakter akhir) agar token FCM dan kredensial sensitif tidak pernah bocor ke log konsol.

---

## 🚀 Panduan Penggunaan & Deployment

### 1. Buat Service Account Key di Firebase Console
1. Buka [Firebase Console](https://console.firebase.google.com/) dan pilih project **HappyFamily** (Project ID: `happyfamily-ryndxtr`).
2. Masuk ke menu **Project settings** (ikon roda gigi di kiri atas) ➔ pilih tab **Service accounts**.
3. Pastikan Firebase Admin SDK terpilih, lalu klik tombol **Generate new private key** dan konfirmasi **Generate key**.
4. Berkas JSON privat akan otomatis terunduh ke komputer Anda.
> ⚠️ **PENTING:** Simpan berkas JSON ini dengan aman di komputer lokal Anda! **JANGAN** sekali-kali memasukkan, mengunggah, atau melakukan commit berkas ini ke repositori Git maupun chat publik mana pun.

---

### 2. Konfigurasi Rahasia (Secrets) pada Cloudflare Worker
Pindah ke direktori Worker dan masukkan rahasia ke Cloudflare via Wrangler:

```bash
cd tools/fcm-sender

# 1. Masukkan seluruh isi berkas JSON Service Account yang diunduh tadi
npx wrangler secret put GOOGLE_SERVICE_ACCOUNT_JSON
# Saat diminta input, salin & tempel (paste) seluruh teks JSON service account, lalu tekan Enter.

# 2. Buat token sandi unik untuk melindungi endpoint Worker Anda
npx wrangler secret put SENDER_SECRET
# Masukkan string acak yang panjang dan aman (contoh hasil dari: openssl rand -hex 32)
```

---

### 3. Deploy Worker ke Cloudflare
Jalankan perintah deployment:

```bash
npx wrangler deploy
```

Setelah deploy selesai, Wrangler akan menampilkan URL publik Worker Anda, misalnya:
`https://tether-fcm-sender.<subdomain-anda>.workers.dev`

Cek status endpoint dengan membukanya di peramban atau melalui curl:
```bash
curl https://tether-fcm-sender.<subdomain-anda>.workers.dev/
# Respon yang diharapkan: "tether fcm sender aktif"
```

---

### 4. Uji Coba Pengiriman Notifikasi Push (Curl Test)

1. Buka aplikasi **@rvmeur Tether** di perangkat Android Anda.
2. Tekan tombol **"salin token fcm."** untuk menyalin token FCM perangkat ke clipboard.
3. Jalankan perintah curl berikut di terminal:

```bash
curl -X POST https://tether-fcm-sender.<subdomain-anda>.workers.dev/send \
  -H "Authorization: Bearer <SENDER_SECRET_ANDA>" \
  -H "Content-Type: application/json" \
  -d '{
    "token": "<TEMPEL_TOKEN_FCM_DARI_APLIKASI>",
    "title": "Halo dari Tether",
    "body": "Pengujian notifikasi FCM jarak jauh berhasil!"
  }'
```

**Respon Sukses:**
```json
{
  "ok": true,
  "name": "projects/happyfamily-ryndxtr/messages/1731671234567890"
}
```

**Respon Gagal / Error:**
```json
{
  "ok": false,
  "error": "Pesan rincian kegagalan"
}
```

---

## 🔒 Catatan Keamanan

1. **Rotasi Kunci:** Jika Service Account JSON atau `SENDER_SECRET` dicurigai bocor, segera cabut (*revoke*) key lama di Firebase Console, buat key baru, dan perbarui secret Worker dengan `npx wrangler secret put`.
2. **Kerahasiaan Endpoint & Secret:** Jangan bagikan URL endpoint Worker dan `SENDER_SECRET` ke publik atau tempat yang tidak terpercaya untuk mencegah penyalahgunaan spam notifikasi.
3. **Sensor Log:** Worker secara otomatis menyensor token FCM (hanya 6 karakter awal + 6 karakter akhir) dan tidak pernah mencatat `private_key` ke konsol.
