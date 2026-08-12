# Phase 2 Plan: UI Style Refactoring (rvmeur.com Design)

## Tasks

- [ ] Task 1: Refactor theme color tokens in `Color.kt`
  - Update `CreamBackground` to `#FAF8F3`.
  - Update `CardSurface` to `#F6F3EB` (soft warm card panel).
  - Update `SurfaceVariant` to `#F0EBE0`.
  - Update `TextPrimary` to `#26170C` (deep warm dark brown).
  - Update `BorderSoft` to `#E8E0D8` and `BorderMedium` to `#D2C4BC`.
  - Update `AccentGold` and status color accents to map to `AccentGreen` (`#2D4636`) and `AccentOlive` (`#56642B`).

- [ ] Task 2: Refactor theme mapping in `Theme.kt`
  - Ensure `MaterialTheme` maps primary accent color to `AccentGreen` (`#2D4636`) instead of gold.

- [ ] Task 3: Refactor typography styles in `Type.kt`
  - Update display, headline, and title styles to use `FontFamily.Serif`.
  - Update body and label styles to use `FontFamily.SansSerif`.

- [ ] Task 4: Verify UI compilation
  - Run `./gradlew compileDebugKotlin` to ensure no theme resource references are broken.
