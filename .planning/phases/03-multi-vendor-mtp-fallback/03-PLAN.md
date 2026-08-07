# Plan: Phase 3 - Multi-Vendor MTP Fallback

This plan supports folder listing and sync capabilities for generic MTP cameras (non-Canon) by recognizing multi-vendor database files and implementing a robust Size & Date Diff fallback sync.

## User Review Required

> [!IMPORTANT]
> **Multi-Vendor Database Folders & Formats:** The scanner will recognize `.CTG`, `.DAT`, `.db`, and `.xml` files located in system folders (case-insensitive checks for `AVF_INFO`, `FFDB`, `CANONMSC`, `MISC`, `M4ROOT`, `PRIVATE`, `SYSTEM`) and root-level files like `NC_FLLST.DAT` to trigger index rescans.
> 
> **Size & Date Diff Fallback:** If no database files are found on the camera, the app falls back to scanning the image directories recursively and matching files by name, size, and modified time.

## Proposed Changes

### Database Detection

#### [MODIFY] [CameraDiscoveryEngine.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/usb/CameraDiscoveryEngine.kt)
- Expand `CTG_PATTERNS` to match various vendor databases:
  - Extensions: `.ctg`, `.dat`, `.db`, `.xml`.
  - Check case-insensitively in `AVF_INFO`, `FFDB`, `CANONMSC`, `MISC`, `M4ROOT`, `PRIVATE`, `SYSTEM`.
  - Handle root-level databases (e.g., `NC_FLLST.DAT`).
- Update `detectDirtyFolders` to scan and detect any of these files' timestamps.
- Update `pollForNewFiles` to:
  - First, attempt database-timestamp change detection.
  - If no database files exist on the storage, run the fallback `Size & Date Diff` scanning algorithm across the entire tree by comparing the file list metadata (name, size, modifiedAt) directly with `cameraMediaDao` entries.

## Verification Plan

### Automated Tests
Run unit tests for discovery and polling logic:
```bash
./gradlew test
```

### Manual Verification
1. Connect a non-Canon camera (e.g. Sony, Fuji, Nikon).
2. Start the sync session.
3. Take a new photo.
4. Verify that the app detects the new photo via database file change detection (if supported by camera) or size/date polling (fallback) and adds it to the upload queue.
5. Verify that no duplicate entries are created for existing photos.
