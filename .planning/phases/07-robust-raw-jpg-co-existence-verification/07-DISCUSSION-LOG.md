# Phase 7 Discussion Log - Robust RAW+JPG Co-existence & Verification

**Date:** 2026-08-07

## Discussion Summary

We discussed the co-existence and synchronization strategies for concurrent RAW and JPG photos under Phase 7:

1. **Robust Reconnection**:
   - **Decision**: Perform individual filename comparisons (including extensions) against database records to determine seen handles, instead of purely relying on numerical threshold checks.
   - **Rationale**: If one format (e.g. JPG) completes but the other (e.g. RAW) fails before disconnection, the failed file must be detected and uploaded on reconnection even though it shares the same file number.

2. **Parallel Transfers**:
   - **Decision**: Dispatch RAW and JPG uploads concurrently in parallel.
   - **Rationale**: Reduces upload delay for high-throughput shoots where immediate availability of both formats on the remote NAS is required.
