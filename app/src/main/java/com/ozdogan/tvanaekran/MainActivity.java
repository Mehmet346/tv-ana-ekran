package com.ozdogan.tvanaekran;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    /** ADB ile isim vermek icin:  am start -n com.ozdogan.tvanaekran/.MainActivity --es isim Ad */
    private static final String EXTRA_NAME = "isim";
    private static final long RAM_INTERVAL = 4_000;
    /** Ana ekrana donulunce, acilan uygulamanin kapanmasina zaman taniyip sonra temizle. */
    private static final long AUTO_CLEAN_DELAY = 2_500;
    private static final long AUTO_CLEAN_INTERVAL = 60_000;
    /** Durum noktalari: /proc okuma ve tek DNS sorgusu, 30 sn'de bir yeterince ucuz. */
    private static final long NET_INTERVAL = 30_000;
    private static final long WEATHER_INTERVAL = 30 * 60_000;
    /** "Bosalt" bunlara dokunmaz: AirPlay acik kalsin, sistem arayuzu bozulmasin. */
    private static final Set<String> KEEP_ALIVE = new HashSet<>(java.util.Arrays.asList(
            AppRepository.AIRSCREEN, AppRepository.LOCALSEND, "com.android.systemui", "android"));
    /** Arka plan uygulamasi acildiktan sonra ana ekrana donmeden once beklenen sure. */
    private static final long SERVICE_WARMUP = 1_000;
    /**
     * LocalSend kendini koruyan bir servis calistirmiyor; bellek sikisinca Android onu kapatiyor.
     * Ana ekran acikken kapanmissa yeniden baslatilir, ama ekrani rahatsiz etmemek icin en fazla bu siklikla.
     */
    private static final long LOCALSEND_RESTART_GAP = 5 * 60_000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    private AppRepository repo;
    private List<AppEntry> apps = new ArrayList<>();

    private View heroBg;
    private GradientDrawable heroGradient;
    private int heroColor = Colors.BASE;
    private ValueAnimator heroAnim;
    private TextView heroTitle, heroSub, heroLetter, heroAction, greeting;
    private LinearLayout appsRow, inputsRow;
    private HorizontalScrollView appsScroll;
    private TextView appsCount, weather, speedText, ramText;
    private View airplayDot, localsendDot, adDot, ramDot;
    private boolean speedRunning;
    private TextView hint;
    private boolean resumed;
    /** -LOCALSEND_RESTART_GAP: ilk kontrolde hemen baslatilabilsin. */
    private long lastLocalSendStart = -LOCALSEND_RESTART_GAP;
    /** Tasima modundaki uygulama; null ise normal gezinme. */
    private AppEntry moving;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            reloadApps(null);
        }
    };

    private final Runnable ramTick = new Runnable() {
        @Override
        public void run() {
            updateRam();
            ui.postDelayed(this, RAM_INTERVAL);
        }
    };

    private final Runnable netTick = new Runnable() {
        @Override
        public void run() {
            refreshNetworkStatus();
            ui.postDelayed(this, NET_INTERVAL);
        }
    };

    private final Runnable weatherTick = new Runnable() {
        @Override
        public void run() {
            refreshWeather();
            ui.postDelayed(this, WEATHER_INTERVAL);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        repo = new AppRepository(this);
        applyNameExtra(getIntent());

        heroBg = findViewById(R.id.heroBg);
        heroGradient = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Colors.BASE, Colors.BASE, Colors.BASE});
        heroBg.setBackground(heroGradient);
        heroTitle = findViewById(R.id.heroTitle);
        heroSub = findViewById(R.id.heroSub);
        heroLetter = findViewById(R.id.heroLetter);
        heroAction = findViewById(R.id.heroAction);
        greeting = findViewById(R.id.greeting);
        appsRow = findViewById(R.id.appsRow);
        appsScroll = findViewById(R.id.appsScroll);
        appsCount = findViewById(R.id.appsCount);
        inputsRow = findViewById(R.id.inputsRow);
        weather = findViewById(R.id.weather);
        speedText = findViewById(R.id.speedText);
        ramText = findViewById(R.id.ramText);
        airplayDot = findViewById(R.id.airplayDot);
        localsendDot = findViewById(R.id.localsendDot);
        adDot = findViewById(R.id.adDot);
        ramDot = findViewById(R.id.ramDot);
        hint = findViewById(R.id.hint);

        findViewById(R.id.airplayChip).setOnClickListener(v -> onAirplayClick());
        findViewById(R.id.localsendChip).setOnClickListener(v -> onLocalSendClick());
        findViewById(R.id.adChip).setOnClickListener(v -> onAdChipClick());
        findViewById(R.id.speedChip).setOnClickListener(v -> runSpeedTest());
        findViewById(R.id.ramChip).setOnClickListener(v -> freeMemory());
        findViewById(R.id.settingsChip).setOnClickListener(v -> showSettings());
        for (int id : new int[]{R.id.airplayChip, R.id.localsendChip, R.id.adChip, R.id.speedChip, R.id.ramChip, R.id.settingsChip}) {
            findViewById(id).setOnFocusChangeListener((v, has) -> {
                if (has) showChipHero((View) v);
            });
        }

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f);

        reloadApps(repo.lastOpened());
        // LocalSend'i baslatma isini refreshNetworkStatus'taki bekci yapar (TV acilisi dahil)
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        greeting.setText(greetingText());
        // HDMI'ye cihaz takilip cikarilmis olabilir; odak girislerdeyse dokunma
        View f = getCurrentFocus();
        if (f == null || f.getParent() != inputsRow) loadInputs();
        ui.post(ramTick);
        ui.post(netTick);
        ui.post(weatherTick);
        ui.postDelayed(autoClean, AUTO_CLEAN_DELAY);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        ui.removeCallbacks(ramTick);
        ui.removeCallbacks(netTick);
        ui.removeCallbacks(weatherTick);
        ui.removeCallbacks(autoClean);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        bg.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        applyNameExtra(intent);
        greeting.setText(greetingText());
        // Ana ekrandayken HOME'a basildi: basa don
        if (appsRow.getChildCount() > 0) {
            appsScroll.smoothScrollTo(0, 0);
            appsRow.getChildAt(0).requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        // Tasima modundaysa bitir, degilse Canli TV'ye gec
        if (moving != null) finishMove();
        else launchLiveTv(null);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (f != null && f.getTag() instanceof AppEntry) {
                showAppMenu((AppEntry) f.getTag());
                return true;
            }
            showSettings();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---------------------------------------------------------------- Uygulamalar

    private void reloadApps(String focusPkg) {
        if (focusPkg == null) {
            View f = getCurrentFocus();
            if (f != null && f.getTag() instanceof AppEntry) focusPkg = ((AppEntry) f.getTag()).pkg;
        }
        apps = repo.loadAll();
        appsRow.removeAllViews();
        View toFocus = null;
        int visible = 0;
        for (AppEntry e : apps) {
            if (e.hidden) continue;
            View card = makeCard(e);
            appsRow.addView(card);
            visible++;
            if (e.pkg.equals(focusPkg)) toFocus = card;
        }
        appsCount.setText(String.valueOf(visible));
        if (toFocus == null && appsRow.getChildCount() > 0) toFocus = appsRow.getChildAt(0);
        if (toFocus != null) {
            View target = toFocus;
            target.post(target::requestFocus);
        }
    }

    private View makeCard(AppEntry e) {
        FrameLayout card = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(150), dp(84));
        lp.setMarginEnd(dp(14));
        card.setLayoutParams(lp);
        card.setFocusable(true);
        card.setClickable(true);
        card.setLongClickable(true);
        card.setTag(e);
        card.setContentDescription(e.label);

        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Colors.lighten(e.color, 0.1f), Colors.darken(e.color, 0.35f)});
        bg.setCornerRadius(dp(10));
        card.setBackground(bg);
        card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        card.setClipToOutline(true);

        if (e.banner != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(e.banner);
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(e.icon);
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.TOP | Gravity.END);
            ilp.setMargins(0, dp(8), dp(10), 0);
            card.addView(icon, ilp);

            TextView label = new TextView(this);
            label.setText(e.label);
            label.setTextColor(Color.WHITE);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);
            FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.START);
            tlp.setMargins(dp(10), 0, dp(10), dp(8));
            card.addView(label, tlp);
        }

        card.setForeground(focusRing(Color.WHITE));

        card.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f)
                    .translationZ(has ? dp(8) : 0).setDuration(140).start();
            if (has && moving == null) showAppHero(e);
        });
        card.setOnClickListener(v -> {
            if (moving != null) finishMove();
            else launch(e);
        });
        card.setOnLongClickListener(v -> {
            showAppMenu(e);
            return true;
        });
        card.setOnKeyListener(this::onRowKey);
        return card;
    }

    /**
     * Sag/sol tuslarini satir icinde biz yonetiyoruz: HorizontalScrollView'in kendi odak aramasi
     * bu cihazda bazen satirin sonuna atliyor.
     */
    private boolean onRowKey(View v, int keyCode, KeyEvent ev) {
        if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (moving != null) return onMoveKey(v, keyCode);
        int dir = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 0;
        if (dir == 0) return false;
        LinearLayout row = (LinearLayout) v.getParent();
        int next = row.indexOfChild(v) + dir;        if (next < 0 || next >= row.getChildCount()) return true; // satir sonunda dur
        View target = row.getChildAt(next);
        target.requestFocus();
        HorizontalScrollView scroll = (HorizontalScrollView) row.getParent();
        // Odaktakinin solunda bir oge gorunur kalsin
        View prev = next > 0 ? row.getChildAt(next - 1) : null;
        int x = prev != null ? prev.getLeft() - dp(20) : 0;
        scroll.smoothScrollTo(Math.max(0, x), 0);
        return true;
    }

    private void launch(AppEntry e) {
        try {
            startActivity(e.launch);
            repo.setLastOpened(e.pkg);
        } catch (ActivityNotFoundException | SecurityException ex) {
            toast(e.label + " açılamadı");
        }
    }

    private void showAppMenu(AppEntry e) {
        String[] items = {"Taşı  (◀ ▶ ile kaydır, OK ile bırak)", "En başa taşı", "Gizle", "Uygulama bilgisi"};
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle(e.label)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: startMove(e); break;
                        case 1: moveToFront(e); break;
                        case 2: hide(e); break;
                        case 3: openAppInfo(e.pkg); break;
                    }
                })
                .show();
    }

    private void moveToFront(AppEntry e) {
        apps.remove(e);
        apps.add(0, e);
        repo.saveOrder(apps);
        reloadApps(e.pkg);
    }

    // ---------------------------------------------------------------- Tasima modu

    private void startMove(AppEntry e) {
        View card = cardFor(e);
        if (card == null) return;
        moving = e;
        card.setForeground(focusRing(getColor(R.color.accent)));
        card.requestFocus();
        showHero("Taşınıyor", e.label, "◀ ▶ ile yerini seç, OK ile bırak", e.color, "OK  bırak");
        hint.setText("◀ ▶  taşı     OK / Geri  bırak");
    }

    /** Tasima modunda: sag/sol karti kaydirir, OK ve geri birakir, yukari/asagi kilitli. */
    private boolean onMoveKey(View v, int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_BACK) {
            finishMove();
            return true;
        }
        int dir = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 0;
        if (dir == 0) return true;
        int i = apps.indexOf(moving);
        int j = i + dir;
        while (j >= 0 && j < apps.size() && apps.get(j).hidden) j += dir;
        if (j < 0 || j >= apps.size()) return true;
        Collections.swap(apps, i, j);

        int rowIdx = appsRow.indexOfChild(v);
        appsRow.removeView(v);
        appsRow.addView(v, rowIdx + dir);
        v.requestFocus();
        View prev = rowIdx + dir > 0 ? appsRow.getChildAt(rowIdx + dir - 1) : null;
        appsScroll.post(() -> appsScroll.smoothScrollTo(prev != null ? Math.max(0, prev.getLeft() - dp(20)) : 0, 0));
        return true;
    }

    private void finishMove() {
        AppEntry e = moving;
        moving = null;
        repo.saveOrder(apps);
        hint.setText(R.string.hint);
        reloadApps(e.pkg);
        toast(e.label + " yerleştirildi");
    }

    private View cardFor(AppEntry e) {
        for (int i = 0; i < appsRow.getChildCount(); i++) {
            if (appsRow.getChildAt(i).getTag() == e) return appsRow.getChildAt(i);
        }
        return null;
    }

    private StateListDrawable focusRing(int color) {
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(10));
        ring.setStroke(dp(3), color);
        StateListDrawable fg = new StateListDrawable();
        fg.addState(new int[]{android.R.attr.state_focused}, ring);
        fg.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return fg;
    }

    private void hide(AppEntry e) {
        Set<String> hidden = repo.hiddenSet();
        hidden.add(e.pkg);
        repo.saveHidden(hidden);
        reloadApps(null);
        toast(e.label + " gizlendi · ⚙ > Gizli uygulamalar");
    }

    private void openAppInfo(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ex) {
            toast("Uygulama bilgisi açılamadı");
        }
    }

    // ---------------------------------------------------------------- Girisler

    private void loadInputs() {
        inputsRow.removeAllViews();
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return;
        List<TvInputInfo> list = new ArrayList<>(tim.getTvInputList());
        Collections.sort(list, (a, b) -> {
            String la = a.loadLabel(this).toString(), lb = b.loadLabel(this).toString();
            int ra = inputRank(la), rb = inputRank(lb);
            return ra != rb ? ra - rb : la.compareToIgnoreCase(lb);
        });
        for (TvInputInfo info : list) {
            // Sadece uydu ve HDMI'lar; anten, analog, AV, VGA gosterilmez
            if (info.isHidden(this) || inputRank(info.loadLabel(this).toString()) > 2) continue;
            CharSequence label = info.loadCustomLabel(this);
            if (TextUtils.isEmpty(label)) label = friendlyInputName(info.loadLabel(this).toString());
            boolean connected = tim.getInputState(info.getId()) != TvInputManager.INPUT_STATE_DISCONNECTED;
            String sub = connected ? "Bağlı · açmak için OK" : "Bağlı cihaz yok";
            addInputChip(label.toString(), connected, v -> launchLiveTv(info), sub);
        }
    }

    /** Realtek'in teknik giris adlarini anlasilir hale getirir. */
    private static String friendlyInputName(String raw) {
        switch (raw.toUpperCase(Locale.US).replace(" ", "")) {
            case "DVB-S": return "Uydu";
            case "DVB-T": return "Anten";
            case "DVB-C": return "Kablo TV";
            case "ATV": return "Analog TV";
            case "YPBPR": return "Komponent";
            case "VGA": return "VGA (PC)";
            default: return raw;
        }
    }

    /** Sik kullanilanlar once: uydu, anten, HDMI'lar; eski girisler sona. */
    private static int inputRank(String raw) {
        String r = raw.toUpperCase(Locale.US).replace(" ", "");
        if (r.equals("DVB-S")) return 0;
        if (r.startsWith("HDMI")) return 2;
        if (r.equals("DVB-T")) return 3;
        if (r.equals("DVB-C")) return 3;
        if (r.startsWith("AV")) return 4;
        if (r.equals("ATV")) return 5;
        return 6;
    }

    private void addInputChip(String label, boolean connected, View.OnClickListener click, String sub) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chip.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), 0, dp(14), 0);
        chip.setBackgroundResource(R.drawable.input_bg);
        chip.setTextColor(new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{Colors.BASE, connected ? Color.WHITE : 0x80FFFFFF}));
        chip.setFocusable(true);
        chip.setClickable(true);
        chip.setOnClickListener(click);
        chip.setOnKeyListener(this::onRowKey);
        chip.setOnFocusChangeListener((v, has) -> {
            if (has) showHero("Giriş", label, sub, 0xFF2A6FDB, "OK  ile aç");
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(28));
        lp.setMarginEnd(dp(8));
        inputsRow.addView(chip, lp);
    }

    private void launchLiveTv(TvInputInfo info) {
        Intent it;
        if (info == null) {
            it = getPackageManager().getLeanbackLaunchIntentForPackage("com.android.tv");
            if (it == null) it = getPackageManager().getLaunchIntentForPackage("com.android.tv");
        } else {
            Uri uri = info.isPassthroughInput()
                    ? TvContract.buildChannelUriForPassthroughInput(info.getId())
                    : TvContract.buildChannelsUriForInput(info.getId());
            it = new Intent(Intent.ACTION_VIEW, uri);
        }
        if (it == null) {
            toast("Canlı TV bulunamadı");
            return;
        }
        try {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ex) {
            if (info != null) launchLiveTv(null);
            else toast("Canlı TV açılamadı");
        }
    }

    // ---------------------------------------------------------------- Hero

    private void showAppHero(AppEntry e) {
        String sub = e.pkg.equals(repo.lastOpened()) ? "Kaldığın yerden devam et" : "Açmaya hazır";
        showHero(greetingText(), e.label, sub, e.color, "OK  ile aç");
    }

    private void showChipHero(View chip) {
        int id = chip.getId();
        if (id == R.id.airplayChip) {
            showHero("AirPlay", "Ekran yansıtma", "iPhone / Mac: Denetim Merkezi > Ekran Yansıtma", 0xFF2AA198, "OK  AirScreen'i aç");
        } else if (id == R.id.localsendChip) {
            showHero("LocalSend", "Dosya al", "Telefondan LocalSend > Gönder ile APK, foto, video at", 0xFF1E8C7A, "OK  durumu / başlat");
        } else if (id == R.id.adChip) {
            showHero("AdGuard DNS", "Reklam engeli", "Modem DNS'i: 94.140.14.14", 0xFF2E9E5B, "OK  durumu yenile");
        } else if (id == R.id.speedChip) {
            showHero("İnternet", "Hız testi", "Cloudflare üzerinden 8 saniyelik indirme", 0xFFE08A1E, "OK  testi başlat");
        } else if (id == R.id.ramChip) {
            showHero("Bellek", "RAM'i boşalt", "Arka plandakileri kapatır; AirPlay ve LocalSend açık kalır", 0xFFD6336C, "OK  boşalt");
        } else if (id == R.id.settingsChip) {
            showHero("Ana ekran", "Ayarlar", "Gizli uygulamalar, sıralama, TV ayarları", 0xFF6C5CE7, "OK  aç");
        }
    }

    private void showHero(String top, String title, String sub, int color, String action) {
        greeting.setText(top);
        heroTitle.setText(title);
        heroSub.setText(sub);
        heroAction.setText(action);
        heroLetter.setText(title.isEmpty() ? "" : title.substring(0, 1).toUpperCase(new Locale("tr")));
        heroTitle.setTextColor(Colors.lighten(color, 0.35f));
        heroLetter.setTextColor(Colors.lighten(color, 0.15f));
        animateHeroColor(color);
    }

    private void animateHeroColor(int target) {
        if (heroAnim != null) heroAnim.cancel();
        heroAnim = ValueAnimator.ofObject(new ArgbEvaluator(), heroColor, target);
        heroAnim.setDuration(260);
        heroAnim.addUpdateListener(a -> {
            heroColor = (int) a.getAnimatedValue();
            heroGradient.setColors(new int[]{
                    Colors.BASE,
                    Colors.blend(Colors.BASE, heroColor, 0.35f),
                    Colors.blend(Colors.BASE, heroColor, 0.8f)});
        });
        heroAnim.start();
    }

    private String greetingText() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String g;
        if (h >= 5 && h < 12) g = "Günaydın";
        else if (h >= 12 && h < 18) g = "İyi günler";
        else if (h >= 18 && h < 23) g = "İyi akşamlar";
        else g = "İyi geceler";
        String name = repo.userName();
        return name.isEmpty() ? g : g + " " + name;
    }

    // ---------------------------------------------------------------- Ust cubuk

    private void updateRam() {
        long[] r = Status.ram(this);
        ramText.setText("RAM %" + r[0]);
        int c = r[0] < 70 ? getColor(R.color.ok) : r[0] < 85 ? getColor(R.color.warn) : getColor(R.color.accent);
        ramDot.setBackgroundTintList(ColorStateList.valueOf(c));
    }

    private void freeMemory() {
        long before = Status.ram(this)[1];
        killBackgroundApps();
        ramText.setText("Boşaltılıyor…");
        ui.postDelayed(() -> {
            long[] r = Status.ram(this);
            long gained = Math.max(0, r[1] - before);
            updateRam();
            toast("+" + gained + " MB boşaldı · şimdi " + r[1] + " MB boş");
        }, 1500);
    }

    /** Ana ekran gorunurken diger her sey arka plandadir; hepsini kapat. */
    private void killBackgroundApps() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ApplicationInfo ai : getPackageManager().getInstalledApplications(0)) {
            if (ai.packageName.equals(getPackageName()) || KEEP_ALIVE.contains(ai.packageName)) continue;
            am.killBackgroundProcesses(ai.packageName);
        }
    }

    /** Ana ekran acik kaldikca tekrarlar: kapatilan uygulamalar yayinlarla kendiliginden geri aciliyor. */
    private final Runnable autoClean = new Runnable() {
        @Override
        public void run() {
            bg.execute(() -> {
                killBackgroundApps();
                ui.postDelayed(MainActivity.this::updateRam, 1000);
            });
            ui.postDelayed(this, AUTO_CLEAN_INTERVAL);
        }
    };

    private void refreshNetworkStatus() {
        bg.execute(() -> {
            boolean air = Status.listening(Status.AIRSCREEN_PORT);
            boolean send = Status.listening(Status.LOCALSEND_PORT);
            boolean ad = Status.adBlockActive();
            boolean restart = !send && repo.isInstalled(AppRepository.LOCALSEND)
                    && SystemClock.elapsedRealtime() - lastLocalSendStart > LOCALSEND_RESTART_GAP;
            ui.post(() -> {
                setDot(airplayDot, air);
                setDot(localsendDot, send);
                setDot(adDot, ad);
                if (restart && resumed && moving == null) startInBackground(AppRepository.LOCALSEND);
            });
        });
    }

    private void refreshWeather() {
        bg.execute(() -> {
            String w = Status.weather();
            if (w != null) ui.post(() -> weather.setText(w));
        });
    }

    /**
     * Arka plan uygulamasini (or. LocalSend) baslatir ve kisa sure sonra ana ekrana geri doner.
     * Uygulama sureci acik kalir; KEEP_ALIVE sayesinde RAM temizligi ona dokunmaz.
     */
    private void startInBackground(String pkg) {
        Intent it = repo.launchIntent(pkg);
        if (it == null) {
            toast("Uygulama yüklü değil");
            return;
        }
        if (AppRepository.LOCALSEND.equals(pkg)) lastLocalSendStart = SystemClock.elapsedRealtime();
        startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ui.postDelayed(() -> startActivity(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)), SERVICE_WARMUP);
        ui.postDelayed(this::refreshNetworkStatus, SERVICE_WARMUP + 4_000);
    }

    private void onLocalSendClick() {
        bg.execute(() -> {
            boolean on = Status.listening(Status.LOCALSEND_PORT);
            ui.post(() -> {
                setDot(localsendDot, on);
                if (on) toast("LocalSend hazır · telefonda LocalSend > Gönder > bu TV");
                else if (!repo.isInstalled(AppRepository.LOCALSEND)) toast("LocalSend yüklü değil");
                else startInBackground(AppRepository.LOCALSEND);
            });
        });
    }

    private void setDot(View dot, boolean on) {
        dot.setBackgroundTintList(ColorStateList.valueOf(getColor(on ? R.color.ok : R.color.off)));
    }

    private void onAirplayClick() {
        bg.execute(() -> {
            boolean on = Status.listening(Status.AIRSCREEN_PORT);
            ui.post(() -> {
                setDot(airplayDot, on);
                if (on) {
                    toast("AirPlay hazır · iPhone'da Ekran Yansıtma'yı aç");
                } else {
                    Intent it = repo.launchIntent(AppRepository.AIRSCREEN);
                    if (it != null) startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    else toast("AirScreen yüklü değil");
                }
            });
        });
    }

    private void onAdChipClick() {
        bg.execute(() -> {
            boolean on = Status.adBlockActive();
            ui.post(() -> {
                setDot(adDot, on);
                toast(on ? "Reklam engeli açık (AdGuard DNS)"
                        : "Reklam engeli kapalı · TV yeniden başladıysa bilgisayarda tv-dns.ps1'i çalıştır");
            });
        });
    }

    private void runSpeedTest() {
        if (speedRunning) return;
        speedRunning = true;
        speedText.setText("Ölçülüyor…");
        bg.execute(() -> {
            double mbps = Status.speedMbps();
            ui.post(() -> {
                speedRunning = false;
                speedText.setText(mbps < 0 ? "Hız testi · hata" : String.format(new Locale("tr"), "↓ %.0f Mbps", mbps));
            });
        });
    }

    // ---------------------------------------------------------------- Ayarlar

    private void showSettings() {
        String[] items = {"Adın", "Gizli uygulamalar", "TV ayarları", "Hava durumunu yenile", "Varsayılan düzene dön"};
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle("Ana ekran")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: showNameDialog(); break;
                        case 1: showHiddenPicker(); break;
                        case 2:
                            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            break;
                        case 3: refreshWeather(); break;
                        case 4:
                            repo.resetLayout();
                            reloadApps(null);
                            break;
                    }
                })
                .show();
    }

    private void showNameDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(repo.userName());
        input.setSelection(input.getText().length());
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(input);
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle("Selamlamada görünecek ad")
                .setView(box)
                .setPositiveButton("Kaydet", (d, w) -> {
                    repo.setUserName(input.getText().toString());
                    greeting.setText(greetingText());
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void applyNameExtra(Intent intent) {
        if (intent != null && intent.hasExtra(EXTRA_NAME)) {
            repo.setUserName(String.valueOf(intent.getStringExtra(EXTRA_NAME)));
        }
    }

    private void showHiddenPicker() {
        List<AppEntry> all = repo.loadAll();
        String[] labels = new String[all.size()];
        boolean[] checked = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) {
            labels[i] = all.get(i).label;
            checked[i] = all.get(i).hidden;
        }
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle("İşaretliler gizlenir")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Kaydet", (d, w) -> {
                    Set<String> hidden = new HashSet<>();
                    for (int i = 0; i < all.size(); i++) if (checked[i]) hidden.add(all.get(i).pkg);
                    repo.saveHidden(hidden);
                    reloadApps(null);
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    // ---------------------------------------------------------------- Yardimcilar

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
