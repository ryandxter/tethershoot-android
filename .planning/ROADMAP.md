# Roadmap

## Milestone v2: Fluid Wizard & Agency-Grade UI/UX Overhaul (Active)

### Phase 1: Design System & Motion Foundations (Phase 1)
- **Goal:** Build core design system components in Jetpack Compose matching `rvmeur.com` brand tokens, double-bezel cards, kinetic button physics, and the reusable `FluidDotNavigationBar` component.
- **Requirements:** `FOUND-01`, `FOUND-02`, `FOUND-03`, `DOTBAR-01`, `DOTBAR-02`, `DOTBAR-03`
- **Success Criteria:**
  1. `DoppelrandCard` double-bezel container reusable composable created and tested.
  2. `FluidDotNavigationBar` animates smoothly with `PagerState` drag/swipe offset.
  3. Interactive buttons incorporate trailing icon pills and scale-down haptic physics.

### Phase 2: Fluid Swipeable Setup Wizard (Phase 2)
- **Goal:** Implement the gesture-driven swipeable wizard flow replacing rigid standalone screens for Google Drive Auth, Folder Selection, Camera Connection, and Session Rules.
- **Requirements:** `WIZARD-01`, `WIZARD-02`, `WIZARD-03`, `WIZARD-04`
- **Success Criteria:**
  1. Single `FluidWizardScreen` hosts `HorizontalPager` with 3 steps.
  2. Users can swipe smoothly between steps or tap the dynamic dot navigation bar.
  3. Page validation prevents advancing to tethering before Google Drive auth & folder selection are configured.

### Phase 3: Live Shoot Dashboard & Gallery Polish (Phase 3)
- **Goal:** Overhaul the active tethering session view into a high-end studio dashboard with real-time transfer counters, pulse status indicators, and animated photo stream gallery.
- **Requirements:** `DASHBOARD-01`, `DASHBOARD-02`
- **Success Criteria:**
  1. Active session screen features glassmorphism top bar, live transfer counter badges, and pulse status indicators.
  2. Photo gallery grid displays real-time uploaded images with smooth fade-in transitions and upload status badges.

---

## Previous Milestones (Archived)

### Milestone v1.0: Rebranding & Google Drive Migration (Shipped: 2026-08-12)
- [x] Phase 1: Rebranding & Package Rename
- [x] Phase 2: UI Style Refactoring (rvmeur.com Design)
- [x] Phase 3: Google Drive Integration & NAS Replacement
- Archive: [v1.0-rebranding-ROADMAP.md](file:///Users/chiio/VSCode/TransferInFuji/.planning/milestones/v1.0-rebranding-ROADMAP.md)

### Milestone v2 (Legacy): Camera File Numbering & Traversal Alignment (Shipped: 2026-08-07)
- [x] Phase 6: Camera File Number Baseline Extraction
- [x] Phase 7: Robust RAW+JPG Co-existence & Verification
- [x] Phase 8: Canon and Fuji Connection Polish
