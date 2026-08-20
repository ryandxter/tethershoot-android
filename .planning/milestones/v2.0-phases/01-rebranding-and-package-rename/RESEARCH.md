# Phase 1 Research: Rebranding & Package Rename

## Context & Objective
The goal is to eliminate all references to "Reflection" or "rvmeur.com" in the codebase and rename the application identity and Android package namespace to `com.rvmeur.transferin` (representing "rvmeur.com").

## Codebase Analysis & Occurrences of "Reflection"

1. **Android Package & Namespace declarations:**
   - Folder paths:
     - `app/src/main/java/com/reflection/transferin/` (and all subdirectories)
     - `app/src/test/java/com/reflection/transferin/`
     - `app/src/androidTest/java/com/reflection/transferin/`
   - Every `.kt` file starts with `package com.rvmeur.transferin...` and imports from this package.

2. **Gradle Build Files:**
   - `app/build.gradle.kts` defines:
     - `namespace = "com.rvmeur.transferin"`
     - `applicationId = "com.rvmeur.transferin"`

3. **Android Manifest:**
   - `app/src/main/AndroidManifest.xml` references package authorities and components:
     - FileProvider authority: `com.rvmeur.transferin.fileprovider`
     - Service, receiver and activity declarations.

4. **Room Database Schema:**
   - `app/schemas/com.rvmeur.transferin.data.local.db.TransferInDatabase/` contains schema files named under the package hierarchy.

5. **App Name Strings:**
   - `app/src/main/res/values/strings.xml` defines user-facing names (e.g., `app_name`).

## Migration Strategy

1. **Rename Directories:**
   - Move all directories under `java/com/reflection/` to `java/com/rvmeur/`.
   - Ensure the new structure is `java/com/rvmeur/transferin/`.

2. **Update Package Declarations & Imports:**
   - Replace `package com.rvmeur.transferin` with `package com.rvmeur.transferin` in all files.
   - Replace import statements matching `com.rvmeur.transferin.*` with `com.rvmeur.transferin.*`.

3. **Update Build Configurations:**
   - Update `app/build.gradle.kts` namespace and applicationId.
   - Update schema export path.

4. **Update AndroidManifest.xml:**
   - Update all classes, package declarations, and FileProvider authorities.
