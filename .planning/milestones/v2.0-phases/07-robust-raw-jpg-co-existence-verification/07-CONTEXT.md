# Phase 7 Context - Robust RAW+JPG Co-existence & Verification

## Decisions

1. **Reconnection Verification (Individual Filename Match)**:
   - To prevent RAW files from being skipped when their twin JPG files are successfully uploaded (or vice versa) during a network drop, the initialization logic will compare complete filenames (including extensions) against the database records.
   - We will collect a set of all successfully uploaded filenames for the active session and only mark files on the card as "seen" in `seenHandles` if their exact filename matches an entry in the database.
   - This removes the fragility of numeric-only baseline checks for seen handles.

2. **Parallel Upload Execution**:
   - RAW and JPG files detected concurrently will be uploaded in parallel (concurrently) rather than sequentially to maximize bandwidth and minimize transfer latency.
   - The queue runner and WorkManager configuration must allow concurrent dispatching of up to 2 upload tasks.

## Open Questions
- None.
