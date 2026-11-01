#!/bin/sh
# Baut midnox-tv.apk (benoetigt Android SDK: platforms;android-28, build-tools;35.0.0)
set -e
B=${ANDROID_HOME:-$HOME/sdk}/build-tools/35.0.0; J=${ANDROID_HOME:-$HOME/sdk}/platforms/android-28/android.jar
rm -rf build && mkdir -p build/res build/classes
$B/aapt2 compile --dir res -o build/res/
$B/aapt2 link -o build/base.apk -I $J --manifest AndroidManifest.xml -A assets build/res/*.flat --java build/gen
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath $J -classpath $J -d build/classes $(find src build/gen -name '*.java')
(cd build/classes && jar cf ../classes.jar .)
$B/d8 --min-api 22 --lib $J --output build build/classes.jar
cp build/base.apk build/unsigned.apk && (cd build && zip -q -j unsigned.apk classes.dex)
$B/zipalign -f 4 build/unsigned.apk build/aligned.apk
$B/apksigner sign --ks midnox.jks --ks-pass pass:midnox123 --out build/midnox-radio.apk build/aligned.apk
echo "fertig: build/midnox-radio.apk"
