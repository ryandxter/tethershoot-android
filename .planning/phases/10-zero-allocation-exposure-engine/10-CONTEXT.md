# Phase 10: Zero-Allocation Exposure & Luminance Engine — Context & Decisions

**Phase:** 10  
**Title:** Zero-Allocation Exposure & Luminance Engine  
**Status:** Context Gathered & Decisions Locked  
**Date:** 2026-08-20  

---

## Domain Boundary

Pure domain mathematical analysis engine computing 256-bin luminance histograms in $<1.8\text{ms}$ with zero JVM heap allocations, multi-feature Shannon entropy classification for flash misfires and blown highlights, and AndroidX Microbenchmark verification.

---

## Implementation Decisions Locked

### 1. Luminance Calculation Math
- **Formula:** Fixed-point ITU-R BT.601 integer bit-shift math:
  $$Y = (77 \cdot R + 150 \cdot G + 29 \cdot B) \gg 8$$
- **Allocation Budget:** $0\text{ B}$ JVM heap allocation in the hot loop. Uses pre-allocated reusable `IntArray(256)` bin buffers and `BitmapFactory.Options.inBitmap` mutable pooling.
- **Execution Target:** $<1.8\text{ms}$ per frame (measured on downsampled $256 \times 170$ preview slices).

### 2. Flash Misfire & Underexposure Classification (`UNDEREXPOSED_CRITICAL`)
- **Multi-Feature Decision Rule:**
  - Sensor Black-Floor Density: $>98\%$ of pixels have luminance $Y \le 3$.
  - Mean Luminance: $\bar{Y} < 15.0$.
  - Shannon Histogram Entropy: $H = -\sum p_i \log_2(p_i) < 1.0$.
- **Rationale:** Prevents false positives on intentional low-key studio portraits or silhouette compositions with high entropy ($H \ge 3.0$).

### 3. Highlight Blowout Classification (`OVEREXPOSED_BLOWN`)
- **Dual-Condition Decision Rule:**
  - Highlight Saturation: $>20\%$ of pixels clipped at $Y \ge 248$.
  - Mean Luminance: $\bar{Y} > 230.0$.
- **Rationale:** Distinguishes small specular highlights (e.g. jewelry reflections, strobe hotspots) from catastrophic whole-frame overexposure.

### 4. Color Profile & Film Simulation Standardization
- **Policy:** Strict sRGB Standard baseline. Evaluates standard decoded sRGB preview pixels uniformly across Fujifilm, Canon, Sony, and Nikon cameras without vendor-specific tone curve overrides.

### 5. Verification & Performance Gate (`QUAL-01`)
- **Harness:** AndroidX Microbenchmark suite + synthetic test fixtures (synthetic black, pure white, linear gradient, Gaussian noise) + real RAW preview samples (`.RAF`, `.CR3`, `.ARW`, `.NEF`).
- **Pass Gate:** Execution time $<1.8\text{ms}$, 0 B heap allocation delta, 100% classification accuracy.

---

## Canonical References
- `app/src/main/java/com/rvmeur/transferin/data/usb/DirectByteBufferPool.kt` (Off-heap memory manager)
- `.planning/research/STACK.md` (Zero-allocation fixed-point math specifications)
- `.planning/research/ARCHITECTURE.md` (ExposureAnalyzer component architecture)
- `.planning/research/PITFALLS.md` (GC churn & Shannon entropy mitigations)
