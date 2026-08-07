# Requirements - Milestone v2

**Analysis Date:** 2026-08-07

## Milestone v2 Active Requirements

### 1. Camera File Numbering Baseline Extraction (NUM)
- [ ] **NUM-01**: On tethered mode start, the system must scan existing media on the camera memory card and extract the starting baseline number from the filenames (e.g. `DSCF0018.JPG` -> `18`).
- [ ] **NUM-02**: The system must support extraction for various camera naming patterns and customized prefixes (e.g., `IMG_`, `DSCF_`, `_DSF`, `_IMG`) without resetting to `0001` or getting stuck.
- [ ] **NUM-03**: The baseline numbering must be persistent or calculated at every session initialization to prevent database reset to 1 when reconnecting tethered mode.

### 2. MTP Subfolder Navigation & Traverse Audit (DIR)
- [ ] **DIR-01**: The system must support traversing camera memory subfolders (e.g. `/DCIM/100FUJI/`, `/DCIM/101CANON/`) to locate files, accounting for cameras placing photos inside nested directories instead of root folders.
- [ ] **DIR-02**: The system must identify that camera database files (e.g., `.ctg`, `.dat`, `.db` files) reside in non-photo system folders (like `/MISC/` or similar) and avoid scanning them as new photos.

### 3. RAW+JPG Co-existence & Sync (RAW)
- [ ] **RAW-01**: The system must support detecting and uploading both RAW and JPG files taken concurrently (with the same numbering baseline) without one blocking the other during real-time sync.

## Out of Scope
- Direct local media file editing or metadata manipulation within the app.

## Traceability Matrix
- **NUM-01**: Phase 6 (Camera File Number Baseline Extraction)
- **NUM-02**: Phase 6 (Camera File Number Baseline Extraction)
- **NUM-03**: Phase 6 (Camera File Number Baseline Extraction)
- **DIR-01**: Phase 6 (Camera File Number Baseline Extraction)
- **DIR-02**: Phase 6 (Camera File Number Baseline Extraction)
- **RAW-01**: Phase 7 (Robust RAW+JPG Co-existence & Verification)
