# Project Research Summary

**Project:** TransferIn Android App - Canon Camera Transfer
**Domain:** Mobile camera tethering with upload integrity verification
**Researched:** 2026-07-31
**Confidence:** MEDIUM-HIGH

## Executive Summary

TransferIn is an Android application for tethered Canon camera shooting, enabling automatic photo transfer to NAS via SFTP. The current implementation monitors CTG (Canon Thumbnail Group) files for new photos and transfers them via JSch SFTP, but lacks robust resilience features and integrity verification. Research identifies three capability gaps: (1) CTG binary parsing for authoritative file cataloging, (2) SFTP session pooling with reconnection resilience, and (3) Room-based upload checkpointing for resume capability.

The recommended approach extends existing infrastructure without new dependencies. CTG files are SQLite databases (not custom binary format as initially assumed), enabling standard SQLite parsing. JSch session pooling requires a ConcurrentLinkedQueue with health-checked session reuse. Room already supports checkpoint persistence via entity extensions. The key risk is CTG format variation across Canon firmware versions; defensive parsing with fallback is essential.

Critical pitfalls to avoid: (1) SFTP session exhaustion from per-request connection creation, (2) MTP handle staleness after USB disconnect, and (3) database/SFTP state divergence causing silent data loss. These are solvable but require deliberate architecture additions.

## Key Findings

### Recommended Stack

**Core technologies** (no new dependencies required):

| Technology | Purpose | Rationale |
|------------|---------|-----------|
| JSch MWiede Fork 0.2.26 | SFTP client | Already in use; maintained fork with bug fixes |
| Room 2.8.4 | Local persistence | Already in use; extend for checkpoint storage |
| SQLiteDatabase (android) | CTG parsing | CTG files are SQLite databases, not binary |
| WorkManager 2.11.2 | Background processing | Already in use; extend for checkpointing worker |

**Session pooling pattern:**
- Pool size: 2 concurrent sessions per credential set
- Keepalive: 30s interval, 3 max misses
- Reconnection: exponential backoff (1s, 2s, 4s, max 30s)
- Health check on acquire before use

**Checkpoint persistence pattern:**
- Save every 1MB or 30 seconds during upload
- Store: jobId, localPath, remotePath, totalBytes, bytesUploaded, status, retryCount
- Resume from stored offset on recovery

### Expected Features

**Must have (table stakes):**
- CTG file timestamp/size monitoring — detect new photos without polling (already implemented)
- MTP file enumeration — list all files on storage (already implemented)
- SFTP upload with progress — transfer files to NAS (already implemented)
- Upload deduplication — skip already-transferred files (partially implemented)

**Should have (differentiators):**
- CTG binary parsing — authoritative file catalog from camera, stable handles
- Upload checkpoint/resume — recover from interruptions without restarting from 0
- SFTP session pooling — reduce connection overhead, improve resilience
- Integrity verification — compare CTG state vs uploaded state for confidence

**Defer (v2+):**
- Content hash verification — requires Canon native PTP extension support
- Checksum-based deduplication — camera may not store checksums in CTG

### Architecture Approach

The architecture follows Clean Architecture with MVVM, Repository pattern, and domain engines. Three new component clusters integrate at the data layer:

1. **CtgParser** (`data/usb/CtgParser.kt`) — downloads and parses CTG files via SQLite
2. **CtgRepository** (`domain/repository/CtgRepository.kt` + `CtgRepositoryImpl`) — interface for CTG operations with snapshot storage
3. **SftpSessionPool** (`data/sftp/SftpSessionPool.kt`) — manages JSch session reuse with health checks

Upload resilience requires:
- `SftpSessionPool` wrapping JSch sessions
- `TransferQueueItemEntity` extended with `uploadOffsetBytes` and checkpoint fields
- `TransferUploadWorker` checkpoint logic every 1MB/30s
- Two-phase upload commit (upload to .part, verify, atomic rename)

### Critical Pitfalls

1. **CTG Binary Format Misunderstanding** — CTG files are SQLite databases, not custom binary. Attempting binary parsing causes ArrayIndexOutOfBoundsException. Prevention: use SQLiteDatabase.openDatabase() with OPEN_READONLY.

2. **SFTP Session Per-Request Exhaustion** — Creating new session+channel for every upload (current state) exhausts server limits and adds overhead. Prevention: implement session pooling with reuse.

3. **MTP Handle Staleness** — Object handles become invalid on USB disconnect. Any cached handle references become dangling. Prevention: validate session state before handle access; use path-based identification as fallback.

4. **Database vs SFTP State Divergence** — Database marks SUCCESS immediately after SFTP put() returns, but data may not be on disk. Prevention: two-phase commit with remote verification before marking success.

5. **USB Disconnect During Upload** — JSch throws channel errors on async disconnect. Prevention: upload to .part temp file, verify size, atomic rename.

## Implications for Roadmap

Based on research, a 4-phase approach addresses the capability gaps in dependency order:

### Phase 1: CTG Binary Parsing
**Rationale:** Lowest risk; establishes foundation for smart detection. CTG parsing is isolated from other components.
**Delivers:** CtgParser, CtgRepository interface, CtgSnapshotEntity, CtgSnapshotDao
**Implements:** Canon .CTG database parsing for authoritative file cataloging
**Avoids:** Pitfall #1 (CTG format misunderstanding) — uses SQLite approach from STACK.md

### Phase 2: SftpSessionPool
**Rationale:** Session pooling is independent of upload logic; enables resilience for Phase 3
**Delivers:** SftpSessionPool, SftpSession wrapper, updated JschSftpClient with pool integration
**Implements:** JSch connection pooling with health checks
**Avoids:** Pitfall #2 (session exhaustion) — pooled session reuse

### Phase 3: Upload Checkpoint/Resume
**Rationale:** Depends on Phase 2 (session pool) for resilience during checkpoint recovery
**Delivers:** Extended TransferQueueItemEntity, checkpoint DAO methods, modified TransferUploadWorker
**Implements:** Room-based checkpoint persistence and resume capability
**Avoids:** Pitfall #4 (state divergence) — two-phase commit pattern

### Phase 4: Integrity Verification
**Rationale:** Depends on Phases 1-3; uses CTG catalog and checkpoint infrastructure
**Delivers:** CtgIntegrityChecker, CtgAwareDeduplicator integration
**Implements:** Compare camera state vs uploaded state for confidence
**Avoids:** Pitfall #3 (handle staleness) — validate session before CTG access

### Phase Ordering Rationale

1. **CTG parsing first** — lowest risk, isolated, enables downstream features
2. **Session pooling second** — independent of business logic, foundation for resilience
3. **Checkpoint/resume third** — depends on session pool for retry resilience
4. **Integrity verification last** — depends on all previous phases

### Research Flags

Phases needing deeper research during planning:
- **Phase 1 (CTG Parsing):** CTG format varies by Canon model/firmware. Needs sample files from multiple cameras (R5, R6, R8, older models) for validation.
- **Phase 4 (Integrity):** Unknown which Canon cameras include checksums in CTG. May need to fall back to size-only verification.

Phases with standard patterns (skip research-phase):
- **Phase 2 (Session Pooling):** JSch patterns well-established, no novel requirements.
- **Phase 3 (Checkpoint):** Room + WorkManager patterns well-documented, existing codebase patterns to follow.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | No new dependencies; existing libraries sufficient |
| Features | MEDIUM | CTG format variation creates uncertainty; integrity verification gaps |
| Architecture | HIGH | Follows existing patterns; minimal disruption |
| Pitfalls | HIGH | Based on codebase analysis + domain knowledge |

**Overall confidence:** MEDIUM-HIGH

### Gaps to Address

- **CTG format variation:** Reverse-engineering from actual camera samples needed; multiple format versions likely
- **Checksum support:** Unknown if/which Canon cameras include checksums in CTG
- **Performance testing:** Need to measure CTG download/parse time on various camera models
- **Concurrent capture handling:** How to handle new CTG data during upload queue processing
- **Server resume support:** Not verified if all NAS SFTP servers support resume at byte offset

## Sources

### Primary (HIGH confidence)
- JSch MWiede Fork (https://github.com/mwiede/jsch) — session pooling API
- Room Database Documentation (https://developer.android.com/training/data-storage/room) — persistence patterns
- WorkManager Documentation (https://developer.android.com/topic/libraries/architecture/workmanager) — checkpoint worker patterns

### Secondary (MEDIUM confidence)
- TransferIn codebase analysis: `CtgWatcher.kt`, `JschSftpClient.kt`, `TransferQueueDao.kt`, `FileStabilityChecker.kt`
- Canon PTP extension codes from `CanonCameraInitializer.kt`

### Tertiary (LOW confidence)
- CTG binary format specification — requires reverse-engineering from actual camera samples
- Canon SDK documentation for CTG file format — internal knowledge, not publicly verified

---
*Research completed: 2026-07-31*
*Ready for roadmap: yes*
