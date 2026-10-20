---
sketch: 001
name: all-in-one-studio-suite
question: "Bagaimana seluruh arsitektur layar aplikasi TransferIn (Setup Wizard, Tether Session, Culling Gallery, dan Inspection Modal) bersinergi dalam alur studio mobile tanpa perlu real device?"
winner: null
tags: [suite, mobile-frame, gallery, wizard, culling, modal, jetpack-compose]
---

# 001: All-in-One Studio App Suite

## Design Question
Bagaimana merekonstruksi dan memvisualisasikan seluruh layar utama aplikasi `TransferIn` (Jetpack Compose) secara pixel-accurate dan interaktif tanpa memerlukan perangkat Android fisik?

## Variants

- **Variant A: Native Mobile Studio (Fluid Navigation)**:
  Simulasi frame perangkat mobile dengan hardware notch, status bar live telemetri (USB-C PTP 48MB/s), serta navigasi bawah `FluidDotNavigationBar` yang menghubungkan:
  1. `Setup Wizard` (`FluidWizardScreen.kt`) dengan deteksi PTP Direct Bulk-IN Fujifilm X-T5 dan verifikasi target Google Drive.
  2. `Active Session Dashboard` (`SessionActiveScreen.kt`) dengan bar progres transfer berkas RAF, metrik kecepatan, dan antrean.
  3. `Studio Gallery & Culling` (`CameraGalleryScreen.kt`) dengan filter chip interaktif (`semua`, `optimal`, `under`, `blown`) dan kartu foto concentric `DoppelrandCard`.
  4. `Full-Screen Dark Inspection Modal` (`UI-03`) lengkap dengan kurva Bézier 256-bin histogram, 4-stat EXIF strip (`ISO`, `Shutter`, `Aperture`, `Simulasi`), dan tombol kinetic force-upload.
  5. `Session Summary` (`SessionSummaryScreen.kt`) dengan ringkasan bitwise verifikasi.

- **Variant B: Dual-Panel Studio Workstation (Tablet Mode)**:
  Layout split 2 panel: Sidebar kiri untuk kontrol tether hardware, kecepatan, dan target penyimpanan; panel kanan untuk kurasi galeri studio berkolom 3.

- **Variant C: High-Contrast Field Shoot Mode**:
  Tema gelap sunlight-proof dengan kontras tinggi, border olive/emerald tebal, dan tombol aksi kinetic berukuran ekstra besar untuk pemotretan luar ruangan.

## How to View
Buka berkas `index.html` langsung di browser Anda atau gunakan live preview:
```bash
open .planning/sketches/001-all-in-one-studio-suite/index.html
```
