#!/usr/bin/env bash
# Compile le prototype natif en APK, sans Gradle (mêmes outils que launcher/android/build.sh).
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-30/android.jar"
OUT=build
JDK="${JAVA_HOME:-$HOME/Android/jdk}"
export PATH="$JDK/bin:$PATH"

rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/assets/photos/mini" "$OUT/assets/fonts"
cp ../launcher/photos/*.jpg "$OUT/assets/photos/" && cp ../launcher/photos/mini/*.jpg "$OUT/assets/photos/mini/" && cp ../launcher/fonts/outfit.woff2 "$OUT/assets/fonts/"

"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
# -0 jpg : les photos sont déjà compressées, les stocker telles quelles accélère leur lecture
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -A "$OUT/assets" -0 jpg \
  --java "$OUT/gen" -o "$OUT/unsigned.apk" "$OUT/res.zip"

javac -source 8 -target 8 -nowarn -Xlint:-options -classpath "$JAR" -d "$OUT/classes" $(find src "$OUT/gen" -name '*.java')
(cd "$OUT/classes" && jar cf ../classes.jar .)
"$BT/d8" --min-api 26 --lib "$JAR" --output "$OUT" "$OUT/classes.jar"
(cd "$OUT" && zip -q unsigned.apk classes.dex)

"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
# Même clé que l'accueil web (launcher/android/debug.keystore)
"$BT/apksigner" sign --ks ../launcher/android/debug.keystore --ks-pass pass:android \
  --out AccueilNatif.apk "$OUT/aligned.apk"
echo "OK -> $(pwd)/AccueilNatif.apk"
