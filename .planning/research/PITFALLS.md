# Research: Common Pitfalls & Prevention

**Date:** 2026-08-07

## 1. Network & SFTP Pool Pitfalls
- **High Resource Overhead:** Creating keepalive intervals shorter than 15s drains mobile batteries rapidly. Keep it at 30s.
- **Circuit Breaker Lockup:** If the circuit breaker remains OPEN without a proper HALF_OPEN transition and test-connect, users are blocked from transferring photos even after restoring cellular connectivity.

## 2. USB Camera Connectivity Pitfalls
- **USB Permission Race:** Requesting USB permissions twice on high-frequency detach-attach events crashes the app or leaves double callback registers. Ensure permission request state is guarded.
- **PTP Session Corruption:** If a PTP session is not closed properly before starting a new one, the camera firmware may crash or lock up, requiring a battery pull. Always wrap PTP initialization in try-catch and run reset routines if a timeout occurs.

## 3. Service Lifecycle Pitfalls
- **Foreground Service Exceptions:** Starting `UsbTetherService` without specifying the service type dynamically in code (`startForeground(..., FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)`) triggers a crash on Android 14+ devices.
- **Thread Exhaustion:** Launching un-debounced watcher coroutines on USB attach event spawns redundant threads, locking database transactions.

## 4. Large Media Transfer Pitfalls
- **Corrupted Resumed Uploads:** Resuming transfers without validating size/hash matching can lead to corrupted image headers. Read check data from both client and server before appending bytes.
- **Orphaned Server Temp Files:** Appending directly to the main destination file can corrupt it if connection fails mid-transfer. Use `.tmp` suffixes, renaming them only upon completion. Clean up stale `.tmp` files during pool initialization.

## 5. Room Database Migrations
- **Schema Mismatches:** Hand-written migration SQL code must exactly match Room schema compiler configurations. Any differences in default values or nullability attributes trigger runtime crashes.

---

*Research: 2026-08-07*
