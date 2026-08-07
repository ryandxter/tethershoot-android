# Phase 4: Resumable Transfers & Server Cleanup - Discussion Log

**Date:** 2026-08-07

## Discussion Summary

Kami mendiskusikan mekanisme resume berkas berukuran besar dan pembersihan berkas unggahan sementara (temporary files) di server NAS.

## Items Discussed

### 1. Temporary Suffix Alignment
- **Options Presented:**
  1. **Penyelarasan Suffix .tmp [Terpilih]:** Mengubah seluruh internal kode penanganan berkas `.part` menjadi `.tmp`.
  2. **Keep .part Internal:** Biarkan internal tetap menggunakan `.part`.
- **Decision:** Opsi penyelarasan suffix `.tmp` terpilih agar konsisten dengan spesifikasi kebutuhan proyek (FILE-01 & FILE-03).

### 2. Orphaned File Cleanup & Age
- **Options Presented:**
  1. **Pembersihan Otomatis Saat Sesi Baru [Terpilih]:** Inisiasi pembersihan saat startup sesi baru.
  2. **Pembersihan Manual:** Opsi hapus lewat UI.
- **Decision:** Pembersihan otomatis terpilih. Kami juga sepakat untuk mempersingkat batasan usia berkas `.tmp` usang menjadi **5 menit** (kurang dari 10 menit) untuk mencegah penumpukan berkas sampah pada server NAS.

---

*Discussion logged: 2026-08-07*
