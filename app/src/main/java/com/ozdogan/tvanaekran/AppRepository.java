package com.ozdogan.tvanaekran;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Uygulamalari bulur; sira, gizleme ve son acilan uygulamayi saklar. */
final class AppRepository {
    /** Ilk acilista one alinanlar (bu sirayla). */
    private static final List<String> DEFAULT_FIRST = Arrays.asList(
            "com.android.tv",
            "com.google.android.youtube.tv",
            "org.smarttube.stable",
            "com.rtk.mediabrowser",
            "com.rtk.android.miracast",
            "com.realtek.tv.settings");

    /** Ust cubukta kendi dugmesi olan arka plan servisleri; satirda hic gosterilmez. */
    static final String AIRSCREEN = "com.ionitech.airscreen";
    static final String LOCALSEND = "org.localsend.localsend_app";
    private static final Set<String> SERVICE_APPS = new HashSet<>(Arrays.asList(AIRSCREEN, LOCALSEND));

    /** Ilk acilista gizlenenler; ayarlardan geri acilabilir. */
    private static final Set<String> DEFAULT_HIDDEN = new HashSet<>(Arrays.asList(
            "com.android.vending",
            "com.atmaca.otaupdater",
            "com.realtek.media.player",
            "com.realtek.tv.actionmenu",
            "com.hikeen.settings"));

    private static final String KEY_ORDER = "order";
    private static final String KEY_HIDDEN = "hidden";
    private static final String KEY_LAST = "last";
    private static final String KEY_NAME = "name";

    private final Context ctx;
    private final PackageManager pm;
    private final SharedPreferences prefs;

    AppRepository(Context c) {
        ctx = c.getApplicationContext();
        pm = ctx.getPackageManager();
        prefs = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    /** Gizliler dahil tum uygulamalar, kayitli siraya gore. */
    List<AppEntry> loadAll() {
        Map<String, AppEntry> map = new LinkedHashMap<>();
        addFrom(Intent.CATEGORY_LEANBACK_LAUNCHER, map);
        addFrom(Intent.CATEGORY_LAUNCHER, map);
        map.remove(ctx.getPackageName());
        map.keySet().removeAll(SERVICE_APPS);

        Set<String> hidden = hiddenSet();
        List<String> order = order();
        List<AppEntry> ordered = new ArrayList<>();
        for (String p : order) {
            AppEntry e = map.remove(p);
            if (e != null) ordered.add(e);
        }
        // Sirasi hic ayarlanmamislar (USB'den yeni yuklenenler dahil) sona, alfabetik
        List<AppEntry> rest = new ArrayList<>(map.values());
        Collections.sort(rest, (a, b) -> a.label.compareToIgnoreCase(b.label));
        ordered.addAll(rest);
        for (AppEntry e : ordered) e.hidden = hidden.contains(e.pkg);
        return ordered;
    }

    private void addFrom(String category, Map<String, AppEntry> map) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (map.containsKey(ai.packageName)) continue;
            AppEntry e = new AppEntry();
            e.pkg = ai.packageName;
            e.label = ri.loadLabel(pm).toString();
            e.launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setClassName(ai.packageName, ai.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            e.banner = ai.loadBanner(pm);
            e.icon = ri.loadIcon(pm);
            e.color = Colors.dominant(e.banner != null ? e.banner : e.icon);
            map.put(e.pkg, e);
        }
    }

    List<String> order() {
        String s = prefs.getString(KEY_ORDER, null);
        if (s == null) return new ArrayList<>(DEFAULT_FIRST);
        return new ArrayList<>(Arrays.asList(TextUtils.split(s, ",")));
    }

    void saveOrder(List<AppEntry> apps) {
        List<String> pkgs = new ArrayList<>();
        for (AppEntry e : apps) pkgs.add(e.pkg);
        prefs.edit().putString(KEY_ORDER, TextUtils.join(",", pkgs)).apply();
    }

    Set<String> hiddenSet() {
        if (!prefs.contains(KEY_HIDDEN)) return new HashSet<>(DEFAULT_HIDDEN);
        return new HashSet<>(prefs.getStringSet(KEY_HIDDEN, Collections.emptySet()));
    }

    void saveHidden(Set<String> hidden) {
        prefs.edit().putStringSet(KEY_HIDDEN, new HashSet<>(hidden)).apply();
    }

    /** Selamlamada gosterilen isim; bos olabilir. */
    String userName() {
        return prefs.getString(KEY_NAME, "");
    }

    void setUserName(String name) {
        prefs.edit().putString(KEY_NAME, name.trim()).apply();
    }

    boolean isInstalled(String pkg) {
        try {
            return pm.getApplicationInfo(pkg, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    Intent launchIntent(String pkg) {
        Intent it = pm.getLeanbackLaunchIntentForPackage(pkg);
        return it != null ? it : pm.getLaunchIntentForPackage(pkg);
    }

    String lastOpened() {
        return prefs.getString(KEY_LAST, null);
    }

    void setLastOpened(String pkg) {
        prefs.edit().putString(KEY_LAST, pkg).apply();
    }

    void resetLayout() {
        prefs.edit().remove(KEY_ORDER).remove(KEY_HIDDEN).apply();
    }
}
