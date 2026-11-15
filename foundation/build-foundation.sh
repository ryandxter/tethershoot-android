#!/usr/bin/env bash
# Build manual TransferIn Foundation (Stage 1) TANPA Gradle — Kotlin murni + framework Android.
# Pipeline: K2JVMCompiler -> aapt2 link -> javac (R.java) -> d8 -> packaging -> zipalign -> apksigner.
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

VERSION_FILE="$PROJ/version.txt"
if [ ! -f "$VERSION_FILE" ]; then
  echo "Error: Berkas versi $VERSION_FILE tidak ditemukan!" >&2
  exit 1
fi
V_CODE="$(grep -E '^versionCode=' "$VERSION_FILE" | head -n1 | cut -d'=' -f2 | tr -d ' \r\n')"
V_NAME="$(grep -E '^versionName=' "$VERSION_FILE" | head -n1 | cut -d'=' -f2 | tr -d ' \r\n')"

if [ -z "$V_CODE" ] || [ -z "$V_NAME" ]; then
  echo "Error: versionCode atau versionName tidak terdefinisi di $VERSION_FILE!" >&2
  exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT/dex" "$OUT/gen" "$OUT/r-classes"

# Kumpulkan semua jar vendor
VENDOR_JARS=""
for jar in "$PROJ/vendor/jars"/*.jar; do
  [ -f "$jar" ] || continue
  VENDOR_JARS="${VENDOR_JARS:+${VENDOR_JARS}:}$jar"
done

echo "[1/5] Kompilasi Kotlin -> classes.jar"
java -cp "$KOTLIN_LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -cp "$PLATFORM:$VENDOR_JARS" \
  -include-runtime \
  -no-reflect \
  -d "$OUT/classes.jar" \
  $(find "$PROJ/app/src/main/java" -name '*.kt' | sort)

echo "[2/5] aapt2 compile + link & kompilasi R pustaka"
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
  --java "$OUT/gen" \
  --extra-packages com.google.android.gms.common:com.google.android.gms.base:com.google.firebase:com.google.firebase.messaging \
  --min-sdk-version 31 --target-sdk-version 35 \
  --version-code "$V_CODE" --version-name "$V_NAME" \
  "$OUT/res.zip" \
  ${VENDOR_RES_ZIPS[@]+"${VENDOR_RES_ZIPS[@]}"} \
  -A "$PROJ/app/src/main/assets"

find "$OUT/gen" -name '*.java' > "$OUT/r-sources.txt"
if [ -s "$OUT/r-sources.txt" ]; then
  javac -cp "$PLATFORM" -d "$OUT/r-classes" @"$OUT/r-sources.txt"
  jar --create --file "$OUT/r.jar" -C "$OUT/r-classes" .
fi

echo "[3/5] d8 -> classes.dex"
"$BT/d8" --lib "$PLATFORM" --output "$OUT/dex" "$OUT/classes.jar" "$OUT/r.jar" "$PROJ/vendor/jars"/*.jar

echo "[4/5] Gabungkan META-INF/services + sisipkan ke APK + zipalign + tanda tangan debug"
SERVICES_STAGING="$OUT/staging/META-INF/services"
mkdir -p "$SERVICES_STAGING"
TMP_SERVICES="$OUT/tmp-services"

for jar in "$PROJ/vendor/jars"/*.jar; do
  [ -f "$jar" ] || continue
  rm -rf "$TMP_SERVICES"
  mkdir -p "$TMP_SERVICES"
  unzip -q -o "$jar" "META-INF/services/*" -d "$TMP_SERVICES" 2>/dev/null || true
  if [ -d "$TMP_SERVICES/META-INF/services" ]; then
    for svc_file in "$TMP_SERVICES/META-INF/services"/*; do
      [ -f "$svc_file" ] || continue
      svc_name="$(basename "$svc_file")"
      tr -d '\r' < "$svc_file" >> "$SERVICES_STAGING/$svc_name.tmp"
      echo "" >> "$SERVICES_STAGING/$svc_name.tmp"
    done
  fi
done
rm -rf "$TMP_SERVICES"

for tmp_file in "$SERVICES_STAGING"/*.tmp; do
  [ -f "$tmp_file" ] || continue
  svc_name="$(basename "$tmp_file" .tmp)"
  awk 'NF && !seen[$0]++' "$tmp_file" > "$SERVICES_STAGING/$svc_name"
  rm -f "$tmp_file"
done

(cd "$OUT/dex" && zip -q "$OUT/app-unsigned.apk" classes*.dex)
if [ -d "$OUT/staging/META-INF" ]; then
  (cd "$OUT/staging" && zip -q -r "$OUT/app-unsigned.apk" META-INF)
fi

"$BT/zipalign" -f 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"
if [ ! -f "$PROJ/debug.keystore" ]; then
  keytool -genkeypair -keystore "$PROJ/debug.keystore" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$PROJ/debug.keystore" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/Transfer-v${V_NAME}.apk" "$OUT/app-aligned.apk"

echo "[5/5] Verifikasi"
"$BT/apksigner" verify --print-certs "$OUT/Transfer-v${V_NAME}.apk" | head -n 3
unzip -l "$OUT/Transfer-v${V_NAME}.apk" | grep -E "classes.dex" || true
ls -la "$OUT/Transfer-v${V_NAME}.apk"
echo "BUILD OK: $OUT/Transfer-v${V_NAME}.apk"
