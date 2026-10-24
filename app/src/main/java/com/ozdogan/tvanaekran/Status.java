package com.ozdogan.tvanaekran;

import android.app.ActivityManager;
import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.Locale;

/** Ust cubuktaki durum bilgileri. Ag isleri arka plan thread'inde cagrilmali. */
final class Status {
    private static final String WEATHER_URL =
            "https://api.open-meteo.com/v1/forecast?latitude=41.01&longitude=28.98"
                    + "&current=temperature_2m,weather_code&timezone=auto";
    private static final String SPEED_URL = "https://speed.cloudflare.com/__down?bytes=25000000";
    static final int AIRSCREEN_PORT = 7000;
    static final int LOCALSEND_PORT = 53317;

    private Status() {}

    /** Kullanilan RAM yuzdesi ve bos MB. */
    static long[] ram(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long pct = 100 - (mi.availMem * 100 / mi.totalMem);
        return new long[]{pct, mi.availMem / (1024 * 1024)};
    }

    /** Cihazda bu TCP portunu dinleyen biri var mi (/proc/net uzerinden, ag trafigi olmadan). */
    static boolean listening(int port) {
        String hexPort = String.format(Locale.US, ":%04X", port);
        for (String f : new String[]{"/proc/net/tcp6", "/proc/net/tcp"}) {
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] p = line.trim().split("\\s+");
                    if (p.length > 3 && p[1].endsWith(hexPort) && "0A".equals(p[3])) return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /** AdGuard DNS reklam alan adini 0.0.0.0'a yonlendiriyorsa reklam engeli acik. */
    static boolean adBlockActive() {
        try {
            for (InetAddress a : InetAddress.getAllByName("doubleclick.net")) {
                if (!a.isAnyLocalAddress()) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** "12° · Parçalı bulutlu" gibi; hata olursa null. */
    static String weather() {
        try {
            JSONObject cur = new JSONObject(get(WEATHER_URL)).getJSONObject("current");
            long t = Math.round(cur.getDouble("temperature_2m"));
            return t + "° · " + weatherText(cur.getInt("weather_code"));
        } catch (Exception e) {
            return null;
        }
    }

    private static String weatherText(int code) {
        if (code == 0) return "Açık";
        if (code == 1) return "Az bulutlu";
        if (code == 2) return "Parçalı bulutlu";
        if (code == 3) return "Kapalı";
        if (code == 45 || code == 48) return "Sisli";
        if (code >= 51 && code <= 57) return "Çisenti";
        if (code >= 61 && code <= 67) return "Yağmurlu";
        if (code >= 71 && code <= 77) return "Karlı";
        if (code >= 80 && code <= 82) return "Sağanak";
        if (code == 85 || code == 86) return "Kar sağanağı";
        if (code >= 95) return "Fırtına";
        return "";
    }

    /** En fazla 8 saniye indirip Mbps hesaplar; hata olursa -1. */
    static double speedMbps() {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(SPEED_URL).openConnection();
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            byte[] buf = new byte[32 * 1024];
            long bytes = 0;
            long start = System.nanoTime();
            try (InputStream in = con.getInputStream()) {
                int n;
                while ((n = in.read(buf)) > 0) {
                    bytes += n;
                    if (System.nanoTime() - start > 8_000_000_000L) break;
                }
            }
            double sec = (System.nanoTime() - start) / 1e9;
            return bytes * 8 / sec / 1e6;
        } catch (Exception e) {
            return -1;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(5000);
        con.setReadTimeout(5000);
        try (InputStream in = con.getInputStream()) {
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
            return sb.toString();
        } finally {
            con.disconnect();
        }
    }
}
