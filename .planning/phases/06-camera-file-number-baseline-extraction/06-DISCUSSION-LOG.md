# Phase 6 Discussion Log - Camera File Number Baseline Extraction

**Date:** 2026-08-07

## Discussion Summary

We aligned on three critical questions regarding how `PhotoWatcher` should initialize the filename numbering baseline and handle directory structures on Fuji and other MTP cameras:

1. **Folder Traversal**:
   - **Decision**: Recursive MTP scan. The engine will perform a full search across all directories rather than hardcoding DCIM constraints.
   - **Rationale**: Ensures resilience for any camera layout.

2. **File Number Extraction**:
   - **Decision**: Flexible digit extraction regex (trailing digits before extension).
   - **Rationale**: Supports any camera prefix (`IMG_`, `DSCF_`, etc.) and customized camera prefixes out of the box.

3. **Reset Prevention**:
   - **Decision**: Use the maximum value between the highest file number on the camera card and the highest file number stored in the local database for the current project session.
   - **Rationale**: Prevents starting over from `0001` or causing name conflicts on the NAS if the photographer deletes card contents or formats the card during a shoot.
