# Phase 4: Zero-Leak Hardware Core & Resource Lifecycle - Context

**Gathered:** 2026-08-20  
**Status:** Ready for planning  
**Mode:** Autonomous Orchestration

<domain>
## Phase Boundary
Establish a zero-leak hardware governance core: `SafeUsbTransportManager` (IO latch preventing native SIGSEGV in libusbhost.so), `UsbHardwareSession` state machine, `DirectByteBufferPool` (128KB/1MB slices) eliminating JVM heap allocations, Android 14/15 `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` compliance, and leak-free `use { }` resource wrapping across repositories and workers.
Requirements: `CORE-01`, `CORE-02`, `CORE-03`, `CORE-04`, `CORE-05`.
</domain>

<decisions>
## Implementation Decisions
- UsbHardwareSession implements AutoCloseable with deterministic state transitions.
- SafeUsbTransportManager encapsulates all bulkTransfer and controlTransfer calls with atomic state locks and latch checks.
- DirectByteBufferPool manages reusable off-heap ByteBuffers with strict acquisition/release tracking.
- UsbTetherService runs immediate startForeground() in onCreate() with Connected Device notification.
- Teardown sequence runs in application-level NonCancellable coroutine context with a 3,500ms watchdog timeout.
- File descriptor and stream operations in UploadQueueManager, TransferUploadWorker, and DAOs are strictly wrapped in .use { }.
</decisions>
