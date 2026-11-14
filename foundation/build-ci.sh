#!/usr/bin/env bash
# ==============================================================================
# TransferIn Foundation — Build CI Pipeline (GitHub Actions runner ubuntu-24.04)
# TANPA Gradle — Kotlin murni + framework Android.
# Pipeline: K2JVMCompiler -> d8 -> aapt2 -> zipalign -> apksigner.
#
# PENTING: Skrip ini adalah cermin CI dari foundation/build-foundation.sh lokal.
# Keduanya harus dijaga tetap setara (flags kompilasi, tool arguments, urutan langkah).
# ==============================================================================
set -euo pipefail

# Deteksi direktori root repositori dan path foundation
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

PROJ="$REPO_ROOT/foundation"
RUNNER_TEMP="${RUNNER_TEMP:-/tmp}"
OUT="$RUNNER_TEMP/tether-build"

rm -rf "$OUT"
mkdir -p "$OUT/dex"

# 1. Java runtime (JDK 21 dari setup-java di runner CI)
if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

# 2. Kotlin Compiler 2.2.10 (unduh dari GitHub bila belum ada di $RUNNER_TEMP/kotlinc)
KOTLINC_DIR="$RUNNER_TEMP/kotlinc"
if [ ! -d "$KOTLINC_DIR/lib" ]; then
  echo "Mengunduh kotlinc 2.2.10 ke $RUNNER_TEMP..."
  curl -sSL "https://github.com/JetBrains/kotlin/releases/download/v2.2.10/kotlin-compiler-2.2.10.zip" -o "$RUNNER_TEMP/kotlinc.zip"
  unzip -q -o "$RUNNER_TEMP/kotlinc.zip" -d "$RUNNER_TEMP"
  rm -f "$RUNNER_TEMP/kotlinc.zip"
fi

if [ -d "$RUNNER_TEMP/kotlinc/lib" ]; then
  KOTLIN_LIB="$RUNNER_TEMP/kotlinc/lib"
elif [ -d "$KOTLINC_DIR/lib" ]; then
  KOTLIN_LIB="$KOTLINC_DIR/lib"
else
  echo "Error: Library kotlinc tidak ditemukan di $RUNNER_TEMP/kotlinc/lib!" >&2
  exit 1
fi

# 3. Android SDK dari ANDROID_HOME bawaan runner
SDK="${ANDROID_HOME:-/usr/local/lib/android/sdk}"
PLATFORM="$SDK/platforms/android-35/android.jar"
BT="$SDK/build-tools/35.0.0"

if [ ! -f "$PLATFORM" ] || [ ! -x "$BT/d8" ] || [ ! -x "$BT/aapt2" ] || [ ! -x "$BT/zipalign" ] || [ ! -x "$BT/apksigner" ]; then
  echo "Memasang platforms;android-35 dan build-tools;35.0.0 lewat sdkmanager..."
  SDKMANAGER=""
  if [ -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
    SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
  elif command -v sdkmanager >/dev/null 2>&1; then
    SDKMANAGER="sdkmanager"
  fi

  if [ -n "$SDKMANAGER" ]; then
    yes | "$SDKMANAGER" --sdk_root="$SDK" "platforms;android-35" "build-tools;35.0.0" >/dev/null 2>&1 || true
  else
    echo "Peringatan: sdkmanager tidak ditemukan, melanjutkan dengan toolchain yang ada..." >&2
  fi
fi

if [ ! -f "$PLATFORM" ]; then
  echo "Error: android.jar tidak ditemukan di $PLATFORM" >&2
  exit 1
fi

# 4. Keystore penandatangan produksi WAJIB
KEYSTORE="${KEYSTORE_PATH:-}"
if [ -z "$KEYSTORE" ] || [ ! -f "$KEYSTORE" ] || [ ! -s "$KEYSTORE" ]; then
  echo "Error: KEYSTORE_PATH wajib diset, berkas harus ada dan tidak boleh kosong (ukuran > 0)!" >&2
  exit 1
fi

# 5. Resolusi Versi (foundation/version.txt, env VERSION_CODE/VERSION_NAME menimpa bila terisi)
VERSION_FILE="$PROJ/version.txt"
FILE_CODE=""
FILE_NAME=""
if [ -f "$VERSION_FILE" ]; then
  FILE_CODE="$(grep -E '^versionCode=' "$VERSION_FILE" | head -n1 | cut -d'=' -f2 | tr -d ' \r\n')"
  FILE_NAME="$(grep -E '^versionName=' "$VERSION_FILE" | head -n1 | cut -d'=' -f2 | tr -d ' \r\n')"
fi

V_CODE="${VERSION_CODE:-$FILE_CODE}"
V_NAME="${VERSION_NAME:-$FILE_NAME}"

if [ -z "$V_CODE" ] || [ -z "$V_NAME" ]; then
  echo "Error: versionCode atau versionName tidak terdefinisi di $VERSION_FILE atau env." >&2
  exit 1
fi

echo "==> Memulai Build TransferIn Foundation v${V_NAME} (code: ${V_CODE})"

# Kumpulkan semua jar vendor (PERSIS build-foundation.sh)
VENDOR_JARS=""
for jar in "$PROJ/vendor/jars"/*.jar; do
  [ -f "$jar" ] || continue
  VENDOR_JARS="${VENDOR_JARS:+${VENDOR_JARS}:}$jar"
done

# [1/5] Kompilasi Kotlin -> classes.jar (PERSIS argumen build-foundation.sh)
echo "[1/5] Kompilasi Kotlin -> classes.jar"
java -cp "$KOTLIN_LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -cp "$PLATFORM:$VENDOR_JARS" \
  -include-runtime \
  -no-reflect \
  -d "$OUT/classes.jar" \
  $(find "$PROJ/app/src/main/java" -name '*.kt' | sort)

# [2/5] d8 -> classes.dex (PERSIS argumen build-foundation.sh)
echo "[2/5] d8 -> classes.dex"
"$BT/d8" --lib "$PLATFORM" --output "$OUT/dex" "$OUT/classes.jar" "$PROJ/vendor/jars"/*.jar

# [3/5] aapt2 compile + link (PERSIS argumen build-foundation.sh + manifest injection)
echo "[3/5] aapt2 compile + link"
sed 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.rvmeur.transfer">|' \
  "$PROJ/app/src/main/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
"$BT/aapt2" compile --dir "$PROJ/app/src/main/res" -o "$OUT/res.zip"

VENDOR_RES_ZIPS=()
if [ -d "$PROJ/vendor/res" ]; then
  for res_dir in "$PROJ/vendor/res"/*; do
    [ -d "$res_dir" ] || continue
    if [ -n "$(find "$res_dir" -type f -print -quit 2>/dev/null)" ]; then
      res_name="$(basename "$res_dir")"
      zip_out="$OUT/vendor-res-$res_name.zip"
      "$BT/aapt2" compile --dir "$res_dir" -o "$zip_out"
      VENDOR_RES_ZIPS+=("$zip_out")
    fi
  done
fi

"$BT/aapt2" link -o "$OUT/app-unsigned.apk" \
  -I "$PLATFORM" \
  --manifest "$OUT/AndroidManifest.xml" \
  --min-sdk-version 31 --target-sdk-version 35 \
  --version-code "$V_CODE" --version-name "$V_NAME" \
  "$OUT/res.zip" \
  ${VENDOR_RES_ZIPS[@]+"${VENDOR_RES_ZIPS[@]}"} \
  -A "$PROJ/app/src/main/assets"

# [4/5] Sisipkan classes.dex + zipalign + tanda tangan (PERSIS argumen build-foundation.sh)
echo "[4/5] Sisipkan classes.dex + zipalign + tanda tangan"
(cd "$OUT/dex" && zip -q "$OUT/app-unsigned.apk" classes*.dex)
"$BT/zipalign" -f 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/Transfer-v${V_NAME}.apk" "$OUT/app-aligned.apk"

# [5/5] Verifikasi penandatanganan dan salin keluaran ke dist/
echo "[5/5] Verifikasi"
DIST_DIR="$REPO_ROOT/dist"
mkdir -p "$DIST_DIR"
cp "$OUT/Transfer-v${V_NAME}.apk" "$DIST_DIR/Transfer-v${V_NAME}.apk"

APK_SHA256="$(sha256sum "$DIST_DIR/Transfer-v${V_NAME}.apk" | awk '{print $1}')"

cat <<EOF > "$DIST_DIR/version.json"
{
  "versionCode": $V_CODE,
  "versionName": "$V_NAME",
  "apkFile": "Transfer-v${V_NAME}.apk",
  "apkSha256": "$APK_SHA256"
}
EOF

"$BT/apksigner" verify --print-certs "$DIST_DIR/Transfer-v${V_NAME}.apk"
unzip -l "$DIST_DIR/Transfer-v${V_NAME}.apk" | grep -E "classes.dex" || true
ls -la "$DIST_DIR/Transfer-v${V_NAME}.apk"
echo "BUILD OK: $DIST_DIR/Transfer-v${V_NAME}.apk"
cat "$DIST_DIR/version.json"
