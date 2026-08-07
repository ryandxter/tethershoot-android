# Phase 2: USB Debounce & Service Compliance - Context

**Gathered:** 2026-08-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Debounce physical connection drops and enforce Android 14+ foreground service constraints.

</domain>

<decisions>
## Implementation Decisions

### USB Event Debounce Strategy
- **D-01:** Menerapkan strategi pembatalan job (cancel-previous) pada `UsbDisconnectHandler.kt`. Setiap kali broadcast event USB (`ATTACHED` atau `DETACHED`) baru masuk, coroutine/job stabilisasi yang sedang berjalan sebelumnya akan langsung dibatalkan (cancelled) untuk menghindari race condition rekoneksi paralel.

### Android 14+ connectedDevice Compliance
- **D-02:** `UsbTetherService` wajib memverifikasi permission runtime `POST_NOTIFICATIONS` dan izin foreground service terkait sebelum melakukan `startForeground` dengan tipe `connectedDevice`.
- **D-03:** Penanganan kegagalan permission runtime foreground service akan didokumentasikan dan ditangani secara anggun (graceful termination) tanpa menyebabkan aplikasi mengalami crash mendadak.

### the agent's Discretion
- Desain logger dan pelacakan transisi state pada handler USB.
- Mekanisme throttling notifikasi jika terjadi attach/detach yang terlalu cepat.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Requirements & Roadmap
- `.planning/PROJECT.md` — Core value and architecture guidelines.
- `.planning/REQUIREMENTS.md` — Requirement specifications (USB-01, SVC-01).
- `.planning/ROADMAP.md` — Roadmap phases definitions.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `UsbDisconnectHandler` (`app/src/main/java/com/reflection/transferin/service/UsbDisconnectHandler.kt`): Mengatur deteksi siklus pasang-lepas USB dan stabilisasinya.
- `UsbTetherService` (`app/src/main/java/com/reflection/transferin/service/UsbTetherService.kt`): Layanan latar depan (Foreground Service) untuk memantau tethering kamera USB.

### Established Patterns
- Menggunakan `BroadcastReceiver` dinamis untuk memantau intent filter `UsbManager.ACTION_USB_DEVICE_ATTACHED` dan `ACTION_USB_DEVICE_DETACHED`.
- Menggunakan coroutine scope `handlerScope` dengan dispatcher Main/IO untuk koordinasi asinkron.

</code_context>

<deferred>
## Deferred Ideas

- Tidak ada penundaan fitur baru untuk fase ini.

</deferred>

---

*Phase: 02-usb-debounce-service-compliance*
*Context gathered: 2026-08-07*
