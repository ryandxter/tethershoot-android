# Phase 6: Canon EOS Direct PTP Engine & Fast Preview Extraction - Context

**Gathered:** 2026-08-20  
**Status:** Ready for planning  
**Mode:** Autonomous Wave 2 Parallel

<domain>
## Phase Boundary
Implement `CanonPtpAdapter` (VID `0x04A9`) in EOS Remote/Event mode (`0x9114 = 1`, `0x9115 = 1`), continuous `0x9116 EOS_GetEvent` DIGIC event ring buffer draining (preventing "Busy" shutter lock), sub-500ms `.CR3` ISO-BMFF box parsing (`PRVW`/`THMB` atoms in first 128KB), two-stage stability-verified `.CTG` fallback, and simultaneous dual-slot handle isolation (Slot 1 CFexpress + Slot 2 SD).
Requirements: `CANON-01`, `CANON-02`, `CANON-03`, `CANON-04`.
</domain>
