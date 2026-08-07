# Phase 3: Multi-Vendor MTP Fallback - Validation Strategy

**Date:** 2026-08-07

## 1. Automated Tests

### Unit Tests
- **CameraDiscoveryEngineTest.kt:**
  - Test `detectDirtyFolders` recognizes vendor-specific databases (`AVF_INFO/SONY.DAT`, `FFDB/FUJI.db`, `MISC/GOPRO.db`, `NC_FLLST.DAT`, `PRIVATE/M4ROOT/MEDIABAR.xml`) case-insensitively.
  - Test `pollForNewFiles` fallback behavior when no database files are present (Size & Date Diff comparison).

## 2. Manual Verification

### Vendor Database Detection Test
1. Set up simulated camera file structures for:
   - Sony: `AVF_INFO/SONY.DAT`
   - Nikon: `NC_FLLST.DAT`
   - Fuji: `FFDB/FUJI.db`
2. Change the timestamp of the database file.
3. Verify that `pollForNewFiles` detects the folder as dirty and scans for new items.

### Size & Date Diff Fallback Test
1. Set up a generic storage path containing only `DCIM/100MEDIA/` with images (no database files).
2. Start the sync session.
3. Add a new photo `IMG_0002.JPG` to the folder.
4. Verify that `pollForNewFiles` successfully detects and scans the new photo based on Size/Date metadata comparison against the database.

---

*Validation strategy completed: 2026-08-07*
