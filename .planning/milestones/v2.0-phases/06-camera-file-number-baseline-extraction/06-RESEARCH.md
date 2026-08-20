# Phase 6 Research - Camera File Number Baseline Extraction

## 1. Directory Traversal & Storage Structure
Cameras typically store photos in subfolders nested under `/DCIM/` (e.g. `/DCIM/100FUJI/`, `/DCIM/101CANON/`).
MTP protocol supports full recursive handle discovery by calling `MtpDevice.getObjectHandles(storageId, format = 0, parent = 0)`.
We will verify that `usbHelper.getAllObjectHandles(storageId)` returns all handles recursively. This is confirmed by checking `UsbHelper.kt` line 298.

## 2. Filename Number Extraction
Camera files have prefixes like `DSCF`, `IMG_`, etc., followed by sequential numbers (e.g. `DSCF0018.JPG`).
We can use a regex to extract the digits right before the file extension:
```kotlin
val numberRegex = Regex("(\\d+)\\.[a-zA-Z0-9]+$")
val filename = "DSCF0018.JPG"
val match = numberRegex.find(filename)
val fileNumber = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
```
This regex naturally works for any camera prefix (customized or default) because it scans backwards from the file extension to match the continuous digits.

## 3. Reset Prevention Anchors
To prevent starting numbering from `0001` or duplicate filenames when tethered session reconnects, or if the memory card is formatted/emptied, the initial baseline number is derived dynamically as:
```kotlin
val baseline = maxOf(highestCardFileNumber, highestLatestImageNumber, highestDbUploadedNumber)
```
Where:
- `highestCardFileNumber` is the maximum number found in any file currently on the card.
- `highestLatestImageNumber` is the number parsed from the single most recent file sorted by modified time.
- `highestDbUploadedNumber` is queried from the local app database `transfer_queue_items` for the current project session.

## 4. DB Query for Highest File Number
Inside `SessionViewModel.kt`, we have access to `transferQueueRepository`. We can fetch all files for the active session:
```kotlin
val sessionItems = transferQueueRepository.getBySession(sessionId)
val highestDbNumber = sessionItems.mapNotNull { item ->
    numberRegex.find(item.targetFileName)?.groupValues?.get(1)?.toIntOrNull()
}.maxOrNull() ?: 0
```
This is passed to `PhotoWatcher.initialize(dbHighestNumber)` on launch.
