# Phase 3 Technical Research — Live Shoot Dashboard & Gallery Polish

**Milestone:** v2 (Fluid Wizard & Agency-Grade UI/UX Overhaul)
**Phase:** 03 — Live Shoot Dashboard & Gallery Polish
**Date:** 2026-08-12

---

## 1. Existing Session & Gallery Codebase Analysis

### 1.1 `SessionActiveScreen.kt`
- **Location:** `app/src/main/java/com/rvmeur/transferin/feature/sessionactive/SessionActiveScreen.kt`
- **Current Pattern:** Contains `CameraStatusHeader`, `QuickStatsCard`, `StatusDetailsCard`, and `ActionButtonsArea`.
- **Refactoring Strategy:**
  - Replace standard Material cards with `DoppelrandCard` containers.
  - Implement 4-stat metric grid (`antrean`, `mengunggah`, `berhasil`, `gagal`) using a 2-column x 2-row `DoppelrandCard` inner layout with monospace metric numbers (`RobotoMono`, `28.sp`, Bold).
  - Use `RvmHeaderBar` (top app bar with `.dot-pulse` status aura) and `RvmFooterBar` (bottom web intent footer).
  - Replace action buttons with `KineticButton` instances (`"buka galeri terunggah"`, `"selesaikan sesi tethering"`).

### 1.2 `UploadedGalleryScreen.kt` & Full-Screen Image Viewer Modal
- **Location:** `app/src/main/java/com/rvmeur/transferin/feature/gallery/UploadedGalleryScreen.kt`
- **Current Pattern:** Uses standard top bar, filter chip row, and adaptive grid items.
- **Refactoring Strategy:**
  - Apply RVM quiet luxury tokens (`Ground` `#FAF8F3`, `CardSurface` `#F6F3EB`, `SurfaceVariant` `#F0EBE0`).
  - Style 3-column thumbnail grid using `DoppelrandCard` items (1:1 aspect ratio) with monospace metadata tags (`RAW`, `JPG`) and TalkBack semantics.
  - Build full-screen image viewer modal overlay with dark quiet luxury backdrop (`Color(0xEE12100E)`), monospace header info, gesture pinch-zoom viewport, and bottom action bar (`"unduh file"`, `"buka di google drive"`, `"hapus"`).

### 1.3 `SessionSummaryScreen.kt`
- **Location:** `app/src/main/java/com/rvmeur/transferin/feature/sessionsummary/SessionSummaryScreen.kt`
- **Current Pattern:** Standard M3 Card summary with simple text rows.
- **Refactoring Strategy:**
  - Wrap session summary details (customer name, target Google Drive folder, duration, total files, success count, failed count) inside `DoppelrandCard`.
  - Use `CormorantGaramond` for headline, `RobotoMono` for path and metric numbers, and `KineticButton` labeled `"selesai & kembali ke setup"` with trailing arrow `↗`.

---

## 2. Technical Implementation Details

### 4-Stat Metric Grid Component Snippet
```kotlin
LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
) {
    items(metricItems) { metric ->
        DoppelrandCard(
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "${metric.label}: ${metric.count}"
            },
            outerShape = RoundedCornerShape(16.dp),
            innerShape = RoundedCornerShape(12.dp),
            outerColor = SurfaceVariant,
            innerColor = CardSurface
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = metric.count.toString(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = RobotoMono,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = metric.color
                    )
                )
                Text(
                    text = metric.label.toBrandLowercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = DMSans,
                        color = TextMutedLight
                    )
                )
            }
        }
    }
}
```

---

## 3. Build & Compilation Verification Strategy

1. **Compilation Check**: `./gradlew compileDebugKotlin` after refactoring each screen.
2. **Quality Audit**: Ensure zero errors, 44.dp minimum touch targets, and full-screen image viewer modal integration.
