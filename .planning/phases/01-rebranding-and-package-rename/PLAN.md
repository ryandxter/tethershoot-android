# Phase 1 Plan: Rebranding & Package Rename

## Tasks

- [ ] Task 1: Rename package directories to `com/rvmeur/transferin`
  - Move `app/src/main/java/com/reflection/transferin` to `app/src/main/java/com/rvmeur/transferin`
  - Move `app/src/test/java/com/reflection/transferin` to `app/src/test/java/com/rvmeur/transferin`
  - Move `app/src/androidTest/java/com/reflection/transferin` to `app/src/androidTest/java/com/rvmeur/transferin`
  - Move schema directories `app/schemas/com.rvmeur.transferin...` to `app/schemas/com.rvmeur.transferin...`

- [ ] Task 2: Replace package declarations and imports in Kotlin files
  - Update all `.kt` source files under `src/main`, `src/test`, and `src/androidTest`.
  - Replace `com.rvmeur.transferin` with `com.rvmeur.transferin`.

- [ ] Task 3: Update build files and AndroidManifest.xml
  - Modify `app/build.gradle.kts` namespace and applicationId.
  - Modify `app/src/main/AndroidManifest.xml` components and authorities.

- [ ] Task 4: Rebrand app identity strings
  - Modify `app/src/main/res/values/strings.xml` to rename the application name to "rvmeur.com" or "RVM Transfer".

- [ ] Task 5: Compilation Verification
  - Run `./gradlew compileDebugKotlin` to verify the codebase successfully builds under the new namespace.
