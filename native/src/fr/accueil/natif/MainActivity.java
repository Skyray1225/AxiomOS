package fr.accueil.natif;

import android.animation.TimeInterpolator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Prototype natif de l'accueil, thème Modern : photo de fond (une toutes les 2 h), vignettage la nuit,
 * salutation, heure et date, dock (favoris puis tri par usage), mode édition (appui long sur OK),
 * barre du haut (Préférences, Paramètres) et fenêtre Préférences.
 * Test : am start -n fr.accueil.natif/.MainActivity [--ef heure 21.5]
 */
public class MainActivity extends Activity implements PrefsView.Host {

    private static final String TAG = "AccueilNatif";
    static final TimeInterpolator EASE = new PathInterpolator(.22f, .8f, .24f, 1f);

    // Ordre du système : ces applis d'abord, puis les autres par ordre alphabétique, Paramètres en dernier.
    private static final List<String> PRIORITY = Arrays.asList(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom",
            "com.canal.android.canal",
            "tv.twitch.android.app",
            "fr.tf1.mytf1",
            "fr.m6.m6replay",
            "fr.francetv.pluzz",
            "tv.arte.plus7");
    private static final String SETTINGS = "com.android.tv.settings";
    private static final int VISIBLE = 5, PHOTO_COUNT = 8;
    private static final double LAT = 42.6986, LON = 2.8956;

    static class App {
        String label, pkg, cls;
        Bitmap banner;
        String key() { return pkg + "/" + cls; }
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final SparseArray<Typeface> fonts = new SparseArray<>();
    private final Map<String, Bitmap> bannerCache = new ConcurrentHashMap<>();
    private Store store;

    private int W, H, tileW, tileH, gap, pad, dockW, dockH, dockLeft, dockTop;
    private float vw, vh;
    private final ImageView[] photos = new ImageView[2];
    private int photoOn = 0, photoIdx = -1;
    private VignetteView vignette;
    private Boolean night;
    private TextView greet, clock, date, title, toast;
    private DockView dock;
    private FrameLayout track;
    private LinearLayout hint;
    private PillButton prefsBtn, gearBtn;
    private PrefsView prefs;

    private List<App> allApps = new ArrayList<>(); // toutes les applis, ordre du système
    private List<App> shown = new ArrayList<>();   // celles du dock, dans l'ordre affiché
    private final List<TileView> tiles = new ArrayList<>();
    private int focus = 0, offset = 0;
    private boolean editing, onTop, prefsOpen, topOnGear = true;
    private boolean okDown, okLong;
    private float forcedHour = Float.NaN; // test : --ef heure 21.5
    private boolean outfitBroken;
    private FrameLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        long t0 = System.nanoTime();
        super.onCreate(savedInstanceState);
        store = new Store(this);
        forcedHour = getIntent().getFloatExtra("heure", Float.NaN);
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(size);
        W = size.x;
        H = size.y;
        vw = W / 100f;
        vh = H / 100f;

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF15181C);
        // Seule vue « focusable » : sert à sortir du mode tactile, sans cadre de sélection d'Android
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setDefaultFocusHighlightEnabled(false);
        for (int i = 0; i < 2; i++) {
            photos[i] = new ImageView(this);
            photos[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
            photos[i].setAlpha(0f);
            root.addView(photos[i], match());
        }
        vignette = new VignetteView(this);
        vignette.setAlpha(0f);
        root.addView(vignette, match());

        // Salutation, heure et date, en haut à gauche
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        greet = text(1.05f * vw, 500, .24f, true);
        greet.setAlpha(.9f);
        clock = text(6.6f * vw, 200, -.02f, false);
        date = text(1.05f * vw, 500, .22f, true);
        date.setTextColor(0xDBFFFFFF);
        top.addView(greet, margins(px(.2f), 0, 0, Math.round(.4f * vh)));
        top.addView(clock, margins(0, 0, 0, 0));
        top.addView(date, margins(px(.2f), Math.round(.6f * vh), 0, 0));
        FrameLayout.LayoutParams tlp = wrap(Gravity.TOP | Gravity.LEFT);
        tlp.leftMargin = px(4.5f);
        tlp.topMargin = Math.round(7 * vh);
        root.addView(top, tlp);

        // Barre du haut : Préférences et Paramètres (flèche haut depuis le dock)
        LinearLayout bar = new LinearLayout(this);
        bar.setClipChildren(false); // le bouton sélectionné grossit au-delà de la barre
        prefsBtn = new PillButton(R.drawable.ic_prefs, "Préférences");
        gearBtn = new PillButton(R.drawable.ic_gear, "Paramètres");
        bar.addView(prefsBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(2.5f)));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(2.5f));
        glp.leftMargin = px(1.2f);
        bar.addView(gearBtn, glp);
        FrameLayout.LayoutParams blp = wrap(Gravity.TOP | Gravity.RIGHT);
        blp.topMargin = Math.round(5 * vh);
        blp.rightMargin = px(3);
        root.addView(bar, blp);

        // Dock : 5 tuiles visibles
        tileW = px(15.5f);
        tileH = Math.round(tileW * 9 / 16f);
        gap = px(1.6f);
        pad = px(1.6f);
        dock = new DockView(this, 2.6f * vw);
        dock.setElevation(2 * vw);
        track = new FrameLayout(this);
        track.setClipChildren(false);
        FrameLayout.LayoutParams trp = new FrameLayout.LayoutParams(tileW, tileH);
        trp.leftMargin = pad;
        trp.topMargin = pad;
        dock.addView(track, trp);
        root.addView(dock, new FrameLayout.LayoutParams(0, 0));

        // Nom de l'appli sélectionnée, au-dessus du dock
        title = text(2.7f * vw, 300, 0, false);
        title.setShadowLayer(.8f * vw, 0, 2, 0x66000000);
        root.addView(title, wrap(Gravity.BOTTOM | Gravity.LEFT));
        layoutDock(VISIBLE);

        // Aide du mode édition, au-dessus du dock
        hint = buildHint();
        FrameLayout.LayoutParams hlp = wrap(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hlp.bottomMargin = Math.round(6 * vh) + tileH + px(5.5f);
        hint.setAlpha(0);
        hint.setTranslationY(vw);
        root.addView(hint, hlp);

        // Message bref au centre de l'écran
        toast = new TextView(this);
        toast.setTextSize(TypedValue.COMPLEX_UNIT_PX, 1.4f * vw);
        toast.setTypeface(font(400));
        toast.setTextColor(0xFF2C3A33);
        toast.setPadding(px(2), px(1), px(2), px(1));
        toast.setBackground(rounded(0xCCFFFFFF, vw, 0, 0));
        toast.setAlpha(0);
        toast.setElevation(11 * vw);
        FrameLayout.LayoutParams xlp = wrap(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        xlp.topMargin = Math.round(.48f * H - 2.2f * vw);
        root.addView(toast, xlp);

        prefs = new PrefsView(this, this, vw, store.animations);
        prefs.setVisibility(View.GONE);
        prefs.setElevation(10 * vw); // au-dessus du dock, qui a sa propre élévation
        root.addView(prefs, match());

        setContentView(root);
        showPhoto(photoForNow(), false);
        updateNight(false);
        tick();
        loadApps();
        hideSystemUi();
        Log.i(TAG, "onCreate " + (System.nanoTime() - t0) / 1000000 + " ms");
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        minuteTick.run();
        loadApps(); // applis installées ou retirées, et tri par usage mis à jour
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(minuteTick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra("heure")) {
            forcedHour = intent.getFloatExtra("heure", Float.NaN);
            updateNight(true);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
            // Sinon Android avale la première flèche pour quitter le mode tactile (ensureTouchMode)
            root.requestFocusFromTouch();
        }
    }

    /* ===== Télécommande ===== */

    private static boolean isOk(int k) {
        return k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private static boolean isArrow(int k) {
        return k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
                || k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    // Appui long sur OK : signal « touche maintenue » d'Android, ou à défaut 650 ms sans relâcher
    private final Runnable longOk = () -> {
        if (okLong) return;
        okLong = true;
        if (!onTop && !prefsOpen) setEditing(!editing);
    };

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        boolean back = k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE;

        if (prefsOpen) {
            if (back) {
                if (!down) closePrefs();
                return true;
            }
            if (isOk(k) || isArrow(k)) {
                if (down && e.getRepeatCount() == 0) prefs.onKey(k);
                if (!down && isOk(k)) okDown = false;
                return true;
            }
            return super.dispatchKeyEvent(e);
        }

        if (isOk(k)) {
            if (down) {
                if (e.getRepeatCount() > 0) {
                    if (okDown && !okLong) { ui.removeCallbacks(longOk); longOk.run(); }
                    return true;
                }
                if (okDown) return true;
                okDown = true;
                okLong = false;
                ui.postDelayed(longOk, 650);
            } else {
                ui.removeCallbacks(longOk);
                if (!okDown) return true;
                okDown = false;
                if (okLong) return true; // l'appui long a déjà basculé le mode édition
                if (onTop) {
                    if (topOnGear) openSettings();
                    else openPrefs();
                } else if (editing) setEditing(false);
                else launch();
            }
            return true;
        }
        if (back) {
            if (!down) onBack();
            return true;
        }
        if (!isArrow(k)) return super.dispatchKeyEvent(e);
        if (!down) return true;

        if (editing) {
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) moveApp(1);
            else if (k == KeyEvent.KEYCODE_DPAD_LEFT) moveApp(-1);
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN) toggleHidden();
            else togglePin();
        } else if (k == KeyEvent.KEYCODE_DPAD_UP) {
            if (!onTop) selectTop(true);
        } else if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (onTop) leaveTop();
        } else if (onTop) {
            selectTop(k == KeyEvent.KEYCODE_DPAD_RIGHT);
        } else {
            focusOn(focus + (k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1));
        }
        return true;
    }

    private void onBack() {
        if (editing) setEditing(false);
        else if (onTop) leaveTop();
        // sinon : écran d'accueil, le bouton retour ne fait rien
    }

    /* ===== Heure, date, salutation ; photo et nuit vérifiées à chaque minute ===== */

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            tick();
            showPhoto(photoForNow(), true);
            updateNight(true);
            ui.removeCallbacks(this);
            ui.postDelayed(this, 60000 - System.currentTimeMillis() % 60000 + 50);
        }
    };

    private String clockText() {
        Calendar n = Calendar.getInstance();
        return String.format(Locale.FRANCE, "%02d:%02d", n.get(Calendar.HOUR_OF_DAY), n.get(Calendar.MINUTE));
    }

    private void tick() {
        Calendar n = Calendar.getInstance();
        int h = n.get(Calendar.HOUR_OF_DAY);
        clock.setText(clockText());
        greet.setText(h < 5 || h >= 18 ? "Bonsoir" : h < 12 ? "Bonjour" : "Bon après-midi");
        date.setText(new SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(n.getTime()));
    }

    private Calendar now() {
        Calendar c = Calendar.getInstance();
        if (!Float.isNaN(forcedHour)) {
            c.set(Calendar.HOUR_OF_DAY, (int) Math.floor(forcedHour));
            c.set(Calendar.MINUTE, Math.round(forcedHour % 1 * 60));
        }
        return c;
    }

    /** Nuit = du coucher du soleil + 40 min au lever - 40 min, à Perpignan (formule NOAA, comme la version web). */
    static boolean isNight(Calendar c) {
        double rad = Math.PI / 180;
        int m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        double g = 2 * Math.PI / 365 * (c.get(Calendar.DAY_OF_YEAR) - 1);
        double eqt = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g)
                - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
        double decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g)
                + 0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
        double ha = Math.acos(Math.cos(90.833 * rad) / (Math.cos(LAT * rad) * Math.cos(decl))
                - Math.tan(LAT * rad) * Math.tan(decl)) / rad;
        double tz = c.getTimeZone().getOffset(c.getTimeInMillis()) / 60000.0;
        double noon = 720 - 4 * LON - eqt + tz;
        return m < noon - 4 * ha - 40 || m >= noon + 4 * ha + 40;
    }

    private void updateNight(boolean animate) {
        boolean n = isNight(now());
        if (night != null && night == n) return;
        night = n;
        vignette.animate().cancel();
        if (animate) vignette.animate().alpha(n ? 1 : 0).setDuration(3000);
        else vignette.setAlpha(n ? 1 : 0);
    }

    /* ===== Photo de fond, renouvelée toutes les 2 heures ===== */

    private static int photoForNow() {
        return (int) ((System.currentTimeMillis() / 7_200_000L) % PHOTO_COUNT);
    }

    private Bitmap decodeAsset(String path) {
        try (InputStream in = getAssets().open(path)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    private void showPhoto(final int idx, boolean animate) {
        if (idx == photoIdx) return;
        photoIdx = idx;
        if (!animate) { // premier affichage : tout de suite, sans fondu
            Bitmap b = decodeAsset("photos/" + (idx + 1) + ".jpg");
            photos[photoOn].setImageBitmap(b);
            photos[photoOn].setAlpha(1f);
            dock.setBackdrop(b);
            return;
        }
        worker.execute(() -> {
            final Bitmap b = decodeAsset("photos/" + (idx + 1) + ".jpg");
            ui.post(() -> {
                if (b == null || idx != photoIdx) return;
                final ImageView old = photos[photoOn];
                photoOn = 1 - photoOn;
                ImageView next = photos[photoOn];
                next.setImageBitmap(b);
                next.animate().alpha(1).setDuration(3000);
                old.animate().alpha(0).setDuration(3000).withEndAction(() -> old.setImageDrawable(null));
                dock.setBackdrop(b);
            });
        });
    }

    /* ===== Applis ===== */

    private void loadApps() {
        worker.execute(() -> {
            final List<App> fresh = queryApps();
            ui.post(() -> {
                allApps = fresh;
                if (editing) return; // jamais de réorganisation pendant l'édition
                List<App> want = visibleApps();
                if (keys(want).equals(keys(shown))) return;
                buildDock(focus < shown.size() ? shown.get(focus).pkg : null);
            });
        });
    }

    private static List<String> keys(List<App> list) {
        List<String> k = new ArrayList<>();
        for (App a : list) k.add(a.key());
        return k;
    }

    private List<App> queryApps() {
        final PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<App> list = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.startsWith("fr.accueil.")) continue; // les accueils eux-mêmes
            App a = new App();
            a.label = ri.loadLabel(pm).toString();
            a.pkg = pkg;
            a.cls = ri.activityInfo.name;
            Bitmap b = bannerCache.get(a.key());
            if (b == null) {
                b = banner(ri, pm);
                if (b != null) bannerCache.put(a.key(), b);
            }
            a.banner = b;
            list.add(a);
        }
        Collections.sort(list, (a, b) -> {
            int r = Integer.compare(rank(a.pkg), rank(b.pkg));
            return r != 0 ? r : a.label.compareToIgnoreCase(b.label);
        });
        return list;
    }

    private static int rank(String pkg) {
        if (pkg.equals(SETTINGS)) return Integer.MAX_VALUE;
        int i = PRIORITY.indexOf(pkg);
        return i >= 0 ? i : PRIORITY.size();
    }

    /** Bannière dessinée directement à la taille de la tuile (ou icône centrée sur fond blanc). */
    private Bitmap banner(ResolveInfo ri, PackageManager pm) {
        try {
            Drawable d = ri.activityInfo.loadBanner(pm);
            if (d == null) d = ri.activityInfo.applicationInfo.loadBanner(pm);
            boolean isIcon = d == null;
            if (isIcon) d = ri.loadIcon(pm);
            Bitmap bmp = Bitmap.createBitmap(tileW, tileH, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            if (isIcon) {
                c.drawColor(Color.WHITE);
                int s = Math.round(tileH * 2 / 3f);
                d.setBounds((tileW - s) / 2, (tileH - s) / 2, (tileW + s) / 2, (tileH + s) / 2);
            } else {
                d.setBounds(0, 0, tileW, tileH);
            }
            d.draw(c);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    /** Applis du dock : favoris puis usage ; les masquées n'apparaissent qu'en mode édition. */
    private List<App> visibleApps() {
        List<App> all = store.ordered(allApps);
        if (editing) return all;
        List<App> out = new ArrayList<>();
        for (App a : all) if (!store.hidden.contains(a.pkg)) out.add(a);
        return out.isEmpty() ? all : out; // jamais de dock vide
    }

    /** Taille du dock selon le nombre d'applis (5 au plus), et le titre aligné dessus. */
    private void layoutDock(int n) {
        n = Math.max(1, Math.min(n, VISIBLE));
        dockW = tileW * n + gap * (n - 1) + 2 * pad;
        dockH = tileH + 2 * pad;
        dockLeft = (W - dockW) / 2;
        dockTop = H - Math.round(6 * vh) - dockH;
        FrameLayout.LayoutParams dlp = (FrameLayout.LayoutParams) dock.getLayoutParams();
        if (dlp.width != dockW) {
            dlp.width = dockW;
            dlp.height = dockH;
            dlp.leftMargin = dockLeft;
            dlp.topMargin = dockTop;
            dock.setLayoutParams(dlp);
            FrameLayout.LayoutParams ttl = (FrameLayout.LayoutParams) title.getLayoutParams();
            ttl.leftMargin = dockLeft + px(1.6f);
            ttl.bottomMargin = H - dockTop + px(1);
            title.setLayoutParams(ttl);
        }
        dock.setScreenPosition(dockLeft, dockTop, dockW, dockH, W);
    }

    private void buildDock(String keepPkg) {
        shown = visibleApps();
        layoutDock(shown.size());
        track.removeAllViews();
        tiles.clear();
        int step = tileW + gap;
        for (int i = 0; i < shown.size(); i++) {
            App a = shown.get(i);
            TileView t = new TileView(this, a.banner, vw);
            t.setFlags(store.pins.contains(a.pkg), store.hidden.contains(a.pkg));
            t.setEditing(editing);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(tileW, tileH);
            lp.leftMargin = i * step;
            track.addView(t, lp);
            tiles.add(t);
        }
        ViewGroup.LayoutParams tp = track.getLayoutParams();
        tp.width = Math.max(tileW, shown.size() * step - gap);
        track.setLayoutParams(tp);
        if (tiles.isEmpty()) return;

        int f = -1;
        for (int i = 0; i < shown.size(); i++) if (shown.get(i).pkg.equals(keepPkg)) f = i;
        focus = f >= 0 ? f : Math.min(focus, tiles.size() - 1);
        offset = Math.min(offset, Math.max(0, tiles.size() - VISIBLE));
        if (focus < offset) offset = focus;
        if (focus > offset + VISIBLE - 1) offset = focus - VISIBLE + 1;
        track.animate().cancel();
        track.setTranslationX(-offset * step);
        tiles.get(focus).setSelected(!onTop, false, false);
        title.setText(shown.get(focus).label);
    }

    private void focusOn(int i) {
        if (tiles.isEmpty()) return;
        i = Math.max(0, Math.min(tiles.size() - 1, i));
        if (i == focus) return;
        tiles.get(focus).setSelected(false, true, false);
        focus = i;
        tiles.get(focus).setSelected(true, true, store.animations);
        if (focus < offset) offset = focus;
        if (focus > offset + VISIBLE - 1) offset = focus - VISIBLE + 1;
        track.animate().translationX(-offset * (tileW + gap)).setDuration(300).setInterpolator(EASE);
        showTitle(shown.get(focus).label);
    }

    private void showTitle(final String name) {
        title.animate().alpha(0).setDuration(100).withEndAction(() -> {
            title.setText(name);
            title.animate().alpha(editing ? 0 : 1).setDuration(150);
        });
    }

    private void launch() {
        if (focus >= shown.size()) return;
        App a = shown.get(focus);
        store.recordLaunch(a.pkg);
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setClassName(a.pkg, a.cls)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (Exception e) {
            Intent fallback = getPackageManager().getLeanbackLaunchIntentForPackage(a.pkg);
            if (fallback != null) startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    /* ===== Mode édition : appui long sur OK ===== */

    private void setEditing(boolean on) {
        String keep = focus < shown.size() ? shown.get(focus).pkg : null;
        editing = on;
        if (on && onTop) leaveTop();
        buildDock(keep);
        hint.animate().alpha(on ? 1 : 0).translationY(on ? 0 : vw).setDuration(on ? 500 : 400).setInterpolator(EASE);
        title.animate().alpha(on ? 0 : 1).setDuration(150);
    }

    /** Déplacer une appli l'épingle (elle quitte le tri automatique). */
    private void moveApp(int dir) {
        String p = shown.get(focus).pkg;
        List<String> pins = store.pins;
        int i = pins.indexOf(p);
        if (i < 0) {
            pins.add(p);
            i = pins.size() - 1;
            if (dir > 0) { store.savePins(); buildDock(p); return; }
        }
        int j = i + dir;
        if (j >= 0 && j < pins.size()) Collections.swap(pins, i, j);
        store.savePins();
        buildDock(p);
    }

    private void togglePin() {
        String p = shown.get(focus).pkg;
        if (!store.pins.remove(p)) store.pins.add(p);
        store.savePins();
        buildDock(p);
    }

    private void toggleHidden() {
        String p = shown.get(focus).pkg;
        if (!store.hidden.remove(p)) store.hidden.add(p);
        store.saveHidden();
        buildDock(p);
    }

    /* ===== Barre du haut ===== */

    private void selectTop(boolean gear) {
        if (!onTop && !tiles.isEmpty()) tiles.get(focus).setSelected(false, true, false);
        onTop = true;
        topOnGear = gear;
        gearBtn.setFocus(gear);
        prefsBtn.setFocus(!gear);
    }

    private void leaveTop() {
        onTop = false;
        gearBtn.setFocus(false);
        prefsBtn.setFocus(false);
        if (!tiles.isEmpty()) tiles.get(focus).setSelected(true, true, store.animations);
    }

    private void openSettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent().setClassName(SETTINGS, "com.android.tv.settings.MainSettings")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }
    }

    /* ===== Préférences ===== */

    private void openPrefs() {
        prefsOpen = true;
        gearBtn.setFocus(false);
        prefsBtn.setFocus(false);
        prefs.open("photo", decodeAsset("photos/mini/" + (photoIdx + 1) + ".jpg"), clockText());
        prefs.setVisibility(View.VISIBLE);
    }

    @Override
    public void closePrefs() {
        prefsOpen = false;
        prefs.setVisibility(View.GONE);
        selectTop(false);
    }

    @Override
    public void applyTheme(String id) {
        if (PrefsView.AVAILABLE.equals(id)) return;
        String name = PrefsView.NAMES[Arrays.asList(PrefsView.THEMES).indexOf(id)];
        showToast("« " + name + " » arrive bientôt dans la version native");
    }

    @Override
    public void setAnimations(boolean on) {
        store.animations = on;
        store.saveAnimations();
        prefs.setAnimations(on);
    }

    private void showToast(String msg) {
        toast.setText(msg);
        toast.animate().cancel();
        toast.setScaleX(.95f);
        toast.setScaleY(.95f);
        toast.animate().alpha(1).scaleX(1).scaleY(1).setStartDelay(0).setDuration(300).withEndAction(() ->
                toast.animate().alpha(0).setStartDelay(1400).setDuration(300));
    }

    /* ===== Éléments d'interface ===== */

    /** Bouton en pilule de la barre du haut : icône et libellé, contour blanc quand il est sélectionné. */
    private class PillButton extends LinearLayout {
        PillButton(int icon, String label) {
            super(MainActivity.this);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(px(1.2f), 0, px(1.2f), 0);
            ImageView iv = new ImageView(MainActivity.this);
            iv.setImageResource(icon);
            addView(iv, new LayoutParams(px(1.15f), px(1.15f)));
            TextView t = new TextView(MainActivity.this);
            t.setText(label);
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, .95f * vw);
            t.setTypeface(font(500));
            t.setLetterSpacing(.02f);
            t.setTextColor(Color.WHITE);
            t.setIncludeFontPadding(false);
            LayoutParams tlp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.leftMargin = px(.55f);
            addView(t, tlp);
            setFocus(false);
        }

        void setFocus(boolean on) {
            setBackground(rounded(on ? 0x42FFFFFF : 0x24FFFFFF, 99 * vw, on ? px(.14f) : 1, on ? 0xFFFFFFFF : 0x33FFFFFF));
            animate().alpha(on ? 1 : .9f).scaleX(on ? 1.06f : 1).scaleY(on ? 1.06f : 1).setDuration(350).setInterpolator(EASE);
        }
    }

    /** « ← → Déplacer · ↑ Épingler · ↓ Masquer · OK Terminer » */
    private LinearLayout buildHint() {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(px(1.6f), px(.7f), px(1.6f), px(.7f));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x8C3A3D44, 0x8C1A1C20});
        bg.setCornerRadius(99 * vw);
        bg.setStroke(1, 0x66FFFFFF);
        h.setBackground(bg);
        String[][] parts = {{"← →", "Déplacer"}, {"↑", "Épingler"}, {"↓", "Masquer"}, {"OK", "Terminer"}};
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                View dot = new View(this);
                dot.setBackground(rounded(0x66FFFFFF, vw, 0, 0));
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(.3f), px(.3f));
                dl.leftMargin = dl.rightMargin = px(.8f);
                h.addView(dot, dl);
            }
            TextView key = text(1.1f * vw, 600, 0, false);
            key.setText(parts[i][0]);
            key.setShadowLayer(0, 0, 0, 0);
            key.setPadding(px(.5f), px(.1f), px(.5f), px(.1f));
            key.setBackground(rounded(0x59FFFFFF, .4f * vw, 0, 0));
            h.addView(key);
            TextView lbl = text(1.1f * vw, 400, 0, false);
            lbl.setText(parts[i][1]);
            lbl.setShadowLayer(0, 0, 0, 0);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ll.leftMargin = px(.8f);
            h.addView(lbl, ll);
        }
        return h;
    }

    /* ===== Outils d'affichage ===== */

    /** Police Outfit (variable) au poids demandé ; police système si Android ne sait pas la lire. */
    @Override
    public Typeface font(int weight) {
        Typeface t = fonts.get(weight);
        if (t != null) return t;
        if (!outfitBroken) try {
            t = new Typeface.Builder(getAssets(), "fonts/outfit.woff2")
                    .setFontVariationSettings("'wght' " + weight).build();
        } catch (Exception e) {
            t = null;
        }
        if (t == null) {
            outfitBroken = true; // ne pas retenter pour chaque poids
            t = Build.VERSION.SDK_INT >= 28 ? Typeface.create(Typeface.SANS_SERIF, weight, false) : Typeface.SANS_SERIF;
        }
        fonts.put(weight, t);
        return t;
    }

    private int px(float v) {
        return Math.round(v * vw);
    }

    private static GradientDrawable rounded(int color, float radius, int strokeW, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (strokeW > 0) g.setStroke(strokeW, strokeColor);
        return g;
    }

    private TextView text(float sizePx, int weight, float letterSpacing, boolean caps) {
        TextView v = new TextView(this);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
        v.setTypeface(font(weight));
        v.setLetterSpacing(letterSpacing);
        v.setTextColor(Color.WHITE);
        v.setIncludeFontPadding(false);
        v.setSingleLine(true);
        v.setAllCaps(caps); // après setSingleLine, qui remplacerait la mise en majuscules
        v.setShadowLayer(.7f * vw, 0, 1, 0x59000000);
        return v;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static FrameLayout.LayoutParams wrap(int gravity) {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
    }

    private static LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
