# Phase 3: Multi-Vendor MTP Fallback - Context

**Gathered:** 2026-08-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Support folder listing and sync capabilities for generic MTP cameras (non-Canon).

</domain>

<decisions>
## Implementation Decisions

### Non-Canon Fallback & Database Detection
- **D-01:** Deteksi fallback diaktifkan secara otomatis jika tidak ada file CTG/database terdeteksi pada penyimpanan kamera.
- **D-02:** Menambahkan dukungan pola deteksi berkas database vendor lain selain Canon (seperti berkas `.DAT` untuk Sony/Nikon/Fujifilm, misalnya `CAMERADB.DAT`, `SONY.DAT`, `NIKON.DAT`, `FUJI.DAT`). Jika berkas database ini berubah timestamp-nya, folder ditandai sebagai kotor (dirty).

### Polling & Sync Strategy
- **D-03:** Jika tidak ada berkas database (`.CTG`/`.DAT`) sama sekali, sistem akan menggunakan strategi *Size & Date Diff* polling. Sistem akan melakukan listing berkas secara periodik di folder MTP dan membandingkan nama berkas, ukuran (size), serta tanggal modifikasi (modifiedTime) dengan database lokal (`CameraMediaItemEntity`) untuk menemukan berkas baru tanpa mengandalkan berkas cache eksternal.

### the agent's Discretion
- Penamaan konstanta pola file database `.DAT` tambahan.
- Penyesuaian query pencarian database lokal untuk optimalisasi pembandingan.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Requirements & Roadmap
- `.planning/PROJECT.md` — Core value and architecture guidelines.
- `.planning/REQUIREMENTS.md` — Requirement specifications (USB-02).
- `.planning/ROADMAP.md` — Roadmap phases definitions.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `CameraDiscoveryEngine` (`app/src/main/java/com/reflection/transferin/data/usb/CameraDiscoveryEngine.kt`): Mengatur pemindaian dan polling berkas media dari kamera USB.
- `GenericCameraInitializer` (`app/src/main/java/com/reflection/transferin/data/usb/vendor/GenericCameraInitializer.kt`): Fallback initializer untuk kamera non-Canon.

### Established Patterns
- Pengecekan berkas `.CTG` untuk menandai folder kotor.
- Penggunaan hash dari document ID sebagai pseudo-handle untuk berkas media non-Canon.

</code_context>

<deferred>
## Deferred Ideas

- Tidak ada penundaan fitur baru untuk fase ini.

</deferred>

---

*Phase: 03-multi-vendor-mtp-fallback*
*Context gathered: 2026-08-07*
