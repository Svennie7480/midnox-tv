package de.midnox.emby.live;

import android.os.Handler;
import android.os.Looper;

/** Nur der UI-Handler – Bilder laedt in midnox Media die Web-Oberflaeche selbst. */
final class Img {
    static final Handler ui = new Handler(Looper.getMainLooper());
}
