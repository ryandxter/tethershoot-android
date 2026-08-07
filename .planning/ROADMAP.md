# Roadmap

## Milestone v1 Phases (Completed)
- [x] Phase 1: SFTP Pooling & Reconnection Resilience
- [x] Phase 2: USB Debounce & Service Compliance
- [x] Phase 3: Multi-Vendor MTP Fallback
- [x] Phase 4: Resumable Transfers & Server Cleanup
- [x] Phase 5: Room Migration Validation Suite

## Milestone v2 Phases

### Phase 6: Camera File Number Baseline Extraction
- **Goal:** Robustly extract the file number baseline from the camera's folder/file layout on session start.
- **Requirements:** `NUM-01`, `NUM-02`, `NUM-03`, `DIR-01`, `DIR-02`
- **Success Criteria:**
  1. `PhotoWatcher` scans all MTP objects, filters out system folders (e.g. `/MISC/` or databases), and parses the highest file number (e.g. `DSCF0018` -> `18`) at launch.
  2. Baseline number does not reset to `0001` when reconnecting tethered mode.
  3. Traverses subfolders nested under `/DCIM/` to locate all media files.

### Phase 7: Robust RAW+JPG Co-existence & Verification
- **Goal:** Ensure RAW+JPG taken concurrently sync without race conditions or file skips.
- **Requirements:** `RAW-01`
- **Success Criteria:**
  1. Taking RAW+JPG simultaneously enqueues both files based on the matched number baseline and distinct extensions.
  2. Automated tests verify `PhotoWatcher` does not drop raw or jpeg files during concurrent emission.
