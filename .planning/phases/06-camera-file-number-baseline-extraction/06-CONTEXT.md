# Phase 6 Context - Camera File Number Baseline Extraction

## Decisions

1. **Directory Traversal strategy**:
   - Perform a full recursive MTP object handles scan (DFS/BFS) on the camera storage during initialization. This scans all subfolders (including nested folders under `DCIM/` such as `100FUJI/`, `100CANON/`) to correctly catalog existing photos.
   - Database files (`.ctg`, `.dat`, `.db`) which are located in non-photo system folders (like `MISC/`) are parsed or filtered out so they do not get treated as photos.

2. **File Number Extraction Pattern**:
   - Use a flexible regular expression to auto-detect and extract the final sequence of digits immediately preceding the file extension.
   - Examples:
     - `DSCF0018.JPG` -> `18`
     - `IMG_2345.CR3` -> `2345`
     - `_DSF1234.RAF` -> `1234`
     - Custom user prefix patterns are naturally supported since we extract any digits trailing the letters.

3. **Numbering Reset Prevention (Highest-Of fallback)**:
   - When tethered mode starts, query both:
     1. The highest file number currently on the camera card.
     2. The highest file number successfully uploaded/queued in the local database for the active session.
   - Set the initial baseline number to the **maximum of these two values**. This prevents duplicate uploads or naming collisions on the NAS if the memory card is formatted or files are deleted mid-session.

## Open Questions
- None. All major implementation paths have been finalized with user input.
