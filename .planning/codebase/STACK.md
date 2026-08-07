# Technology Stack

**Analysis Date:** 2026-08-07

## Languages

**Primary:**
- Kotlin 2.2.10 - Android application development

**Secondary:**
- XML - Android manifest, resources (USB device filters, file provider paths, backup rules)

## Runtime

**Environment:**
- Android SDK 37 (target and compile)
- Min SDK 31 (Android 12)

**Build Tool:**
- Gradle 9.4.1 (via Gradle Wrapper)
- Android Gradle Plugin (AGP) 9.2.1
- Kotlin Symbol Processing (KSP)

## Frameworks

**Core UI:**
- Jetpack Compose (Compose BOM) - Modern declarative UI toolkit
  - Activity Compose (`androidx.activity:activity-compose`)
  - Compose UI, UI Graphics, Tooling Preview
  - Material 3 (`androidx.compose.material3`)
  - Material Icons (Core & Extended)

**Navigation:**
- Navigation Compose (`androidx.navigation:navigation-compose:2.9.8`)

**Database / Persistence:**
- Room 2.8.4 - Local SQLite persistence
  - Room Runtime (`androidx.room:room-runtime`)
  - Room KTX (`androidx.room:room-ktx`)
  - Room Compiler via KSP (`androidx.room:room-compiler`)

**Background Processing:**
- WorkManager 2.11.2 (`androidx.work:work-runtime-ktx`) - background upload task scheduling

**Image Loading:**
- Coil 2.6.0 (`io.coil-kt:coil-compose`) - Async image loading for media library / gallery

## Key Dependencies

**Core Android & Lifecycle:**
- `androidx.core:core-ktx` - Kotlin extensions
- `androidx.lifecycle:lifecycle-runtime-ktx` - Lifecycle scope and coroutines integrations
- `androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0` - Lifecycle-aware view models for Compose
- `androidx.documentfile:documentfile:1.1.0` - Document tree access (SAF)

**Network & File Transfer:**
- JSch 0.2.26 (`com.github.mwiede:jsch`) - Java Secure Channel for SFTP uploads to NAS/servers
- OkHttp 4.12.0 (`com.squareup.okhttp3:okhttp`) - HTTP client

## Configuration

**Build Configuration:**
- `app/build.gradle.kts` - App module build configuration
- `build.gradle.kts` - Root project build configuration
- `settings.gradle.kts` - Repository and plugin management
- `gradle.properties` - JVM options and configurations

**Android Manifest & Permissions:**
- `android.permission.INTERNET` - Network access for SFTP/WebDAV
- `android.permission.FOREGROUND_SERVICE` - Foreground service for active session
- `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE` - Foreground service for connected USB devices
- `android.permission.CHANGE_NETWORK_STATE` / `android.permission.CHANGE_WIFI_STATE` - Wi-Fi/Tethering state control
- USB Host feature (`android.hardware.usb.host`)

**Resources:**
- USB device filter: `app/src/main/res/xml/device_filter.xml`
- File provider paths: `app/src/main/res/xml/file_paths.xml`
- Backup rules: `app/src/main/res/xml/backup_rules.xml`

## Platform Requirements

**Development:**
- Android Studio (Koala/Ladybug or later)
- JDK 17+ (required for Gradle and Android SDK 37)
- Gradle 9.4.1 (via wrapper)

**Production:**
- Android 12+ (API 31+)
- USB OTG / USB Host support on the device
- Compatible Camera supporting MTP/PTP protocols (with specific vendor filter support e.g. Canon)

---

*Stack analysis: 2026-08-07*
