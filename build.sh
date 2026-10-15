#!/usr/bin/env bash
# Сборка APK без Gradle: javac + d8 + aapt2 + zipalign + apksigner.
# Нужны: JDK 17 и Android SDK (platforms;android-34, build-tools;34.0.0).
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
BT_VER="${BT_VER:-34.0.0}"
API="${API:-34}"
BT="$ANDROID_HOME/build-tools/$BT_VER"
ANDROID_JAR="$ANDROID_HOME/platforms/android-$API/android.jar"

for f in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$ANDROID_JAR"; do
  [ -e "$f" ] || { echo "Не найдено: $f (проверь ANDROID_HOME / версии)"; exit 1; }
done
command -v javac >/dev/null || { echo "Нет javac — поставь JDK 17"; exit 1; }

cd "$(dirname "$0")"
rm -rf build && mkdir -p build/obj build/dex

echo "[1/6] aapt2 link (манифест)"
"$BT/aapt2" link -o build/base.apk -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml \
  --min-sdk-version 24 --target-sdk-version "$API" \
  --version-code 1 --version-name 0.1

echo "[2/6] javac"
javac --release 11 -classpath "$ANDROID_JAR" -d build/obj \
  $(find src -name '*.java')

echo "[3/6] d8 (class -> dex)"
"$BT/d8" --lib "$ANDROID_JAR" --min-api 24 --output build/dex \
  $(find build/obj -name '*.class')

echo "[4/6] добавляем classes.dex в APK"
cp build/base.apk build/unaligned.apk
( cd build/dex && zip -q -u ../unaligned.apk classes.dex )

echo "[5/6] zipalign"
"$BT/zipalign" -f -p 4 build/unaligned.apk build/aligned.apk

echo "[6/6] подпись"
KS="${KS:-$HOME/dnevnik-debug.keystore}"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias key -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass android -keypass android \
    -dname "CN=Dnevnik Client, O=Local, C=RU"
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out build/DnevnikClient.apk build/aligned.apk

echo
echo "Готово: $(pwd)/build/DnevnikClient.apk"
