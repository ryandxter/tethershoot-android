# FCM Probe (com.rvmeur.fcmprobe)

Aplikasi probe diagnostik mandiri sekali pakai untuk membuktikan hipotesis akar masalah kegagalan inisialisasi Firebase Cloud Messaging (FCM) pada build manual `@rvmeur Tether` (gejala: `NullPointerException: Firebase Messaging component is not present`).

## Tujuan Probe

1. Menguji apakah `FirebaseApp` berhasil diinisialisasi melalui `FirebaseInitProvider`.
2. Melakukan dump refleksi pada container `ComponentRuntime` sebelum pemanggilan apapun untuk melihat daftar komponen yang sudah terdaftar.
3. Mencoba memanggil `FirebaseMessaging.getInstance()` dan `FirebaseInstallations.getInstance()` untuk merekam status awal.
4. Memanggil `discoverComponents()` via interface `ComponentLoader` (`FirebaseApp.getInstance().get(ComponentLoader::class.java)`).
5. Menguji kembali `FirebaseMessaging.getInstance()` dan melakukan dump container #2 setelah `discoverComponents()` dipanggil untuk membuktikan apakah `com.google.firebase.messaging.FirebaseMessaging` berhasil terdaftar secara dinamis.

## Cara Build

Jalankan script build manual dari folder root repositori atau dari dalam folder `fcm-probe/`:

```bash
cd fcm-probe
bash build-probe.sh
```

Toolchain yang digunakan:
- Kotlin Compiler (K2JVMCompiler)
- Android Build Tools 35.0.0 (`aapt2`, `d8`, `zipalign`, `apksigner`)
- JDK 21
- Vendor jar & res dari `../foundation/vendor/`
- Keystore debug dari `../foundation/debug.keystore`

Hasil build berupa berkas APK mandiri:
`fcm-probe/FcmProbe-v0.1.0.apk`

## Cara Instal dan Menjalankan

Pasang APK ke perangkat Android melalui ADB:

```bash
adb install -r FcmProbe-v0.1.0.apk
adb shell am start -n com.rvmeur.fcmprobe/.MainActivity
```

## Cara Membaca probe-log.txt

Setiap langkah dicatat ke **Logcat** dengan tag dan awalan `[PROBE]` serta ke berkas internal aplikasi di storage:

1. **Via Logcat:**
   ```bash
   adb logcat -s PROBE
   ```

2. **Via Berkas Log (`probe-log.txt`):**
   Berkas log disimpan di `getExternalFilesDir(null)` (misalnya `/sdcard/Android/data/com.rvmeur.fcmprobe/files/probe-log.txt`).
   Tarik berkas log menggunakan adb:
   ```bash
   adb pull /sdcard/Android/data/com.rvmeur.fcmprobe/files/probe-log.txt .
   cat probe-log.txt
   ```
