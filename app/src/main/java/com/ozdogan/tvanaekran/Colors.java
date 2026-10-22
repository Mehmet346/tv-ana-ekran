package com.ozdogan.tvanaekran;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

final class Colors {
    static final int BASE = 0xFF0E1230;
    private static final int FALLBACK = 0xFF3B4A8C;

    private Colors() {}

    /** Ikon/banner'in en doygun renklerinin ortalamasi. 24x24'e kucultup bakar, bellek harcamaz. */
    static int dominant(Drawable d) {
        if (d == null) return FALLBACK;
        Bitmap bmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, 24, 24);
        d.draw(c);
        float[] hsv = new float[3];
        long r = 0, g = 0, b = 0, w = 0;
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                int p = bmp.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                Color.colorToHSV(p, hsv);
                if (hsv[1] < 0.35f || hsv[2] < 0.25f) continue;
                int weight = (int) (hsv[1] * hsv[2] * 100);
                r += Color.red(p) * weight;
                g += Color.green(p) * weight;
                b += Color.blue(p) * weight;
                w += weight;
            }
        }
        bmp.recycle();
        if (w == 0) return FALLBACK;
        return Color.rgb((int) (r / w), (int) (g / w), (int) (b / w));
    }

    /** a ile b arasinda t oraninda karisim (t=0 -> a). */
    static int blend(int a, int b, float t) {
        float s = 1f - t;
        return Color.rgb(
                (int) (Color.red(a) * s + Color.red(b) * t),
                (int) (Color.green(a) * s + Color.green(b) * t),
                (int) (Color.blue(a) * s + Color.blue(b) * t));
    }

    static int lighten(int c, float t) {
        return blend(c, Color.WHITE, t);
    }

    static int darken(int c, float t) {
        return blend(c, Color.BLACK, t);
    }
}
