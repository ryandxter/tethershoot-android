# Plan - Phase 6: Camera File Number Baseline Extraction

We will implement starting numbering baseline extraction and directory traverse validation.

## Proposed Changes

### PhotoWatcher

#### [MODIFY] [PhotoWatcher.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/usb/PhotoWatcher.kt)
- Add regular expression pattern `private val numberRegex = Regex("(\\d+)\\.[a-zA-Z0-9]+$")` to parse digits in camera filenames.
- Define helper method `private fun extractFileNumber(name: String): Int` to return the parsed file number.
- Update `initialize(dbHighestNumber: Int = 0): Boolean` to take the highest DB number.
- Update `initializeWithStorages(storageIds: List<Int>, dbHighestNumber: Int)`:
  - Retrieve all handles via MTP recursively.
  - Parse the file numbers to calculate `maxCardNumber`.
  - Sort photos by modified time to locate the most latest image, and parse its number `mostLatestImageNumber` as the validation anchor.
  - Set baseline file number to `maxOf(maxCardNumber, mostLatestImageNumber, dbHighestNumber)`.
- Update `checkForNewPhoto()`:
  - Compare new files' numbers against the baseline number to determine if they are new photos.

### Session Presentation

#### [MODIFY] [SessionViewModel.kt](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/feature/sessionactive/SessionViewModel.kt)
- In `observeMtpConnectivity()`, before calling `photoWatcher.initialize()`, fetch all session items from `transferQueueRepository.getBySession(sessionId)`.
- Parse the highest file number using the regex and pass it to `photoWatcher.initialize(highestDbNumber)`.

---

## Verification Plan

### Manual Verification
1. Open active session with previous uploads (e.g. up to `DSCF0004.jpg`).
2. Disconnect and reconnect camera.
3. Verify that the starting baseline does not reset to `0001` but matches `0004` (highest database entry).
4. Format the memory card and shoot a new photo. Verify it uploads with the next sequential number (e.g. `DSCF0005.jpg`) instead of starting from `0001`.
