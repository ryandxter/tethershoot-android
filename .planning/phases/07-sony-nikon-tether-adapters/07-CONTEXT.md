# Phase 7: Sony Alpha & Nikon Multi-Vendor Tether Adapters - Context

**Gathered:** 2026-08-20  
**Status:** Ready for planning  
**Mode:** Autonomous Wave 2 Parallel

<domain>
## Phase Boundary
Implement `SonyPtpAdapter` (VID `0x054C`) with `0x9280 SDIOConnect` handshake, "PC+Camera" volatile DRAM protection check, multi-packet PTP payload defragmentation, and `.ARW` TIFF SubIFD preview parser. Implement `NikonPtpAdapter` (VID `0x04B0`) with Nikon vendor PTP commands (`0x90C0`/`0x90C1`), discrete dual-slot storage ID enumeration, and exponential backoff retry loop on `0x2019 PTP_RC_DeviceBusy` during EXPEED card flushes.
Requirements: `SONY-01`, `SONY-02`, `NIKON-01`, `NIKON-02`.
</domain>
