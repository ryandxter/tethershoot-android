# Phase 3: Multi-Vendor MTP Fallback - Discussion Log

**Date:** 2026-08-07

## Discussion Summary

Kami mendiskusikan penanganan fallback untuk kamera non-Canon agar sistem tetap dapat mendeteksi foto baru secara real-time.

## Items Discussed

### 1. Fallback & Database Detection
- **Options Presented:**
  1. **Deteksi Fallback Otomatis [Terpilih]:** Mengaktifkan mode fallback jika file `.CTG` absen.
  2. **Vendor ID Filter:** Menggunakan data USB ID.
- **Decision:** Deteksi otomatis terpilih. Kami juga sepakat untuk mendeteksi berkas database `.DAT` lainnya (seperti milik Sony/Nikon/Fujifilm) untuk memperluas jangkauan deteksi real-time berbasis perubahan file index.

### 2. Polling & Sync Strategy
- **Options Presented:**
  1. **Size & Date Diff Polling [Terpilih]:** Membandingkan ukuran dan waktu modifikasi berkas terhadap database lokal.
  2. **Full Periodic Scan:** Scan ulang semua folder setiap beberapa detik.
- **Decision:** Size & Date Diff terpilih karena menghemat memori dan baterai ponsel saat melakukan sinkronisasi pada ratusan/ribuan foto di kamera.

---

*Discussion logged: 2026-08-07*
