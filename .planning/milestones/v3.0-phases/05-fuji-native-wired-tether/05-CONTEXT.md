# Phase 5: Fujifilm Native Wired Tether Engine & Clean Disconnect Lifecycle - Context

**Gathered:** 2026-08-20  
**Status:** Ready for planning  
**Mode:** Autonomous Wave 2 Parallel

<domain>
## Phase Boundary
Implement direct PTP `FujiTetherAdapter` (VID `0x04CB`) bypassing generic MTP file loops, using explicit `0xD214` Host Control and `0xD216` PC+Card property handshakes. Asynchronously drain Interrupt IN endpoint (`0x83`) for `0x4002`/`0xC001` `ObjectAdded` events in <15ms ($O(1)$ discovery), mandatory 4-step clean teardown sequence (`0xD214 = 0x0000` -> `0x1003 CloseSession` -> `releaseInterface` -> `close`), fast embedded JPEG preview extraction from `.RAF` headers without downloading full RAW, and live Film Simulation badge parsing for UI.
Requirements: `FUJI-01`, `FUJI-02`, `FUJI-03`, `FUJI-04`, `FUJI-05`.
</domain>
