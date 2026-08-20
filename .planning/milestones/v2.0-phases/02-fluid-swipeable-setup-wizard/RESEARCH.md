# Phase 2 Technical Research — Fluid Swipeable Setup Wizard

**Milestone:** v2 (Fluid Wizard & Agency-Grade UI/UX Overhaul)
**Phase:** 02 — Fluid Swipeable Setup Wizard
**Date:** 2026-08-12

---

## 1. Existing Setup Architecture & Screen Consolidation

### Current Screen Structure (Legacy Separate Routes)
* **`LoginNasScreen.kt`** (`com.rvmeur.transferin.feature.auth`): Google Drive OAuth & credential fields.
* **`SelectFolderScreen.kt`** (`com.rvmeur.transferin.feature.folder`): Vertical list of remote target folders.
* **`ConnectCameraScreen.kt`** (`com.rvmeur.transferin.feature.connectcamera`): USB tethering discovery and mode selection.
* **`SelectSourceFolderScreen.kt`** (`com.rvmeur.transferin.feature.folder`): SAF local directory selection.

### Phase 2 Consolidated Architecture (`FluidWizardScreen.kt`)
Consolidates the standalone setup routes into a single root `FluidWizardScreen` composable under package `com.rvmeur.transferin.feature.wizard`:
* **Container**: `HorizontalPager(state = pagerState, userScrollEnabled = isSwipeAllowed)`
* **Header Chrome**: `RvmHeaderBar(eyebrowText = "quiet luxury tethering v1.0", isConnected = isCameraConnected)`
* **Bottom Chrome**: `FluidDotNavigationBar` + `RvmFooterBar`

---

## 2. Technical Implementation & Compose Patterns

### 2.1 Forward Swipe Lock Logic
```kotlin
val isStep1Valid = isDriveAuthenticated && selectedTargetFolder != null
val isSwipeAllowed = when (pagerState.currentPage) {
    0 -> isStep1Valid // Disables forward swipe from Step 0 to Step 1 if Step 0 is invalid
    else -> true      // Allows backward and forward navigation once on Step 1 or 2
}
```

### 2.2 2-Column Card Grid Folder Browser
```kotlin
LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
) {
    items(remoteFolders) { folder ->
        val isSelected = selectedFolder?.id == folder.id
        DoppelrandCard(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .clickable { onFolderSelect(folder) },
            outerShape = RoundedCornerShape(16.dp),
            innerShape = RoundedCornerShape(12.dp),
            borderColor = if (isSelected) AccentGreen else LightBorder,
            outerColor = if (isSelected) AccentGreenSubtle else SurfaceVariant
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = if (isSelected) AccentGreen else TextPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = folder.name.toBrandLowercase(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(text = "${folder.itemCount} items", style = MaterialTheme.typography.labelSmall, color = TextMutedLight)
                }
            }
        }
    }
}
```

### 2.3 Camera Live Status Pulse Aura Card
```kotlin
DoppelrandCard(modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = if (isCameraConnected) cameraModelName.toBrandLowercase() else "mencari kamera...",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = CormorantGaramond
            )
            Text(
                text = if (isCameraConnected) "media path: $dcimPath ($photoCount foto)" else "hubungkan kamera via kabel USB/MTP",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = RobotoMono,
                color = TextMutedLight
            )
        }
        // Animated Status Dot Pulse
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale; alpha = pulseAlpha }
                    .background(if (isCameraConnected) AccentOlive else AccentBurgundy, CircleShape)
            )
            Box(
                modifier = Modifier.size(8.dp).background(if (isCameraConnected) AccentOlive else AccentBurgundy, CircleShape)
            )
        }
    }
}
```

---

## 3. Build & Compilation Verification Strategy

1. **Compilation Check**: `./gradlew compileDebugKotlin` to verify clean build.
2. **Navigation Wiring**: Update `MainActivity.kt` to route initial launch flow into `FluidWizardScreen`.
