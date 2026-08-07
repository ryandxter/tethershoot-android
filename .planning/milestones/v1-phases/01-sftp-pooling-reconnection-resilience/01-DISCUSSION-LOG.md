# Phase 1: SFTP Pooling & Reconnection Resilience - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-07
**Phase:** 01-sftp-pooling-reconnection-resilience
**Areas discussed:** Idle Connection Eviction Policy, Reconnection UI/UX Behavior, Health Monitor Check Interval, Circuit Breaker Thresholds

---

## Idle Connection Eviction Policy

| Option | Description | Selected |
|--------|-------------|----------|
| Keep active indefinitely | Biarkan koneksi tetap aktif tanpa batas waktu selama sesi pemotretan aktif | ✓ |
| Evict after 5 minutes | Tutup koneksi idle setelah waktu tunggu 5 menit | |
| Evict after 10 minutes | Tutup koneksi idle setelah waktu tunggu 10 menit | |

**User's choice:** Biarkan koneksi tetap aktif selama sesi pemotretan aktif (berlaku untuk SFTP maupun WebDAV).
**Notes:** Menghilangkan latency inisialisasi ulang saat memproses foto baru yang dipotret sewaktu sesi pemotretan masih berjalan aktif.

---

## Reconnection UI/UX Behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Status-bar indicator | Tampilkan indikator kecil secara senyap di status bar dan masukkan file ke antrean | |
| Full screen overlay | Blokir UI dengan overlay loading penuh | ✓ |
| Pop-up dialog | Tampilkan dialog pop-up pilihan manual | |

**User's choice:** Blokir UI dengan overlay loading penuh.
**Notes:** Memastikan pengguna mengetahui dengan jelas bahwa transfer sedang terhambat sehingga mencegah pengguna mengambil tindakan yang salah di lapangan.

---

## Health Monitor Check Interval

| Option | Description | Selected |
|--------|-------------|----------|
| Every 10s | Polling pemeriksaan setiap 10 detik | |
| Every 5s | Polling pemeriksaan setiap 5 detik | |
| Dynamic Wi-Fi/Cellular | 10 detik saat terhubung ke Wi-Fi, dan 30 detik saat menggunakan data seluler | ✓ |

**User's choice:** Dinamis: 10 detik pada Wi-Fi, dan 30 detik pada data seluler.
**Notes:** Menerapkan optimalisasi penggunaan kuota internet dan daya baterai saat berada di lapangan (menggunakan seluler).

---

## Circuit Breaker Thresholds

| Option | Description | Selected |
|--------|-------------|----------|
| Default limits | 5 kali kegagalan berturut-turut dengan 60 detik cooldown | ✓ |
| Dynamic Config | Dapat dikonfigurasi melalui menu Settings | |
| Strict limits | 3 kali kegagalan dengan 30 detik cooldown | |

**User's choice:** Gunakan batas bawaan (5 kegagalan berturut-turut, 60s cooldown).
**Notes:** Cukup menggunakan batas default tanpa penambahan kompleksitas antarmuka menu pengaturan baru.

---

## the agent's Discretion

- Desain visual loading overlay pada Compose UI.
- Log message detail saat terjadi transisi state pada circuit breaker.

## Deferred Ideas

- Penjadwalan upload otomatis pada jam tertentu di luar sesi pemotretan — ditunda/out of scope.

---

*Phase: 01-sftp-pooling-reconnection-resilience*
*Discussion log generated: 2026-08-07*
