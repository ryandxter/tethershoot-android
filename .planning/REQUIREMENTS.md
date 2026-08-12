# Requirements — Milestone v2 (Fluid Wizard & Agency-Grade UI/UX Overhaul)

**Analysis Date:** 2026-08-12

## Milestone v2 Active Requirements

### 1. Design System & Micro-Interaction Foundations (FOUNDATION)
- [ ] **FOUND-01**: Implement Double-Bezel (`Doppelrand`) container components in Jetpack Compose, featuring nested outer shell, hairline border (`#E8E0D8`), concentric rounded corners, and inner core surface (`#F6F3EB`).
- [ ] **FOUND-02**: Implement tactile button physics with scale interpolation (`0.97f` on press) and nested icon trailing pill wrappers (`w-8 h-8 rounded-full`).
- [ ] **FOUND-03**: Standardize brand typography mapping (lowercase eyebrow tags, Cormorant Garamond display headers, DM Sans body text, Roboto Mono numbers).

### 2. Fluid Dynamic Dot Bar Navigation (DOTBAR)
- [ ] **DOTBAR-01**: Create a custom `FluidDotNavigationBar` Jetpack Compose component with dynamic animated width, morphing pill background, and active/inactive dot sizing linked to `PagerState`.
- [ ] **DOTBAR-02**: Support dot tap gestures for quick page jumping with smooth spring physics (`Spring.StiffnessLow`).
- [ ] **DOTBAR-03**: Display step progress badges and validation state indicators (e.g. green checkmark pulse `#2D4636`, error alert `#7A1A1A`).

### 3. Fluid Swipeable Setup Wizard (WIZARD)
- [ ] **WIZARD-01**: Refactor setup screens into a unified `HorizontalPager` swipeable wizard container with user scroll enable/disable control based on step validation.
- [ ] **WIZARD-02**: Page 1 (Google Drive Auth & Target Folder): Redesign OAuth login button and folder picker into double-bezel cards with real-time status pills.
- [ ] **WIZARD-03**: Page 2 (Camera Tethering & Subfolder Detection): Redesign camera USB/MTP detection screen with animated pulse connection status and DCIM folder hierarchy preview.
- [ ] **WIZARD-04**: Page 3 (Session Preferences): Redesign RAW+JPG rules, auto-sync toggles, and starting file number baseline configuration.

### 4. Active Tethering Dashboard Overhaul (DASHBOARD)
- [ ] **DASHBOARD-01**: Redesign active session screen into an agency-grade live shoot dashboard with glassmorphism header, animated transfer counters, and haptic feedback on new shot capture.
- [ ] **DASHBOARD-02**: Refactor photo stream gallery grid with smooth image loading transitions, upload status overlay badges, and expandable photo inspector.

## Traceability Matrix
- **FOUND-01, FOUND-02, FOUND-03**: Phase 1 (Design System & Motion Foundations)
- **DOTBAR-01, DOTBAR-02, DOTBAR-03**: Phase 1 (Design System & Motion Foundations)
- **WIZARD-01, WIZARD-02, WIZARD-03, WIZARD-04**: Phase 2 (Fluid Swipeable Setup Wizard)
- **DASHBOARD-01, DASHBOARD-02**: Phase 3 (Live Shoot Dashboard & Gallery Polish)
