# Phase 1: SFTP Pooling & Reconnection Resilience - Context

**Gathered:** 2026-08-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Establish a leak-free pool of SFTP sessions with keepalives, exponential backoff, and circuit breakers.

</domain>

<decisions>
## Implementation Decisions

### Idle Connection Eviction Policy
- **D-01:** Baik SFTP maupun WebDAV HTTP/HTTPS, koneksi tetap dibiarkan aktif tanpa batas waktu selama sesi pemotretan aktif untuk meminimalkan latensi transfer data baru.

### Reconnection UI/UX Behavior
- **D-02:** Blokir UI secara penuh dengan overlay loading selama proses menghubungkan kembali (reconnecting) agar fotografer mengetahui bahwa transfer sedang terhambat dan mencegah tindakan lain.

### Health Monitor Check Interval
- **D-03:** Polling berkala pemantauan kesehatan koneksi diatur secara dinamis: pemeriksaan dilakukan setiap 10 detik saat terhubung ke jaringan Wi-Fi, dan diturunkan menjadi setiap 30 detik saat menggunakan jaringan data seluler (untuk efisiensi kuota dan baterai di lapangan).

### Circuit Breaker Thresholds
- **D-04:** Menggunakan batas kegagalan bawaan (default) yaitu 5 kali kegagalan berturut-turut sebelum memicu sirkuit menjadi OPEN, dan menerapkan masa cooldown pemulihan selama 60 detik sebelum sirkuit berpindah ke HALF_OPEN untuk mencoba menyambungkan kembali.

### the agent's Discretion
- Desain visual loading overlay pada Compose UI.
- Log message detail saat terjadi transisi state pada circuit breaker.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Requirements & Roadmap
- `.planning/PROJECT.md` — Core value and architecture guidelines.
- `.planning/REQUIREMENTS.md` — Requirement specifications (SFTP-01, SFTP-02, SFTP-03).
- `.planning/ROADMAP.md` — Roadmap phases definitions.
- `.planning/codebase/CONCERNS.md` — Technical debt and concerns reference.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SftpSessionPool` (`app/src/main/java/com/reflection/transferin/data/sftp/SftpSessionPool.kt`): Menyediakan manajemen siklus hidup koneksi SFTP.
- `SftpSessionReconnector` (`app/src/main/java/com/reflection/transferin/data/sftp/SftpSessionReconnector.kt`): Menyediakan implementasi circuit breaker dan alur exponential backoff.
- `SftpSessionHealthMonitor` (`app/src/main/java/com/reflection/transferin/data/sftp/SftpSessionHealthMonitor.kt`): Menyediakan logika pemeriksaan keepalive untuk session pool.

### Established Patterns
- Coroutines dengan Dispatchers.IO digunakan untuk operasi jaringan SFTP.
- StateFlow digunakan untuk mengekspos status health monitor ke UI.

### Integration Points
- Pengikatan integrasi reconnector dan monitor ke dalam instansiasi `SftpSessionPool` yang digunakan oleh `JschSftpClient`.

</code_context>

<deferred>
## Deferred Ideas

- Penjadwalan upload otomatis pada jam tertentu di luar sesi pemotretan — Out of scope.

</deferred>

---

*Phase: 01-sftp-pooling-reconnection-resilience*
*Context gathered: 2026-08-07*
