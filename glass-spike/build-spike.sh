#!/usr/bin/env bash
# Build manual TransferIn Foundation (Stage 1) TANPA Gradle — Kotlin murni + framework Android.
# Pipeline: K2JVMCompiler -> d8 -> aapt2 -> zipalign -> apksigner.
set -euo pipefail

PROJ="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$HOME/workspace/tools"
export JAVA_HOME="$TOOLS/android-toolchain/jdk21"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="$TOOLS/android-toolchain/android-sdk"
PLATFORM="$SDK/platforms/android-35/android.jar"
BT="$SDK/build-tools/35.0.0"
KOTLIN_LIB="$TOOLS/kotlinc/lib"
OUT="$PROJ/build-manual"

rm -rf "$OUT"
mkdir -p "$OUT/dex"

echo "[1/5] Kompilasi Kotlin -> classes.jar"
java -cp "$KOTLIN_LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -cp "$PLATFORM" \
  -include-runtime \
  -no-reflect \
  -d "$OUT/classes.jar" \
  $(find "$PROJ/app/src/main/java" -name '*.kt' | sort)

echo "[2/5] d8 -> classes.dex"
"$BT/d8" --lib "$PLATFORM" --output "$OUT/dex" "$OUT/classes.jar"

echo "[3/5] aapt2 compile + link"
sed 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.rvmeur.glassspike">|' \
  "$PROJ/app/src/main/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
"$BT/aapt2" compile --dir "$PROJ/app/src/main/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/app-unsigned.apk" \
  -I "$PLATFORM" \
  --manifest "$OUT/AndroidManifest.xml" \
  --min-sdk-version 31 --target-sdk-version 35 \
  --version-code 1 --version-name "0.1.0" \
  "$OUT/res.zip" \
  -A "$PROJ/app/src/main/assets"

echo "[4/5] Sisipkan classes.dex + zipalign + tanda tangan debug"
(cd "$OUT/dex" && zip -q "$OUT/app-unsigned.apk" classes.dex)
"$BT/zipalign" -f 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"
if [ ! -f "$PROJ/debug.keystore" ]; then
  keytool -genkeypair -keystore "$PROJ/debug.keystore" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$PROJ/debug.keystore" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/GlassSpike-v0.1.0.apk" "$OUT/app-aligned.apk"

echo "[5/5] Verifikasi"
"$BT/apksigner" verify --print-certs "$OUT/GlassSpike-v0.1.0.apk" | head -n 3
unzip -l "$OUT/GlassSpike-v0.1.0.apk" | grep -E "classes.dex" || true
ls -la "$OUT/GlassSpike-v0.1.0.apk"
echo "BUILD OK: $OUT/GlassSpike-v0.1.0.apk"
