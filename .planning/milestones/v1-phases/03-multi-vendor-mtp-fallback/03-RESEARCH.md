# Phase 3: Multi-Vendor MTP Fallback - Research

**Date:** 2026-08-07

## 1. Technical Domain Analysis

### Multi-Vendor Database Directory & File Patterns
Based on camera manufacturer specifications and user insights:
- **Canon:** `.CTG` files in `CANONMSC` folder.
- **Sony:** `.DAT` files in `AVF_INFO` folder; XML/metadata in `PRIVATE/M4ROOT` for video.
- **Fujifilm:** `.DAT` or `.db` files in `FFDB` folder.
- **GoPro & DJI:** `.db` files in `MISC` folder.
- **Nikon:** `NC_FLLST.DAT` file at root directory or system folders.

To ensure robustness against customization or memory card swapping, we will implement a flexible change detection algorithm:
1. Scan for any files with extensions `.CTG`, `.DAT`, `.db`, `.xml` inside folders containing typical system/database names (`AVF_INFO`, `FFDB`, `CANONMSC`, `MISC`, `M4ROOT`, `PRIVATE`, `SYSTEM`).
2. Treat `NC_FLLST.DAT` at root or system paths as a top-level database indicator.
3. Watch the `lastModified` times of these files. If any of them change, trigger folder rescan.

### Size & Date Diff Fallback Polling
If no database files are found (e.g. generic cameras/empty cards/swapped environments):
- Fetch all files in the MTP tree (`documentFileScanner.scanTree(treeUri)`).
- Match them with local `CameraMediaItemEntity` DB using stable key `filename` + `sizeBytes` + `modifiedAt`.
- Insert any new items.

## 2. Codebase Scouting & Integration Points

### Reusable Assets
- `CameraDiscoveryEngine` (`app/src/main/java/com/reflection/transferin/data/usb/CameraDiscoveryEngine.kt`): Coordinates directory scanning and MTP polling.

### Identified Gaps & Modifications

#### A. CameraDiscoveryEngine Database File Recognition
Modify `CameraDiscoveryEngine.kt` to:
- Expand `CTG_PATTERNS` to include `.DAT`, `.db`, and `.xml` patterns.
- Recursively check database directories (`AVF_INFO`, `FFDB`, `CANONMSC`, `MISC`, `M4ROOT`, `PRIVATE`, `SYSTEM`).
- Handle root-level database files like `NC_FLLST.DAT`.

#### B. CameraDiscoveryEngine Fallback Sync Logic
- If no database files are present, poll using `Size & Date Diff` by traversing the image directory (`DCIM` or root) recursively.

---

*Phase 3 Research Completed: 2026-08-07*
