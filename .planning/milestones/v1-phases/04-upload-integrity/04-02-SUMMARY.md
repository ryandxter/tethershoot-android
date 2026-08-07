# Phase 04 Plan 02: Stale .part file cleanup Summary

## Overview

| Field | Value |
|-------|-------|
| **Phase** | 04 |
| **Plan** | 02 |
| **Status** | complete |
| **Completion Date** | 2026-07-31T00:00:00Z |

## Objective

Add stale `.part` file cleanup on retry and periodic cleanup of orphaned `.part` files older than 24 hours. This prevents accumulation of incomplete files on the NAS.

## Tasks Completed

| # | Task | Status | Files |
|---|------|--------|-------|
| 1 | Enhance SftpClient and JschSftpClient with listPartFiles | PASS | `SftpClient.kt`, `JschSftpClient.kt` |
| 2 | Add stalePartFileCleanup and handleUploadRetry to TwoPhaseUploadHandler | PASS | `TwoPhaseUploadHandler.kt` |
| 3 | Wire stale cleanup into TransferUploadWorker startup | PASS | `TransferUploadWorker.kt` |

## Files Created/Modified

### Modified

1. **SftpClient.kt**
   - Added `PartFileInfo` data class and `listPartFiles()` signature.
2. **JschSftpClient.kt**
   - Implemented `listPartFiles()` by scanning the directory using JSch `ls` and filtering by `.part` suffix.
3. **TwoPhaseUploadHandler.kt**
   - Implemented `stalePartFileCleanup()` to purge `.part` files older than 24 hours.
   - Implemented `handleUploadRetry()` to perform cleanup of the target file's `.part` and requeue it.
4. **TransferUploadWorker.kt**
   - Wired `stalePartFileCleanup()` to run at the start of `doWork()`.
   - Added `KEY_SKIP_STALE_CLEANUP = "skip_stale_cleanup"` input flag to conditionally bypass cleanup.

## Verification Results

- JUnit unit tests coverage extended to verify:
  - `stalePartFileCleanup deletes files older than 24 hours` (passing)
  - `handleUploadRetry cleans up part file and requeues` (passing)
- Code successfully builds.

## Requirements Addressed

| Requirement | Source | Status |
|-------------|--------|--------|
| RESILIENCE-05 | Phase 04 frontmatter | Addressed |
| RESILIENCE-06 | Phase 04 frontmatter | Addressed |
