# Phase 5: Room Migration Validation Suite - Context

**Gathered:** 2026-08-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Create automated migration test suites to guarantee Room schema update integrity.

</domain>

<decisions>
## Implementation Decisions

### Lokasi Penyimpanan Ekspor Schema (Schema Location)
- **D-01:** Menyimpan ekspor berkas JSON schema Room di direktori `app/schemas`.
- **D-02:** Menambahkan direktori `app/schemas` ke dalam aset `androidTest` sourceSet pada `app/build.gradle.kts` agar dapat diakses oleh `MigrationTestHelper`.

### Registrasi Migration 8 to 9 di App Runtime
- **D-03:** Mendaftarkan `Migration_8_9.MIGRATION_8_9` pada `DatabaseProvider.kt` untuk menghindari destructive migration saat migrasi dari v8 ke v9 pada perangkat pengguna.

### the agent's Discretion
- Menyusun isi berkas JSON schema versi 8 secara manual (berdasarkan versi 9 tanpa tabel `uploaded_fingerprints`) agar `MigrationTestHelper` dapat melakukan simulasi pembuatan database versi 8 dan menjalankan migrasi ke versi 9.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Requirements & Roadmap
- `.planning/PROJECT.md` — Core value and architecture guidelines.
- `.planning/REQUIREMENTS.md` — Requirement specifications (`DB-01`).
- `.planning/ROADMAP.md` — Roadmap phases definitions.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `TransferInDatabase` (`app/src/main/java/com/reflection/transferin/data/local/db/TransferInDatabase.kt`): Kelas RoomDatabase utama versi 9.
- `Migration_8_9` (`app/src/main/java/com/reflection/transferin/data/local/db/Migration_8_9.kt`): Kelas objek berisi `MIGRATION_8_9` dari v8 ke v9.
- `DatabaseProvider` (`app/src/main/java/com/reflection/transferin/data/local/db/DatabaseProvider.kt`): Singleton penyedia instance database.

</code_context>

<deferred>
## Deferred Ideas

- Tidak ada penundaan fitur baru untuk fase ini.

</deferred>

---

*Phase: 05-room-migration-validation-suite*
*Context gathered: 2026-08-07*
