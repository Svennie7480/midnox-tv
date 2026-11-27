#!/bin/sh
# Baut midnox-media-auto.apk (BYD/DiLink: targetSdk 33, eigener Paketname de.midnox.emby.auto, parallel zur normalen App installierbar)
set -e
rm -rf /tmp/emby-auto && mkdir /tmp/emby-auto && tar --exclude=build --exclude='*.apk' -cf - . | tar -C /tmp/emby-auto -xf -
cd /tmp/emby-auto
sed -i 's/package="de.midnox.emby" android:versionCode="[0-9]*" android:versionName="[^"]*"/package="de.midnox.emby.auto" android:versionCode="'${AUTO_CODE:-1}'" android:versionName="'${AUTO_NAME:-1.0}'"/;s/targetSdkVersion="35"/targetSdkVersion="33"/;s/android:label="midnox Media"/android:label="midnox Media Auto"/;s/de.midnox.emby.apk/de.midnox.emby.auto.apk/;s/de.midnox.permission.SHARE/de.midnox.auto.permission.SHARE/g' AndroidManifest.xml
sed -i 's/de.midnox.emby.apk/de.midnox.emby.auto.apk/' src/de/midnox/emby/UpdateProvider.java
sed -i 's#\$B/aapt2 link -o build/base.apk#$B/aapt2 link --custom-package de.midnox.emby -o build/base.apk#;s/midnox-emby.apk/midnox-media-auto.apk/g' build.sh
sh build.sh
cp build/midnox-media-auto.apk /root/midnox-emby/build-auto.apk
