#!/bin/sh
# Baut midnox-live.apk (benoetigt Android SDK: platforms;android-28, build-tools;35.0.0; Media3-Jars in libs/)
set -e
B=${ANDROID_HOME:-$HOME/sdk}/build-tools/35.0.0; J=${ANDROID_HOME:-$HOME/sdk}/platforms/android-28/android.jar
L=$(ls libs/*.jar | tr '\n' ':')
rm -rf build && mkdir -p build/res build/classes build/dex
$B/aapt2 compile --dir res -o build/res/
$B/aapt2 link -o build/base.apk -I $J --manifest AndroidManifest.xml -A assets build/res/*.flat --java build/gen
javac -encoding UTF-8 -source 8 -target 8 -nowarn -bootclasspath $J -classpath $J:$L -d build/classes $(find src build/gen -name '*.java') 2>&1 | grep -v "^Note:\|^warning: \[options\]\|^1 warning\|^Picked up" || true
[ -f build/classes/de/midnox/live/PlayerActivity.class ] || { echo "Kompilieren fehlgeschlagen"; exit 1; }
(cd build/classes && jar cf ../classes.jar .)
$B/d8 --release --min-api 22 --lib $J --output build/dex build/classes.jar libs/*.jar 2>&1 | grep -v "^Warning\|Missing class\|^  " | head -20 || true
cp build/base.apk build/unsigned.apk && (cd build/dex && zip -q -j ../unsigned.apk classes*.dex)
$B/zipalign -f 4 build/unsigned.apk build/aligned.apk
$B/apksigner sign --ks midnox.jks --ks-pass pass:midnox123 --out build/midnox-live.apk build/aligned.apk
echo "fertig: build/midnox-live.apk ($(du -h build/midnox-live.apk | cut -f1))"
