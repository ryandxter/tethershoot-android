# Phase 4: Resumable Transfers & Server Cleanup - Context

**Gathered:** 2026-08-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Support offset resumption for large files and garbage collection of temporary files.

</domain>

<decisions>
## Implementation Decisions

### Suffix File Sementara (Temporary Suffix Alignment)
- **D-01:** Menyelaraskan seluruh kode client SFTP (`JschSftpClient.kt`, `TwoPhaseUploadHandler.kt`, `SftpClient.kt`) untuk menggunakan suffix `.tmp` secara seragam menggantikan `.part`.

### Pembersihan Berkas Yatim Piatu (Orphaned File Cleanup & Age)
- **D-02:** Pembersihan otomatis berkas `.tmp` dilakukan saat inisialisasi sesi (`session active` start).
- **D-03:** Batas usia berkas `.tmp` yang dianggap usang/yatim piatu dipersingkat dari 24 jam menjadi **5 menit** (`5 * 60 * 1000L`) untuk efisiensi ruang penyimpanan di NAS. Berkas `.tmp` yang dimodifikasi lebih lama dari 5 menit yang lalu akan otomatis dibersihkan saat sesi dimulai.

### the agent's Discretion
- Penamaan parameter internal penanganan berkas sementara.
- Pola log dan pelaporan jumlah berkas yang berhasil dibersihkan.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Requirements & Roadmap
- `.planning/PROJECT.md` — Core value and architecture guidelines.
- `.planning/REQUIREMENTS.md` — Requirement specifications (FILE-01, FILE-02, FILE-03).
- `.planning/ROADMAP.md` — Roadmap phases definitions.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `UploadCheckpointManager` (`app/src/main/java/com/reflection/transferin/data/sftp/UploadCheckpointManager.kt`): Mengelola penyimpanan progres offset unggah ke database.
- `TwoPhaseUploadHandler` (`app/src/main/java/com/reflection/transferin/data/sftp/TwoPhaseUploadHandler.kt`): Mengelola inisiasi unggah 2 fase dan pembersihan berkas sementara.
- `JschSftpClient` (`app/src/main/java/com/reflection/transferin/data/sftp/JschSftpClient.kt`): Client SFTP yang melakukan pengunggahan fisik dan operasi penamaan ulang (rename) / penghapusan berkas.

### Established Patterns
- Penggunaan `ChannelSftp.RESUME` untuk melakukan pengunggahan dengan offset.
- Membaca ukuran berkas lokal menggunakan `ContentResolver.openAssetFileDescriptor`.

</code_context>

<deferred>
## Deferred Ideas

- Tidak ada penundaan fitur baru untuk fase ini.

</deferred>

---

*Phase: 04-resumable-transfers-server-cleanup*
*Context gathered: 2026-08-07*
