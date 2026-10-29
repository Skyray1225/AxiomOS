#!/usr/bin/env bash
# Compile l'écran d'accueil en APK, sans Gradle.
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-30/android.jar"
OUT=build
JDK="${JAVA_HOME:-$HOME/Android/jdk}"
export PATH="$JDK/bin:$PATH"

rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/assets"
cp ../index.html "$OUT/assets/" && cp -r ../fonts ../photos "$OUT/assets/"

"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -A "$OUT/assets" \
  -o "$OUT/unsigned.apk" "$OUT/res.zip"

javac -source 8 -target 8 -nowarn -Xlint:-options -classpath "$JAR" -d "$OUT/classes" $(find src -name '*.java')
(cd "$OUT/classes" && jar cf ../classes.jar .)
"$BT/d8" --min-api 26 --lib "$JAR" --output "$OUT" "$OUT/classes.jar"
(cd "$OUT" && zip -q unsigned.apk classes.dex)

"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
KEY=debug.keystore
[ -f "$KEY" ] || keytool -genkeypair -keystore "$KEY" -storepass android -keypass android \
  -alias accueil -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Accueil TV" >/dev/null 2>&1
"$BT/apksigner" sign --ks "$KEY" --ks-pass pass:android --out Accueil.apk "$OUT/aligned.apk"
echo "OK -> $(pwd)/Accueil.apk"
