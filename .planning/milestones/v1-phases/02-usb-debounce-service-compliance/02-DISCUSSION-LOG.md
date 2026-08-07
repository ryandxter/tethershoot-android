# Phase 2: USB Debounce & Service Compliance - Discussion Log

**Date:** 2026-08-07

## Discussion Summary

Kami mendiskusikan penanganan ketahanan koneksi USB terhadap gangguan fisik (kabel longgar) dan kepatuhan Android 14+ untuk layanan latar depan (Foreground Service).

## Items Discussed

### 1. USB Event Debounce Strategy
- **Options Presented:**
  1. **Strategi Pembatalan Job (Cancel-previous) [Terpilih]:** Membatalkan coroutine stabilisasi attach sebelumnya setiap kali ada event USB baru masuk untuk mencegah proses rekoneksi bertumpuk secara paralel.
  2. **Queueing (Antrean):** Memasukkan event ke antrean sekuensial.
- **Decision:** Strategi pembatalan job dipilih karena lebih responsif dan secara efektif mematikan sisa proses dari siklus plug/unplug yang sudah tidak valid.

### 2. Android 14+ connectedDevice Compliance
- **Options Presented:**
  1. **Runtime Verification [Terpilih]:** Menambahkan pemeriksaan permission `POST_NOTIFICATIONS` dan runtime checks sebelum mengaktifkan foreground service.
  2. **Legacy Fallback:** Mengabaikan Connected Device constraints (tidak aman untuk Google Play).
- **Decision:** Runtime verification dipilih demi memastikan kepatuhan penuh terhadap kebijakan Android 14+.

---

*Discussion logged: 2026-08-07*
