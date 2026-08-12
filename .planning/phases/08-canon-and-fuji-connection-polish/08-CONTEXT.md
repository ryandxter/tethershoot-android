# Phase 8 Context - Canon and Fuji Connection Polish

## Decisions

1. **Urutan Penutupan Sesi USB & MTP**:
   - Untuk mengembalikan state Canon ke mode jepret fisik, aplikasi wajib mengirimkan PTP command close session (`EOS_OC_SET_REMOTE_MODE` -> `REMOTE_MODE_OFF`) **sebelum** memanggil `activeMtpDevice.close()`.
   - Mengambil koneksi USB (`UsbDeviceConnection`) dan memanggil `.close()` paling akhir.

2. **Sinkronisasi Device Name di UsbHelper**:
   - `UsbHelper` harus menerima pembaruan `currentSessionDeviceName` saat `CameraUnlockOrchestrator` menetapkan `activeMtpDeviceOverride`.
   - Ini memastikan `isCanonSession()` dan `hasActiveCanonNativeSession()` dapat dihitung dengan benar selama mode tethered berlangsung.

## Open Questions
- None.
