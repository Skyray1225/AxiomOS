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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Prototype natif de l'accueil, thème Modern seulement : photo de fond (une toutes les 2 h),
 * vignettage la nuit, salutation, heure et date, dock de 5 tuiles et nom de l'appli.
 * Test : am start -n fr.accueil.natif/.MainActivity [--ef heure 21.5]
 */
public class MainActivity extends Activity {

    private static final String TAG = "AccueilNatif";
    static final TimeInterpolator EASE = new PathInterpolator(.22f, .8f, .24f, 1f);

    // Ordre de préférence dans le dock ; les autres applis suivent par ordre alphabétique.
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

    private int W, H, tileW, tileH, gap, dockLeft, dockTop;
    private float vw, vh;
    private final ImageView[] photos = new ImageView[2];
    private int photoOn = 0, photoIdx = -1;
    private VignetteView vignette;
    private Boolean night;
    private TextView greet, clock, date, title;
    private DockView dock;
    private FrameLayout track;
    private List<App> apps = new ArrayList<>();
    private final List<TileView> tiles = new ArrayList<>();
    private int focus = 0, offset = 0;
    private float forcedHour = Float.NaN; // test : --ef heure 21.5

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        long t0 = System.nanoTime();
        super.onCreate(savedInstanceState);
        forcedHour = getIntent().getFloatExtra("heure", Float.NaN);
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(size);
        W = size.x;
        H = size.y;
        vw = W / 100f;
        vh = H / 100f;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF15181C);
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
        top.addView(greet, margins(Math.round(.2f * vw), 0, 0, Math.round(.4f * vh)));
        top.addView(clock, margins(0, 0, 0, 0));
        top.addView(date, margins(Math.round(.2f * vw), Math.round(.6f * vh), 0, 0));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = Math.round(4.5f * vw);
        tlp.topMargin = Math.round(7 * vh);
        root.addView(top, tlp);

        // Dock : 5 tuiles visibles
        tileW = Math.round(15.5f * vw);
        tileH = Math.round(tileW * 9 / 16f);
        gap = Math.round(1.6f * vw);
        int pad = Math.round(1.6f * vw);
        int dockW = tileW * VISIBLE + gap * (VISIBLE - 1) + 2 * pad, dockH = tileH + 2 * pad;
        dockLeft = (W - dockW) / 2;
        dockTop = H - Math.round(6 * vh) - dockH;
        dock = new DockView(this, 2.6f * vw);
        dock.setElevation(2 * vw);
        dock.setScreenPosition(dockLeft, dockTop, dockW, dockH, W);
        track = new FrameLayout(this);
        track.setClipChildren(false);
        FrameLayout.LayoutParams trp = new FrameLayout.LayoutParams(tileW, tileH);
        trp.leftMargin = pad;
        trp.topMargin = pad;
        dock.addView(track, trp);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dockW, dockH);
        dlp.leftMargin = dockLeft;
        dlp.topMargin = dockTop;
        root.addView(dock, dlp);

        // Nom de l'appli sélectionnée, au-dessus du dock
        title = text(2.7f * vw, 300, 0, false);
        title.setShadowLayer(.8f * vw, 0, 2, 0x66000000);
        FrameLayout.LayoutParams ttl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.LEFT);
        ttl.leftMargin = dockLeft + Math.round(1.6f * vw);
        ttl.bottomMargin = H - dockTop + Math.round(vw);
        root.addView(title, ttl);

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
        loadApps(); // nouvelles applis installées ou retirées pendant l'absence
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
        if (hasFocus) hideSystemUi();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (down) focusOn(focus - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) focusOn(focus + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (!down && focus < apps.size()) launch(apps.get(focus));
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true; // pas encore de barre du haut dans le prototype
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    @Override
    public void onBackPressed() {
        // Écran d'accueil : le bouton retour ne fait rien.
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

    private void tick() {
        Calendar n = Calendar.getInstance();
        int h = n.get(Calendar.HOUR_OF_DAY);
        clock.setText(String.format(Locale.FRANCE, "%02d:%02d", h, n.get(Calendar.MINUTE)));
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

    private Bitmap decodePhoto(int idx) {
        try (InputStream in = getAssets().open("photos/" + (idx + 1) + ".jpg")) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    private void showPhoto(final int idx, boolean animate) {
        if (idx == photoIdx) return;
        photoIdx = idx;
        if (!animate) { // premier affichage : tout de suite, sans fondu
            Bitmap b = decodePhoto(idx);
            photos[photoOn].setImageBitmap(b);
            photos[photoOn].setAlpha(1f);
            dock.setBackdrop(b);
            return;
        }
        worker.execute(() -> {
            final Bitmap b = decodePhoto(idx);
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
                if (keys(fresh).equals(keys(apps))) return;
                String keep = focus < apps.size() ? apps.get(focus).key() : null;
                apps = fresh;
                buildDock(keep);
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
            a.banner = banner(ri, pm);
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

    private void buildDock(String keepKey) {
        track.removeAllViews();
        tiles.clear();
        int step = tileW + gap;
        for (int i = 0; i < apps.size(); i++) {
            TileView t = new TileView(this, apps.get(i).banner, 1.4f * vw, .18f * vw, .5f * vw);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(tileW, tileH);
            lp.leftMargin = i * step;
            track.addView(t, lp);
            tiles.add(t);
        }
        ViewGroup.LayoutParams tp = track.getLayoutParams();
        tp.width = Math.max(tileW, apps.size() * step - gap);
        track.setLayoutParams(tp);

        int f = 0;
        for (int i = 0; i < apps.size(); i++) if (apps.get(i).key().equals(keepKey)) f = i;
        focus = f;
        offset = Math.max(0, Math.min(focus, apps.size() - VISIBLE));
        track.setTranslationX(-offset * step);
        if (!tiles.isEmpty()) {
            tiles.get(focus).setSelected(true, false);
            title.setText(apps.get(focus).label);
        }
    }

    private void focusOn(int i) {
        if (tiles.isEmpty()) return;
        i = Math.max(0, Math.min(tiles.size() - 1, i));
        if (i == focus) return;
        tiles.get(focus).setSelected(false, true);
        focus = i;
        tiles.get(focus).setSelected(true, true);
        if (focus < offset) offset = focus;
        if (focus > offset + VISIBLE - 1) offset = focus - VISIBLE + 1;
        track.animate().translationX(-offset * (tileW + gap)).setDuration(300).setInterpolator(EASE);
        showTitle(apps.get(focus).label);
    }

    private void showTitle(final String name) {
        title.animate().alpha(0).setDuration(100).withEndAction(() -> {
            title.setText(name);
            title.animate().alpha(1).setDuration(150);
        });
    }

    private void launch(App a) {
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

    /* ===== Outils d'affichage ===== */

    /** Police Outfit (variable) au poids demandé ; police système si Android ne sait pas la lire. */
    private Typeface font(int weight) {
        Typeface t = fonts.get(weight);
        if (t != null) return t;
        try {
            t = new Typeface.Builder(getAssets(), "fonts/outfit.woff2")
                    .setFontVariationSettings("'wght' " + weight).build();
        } catch (Exception e) {
            t = null;
        }
        if (t == null) {
            Log.w(TAG, "Outfit illisible, police système pour le poids " + weight);
            t = Build.VERSION.SDK_INT >= 28 ? Typeface.create(Typeface.SANS_SERIF, weight, false) : Typeface.SANS_SERIF;
        }
        fonts.put(weight, t);
        return t;
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
