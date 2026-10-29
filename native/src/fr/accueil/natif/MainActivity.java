package fr.accueil.natif;

import android.animation.TimeInterpolator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.media.AudioManager;
import android.os.SystemClock;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Accueil natif : décor selon le thème (SceneView), dock en carrousel (Modern, Ambient), en grille
 * (Bullit) ou en liste (Minimal), favoris puis tri par usage, mode édition (appui long sur OK),
 * barre du haut (Préférences, Paramètres), fenêtre Préférences, veille après 1 min et intro AxiomOS.
 * Changer de thème reconstruit l'écran (recreate), la fenêtre Préférences restant ouverte.
 * Tests : am start -n fr.accueil.natif/.MainActivity [--ef heure 21.5] [--ez intro true] [--ez veille true]
 */
public class MainActivity extends Activity implements PrefsView.Host, KidsDialog.Host {

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
    private static final int VISIBLE = 5;

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

    private Theme theme;
    private int W, H, tileW, tileH, gap, pad, dockW, dockH, dockLeft, dockTop;
    private int itemW, itemH, stepX, stepY, rowsVisible, padL, padT, padB; // grille et liste
    private FrameLayout area;      // zone de la grille ou de la liste
    private AmbientHero hero;      // vitrine d'Ambient
    private final Map<String, float[][]> palettes = new ConcurrentHashMap<>();
    // vitrines déjà composées (≈ 3 Mo chacune) : les 8 dernières applis
    private final Map<String, Bitmap> heroCache = Collections.synchronizedMap(new java.util.LinkedHashMap<String, Bitmap>(16, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) {
            return size() > 8;
        }
    });
    private float vw, vh;
    private SceneView scene;
    private IntroView intro;
    private LinearLayout bar;
    private TextView title, toast;
    private DockView dock;
    private FrameLayout track;
    private LinearLayout hint;
    private PillButton prefsBtn, gearBtn;
    private PrefsView prefs;

    private List<App> allApps = new ArrayList<>(); // toutes les applis, ordre du système
    private List<App> shown = new ArrayList<>();   // celles du dock, dans l'ordre affiché
    private final List<DockItem> tiles = new ArrayList<>();
    private int focus = 0, offset = 0;
    private boolean editing, onTop, prefsOpen, topOnGear = true;
    private boolean okDown, okLong, swallowUp;
    private boolean idle, saverPreview;
    private KidsDialog kidsDialog;   // code et menu parent (Kids)
    private boolean kidsPick;        // Kids : choix des applis autorisées
    private TextView guardBadge;     // Kids : avertissement « protection inactive »
    private static String pendingToast; // message à afficher après la reconstruction de l'écran
    private boolean hdmiConnected = true; // la télé reçoit-elle l'image ?
    private long lastIntro = 0;
    private FrameLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        long t0 = System.nanoTime();
        super.onCreate(savedInstanceState);
        store = new Store(this);
        store.unhideAllOnce();
        theme = Theme.of(store.theme);
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
        // Décor du thème : fond, salutation, heure, date et météo
        scene = new SceneView(this, W, H, theme, store);
        scene.forcedHour = getIntent().getFloatExtra("heure", Float.NaN);
        root.addView(scene, match());

        // Barre du haut : Préférences et Paramètres (flèche haut depuis le dock)
        bar = new LinearLayout(this);
        // Marge intérieure : le bouton sélectionné grossit (×1,06) et son contour ne doit pas être coupé
        int bp = px(.6f);
        bar.setPadding(bp, bp, bp, bp);
        bar.setClipToPadding(false); // sinon la marge intérieure sert de zone de découpe
        prefsBtn = new PillButton(R.drawable.ic_prefs, "Préférences");
        gearBtn = new PillButton(R.drawable.ic_gear, "Paramètres");
        int bh = theme == Theme.MINIMAL ? ViewGroup.LayoutParams.WRAP_CONTENT : px(2.5f);
        bar.addView(prefsBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, bh));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, bh);
        glp.leftMargin = px(1.2f);
        bar.addView(gearBtn, glp);
        FrameLayout.LayoutParams blp = wrap(Gravity.TOP | Gravity.RIGHT);
        blp.topMargin = Math.round(5 * vh) - bp;
        blp.rightMargin = px(3) - bp;
        root.addView(bar, blp);
        if (theme == Theme.KIDS) bar.setVisibility(View.GONE); // Kids : sortie seulement par l'espace parent

        tileW = px(15.5f);
        tileH = Math.round(tileW * 9 / 16f);
        gap = px(1.6f);
        pad = px(1.6f);
        title = text(2.7f * vw, 300, 0, false);
        title.setShadowLayer(.8f * vw, 0, 2, 0x66000000);
        if (theme == Theme.AMBIENT) { // vitrine : à droite, sous la barre du haut
            int hw = px(40), hh = Math.round(hw * 9 / 16f);
            hero = new AmbientHero(this, vw, hw, hh);
            FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(hero.imageWidth(), hero.imageHeight(), Gravity.TOP | Gravity.RIGHT);
            hl.rightMargin = px(7) - hero.margin; // l'image déborde de la carte de sa marge transparente
            hl.topMargin = Math.round(17 * vh) - hero.margin;
            root.addView(hero, hl);
        }
        if (theme.dock == Theme.CAROUSEL) {
            // Dock : 5 tuiles visibles, en verre (Modern : photo floutée derrière ; Ambient : teinte sombre)
            dock = new DockView(this, 2.6f * vw);
            if (theme == Theme.AMBIENT) dock.setTint(0x80121418);
            dock.setElevation(2 * vw);
            track = new FrameLayout(this);
            track.setClipChildren(false);
            FrameLayout.LayoutParams trp = new FrameLayout.LayoutParams(tileW, tileH);
            trp.leftMargin = pad;
            trp.topMargin = pad;
            dock.addView(track, trp);
            root.addView(dock, new FrameLayout.LayoutParams(0, 0));
            // Nom de l'appli sélectionnée, au-dessus du dock
            root.addView(title, wrap(Gravity.BOTTOM | Gravity.LEFT));
            layoutDock(VISIBLE);
        } else {
            buildArea(root);
        }

        // Aide du mode édition : au-dessus du dock, ou en haut pour la grille et la liste
        hint = buildHint();
        FrameLayout.LayoutParams hlp;
        if (theme == Theme.BULLIT || theme == Theme.KIDS) {
            hlp = wrap(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            hlp.topMargin = Math.round((theme == Theme.KIDS ? 3 : 6.5f) * vh);
        } else if (theme == Theme.MINIMAL) {
            hlp = wrap(Gravity.TOP | Gravity.LEFT);
            hlp.topMargin = Math.round(5 * vh);
            hlp.leftMargin = px(56);
        } else {
            hlp = wrap(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            hlp.bottomMargin = Math.round(6 * vh) + tileH + px(5.5f);
        }
        hint.setAlpha(0);
        hint.setTranslationY(vw);
        hint.setElevation(3 * vw);
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

        kidsDialog = new KidsDialog(this, this, vw);
        kidsDialog.setElevation(12 * vw); // au-dessus des Préférences (le code se choisit depuis elles)
        root.addView(kidsDialog, match());
        if (theme == Theme.KIDS) {
            guardBadge = text(.9f * vw, 600, 0, false);
            guardBadge.setShadowLayer(0, 0, 0, 0);
            guardBadge.setText("Protection Kids inactive · les boutons raccourcis ne sont pas bloqués");
            guardBadge.setTextColor(0xFF8A3B12);
            guardBadge.setPadding(px(.9f), px(.4f), px(.9f), px(.4f));
            guardBadge.setBackground(rounded(0xEBFFECD6, 2 * vw, 0, 0));
            guardBadge.setVisibility(View.GONE);
            FrameLayout.LayoutParams gl = wrap(Gravity.BOTTOM | Gravity.RIGHT);
            gl.rightMargin = px(2);
            gl.bottomMargin = Math.round(2 * vh);
            root.addView(guardBadge, gl);
        }

        // Intro AxiomOS, par-dessus tout
        intro = new IntroView(this, vw, this::wake);
        intro.setElevation(20 * vw);
        root.addView(intro, match());

        setContentView(root);
        if (theme == Theme.MODERN) scene.setPhotoListener(dock::setBackdrop);
        scene.setInkListener(this::onInk);
        scene.showNow();
        onInk(scene.ink(), scene.inkSoft(), scene.isDark());
        loadApps();
        syncGuard();
        if (pendingToast != null) {
            final String m = pendingToast;
            pendingToast = null;
            ui.postDelayed(() -> showToast(m), 600);
        }

        Intent in = getIntent();
        if (savedInstanceState != null && savedInstanceState.getBoolean("prefs")) { // après un changement de thème
            ui.post(() -> openPrefs(false));
        } else if (in.getBooleanExtra("veille", false)) setSaverPreview(true);
        else if (in.getBooleanExtra("intro", false) || (isDefaultHome() ? bootIntroDue() : savedInstanceState == null)) {
            // Accueil par défaut : une fois par démarrage de la box. Sinon (essais) : à chaque lancement à froid.
            intro.play(in.getIntExtra("introDelay", 500));
            lastIntro = SystemClock.elapsedRealtime();
        }
        // Intro quand la télé se rallume : seulement si cette appli est l'accueil par défaut,
        // pour ne jamais passer devant l'accueil web pendant les essais
        if (isDefaultHome()) {
            registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
            registerReceiver(hdmiPlug, new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG));
        }
        hideSystemUi();
        Log.i(TAG, "onCreate " + (System.nanoTime() - t0) / 1000000 + " ms");
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("prefs", prefsOpen);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        scene.start();
        loadApps(); // applis installées ou retirées, et tri par usage mis à jour
        if (!saverPreview) wake();
        checkGuard();
    }

    @Override
    protected void onPause() {
        scene.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(screenOn);
            unregisterReceiver(hdmiPlug);
        } catch (Exception ignored) {
        }
        ui.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        scene.release();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra("heure")) {
            scene.forcedHour = intent.getFloatExtra("heure", Float.NaN);
            scene.updateNight(true);
        }
        if (intent.getBooleanExtra("veille", false)) setSaverPreview(true);
        if (intent.getBooleanExtra("intro", false)) playIntro(intent.getIntExtra("introDelay", 0));
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
        if (theme == Theme.KIDS) {
            if (!kidsPick && !kidsDialog.isOpen()) kidsDialog.openCode("check", kidsDialog::openMenu);
        } else if (!onTop && !prefsOpen) setEditing(!editing);
    };

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        boolean back = k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE;
        if (!isOk(k) && !isArrow(k) && !back) return super.dispatchKeyEvent(e); // volume, etc.

        if (intro.isPlaying()) { // une touche passe l'intro
            if (down) intro.end(true);
            return true;
        }
        if (saverPreview) {
            if (!down) setSaverPreview(false);
            return true;
        }
        // En veille, la première touche réveille seulement l'accueil
        if (down && e.getRepeatCount() == 0 && wake()) {
            swallowUp = true;
            return true;
        }
        if (swallowUp) {
            if (!down) swallowUp = false;
            return true;
        }

        if (kidsDialog.isOpen()) {
            if (down && e.getRepeatCount() == 0) kidsDialog.onKey(k);
            if (!down) { // l'appui long qui a ouvert le code ne doit pas bloquer OK ensuite
                okDown = false;
                ui.removeCallbacks(longOk);
            }
            return true;
        }

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
                } else if (editing) {
                    if (theme != Theme.MINIMAL) setEditing(false); // Minimal : seul Retour termine
                } else if (kidsPick) toggleKidAllowed();
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

        if (editing && theme == Theme.MINIMAL) { // Minimal : ↑ ↓ déplacent, → épingle ou désépingle
            if (k == KeyEvent.KEYCODE_DPAD_UP) moveApp(-1);
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN) moveApp(1);
            else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) togglePin();
        } else if (editing && theme.dock == Theme.CAROUSEL) { // Modern, Ambient : ← → déplacent, ↑ épingle
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) moveApp(1);
            else if (k == KeyEvent.KEYCODE_DPAD_LEFT) moveApp(-1);
            else if (k == KeyEvent.KEYCODE_DPAD_UP) togglePin();
        } else if (editing) { // Bullit : les quatre flèches déplacent la tuile dans la grille
            int n = shown.size(), c = theme.columns, to = -1;
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) to = focus + 1;
            else if (k == KeyEvent.KEYCODE_DPAD_LEFT) to = focus - 1;
            else if (k == KeyEvent.KEYCODE_DPAD_UP) to = focus - c;
            else if (focus + c < n) to = focus + c;
            else if ((n - 1) / c > focus / c) to = n - 1; // rangée du dessous incomplète
            if (to >= 0 && to < n) moveTo(focus, to);
        } else if (theme.dock != Theme.CAROUSEL && !onTop && k == KeyEvent.KEYCODE_DPAD_UP && focus >= theme.columns) {
            focusOn(focus - theme.columns); // grille ou liste : rangée du dessus
        } else if (theme.dock != Theme.CAROUSEL && !onTop && k == KeyEvent.KEYCODE_DPAD_DOWN) {
            int c = theme.columns;
            if (focus / c < (tiles.size() - 1) / c) focusOn(Math.min(tiles.size() - 1, focus + c));
        } else if (k == KeyEvent.KEYCODE_DPAD_UP) {
            if (!onTop && theme != Theme.KIDS) selectTop(true);
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
        if (kidsPick) setKidsPick(false);
        else if (editing) setEditing(false);
        else if (onTop) leaveTop();
        // sinon : écran d'accueil, le bouton retour ne fait rien
    }

    /* ===== Veille : après une minute sans activité, il ne reste que l'heure sur la photo ===== */

    private static final int IDLE_MS = 60 * 1000;
    private final Runnable goIdle = () -> {
        if (theme.idle && !editing && !prefsOpen && !intro.isPlaying()) setIdle(true);
    };

    /** Relance le compte à rebours de la veille ; vrai si l'accueil était en veille. */
    private boolean wake() {
        boolean was = idle;
        if (idle) setIdle(false);
        ui.removeCallbacks(goIdle);
        ui.postDelayed(goIdle, IDLE_MS);
        return was;
    }

    private void setIdle(boolean on) {
        idle = on;
        dock.animate().alpha(on ? 0 : 1).translationY(on ? 3 * vw : 0).setDuration(1200).setInterpolator(EASE);
        if (hero != null) hero.animate().alpha(on ? 0 : 1).setDuration(1200);
        bar.animate().alpha(on ? 0 : 1).setDuration(600);
        title.animate().alpha(on || editing ? 0 : 1).setDuration(on ? 600 : 150);
        scene.setIdle(on, true);
    }

    /** Aperçu de l'économiseur d'écran (test : --ez veille true) ; une touche le referme. */
    private void setSaverPreview(boolean on) {
        if (!on) { // l'économiseur change la mise en page de l'heure : on reconstruit l'écran
            saverPreview = false;
            recreate();
            return;
        }
        saverPreview = true;
        ui.removeCallbacks(goIdle);
        for (View v : new View[]{dock, area, bar, title, hero, hint}) if (v != null) v.setVisibility(View.GONE);
        scene.setSaver();
    }

    /* ===== Intro AxiomOS ===== */

    private void playIntro(int delayMs) {
        if (prefsOpen) closePrefs();
        if (onTop) leaveTop();
        if (editing) setEditing(false);
        if (saverPreview) setSaverPreview(false);
        ui.removeCallbacks(goIdle);
        intro.play(delayMs);
    }

    /** Ramène l'accueil au premier plan et joue l'intro (pas deux fois en moins de 15 s). */
    private void introNow(int delayMs) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastIntro < 15000) return;
        lastIntro = now;
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra("intro", true)
                .putExtra("introDelay", delayMs));
    }

    // L'intro se joue quand la télé commence à recevoir l'image (allumage de la télé ou de la box)
    private final BroadcastReceiver hdmiPlug = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean connected = intent.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, 0) == 1;
            boolean wasConnected = hdmiConnected;
            hdmiConnected = connected;
            if (isInitialStickyBroadcast()) return;
            if (connected && !wasConnected) introNow(2000);
        }
    };

    // Sortie de veille sans coupure HDMI
    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (hdmiConnected) introNow(2500);
        }
    };

    private boolean isDefaultHome() {
        ResolveInfo r = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return r != null && r.activityInfo != null && getPackageName().equals(r.activityInfo.packageName);
    }

    /** Vrai une seule fois par démarrage de la box, dans les 10 minutes qui suivent. */
    private boolean bootIntroDue() {
        long bootMinute = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60000;
        SharedPreferences sp = getSharedPreferences("accueil", MODE_PRIVATE);
        if (Math.abs(sp.getLong("introBoot", -10) - bootMinute) <= 1) return false;
        if (SystemClock.elapsedRealtime() > 10 * 60 * 1000) return false;
        sp.edit().putLong("introBoot", bootMinute).apply();
        return true;
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
        // Kids : tuiles bien plus grandes, bannières dessinées à leur taille pour rester nettes
        int bw = theme == Theme.KIDS ? itemW : tileW, bh = theme == Theme.KIDS ? itemH : tileH;
        try {
            Drawable d = ri.activityInfo.loadBanner(pm);
            if (d == null) d = ri.activityInfo.applicationInfo.loadBanner(pm);
            boolean isIcon = d == null;
            if (isIcon) d = ri.loadIcon(pm);
            Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            if (isIcon) {
                c.drawColor(Color.WHITE);
                int s = Math.round(bh * 2 / 3f);
                d.setBounds((bw - s) / 2, (bh - s) / 2, (bw + s) / 2, (bh + s) / 2);
            } else {
                d.setBounds(0, 0, bw, bh);
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
        if (theme == Theme.KIDS && !kidsPick) { // Kids : seulement les applis autorisées
            List<App> out = new ArrayList<>();
            for (App a : all) if (store.kidsApps.contains(a.pkg)) out.add(a);
            return out;
        }
        return all; // plus de masquage d'applis, dans aucun thème
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

    /**
     * Grille (Bullit) : 6 colonnes de 17 % de la hauteur jusqu'en bas ; liste (Minimal) : à droite,
     * de 13 % à 94 % de la hauteur. Défilement vertical par rangée.
     */
    private void buildArea(FrameLayout root) {
        area = new FrameLayout(this);
        FrameLayout.LayoutParams al;
        int pl, pt, pr, pb;
        if (theme == Theme.KIDS) {
            // toute la largeur (marges de 9vw comprises) : les grandes ombres des tuiles ne sont pas coupées
            al = new FrameLayout.LayoutParams(W, H - Math.round(26 * vh));
            al.topMargin = Math.round(26 * vh);
            pl = pr = px(9 + 1.6f);
            pt = px(1.6f);
            pb = Math.round(4 * vh);
            int g = px(2.6f), inner = W - pl - pr;
            itemW = (inner - 2 * g) / 3;
            itemH = Math.round(itemW * 9 / 16f);
            stepX = itemW + g;
            stepY = itemH + g;
        } else if (theme == Theme.BULLIT) {
            al = new FrameLayout.LayoutParams(W - px(4), H - Math.round(17 * vh));
            al.leftMargin = px(2);
            al.topMargin = Math.round(17 * vh);
            pl = pr = pt = px(1.4f);
            pb = Math.round(3 * vh);
            int g = px(1.6f), inner = W - px(4) - pl - pr;
            itemW = (inner - 5 * g) / 6;
            itemH = Math.round(itemW * 9 / 16f);
            stepX = itemW + g;
            stepY = itemH + g;
        } else {
            al = new FrameLayout.LayoutParams(W - px(56) - px(5), H - Math.round(13 * vh) - Math.round(6 * vh));
            al.leftMargin = px(56);
            al.topMargin = Math.round(13 * vh);
            pl = pr = px(2);
            pt = pb = px(.6f);
            itemW = al.width - pl - pr - px(1.4f); // la ligne sélectionnée glisse de 1,4vw vers la droite
            itemH = px(4.4f);
            stepX = 0;
            stepY = itemH + px(.2f);
        }
        rowsVisible = Math.max(1, (int) ((al.height - .03f * H) / stepY));
        // La marge fait partie de la zone qui défile : une tuile du bord qui grossit (×1,06) reste
        // dans la zone, au lieu d'être coupée au bord de la grille.
        padL = pl;
        padT = pt;
        padB = pb;
        track = new FrameLayout(this);
        track.setClipChildren(false);
        area.addView(track, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(area, al);
    }

    private void buildDock(String keepPkg) {
        shown = visibleApps();
        if (theme.dock == Theme.CAROUSEL) layoutDock(shown.size());
        track.removeAllViews();
        tiles.clear();
        int cols = theme.columns;
        for (int i = 0; i < shown.size(); i++) {
            App a = shown.get(i);
            View v;
            FrameLayout.LayoutParams lp;
            if (theme.dock == Theme.LIST) {
                v = new ListRow(this, a.label, vw, scene.ink(), scene.inkSoft());
                lp = new FrameLayout.LayoutParams(itemW, itemH);
                lp.leftMargin = padL;
                lp.topMargin = padT + i * stepY;
            } else if (theme.dock == Theme.GRID) {
                TileView tv = new TileView(this, a.banner, vw, theme == Theme.KIDS ? TileView.KIDS : TileView.GRID);
                if (theme == Theme.KIDS) {
                    // inclinaison propre à chaque appli (stable), alternée pour éviter un effet uniforme
                    int hsh = a.pkg.hashCode();
                    float rnd = ((hsh >>> 1) % 1000) / 1000f;
                    tv.setTilt((i % 2 == 1 ? 1 : -1) * (1.2f + rnd * 2.8f), (((hsh >>> 10) % 100) / 100f * 1.2f - .6f) * vw);
                    tv.setKidState(kidsPick ? (store.kidsApps.contains(a.pkg) ? 1 : 2) : 0);
                }
                v = tv;
                lp = new FrameLayout.LayoutParams(itemW, itemH);
                lp.leftMargin = padL + (i % cols) * stepX;
                lp.topMargin = padT + (i / cols) * stepY;
            } else {
                v = new TileView(this, a.banner, vw, TileView.CAROUSEL);
                lp = new FrameLayout.LayoutParams(tileW, tileH);
                lp.leftMargin = i * (tileW + gap);
            }
            DockItem t = (DockItem) v;
            t.setFlags(theme != Theme.BULLIT && store.pins.contains(a.pkg), false); // Bullit : pas de pastille
            t.setEditing(editing);
            track.addView(v, lp);
            tiles.add(t);
        }
        ViewGroup.LayoutParams tp = track.getLayoutParams();
        if (theme.dock == Theme.CAROUSEL) {
            tp.width = Math.max(tileW, shown.size() * (tileW + gap) - gap);
        } else {
            // au moins toute la zone : les ombres des tuiles ne sont pas coupées au bas de la grille
            tp.height = Math.max(area.getLayoutParams().height,
                    padT + padB + Math.max(itemH, ((shown.size() + cols - 1) / cols) * stepY));
        }
        track.setLayoutParams(tp);
        if (tiles.isEmpty()) return;

        int f = -1;
        for (int i = 0; i < shown.size(); i++) if (shown.get(i).pkg.equals(keepPkg)) f = i;
        focus = f >= 0 ? f : Math.min(focus, tiles.size() - 1);
        track.animate().cancel();
        scrollTo(false);
        tiles.get(focus).setSelected(!onTop, false, false);
        title.setText(shown.get(focus).label);
        if (hero != null) ambientFor(focus, true);
    }

    /** Fait défiler le dock pour que l'élément sélectionné soit visible. */
    private void scrollTo(boolean animate) {
        if (theme.dock == Theme.CAROUSEL) {
            offset = Math.min(offset, Math.max(0, tiles.size() - VISIBLE));
            if (focus < offset) offset = focus;
            if (focus > offset + VISIBLE - 1) offset = focus - VISIBLE + 1;
            float x = -offset * (tileW + gap);
            if (animate) track.animate().translationX(x).setDuration(300).setInterpolator(EASE);
            else track.setTranslationX(x);
        } else {
            int row = focus / theme.columns;
            if (row < offset) offset = row;
            if (row > offset + rowsVisible - 1) offset = row - rowsVisible + 1;
            float y = -offset * stepY;
            if (animate) track.animate().translationY(y).setDuration(300).setInterpolator(EASE);
            else track.setTranslationY(y);
        }
    }

    private void focusOn(int i) {
        if (tiles.isEmpty()) return;
        i = Math.max(0, Math.min(tiles.size() - 1, i));
        if (i == focus) return;
        tiles.get(focus).setSelected(false, true, false);
        focus = i;
        tiles.get(focus).setSelected(true, true, store.animations);
        scrollTo(true);
        if (theme.title) showTitle(shown.get(focus).label);
        if (hero != null) ambientFor(focus, false);
    }

    /* ===== Ambient : couleurs et vitrine de l'appli sélectionnée ===== */

    private final Runnable ambientLater = () -> ambientFor(focus, true);

    /** Couleurs et vitrine ; attend 140 ms sans nouvelle touche (une touche maintenue ne recalcule rien). */
    private void ambientFor(int i, boolean now) {
        ui.removeCallbacks(ambientLater);
        if (!now) {
            ui.postDelayed(ambientLater, 140);
            return;
        }
        if (i >= shown.size()) return;
        final App a = shown.get(i);
        float[][] pal = palettes.get(a.pkg);
        if (pal == null) {
            pal = AmbientView.palette(a.banner);
            palettes.put(a.pkg, pal);
        }
        scene.ambient().show(a.pkg, pal, false);
        final int glow = AmbientView.hsl(pal[0][0], pal[0][1], .5f, 1);
        final boolean first = heroCache.isEmpty();
        Bitmap hi = heroCache.get(a.pkg);
        if (hi != null) {
            hero.show(hi, false);
            return;
        }
        worker.execute(() -> { // vitrine composée une fois par appli (carte, reflet, lueur, ombre)
            Bitmap v = null;
            try {
                Bitmap b = bannerHi(a);
                v = b == null ? null : hero.compose(b, glow);
            } catch (Throwable e) {
                Log.e(TAG, "vitrine " + a.pkg, e);
            }
            if (v != null) heroCache.put(a.pkg, v);
            final Bitmap img = v;
            ui.post(() -> { // la liste a pu être rechargée entre-temps : on compare les paquets
                if (focus < shown.size() && shown.get(focus).pkg.equals(a.pkg)) hero.show(img, first);
            });
        });
    }

    private Bitmap bannerHi(App a) {
        try {
            PackageManager pm = getPackageManager();
            Drawable d = pm.getActivityBanner(new android.content.ComponentName(a.pkg, a.cls));
            if (d == null) d = pm.getApplicationBanner(a.pkg);
            if (d == null) return null;
            int w = px(40), h = Math.round(w * 9 / 16f);
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, w, h);
            d.draw(new Canvas(bmp));
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    /** Minimal : couleurs du texte (clair ou sombre) répercutées sur la liste, la barre et l'aide. */
    private void onInk(int ink, int inkSoft, boolean dark) {
        if (theme != Theme.MINIMAL) return;
        for (DockItem t : tiles) ((ListRow) t).setInk(ink, inkSoft);
        if (prefsBtn != null) {
            prefsBtn.setInk(ink);
            gearBtn.setInk(ink);
        }
        if (hint != null) styleHint(hint, !dark);
        if (prefs != null) prefs.setLight(!dark);
    }

    private void showTitle(final String name) {
        title.animate().alpha(0).setDuration(100).withEndAction(() -> {
            title.setText(name);
            title.animate().alpha(editing || idle ? 0 : 1).setDuration(150);
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
        // Grille et liste : l'aide s'affiche en haut, à la place de la barre
        if (theme.dock != Theme.CAROUSEL) bar.animate().alpha(on ? 0 : 1).setDuration(300);
        if (theme.title) title.animate().alpha(on ? 0 : 1).setDuration(150);
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

    /**
     * Place l'appli `from` à la position `to` (grille Bullit). L'ordre du dock = favoris puis usage :
     * pour garder ce placement, les applis jusqu'à `to` (au moins) deviennent les favoris, dans l'ordre affiché.
     */
    private void moveTo(int from, int to) {
        List<String> order = new ArrayList<>();
        for (App a : shown) order.add(a.pkg);
        int pinned = 0;
        while (pinned < order.size() && store.pins.contains(order.get(pinned))) pinned++;
        String p = order.remove(from);
        order.add(to, p);
        store.pins.clear();
        store.pins.addAll(order.subList(0, Math.max(pinned, to + 1)));
        store.savePins();
        buildDock(p);
    }

    private void togglePin() {
        String p = shown.get(focus).pkg;
        if (!store.pins.remove(p)) store.pins.add(p);
        store.savePins();
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
        openPrefs(true);
    }

    private void openPrefs(boolean animate) {
        prefsOpen = true;
        onTop = false;
        gearBtn.setFocus(false);
        prefsBtn.setFocus(false);
        if (!tiles.isEmpty()) tiles.get(focus).setSelected(false, false, false);
        ui.removeCallbacks(goIdle);
        prefs.open(theme.id, scene.decodeAsset("photos/mini/" + (scene.photoIndex() + 1) + ".jpg"), SceneView.clockText());
        prefs.setLight(theme == Theme.MINIMAL && !scene.isDark());
        prefs.show(animate);
    }

    @Override
    public void closePrefs() {
        prefsOpen = false;
        prefs.hide();
        selectTop(false);
        wake();
    }

    @Override
    public void applyTheme(String id) {
        if (!Theme.available(id)) {
            String name = PrefsView.NAMES[Arrays.asList(PrefsView.THEMES).indexOf(id)];
            showToast("« " + name + " » arrive bientôt dans la version native");
            return;
        }
        if (id.equals(theme.id)) return;
        if ("kids".equals(id)) { // le mode Kids demande d'abord un code parent
            Runnable go = () -> {
                store.themePrev = theme.id;
                store.saveKids();
                prefsOpen = false; // pas de Préférences en Kids
                pendingToast = "Mode Kids activé · appui long sur OK pour l'espace parent";
                switchTheme("kids");
            };
            if (store.kidsCode == null) kidsDialog.openCode("set1", go);
            else go.run();
            return;
        }
        switchTheme(id);
    }

    /** Enregistre le thème et reconstruit l'écran (Préférences toujours ouvertes si elles l'étaient). */
    private void switchTheme(String id) {
        store.theme = id;
        store.saveTheme();
        recreate();
    }

    /* ===== Kids : espace parent, applis autorisées, gardien ===== */

    @Override
    public String kidsCode() {
        return store.kidsCode;
    }

    @Override
    public void saveKidsCode(String code) {
        store.kidsCode = code;
        store.saveKids();
    }

    @Override
    public void kidsMenu(int row) {
        if (row == 0) setKidsPick(true);
        else if (row == 1) kidsDialog.openCode("set1", null);
        else { // quitter le mode Kids : retour au thème d'avant
            String prev = store.themePrev;
            switchTheme(Theme.available(prev) && !"kids".equals(prev) ? prev : "photo");
        }
    }

    /** Choix des applis autorisées : toutes les applis s'affichent, OK autorise ou retire. */
    private void setKidsPick(boolean on) {
        kidsPick = on;
        String keep = focus < shown.size() ? shown.get(focus).pkg : null;
        buildDock(keep);
        hint.animate().alpha(on ? 1 : 0).translationY(on ? 0 : vw).setDuration(on ? 500 : 400).setInterpolator(EASE);
        scene.setGreetVisible(!on);
    }

    private void toggleKidAllowed() {
        if (focus >= shown.size()) return;
        String p = shown.get(focus).pkg;
        if (!store.kidsApps.remove(p)) store.kidsApps.add(p);
        store.saveKids();
        syncGuard();
        buildDock(p);
    }

    /** Gardien (service d'accessibilité) : actif seulement en Kids, avec la liste des applis autorisées. */
    private void syncGuard() {
        KidsGuard.set(this, theme == Theme.KIDS, new java.util.HashSet<>(store.kidsApps));
        checkGuard();
    }

    private final Runnable guardCheck = this::checkGuard;

    /** Kids : avertit les parents si le gardien ne tourne pas ; revérifié toutes les 30 s. */
    private void checkGuard() {
        if (guardBadge == null) return;
        guardBadge.setVisibility(KidsGuard.running ? View.GONE : View.VISIBLE);
        ui.removeCallbacks(guardCheck);
        ui.postDelayed(guardCheck, 30 * 1000);
    }

    @Override
    public String minMode() {
        return store.minMode;
    }

    @Override
    public void cycleMinMode(int dir) {
        String[] modes = {"auto", "light", "dark"};
        int i = Arrays.asList(modes).indexOf(store.minMode);
        store.minMode = modes[(i + dir + 3) % 3];
        store.saveMinMode();
        scene.applyInk(true);
    }

    @Override
    public void setAnimations(boolean on) {
        store.animations = on;
        store.saveAnimations();
        prefs.setAnimations(on);
    }

    @Override
    public void showToast(String msg) {
        toast.setText(msg);
        toast.animate().cancel();
        toast.setScaleX(.95f);
        toast.setScaleY(.95f);
        toast.animate().alpha(1).scaleX(1).scaleY(1).setStartDelay(0).setDuration(300).withEndAction(() ->
                toast.animate().alpha(0).setStartDelay(1400).setDuration(300));
    }

    /* ===== Éléments d'interface ===== */

    /**
     * Bouton de la barre du haut : pilule avec icône et libellé (contour blanc quand il est sélectionné) ;
     * en Minimal, simple libellé en capitales, souligné quand il est sélectionné.
     */
    private class PillButton extends LinearLayout {
        private final TextView label;
        private int ink = Color.WHITE;

        PillButton(int icon, String text) {
            super(MainActivity.this);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            boolean min = theme == Theme.MINIMAL;
            label = new TextView(MainActivity.this);
            label.setText(text);
            label.setTypeface(font(500));
            label.setTextColor(Color.WHITE);
            label.setIncludeFontPadding(false);
            if (min) {
                setPadding(px(.4f), px(.3f), px(.4f), px(.3f));
                label.setTextSize(TypedValue.COMPLEX_UNIT_PX, vw);
                label.setLetterSpacing(.18f);
                label.setAllCaps(true);
                addView(label);
            } else {
                setPadding(px(1.2f), 0, px(1.2f), 0);
                ImageView iv = new ImageView(MainActivity.this);
                iv.setImageResource(icon);
                addView(iv, new LayoutParams(px(1.15f), px(1.15f)));
                label.setTextSize(TypedValue.COMPLEX_UNIT_PX, .95f * vw);
                label.setLetterSpacing(.02f);
                LayoutParams tlp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tlp.leftMargin = px(.55f);
                addView(label, tlp);
            }
            setFocus(false);
        }

        void setInk(int color) {
            ink = color;
            label.setTextColor(color);
        }

        void setFocus(boolean on) {
            if (theme == Theme.MINIMAL) {
                label.setPaintFlags(on ? label.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG
                        : label.getPaintFlags() & ~android.graphics.Paint.UNDERLINE_TEXT_FLAG);
                animate().alpha(on ? 1 : .45f).setDuration(350);
                return;
            }
            boolean b = theme == Theme.BULLIT;
            int bg = on ? (b ? 0x3DFFFFFF : 0x42FFFFFF) : (b ? 0x1AFFFFFF : 0x24FFFFFF);
            setBackground(rounded(bg, 99 * vw, on ? px(.14f) : 1, on ? 0xFFFFFFFF : (b ? 0x29FFFFFF : 0x33FFFFFF)));
            animate().alpha(on ? 1 : .9f).scaleX(on ? 1.06f : 1).scaleY(on ? 1.06f : 1).setDuration(350).setInterpolator(EASE);
        }
    }

    /** « ← → Déplacer · ↑ Épingler · ↓ Masquer · OK Terminer » */
    private LinearLayout buildHint() {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(px(1.6f), px(.7f), px(1.6f), px(.7f));
        String[][] parts = theme == Theme.KIDS ? new String[][]{{"OK", "Autoriser / retirer"}, {"Retour", "Terminer"}}
                : theme == Theme.MINIMAL ? new String[][]{{"↑ ↓", "Déplacer"}, {"→", "Épingler"}, {"Retour", "Terminer"}}
                : theme == Theme.BULLIT ? new String[][]{{"← → ↑ ↓", "Déplacer"}, {"OK", "Valider"}}
                : new String[][]{{"← →", "Déplacer"}, {"↑", "Épingler"}, {"OK", "Terminer"}};
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
        styleHint(h, theme == Theme.MINIMAL && !scene.isDark());
        return h;
    }

    /** Aide sombre (texte blanc) ou claire (texte foncé, pour Minimal clair). */
    private void styleHint(LinearLayout h, boolean light) {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                light ? new int[]{0xE6FFFFFF, 0xCCFFFFFF} : new int[]{0x8C3A3D44, 0x8C1A1C20});
        bg.setCornerRadius(99 * vw);
        bg.setStroke(1, light ? 0x26151515 : 0x66FFFFFF);
        h.setBackground(bg);
        int fg = light ? 0xFF151515 : Color.WHITE;
        for (int i = 0; i < h.getChildCount(); i++) {
            View c = h.getChildAt(i);
            if (c instanceof TextView) {
                ((TextView) c).setTextColor(fg);
                if (c.getBackground() != null) c.setBackground(rounded(light ? 0x1A151515 : 0x59FFFFFF, .4f * vw, 0, 0));
            } else c.setBackground(rounded(light ? 0x66151515 : 0x66FFFFFF, vw, 0, 0));
        }
    }

    /* ===== Outils d'affichage ===== */

    @Override
    public Typeface font(int weight) {
        return SceneView.font(weight);
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
        return SceneView.text(this, sizePx, weight, letterSpacing, caps);
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static FrameLayout.LayoutParams wrap(int gravity) {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
