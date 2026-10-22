package com.ozdogan.tvanaekran;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/** Ana ekranda gosterilen tek bir uygulama. */
final class AppEntry {
    String pkg;
    String label;
    Intent launch;
    Drawable banner; // Android TV banner'i (yoksa null)
    Drawable icon;
    int color;       // Kart ve arka plan icin baskin renk
    boolean hidden;
}
