#!/usr/bin/env bash
# Build manual FCM Probe (Package: com.rvmeur.fcmprobe)
# Meniru pola build-spike.sh dan build-foundation.sh (kotlinc -> aapt2 link -> javac R -> d8 -> zipalign -> apksigner)
# Output: fcm-probe/FcmProbe-v0.1.0.apk
set -euo pipefail

PROJ="$(cd "$(dirname "$0")" && pwd)"
FOUNDATION="$(cd "$PROJ/../foundation" && pwd)"
TOOLS="$HOME/workspace/tools"
export JAVA_HOME="$TOOLS/android-toolchain/jdk21"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="$TOOLS/android-toolchain/android-sdk"
PLATFORM="$SDK/platforms/android-35/android.jar"
BT="$SDK/build-tools/35.0.0"
KOTLIN_LIB="$TOOLS/kotlinc/lib"
OUT="$PROJ/build-manual"
APK_OUT="$PROJ/FcmProbe-v0.1.0.apk"

echo "=== MEMULAI BUILD FCM PROBE ==="

if [ ! -f "$PLATFORM" ]; then
  echo "Error: android.jar platform tidak ditemukan di $PLATFORM!" >&2
  exit 1
fi

if [ ! -d "$FOUNDATION/vendor/jars" ]; then
  echo "Error: Direktori vendor jars tidak ditemukan di $FOUNDATION/vendor/jars!" >&2
  exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT/dex" "$OUT/gen" "$OUT/r-classes"

# Kumpulkan semua jar vendor dari ../foundation/vendor/jars
VENDOR_JARS=""
for jar in "$FOUNDATION/vendor/jars"/*.jar; do
  [ -f "$jar" ] || continue
  VENDOR_JARS="${VENDOR_JARS:+${VENDOR_JARS}:}$jar"
done

if [ -z "$VENDOR_JARS" ]; then
  echo "Error: Tidak ada berkas jar vendor di $FOUNDATION/vendor/jars!" >&2
  exit 1
fi

echo "[1/5] Kompilasi Kotlin -> classes.jar"
java -cp "$KOTLIN_LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -cp "$PLATFORM:$VENDOR_JARS" \
  -include-runtime \
  -no-reflect \
  -d "$OUT/classes.jar" \
  $(find "$PROJ/app/src/main/java" -name '*.kt' | sort)

echo "[2/5] aapt2 compile + link & kompilasi R pustaka"
cp "$PROJ/app/src/main/AndroidManifest.xml" "$OUT/AndroidManifest.xml"
"$BT/aapt2" compile --dir "$PROJ/app/src/main/res" -o "$OUT/res.zip"

VENDOR_RES_ZIPS=()
if [ -d "$FOUNDATION/vendor/res" ]; then
  for res_dir in "$FOUNDATION/vendor/res"/*; do
    [ -d "$res_dir" ] || continue
    if [ -n "$(find "$res_dir" -type f -print -quit 2>/dev/null)" ]; then
      res_name="$(basename "$res_dir")"
      zip_out="$OUT/vendor-res-$res_name.zip"
      "$BT/aapt2" compile --dir "$res_dir" -o "$zip_out"
      VENDOR_RES_ZIPS+=("$zip_out")
    fi
  done
fi

ASSETS_ARG=()
if [ -d "$PROJ/app/src/main/assets" ]; then
  ASSETS_ARG=(-A "$PROJ/app/src/main/assets")
fi

"$BT/aapt2" link -o "$OUT/app-unsigned.apk" \
  -I "$PLATFORM" \
  --manifest "$OUT/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --extra-packages com.google.android.gms.common:com.google.android.gms.base:com.google.firebase:com.google.firebase.messaging \
  --min-sdk-version 31 --target-sdk-version 35 \
  --version-code 1 --version-name "0.1.0" \
  "$OUT/res.zip" \
  ${VENDOR_RES_ZIPS[@]+"${VENDOR_RES_ZIPS[@]}"} \
  ${ASSETS_ARG[@]+"${ASSETS_ARG[@]}"}

find "$OUT/gen" -name '*.java' > "$OUT/r-sources.txt"
if [ -s "$OUT/r-sources.txt" ]; then
  javac -cp "$PLATFORM" -d "$OUT/r-classes" @"$OUT/r-sources.txt"
  jar --create --file "$OUT/r.jar" -C "$OUT/r-classes" .
fi

echo "[3/5] d8 -> classes.dex"
D8_INPUTS=("$OUT/classes.jar")
if [ -f "$OUT/r.jar" ]; then
  D8_INPUTS+=("$OUT/r.jar")
fi
for jar in "$FOUNDATION/vendor/jars"/*.jar; do
  [ -f "$jar" ] || continue
  D8_INPUTS+=("$jar")
done

"$BT/d8" --lib "$PLATFORM" --output "$OUT/dex" "${D8_INPUTS[@]}"

echo "[4/5] Gabungkan META-INF/services + sisipkan ke APK + zipalign + tanda tangan debug"
SERVICES_STAGING="$OUT/staging/META-INF/services"
mkdir -p "$SERVICES_STAGING"
TMP_SERVICES="$OUT/tmp-services"

for jar in "$FOUNDATION/vendor/jars"/*.jar; do
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

KEYSTORE="$FOUNDATION/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
  KEYSTORE="$PROJ/debug.keystore"
  if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
      -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
      -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
  fi
fi

"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$APK_OUT" "$OUT/app-aligned.apk"

echo "[5/5] Verifikasi"
"$BT/apksigner" verify --print-certs "$APK_OUT" | head -n 3
unzip -l "$APK_OUT" | grep -E "classes.*\.dex" || true
ls -la "$APK_OUT"
echo "BUILD OK: $APK_OUT"
